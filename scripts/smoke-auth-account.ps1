[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8090",
    [string]$Email,
    [string]$Password = "Vkp-Rc-2026!",
    [string]$AuthContainer = "jai-auth-service",
    [ValidateRange(5, 300)][int]$ReadinessTimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"

function Invoke-RcApi {
    param(
        [Parameter(Mandatory)][ValidateSet("GET", "POST", "PATCH")][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body,
        [Parameter(Mandatory)][int[]]$ExpectedStatus
    )

    $request = @{
        Uri     = "$BaseUrl$Path"
        Method  = $Method
        Headers = $Headers
    }
    $parameters = (Get-Command Invoke-WebRequest).Parameters
    if ($parameters.ContainsKey("SkipHttpErrorCheck")) {
        $request.SkipHttpErrorCheck = $true
    }
    if ($parameters.ContainsKey("UseBasicParsing")) {
        $request.UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $request.ContentType = "application/json; charset=utf-8"
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $request.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }

    try {
        $response = Invoke-WebRequest @request
    }
    catch {
        $httpResponse = $_.Exception.Response
        if ($null -eq $httpResponse) {
            throw
        }

        $content = ""
        $stream = $httpResponse.GetResponseStream()
        if ($null -ne $stream) {
            $reader = [System.IO.StreamReader]::new($stream)
            try {
                $content = $reader.ReadToEnd()
            }
            finally {
                $reader.Dispose()
            }
        }
        $response = [pscustomobject]@{
            StatusCode = [int]$httpResponse.StatusCode
            Content    = $content
        }
    }

    $responseContent = $response.Content
    if ($responseContent -is [byte[]]) {
        $responseContent = [Text.Encoding]::UTF8.GetString($responseContent)
    }
    if ($ExpectedStatus -notcontains [int]$response.StatusCode) {
        throw "$Method $Path expected HTTP $($ExpectedStatus -join '/') but received $($response.StatusCode). Body: $responseContent"
    }

    $parsedBody = $null
    if (-not [string]::IsNullOrWhiteSpace($responseContent)) {
        $parsedBody = $responseContent | ConvertFrom-Json
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

function Wait-ForAuthReadiness {
    param([int]$TimeoutSeconds)

    $checks = @(
        @{ Name = "API Gateway"; Path = "/actuator/health" },
        @{ Name = "Auth Service through Gateway"; Path = "/v1/auth/ping" }
    )

    foreach ($check in $checks) {
        $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
        $lastError = "chưa nhận được phản hồi"
        do {
            try {
                $health = Invoke-RcApi -Method GET -Path $check.Path -ExpectedStatus 200
                if ($health.Body.status -eq "UP") {
                    Write-Host "READY $($check.Name)"
                    $lastError = $null
                    break
                }
                $lastError = "trạng thái '$($health.Body.status)'"
            }
            catch {
                $lastError = $_.Exception.Message
            }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)

        if ($null -ne $lastError) {
            throw "$($check.Name) chưa sẵn sàng sau $TimeoutSeconds giây. Phản hồi cuối: $lastError"
        }
    }
}

function Assert-LocalOtpLogMode {
    $environment = @(& docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $AuthContainer 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể đọc cấu hình container '$AuthContainer'. Hãy chạy local stack bằng Docker Compose."
    }

    $emailSetting = $environment | Where-Object { $_ -match '^EMAIL_ENABLED=' } | Select-Object -First 1
    if ($emailSetting -ne "EMAIL_ENABLED=false") {
        throw "Smoke auth chỉ tự đọc OTP khi $AuthContainer chạy với EMAIL_ENABLED=false."
    }
}

function Wait-ForDevOtp {
    param(
        [Parameter(Mandatory)][string]$RegisteredEmail,
        [Parameter(Mandatory)][DateTimeOffset]$Since,
        [int]$TimeoutSeconds = 15
    )

    $pattern = [regex]::Escape($RegisteredEmail) + ':\s*(?<otp>\d{6})'
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $logs = (& docker logs --since $Since.UtcDateTime.ToString("o") $AuthContainer 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0) {
            throw "Không thể đọc log container '$AuthContainer' để lấy OTP local."
        }
        $match = [regex]::Match($logs, $pattern)
        if ($match.Success) {
            return $match.Groups["otp"].Value
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    throw "Không tìm thấy OTP local cho tài khoản vừa đăng ký trong $TimeoutSeconds giây."
}

function Wait-ForUserProfile {
    param(
        [Parameter(Mandatory)][hashtable]$Headers,
        [int]$TimeoutSeconds = 20
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = "profile chưa được tạo"
    do {
        try {
            return Invoke-RcApi -Method GET -Path "/v1/users/me" -Headers $Headers -ExpectedStatus 200
        }
        catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    throw "User Service không tạo hồ sơ từ sự kiện Kafka trong $TimeoutSeconds giây. Phản hồi cuối: $lastError"
}

if ([string]::IsNullOrWhiteSpace($Email)) {
    $Email = "rc.$([guid]::NewGuid().ToString('N'))@example.invalid"
}

$registeredAt = [DateTimeOffset]::UtcNow
$otpCode = $null
$accessToken = $null
$refreshToken = $null
$rotatedAccessToken = $null
$rotatedRefreshToken = $null

try {
    Write-Host "[1/8] Waiting for local release-candidate stack"
    Wait-ForAuthReadiness -TimeoutSeconds $ReadinessTimeoutSeconds
    Assert-LocalOtpLogMode

    Write-Host "[2/8] Registering a unique customer account"
    $registration = Invoke-RcApi -Method POST -Path "/v1/auth/register" -ExpectedStatus 201 -Body @{
        email    = $Email
        password = $Password
        fullName = "Khách kiểm định RC"
    }
    Assert-Equal $registration.Body.status "UNVERIFIED" "New account status is incorrect"

    Write-Host "[3/8] Verifying the real OTP flow in local email-disabled mode"
    $otpCode = Wait-ForDevOtp -RegisteredEmail $Email -Since $registeredAt
    $tokens = Invoke-RcApi -Method POST -Path "/v1/auth/verify-otp" -ExpectedStatus 200 -Body @{
        userId  = $registration.Body.userId
        otpCode = $otpCode
    }
    $accessToken = $tokens.Body.accessToken
    $refreshToken = $tokens.Body.refreshToken
    if ([string]::IsNullOrWhiteSpace($accessToken) -or [string]::IsNullOrWhiteSpace($refreshToken)) {
        throw "OTP verification did not issue both access and refresh tokens."
    }
    $headers = @{ Authorization = "Bearer $accessToken" }

    Write-Host "[4/8] Reading and updating authenticated identity"
    $identity = Invoke-RcApi -Method GET -Path "/v1/auth/me" -Headers $headers -ExpectedStatus 200
    Assert-Equal $identity.Body.email $Email "Authenticated identity email is incorrect"
    Assert-Equal $identity.Body.status "ACTIVE" "Verified account is not ACTIVE"
    $updatedIdentity = Invoke-RcApi -Method PATCH -Path "/v1/auth/me" -Headers $headers -ExpectedStatus 200 -Body @{
        fullName = "Khách RC Việt Nam"
    }
    Assert-Equal $updatedIdentity.Body.fullName "Khách RC Việt Nam" "Identity update was not persisted"

    Write-Host "[5/8] Waiting for Kafka profile projection and updating preferences"
    $profile = Wait-ForUserProfile -Headers $headers
    Assert-Equal $profile.Body.userId $registration.Body.userId "User profile belongs to another account"
    $updatedProfile = Invoke-RcApi -Method PATCH -Path "/v1/users/me" -Headers $headers -ExpectedStatus 200 -Body @{
        phone          = "0912345678"
        avatarUrl      = "https://cdn.example.invalid/avatars/release-candidate.png"
        preferenceTags = @(
            @{ tagCode = "VAN_HOA"; weight = 1.0 },
            @{ tagCode = "AM_THUC"; weight = 0.8 }
        )
    }
    Assert-Equal $updatedProfile.Body.phone "0912345678" "Profile phone was not persisted"
    Assert-Equal @($updatedProfile.Body.preferenceTags).Count 2 "Profile preferences were not persisted"

    Write-Host "[6/8] Rotating the refresh token"
    $rotated = Invoke-RcApi -Method POST -Path "/v1/auth/refresh" -ExpectedStatus 200 -Body @{
        refreshToken = $refreshToken
    }
    $rotatedAccessToken = $rotated.Body.accessToken
    $rotatedRefreshToken = $rotated.Body.refreshToken
    $rotatedHeaders = @{ Authorization = "Bearer $rotatedAccessToken" }
    Invoke-RcApi -Method GET -Path "/v1/auth/me" -Headers $rotatedHeaders -ExpectedStatus 200 | Out-Null

    Write-Host "[7/8] Logging out and enforcing access-token revocation at Gateway"
    Invoke-RcApi -Method POST -Path "/v1/auth/logout" -Headers $rotatedHeaders -ExpectedStatus 204 -Body @{
        refreshToken = $rotatedRefreshToken
    } | Out-Null
    Invoke-RcApi -Method GET -Path "/v1/auth/me" -Headers $rotatedHeaders -ExpectedStatus 401 | Out-Null

    Write-Host "[8/8] Rejecting the revoked refresh token"
    Invoke-RcApi -Method POST -Path "/v1/auth/refresh" -ExpectedStatus 401 -Body @{
        refreshToken = $rotatedRefreshToken
    } | Out-Null

    Write-Host ""
    Write-Host "Auth & Account smoke test passed." -ForegroundColor Green
    [pscustomobject]@{
        UserId           = $registration.Body.userId
        Email            = $Email
        TestDataRetained = $true
    } | Format-List
}
finally {
    $otpCode = $null
    $accessToken = $null
    $refreshToken = $null
    $rotatedAccessToken = $null
    $rotatedRefreshToken = $null
}
