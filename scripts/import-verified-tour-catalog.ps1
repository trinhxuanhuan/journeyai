[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^https?://')]
    [string]$BaseUrl,

    [string]$AdminAccessToken = $env:VKP_ADMIN_ACCESS_TOKEN,

    [string]$CatalogPath = (Join-Path $PSScriptRoot '..\catalog\verified-tour-catalog.v1.json'),

    [switch]$PublishDepartures,

    [string]$GuideMapPath
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($AdminAccessToken)) {
    throw 'Thiếu access token quản trị. Truyền -AdminAccessToken hoặc đặt VKP_ADMIN_ACCESS_TOKEN.'
}

$resolvedCatalogPath = (Resolve-Path -LiteralPath $CatalogPath).Path
$catalog = Get-Content -LiteralPath $resolvedCatalogPath -Raw | ConvertFrom-Json
if ($catalog.schemaVersion -ne 1) {
    throw "Không hỗ trợ catalog schemaVersion=$($catalog.schemaVersion)."
}

$headers = @{
    Authorization = "Bearer $AdminAccessToken"
    Accept = 'application/json'
}
$apiRoot = $BaseUrl.TrimEnd('/')

function Invoke-VkpJson {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('GET', 'POST', 'PUT')][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body
    )

    $request = @{
        Method = $Method
        Uri = "$apiRoot$Path"
        Headers = $headers
    }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json; charset=utf-8'
        $request.Body = $Body | ConvertTo-Json -Depth 40 -Compress
    }
    Invoke-RestMethod @request
}

$guideMap = $null
if ($PublishDepartures) {
    if ([string]::IsNullOrWhiteSpace($GuideMapPath)) {
        throw '-PublishDepartures yêu cầu -GuideMapPath để phân công HDV thật theo regionKey.'
    }
    $guideMap = Get-Content -LiteralPath (Resolve-Path -LiteralPath $GuideMapPath).Path -Raw | ConvertFrom-Json -AsHashtable
}

$adminTours = @(Invoke-VkpJson -Method GET -Path '/v1/admin/tours')
$imported = @()

foreach ($item in $catalog.items) {
    $matches = @($adminTours | Where-Object { $_.name -ceq $item.tour.name })
    if ($matches.Count -gt 1) {
        throw "Có nhiều tour trùng tên '$($item.tour.name)'. Dừng để tránh cập nhật nhầm dữ liệu."
    }

    if ($matches.Count -eq 1 -and $matches[0].status -eq 'INACTIVE') {
        throw "Tour '$($item.tour.name)' đang INACTIVE và API hiện không có thao tác kích hoạt lại. Hãy xử lý thủ công trước khi nhập."
    }

    $tourId = $null
    if ($matches.Count -eq 0) {
        if ($PSCmdlet.ShouldProcess($item.tour.name, 'Tạo tour đã kiểm chứng')) {
            $created = Invoke-VkpJson -Method POST -Path '/v1/admin/tours' -Body $item.tour
            $tourId = $created.id
            $adminTours += $created
            Write-Host "CREATED $($item.code) -> $tourId"
        }
    } else {
        $tourId = $matches[0].id
        if ($PSCmdlet.ShouldProcess($item.tour.name, "Cập nhật tour $tourId")) {
            $updated = Invoke-VkpJson -Method PUT -Path "/v1/admin/tours/$tourId" -Body $item.tour
            Write-Host "UPDATED $($item.code) -> $tourId"
        }
    }

    if ($null -ne $tourId) {
        $imported += [pscustomobject]@{ Code = $item.code; TourId = $tourId; Tour = $item.tour; Operations = $item.operations }
    }
}

if ($PSCmdlet.ShouldProcess('Elasticsearch tours index', 'Reindex catalog')) {
    Invoke-VkpJson -Method POST -Path '/v1/admin/tours/reindex' | Out-Null
    Write-Host 'REINDEX REQUESTED'
}

if ($PublishDepartures) {
    foreach ($entry in $imported | Where-Object { $null -ne $_.Operations }) {
        $operation = $entry.Operations
        if ($operation.publicationStatus -ne 'DRAFT') {
            throw "Trạng thái lịch của $($entry.Code) không hợp lệ: $($operation.publicationStatus)."
        }
        $guideId = $guideMap[$operation.regionKey]
        if ([string]::IsNullOrWhiteSpace($guideId)) {
            throw "Guide map thiếu HDV thật cho regionKey '$($operation.regionKey)'."
        }

        $existing = @(Invoke-VkpJson -Method GET -Path "/v1/admin/tours/$($entry.TourId)/departures")
        $candidate = [datetime]::ParseExact($operation.firstDepartureOnOrAfter, 'yyyy-MM-dd', $null)
        while ($candidate.DayOfWeek.ToString().ToUpperInvariant() -ne $operation.dayOfWeek) {
            $candidate = $candidate.AddDays(1)
        }

        for ($index = 0; $index -lt $operation.occurrences; $index++) {
            $startDate = $candidate.AddDays(7 * $operation.intervalWeeks * $index)
            $startText = $startDate.ToString('yyyy-MM-dd')
            if ($existing.startDate -contains $startText) {
                Write-Host "SKIPPED EXISTING $($entry.Code) $startText"
                continue
            }

            $body = @{
                startDate = $startText
                endDate = $startDate.AddDays($entry.Tour.durationDays - 1).ToString('yyyy-MM-dd')
                capacity = $operation.capacity
                guideId = $guideId
                priceOverride = $null
            }
            if ($PSCmdlet.ShouldProcess("$($entry.Code) $startText", "Mở Departure với HDV $guideId")) {
                Invoke-VkpJson -Method POST -Path "/v1/admin/tours/$($entry.TourId)/departures" -Body $body | Out-Null
                Write-Host "DEPARTURE CREATED $($entry.Code) $startText"
            }
        }
    }
}

Write-Host "Hoàn tất import catalog verifiedAt=$($catalog.verifiedAt)."
