[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$BaseUrl = "http://localhost:8090",

    [string]$AdminAccessToken = $env:VKP_ADMIN_ACCESS_TOKEN,

    [switch]$IncludeLegacySamples
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminAccessToken)) {
    throw "Thiếu access token quản trị. Truyền -AdminAccessToken hoặc đặt VKP_ADMIN_ACCESS_TOKEN."
}

$headers = @{
    Authorization = "Bearer $AdminAccessToken"
    Accept = "application/json"
}
$apiRoot = $BaseUrl.TrimEnd("/")
$legacySampleNames = @(
    "Da Lat Mong Mo 3N2D",
    "Da Nang 3N2D",
    "Hoi An 2N1D"
)

function Invoke-VkpRequest {
    param(
        [Parameter(Mandatory)][ValidateSet("GET", "POST", "DELETE")][string]$Method,
        [Parameter(Mandatory)][string]$Path
    )

    Invoke-RestMethod -Method $Method -Uri "$apiRoot$Path" -Headers $headers
}

$adminResponse = Invoke-VkpRequest -Method GET -Path "/v1/admin/tours"
$adminTours = @($adminResponse | ForEach-Object { $_ })
Write-Verbose "Đã tải $($adminTours.Count) tour từ API quản trị."
$targets = @(foreach ($tour in $adminTours) {
    if ($tour.status -eq "ACTIVE") {
        $isSmokeTour = $tour.name -match '^\[SMOKE \d{8}-\d{6}\]\s'
        $isLegacySample = $IncludeLegacySamples -and $legacySampleNames -ccontains $tour.name
        if ($isSmokeTour -or $isLegacySample) {
            $tour
        }
    }
})

if ($targets.Count -eq 0) {
    Write-Host "Không có tour test ACTIVE phù hợp với phạm vi dọn dữ liệu."
    return
}

$deactivatedCount = 0
foreach ($tour in $targets) {
    if ($PSCmdlet.ShouldProcess("$($tour.name) [$($tour.id)]", "Soft-deactivate tour test")) {
        Invoke-VkpRequest -Method DELETE -Path "/v1/admin/tours/$($tour.id)" | Out-Null
        $deactivatedCount++
        Write-Host "DEACTIVATED $($tour.id) $($tour.name)"
    }
}

if ($deactivatedCount -gt 0 -and $PSCmdlet.ShouldProcess("Elasticsearch tours index", "Reindex after cleanup")) {
    Invoke-VkpRequest -Method POST -Path "/v1/admin/tours/reindex" | Out-Null
    Write-Host "REINDEX REQUESTED"
}

Write-Host "Hoàn tất: đã soft-deactivate $deactivatedCount/$($targets.Count) tour trong phạm vi."
