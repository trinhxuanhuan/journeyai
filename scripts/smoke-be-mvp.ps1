[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8090",
    [string]$JwtSecret = $env:JWT_SIGNING_SECRET
)

$ErrorActionPreference = "Stop"

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)

    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-SmokeJwt {
    param(
        [Parameter(Mandatory)][string]$Subject,
        [Parameter(Mandatory)][ValidateSet("ADMIN", "CUSTOMER")][string]$Role,
        [Parameter(Mandatory)][string]$Secret
    )

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = @{ alg = "HS256"; typ = "JWT" } | ConvertTo-Json -Compress
    $payloadJson = @{
        sub  = $Subject
        role = $Role
        iat  = $now
        exp  = $now + 3600
    } | ConvertTo-Json -Compress

    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsignedToken = "$header.$payload"
    $hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsignedToken)))
    }
    finally {
        $hmac.Dispose()
    }

    return "$unsignedToken.$signature"
}

function Invoke-MvpApi {
    param(
        [Parameter(Mandatory)][ValidateSet("GET", "POST", "PUT", "PATCH", "DELETE")][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body,
        [Parameter(Mandatory)][int[]]$ExpectedStatus
    )

    $request = @{
        Uri                = "$BaseUrl$Path"
        Method             = $Method
        Headers            = $Headers
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) {
        $request.ContentType = "application/json; charset=utf-8"
        $request.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }

    $response = Invoke-WebRequest @request
    if ($ExpectedStatus -notcontains [int]$response.StatusCode) {
        throw "$Method $Path expected HTTP $($ExpectedStatus -join '/') but received $($response.StatusCode). Body: $($response.Content)"
    }

    $parsedBody = $null
    if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
        $parsedBody = $response.Content | ConvertFrom-Json
    }
    return [pscustomobject]@{
        StatusCode = [int]$response.StatusCode
        Body       = $parsedBody
    }
}

function Assert-Equal {
    param(
        [Parameter(Mandatory)]$Actual,
        [Parameter(Mandatory)]$Expected,
        [Parameter(Mandatory)][string]$Message
    )

    if ($Actual -ne $Expected) {
        throw "$Message. Expected '$Expected', received '$Actual'."
    }
}

if ([string]::IsNullOrWhiteSpace($JwtSecret)) {
    $envFile = Join-Path $PSScriptRoot "../.env"
    if (Test-Path $envFile) {
        $secretLine = Get-Content $envFile | Where-Object { $_ -match '^JWT_SIGNING_SECRET=' } | Select-Object -First 1
        if ($secretLine) {
            $JwtSecret = $secretLine.Substring("JWT_SIGNING_SECRET=".Length).Trim()
        }
    }
}
if ([string]::IsNullOrWhiteSpace($JwtSecret)) {
    throw "JWT signing secret is required. Set JWT_SIGNING_SECRET or pass -JwtSecret."
}

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$adminId = [guid]::NewGuid().ToString()
$customerId = [guid]::NewGuid().ToString()
$secondCustomerId = [guid]::NewGuid().ToString()
$adminHeaders = @{ Authorization = "Bearer $(New-SmokeJwt -Subject $adminId -Role ADMIN -Secret $JwtSecret)" }
$customerHeaders = @{ Authorization = "Bearer $(New-SmokeJwt -Subject $customerId -Role CUSTOMER -Secret $JwtSecret)" }
$secondCustomerHeaders = @{ Authorization = "Bearer $(New-SmokeJwt -Subject $secondCustomerId -Role CUSTOMER -Secret $JwtSecret)" }

Write-Host "[1/9] Checking gateway and AI health"
$health = Invoke-MvpApi -Method GET -Path "/v1/ai/ping" -ExpectedStatus 200
Assert-Equal $health.Body.status "UP" "AI service is not healthy"

Write-Host "[2/9] Creating an active tour guide"
$guide = Invoke-MvpApi -Method POST -Path "/v1/admin/tour-guides" -Headers $adminHeaders -ExpectedStatus 201 -Body @{
    fullName          = "HDV Smoke $runId"
    bio               = "Dữ liệu kiểm thử tự động cho BE MVP"
    yearsOfExperience = 8
}
$guideId = $guide.Body.id

$commonPackage = @{
    accommodation = @("Khách sạn tiêu chuẩn 3 sao, phòng đôi")
    transport     = @("Xe du lịch đời mới theo chương trình")
    meals         = @("Các bữa ăn ghi rõ trong lịch trình")
    tickets       = @("Vé tham quan theo chương trình")
    insurance     = @("Bảo hiểm du lịch nội địa")
}
$cancellationPolicy = @(
    @{ minimumDaysBeforeDeparture = 15; refundPercentage = 100 },
    @{ minimumDaysBeforeDeparture = 7; refundPercentage = 50 },
    @{ minimumDaysBeforeDeparture = 0; refundPercentage = 0 }
)
$itinerary = @(
    @{
        dayNumber = 1
        title = "TP.HCM - Huế"
        activities = @(
            @{ time = "05:30"; description = "Tập trung và khởi hành" },
            @{ time = "18:00"; description = "Nhận phòng và khám phá ẩm thực địa phương" }
        )
    },
    @{
        dayNumber = 2
        title = "Di sản Cố đô"
        activities = @(
            @{ time = "08:00"; description = "Tham quan Đại Nội" },
            @{ time = "14:00"; description = "Trải nghiệm văn hóa làng nghề" }
        )
    },
    @{
        dayNumber = 3
        title = "Huế - TP.HCM"
        activities = @(
            @{ time = "08:00"; description = "Tham quan tự do" },
            @{ time = "13:00"; description = "Khởi hành về điểm đón ban đầu" }
        )
    }
)

Write-Host "[3/9] Creating GROUP and PRIVATE tour packages"
$groupTour = Invoke-MvpApi -Method POST -Path "/v1/admin/tours" -Headers $adminHeaders -ExpectedStatus 201 -Body @{
    name                   = "[SMOKE $runId] TP.HCM - Huế 3N2Đ"
    description            = "Tour ghép trọn gói quảng bá di sản và văn hóa Huế."
    destination            = @{ province = "Thừa Thiên Huế"; geo = @{ lat = 16.4637; lng = 107.5909 } }
    basePrice              = 2800000
    tourType               = "GROUP"
    priceModel             = "PER_PERSON"
    departureLocation      = "TP.HCM"
    meetingPoint           = "Nhà Văn hóa Thanh Niên, Quận 1"
    meetingTime            = "05:30"
    minGroupSize           = 1
    maxGroupSize           = 30
    guideMode              = "INCLUDED"
    durationDays           = 3
    durationNights         = 2
    included               = @("Xe du lịch", "Khách sạn", "Bữa ăn", "Vé tham quan", "HDV", "Bảo hiểm")
    excluded               = @("Chi tiêu cá nhân", "Thuế VAT")
    packageDetails         = $commonPackage
    childPolicy            = @{ description = "Trẻ em tính 70% giá người lớn"; pricePercentage = 70 }
    singleRoomSupplement   = 500000
    cancellationPolicy     = $cancellationPolicy
    itinerary              = $itinerary
}
Assert-Equal $groupTour.Body.tourType "GROUP" "GROUP tour type was not persisted"
Assert-Equal $groupTour.Body.departureLocation "TP.HCM" "Departure location was not persisted"

$privateTour = Invoke-MvpApi -Method POST -Path "/v1/admin/tours" -Headers $adminHeaders -ExpectedStatus 201 -Body @{
    name                   = "[SMOKE $runId] Huế riêng 3N2Đ"
    description            = "Tour riêng cho một nhóm khách, không ghép đoàn."
    destination            = @{ province = "Thừa Thiên Huế"; geo = @{ lat = 16.4637; lng = 107.5909 } }
    basePrice              = 12000000
    tourType               = "PRIVATE"
    priceModel             = "PER_GROUP"
    departureLocation      = "TP.HCM"
    meetingPoint           = "Đón tại địa chỉ trong nội thành TP.HCM"
    meetingTime            = "06:00"
    minGroupSize           = 2
    maxGroupSize           = 8
    guideMode              = "OPTIONAL"
    optionalGuidePrice     = 1500000
    durationDays           = 3
    durationNights         = 2
    included               = @("Xe riêng", "Khách sạn", "Bữa ăn", "Vé tham quan", "Bảo hiểm")
    excluded               = @("HDV nếu khách không chọn", "Chi tiêu cá nhân")
    packageDetails         = $commonPackage
    childPolicy            = @{ description = "Giá theo nhóm, không tách giá trẻ em"; pricePercentage = 100 }
    singleRoomSupplement   = 800000
    cancellationPolicy     = $cancellationPolicy
    itinerary              = $itinerary
}
Assert-Equal $privateTour.Body.priceModel "PER_GROUP" "PRIVATE price model was not persisted"
Assert-Equal $privateTour.Body.guideMode "OPTIONAL" "PRIVATE guide mode was not persisted"

Write-Host "[4/9] Creating and exposing a GROUP departure"
$startDate = [DateOnly]::FromDateTime((Get-Date).AddDays(30)).ToString("yyyy-MM-dd")
$endDate = [DateOnly]::FromDateTime((Get-Date).AddDays(32)).ToString("yyyy-MM-dd")
$departure = Invoke-MvpApi -Method POST -Path "/v1/admin/tours/$($groupTour.Body.id)/departures" -Headers $adminHeaders -ExpectedStatus 201 -Body @{
    startDate     = $startDate
    endDate       = $endDate
    capacity      = 3
    guideId       = $guideId
    priceOverride = 3000000
}
Assert-Equal $departure.Body.availableSeats 3 "Departure availability is incorrect"
Assert-Equal $departure.Body.status "OPEN" "Departure was not created OPEN"

$publicDepartures = Invoke-MvpApi -Method GET -Path "/v1/tours/$($groupTour.Body.id)/departures" -ExpectedStatus 200
if (@($publicDepartures.Body).Count -ne 1) {
    throw "Public Departure API did not expose the created departure."
}

Write-Host "[5/9] Booking GROUP, replaying idempotently, and checking capacity"
$groupBookingBody = @{
    departureId    = $departure.Body.departureId
    singleRoomCount = 1
    participants   = @(
        @{ fullName = "Nguyễn Văn A"; phone = "0900000001"; primaryContact = $true; participantType = "ADULT" },
        @{ fullName = "Nguyễn Bé An"; phone = ""; primaryContact = $false; participantType = "CHILD" }
    )
}
$groupKey = "smoke-group-$runId"
$groupHeaders = $customerHeaders.Clone()
$groupHeaders["Idempotency-Key"] = $groupKey
$groupBooking = Invoke-MvpApi -Method POST -Path "/v1/bookings" -Headers $groupHeaders -Body $groupBookingBody -ExpectedStatus 201
$groupReplay = Invoke-MvpApi -Method POST -Path "/v1/bookings" -Headers $groupHeaders -Body $groupBookingBody -ExpectedStatus 200
Assert-Equal $groupReplay.Body.bookingId $groupBooking.Body.bookingId "Booking idempotency replay returned another booking"
Assert-Equal ([decimal]$groupBooking.Body.totalAmount) ([decimal]5600000) "GROUP price calculation is incorrect"

$overflowHeaders = $secondCustomerHeaders.Clone()
$overflowHeaders["Idempotency-Key"] = "smoke-overflow-$runId"
$overflow = Invoke-MvpApi -Method POST -Path "/v1/bookings" -Headers $overflowHeaders -ExpectedStatus 409 -Body @{
    departureId = $departure.Body.departureId
    participants = @(
        @{ fullName = "Khách B1"; phone = "0900000002"; primaryContact = $true; participantType = "ADULT" },
        @{ fullName = "Khách B2"; phone = ""; primaryContact = $false; participantType = "ADULT" }
    )
}
Assert-Equal $overflow.Body.error "SLOT_UNAVAILABLE" "Departure capacity conflict returned an unexpected error"

Write-Host "[6/9] Booking PRIVATE without shared capacity and assigning its guide"
$privateBookingBody = @{
    tourId               = $privateTour.Body.id
    requestedStartDate   = $startDate
    guideOptionSelected  = $true
    singleRoomCount      = 1
    participants         = @(
        @{ fullName = "Trần Minh Anh"; phone = "0910000001"; primaryContact = $true; participantType = "ADULT" },
        @{ fullName = "Lê Hoàng Nam"; phone = "0910000002"; primaryContact = $false; participantType = "ADULT" },
        @{ fullName = "Trần Gia Hân"; phone = ""; primaryContact = $false; participantType = "CHILD" }
    )
}
$privateHeaders = $customerHeaders.Clone()
$privateHeaders["Idempotency-Key"] = "smoke-private-$runId"
$privateBooking = Invoke-MvpApi -Method POST -Path "/v1/bookings" -Headers $privateHeaders -Body $privateBookingBody -ExpectedStatus 201
Assert-Equal ([decimal]$privateBooking.Body.totalAmount) ([decimal]14300000) "PRIVATE group price calculation is incorrect"
$assignedPrivate = Invoke-MvpApi -Method PATCH -Path "/v1/admin/bookings/$($privateBooking.Body.bookingId)/guide" -Headers $adminHeaders -ExpectedStatus 200 -Body @{ guideId = $guideId }
Assert-Equal $assignedPrivate.Body.bookingType "PRIVATE" "PRIVATE booking type was not derived by the backend"
Assert-Equal $assignedPrivate.Body.assignedGuideId $guideId "PRIVATE guide was not assigned to the booking"
if ($null -ne $assignedPrivate.Body.departureId) {
    throw "PRIVATE booking unexpectedly reserved a shared departure."
}

Write-Host "[7/9] Initiating payment, replaying idempotently, and reading status"
$paymentHeaders = $customerHeaders.Clone()
$paymentHeaders["Idempotency-Key"] = "smoke-payment-$runId"
$paymentBody = @{ bookingId = $privateBooking.Body.bookingId; gateway = "VNPAY" }
$payment = Invoke-MvpApi -Method POST -Path "/v1/payments" -Headers $paymentHeaders -Body $paymentBody -ExpectedStatus 201
$paymentReplay = Invoke-MvpApi -Method POST -Path "/v1/payments" -Headers $paymentHeaders -Body $paymentBody -ExpectedStatus 200
Assert-Equal $paymentReplay.Body.paymentId $payment.Body.paymentId "Payment idempotency replay returned another payment"
$paymentStatus = Invoke-MvpApi -Method GET -Path "/v1/payments/$($payment.Body.paymentId)" -Headers $customerHeaders -ExpectedStatus 200
Assert-Equal $paymentStatus.Body.status "INITIATED" "Payment did not remain safely in INITIATED state"
Assert-Equal ([decimal]$paymentStatus.Body.amount) ([decimal]14300000) "Payment did not use the booking price snapshot"

Write-Host "[8/9] Creating, listing, reading, and sharing an independent AI itinerary"
$ai = Invoke-MvpApi -Method POST -Path "/v1/ai/itineraries" -Headers $customerHeaders -ExpectedStatus 201 -Body @{
    destination   = "Huế"
    days          = 3
    budget        = 6000000
    travelerCount = 2
    groupProfile  = "FAMILY"
    preferences   = @("di sản", "ẩm thực", "làng nghề")
}
$aiList = Invoke-MvpApi -Method GET -Path "/v1/ai/itineraries/me?page=0&size=20" -Headers $customerHeaders -ExpectedStatus 200
if (-not (@($aiList.Body.items).id -contains $ai.Body.id)) {
    throw "Created AI itinerary is missing from the customer list."
}
$aiDetail = Invoke-MvpApi -Method GET -Path "/v1/ai/itineraries/$($ai.Body.id)" -Headers $customerHeaders -ExpectedStatus 200
Assert-Equal $aiDetail.Body.destination "Huế" "AI itinerary detail is incorrect"
$aiRefined = Invoke-MvpApi -Method POST -Path "/v1/ai/itineraries/$($ai.Body.id)/refine" -Headers $customerHeaders -ExpectedStatus 200 -Body @{
    instruction      = "Làm ngày 2 nhẹ hơn và giảm ngân sách xuống 5 triệu"
    lockedDayNumbers = @(1)
}
Assert-Equal $aiRefined.Body.revision 2 "AI itinerary revision was not incremented"
Assert-Equal @($aiRefined.Body.itineraryDays)[1].pace "RELAXED" "AI itinerary did not refine the requested day"
Assert-Equal @($aiRefined.Body.itineraryDays)[0].title @($ai.Body.itineraryDays)[0].title "AI itinerary changed a locked day"
$share = Invoke-MvpApi -Method POST -Path "/v1/ai/itineraries/$($ai.Body.id)/share" -Headers $customerHeaders -ExpectedStatus 200
$shared = Invoke-MvpApi -Method GET -Path $share.Body.sharePath -ExpectedStatus 200
Assert-Equal $shared.Body.id $ai.Body.id "Public share did not return the same AI itinerary"
if ($null -ne $shared.Body.userId) {
    throw "Public AI itinerary leaked its owner identifier."
}
if ($null -ne $shared.Body.refinementHistory) {
    throw "Public AI itinerary leaked its refinement instructions."
}

Write-Host "[9/9] Verifying booking snapshots and final availability"
$groupDetail = Invoke-MvpApi -Method GET -Path "/v1/bookings/$($groupBooking.Body.bookingId)" -Headers $customerHeaders -ExpectedStatus 200
Assert-Equal $groupDetail.Body.bookingType "GROUP" "GROUP booking type is incorrect"
Assert-Equal $groupDetail.Body.priceModel "PER_PERSON" "GROUP booking price model is incorrect"
if ([string]::IsNullOrWhiteSpace($groupDetail.Body.commercialSnapshot)) {
    throw "GROUP booking is missing its commercial snapshot."
}
$finalDeparture = Invoke-MvpApi -Method GET -Path "/v1/tours/$($groupTour.Body.id)/departures" -ExpectedStatus 200
Assert-Equal @($finalDeparture.Body)[0].availableSeats 1 "Departure did not reserve exactly two seats"

Write-Host ""
Write-Host "BE MVP smoke test passed." -ForegroundColor Green
[pscustomobject]@{
    RunId            = $runId
    GroupTourId      = $groupTour.Body.id
    PrivateTourId    = $privateTour.Body.id
    DepartureId      = $departure.Body.departureId
    GroupBookingId   = $groupBooking.Body.bookingId
    PrivateBookingId = $privateBooking.Body.bookingId
    PaymentId        = $payment.Body.paymentId
    AiItineraryId    = $ai.Body.id
} | Format-List
