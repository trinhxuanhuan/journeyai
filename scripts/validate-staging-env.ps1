[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$EnvFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
$values = @{}

foreach ($line in Get-Content -LiteralPath $resolvedEnvFile) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith("#")) {
        continue
    }

    $separatorIndex = $trimmed.IndexOf("=")
    if ($separatorIndex -le 0) {
        throw "Dòng cấu hình không hợp lệ trong file staging: '$trimmed'"
    }

    $name = $trimmed.Substring(0, $separatorIndex).Trim()
    $value = $trimmed.Substring($separatorIndex + 1).Trim().Trim('"').Trim("'")
    $values[$name] = $value
}

$requiredVariables = @(
    "JWT_SIGNING_SECRET",
    "POSTGRES_USER",
    "POSTGRES_PASSWORD",
    "MONGO_USERNAME",
    "MONGO_PASSWORD",
    "TOUR_MONGO_URI",
    "AI_MONGO_URI",
    "INFRA_BIND_HOST",
    "GATEWAY_BIND_HOST",
    "CONTAINER_REGISTRY",
    "CONTAINER_IMAGE_OWNER",
    "RELEASE_VERSION",
    "FRONTEND_BASE_URL",
    "CORS_ALLOWED_ORIGIN",
    "CORS_ALLOWED_ORIGIN_ALT",
    "EMAIL_ENABLED",
    "SMTP_HOST",
    "SMTP_PORT",
    "SMTP_USERNAME",
    "SMTP_PASSWORD",
    "SMTP_FROM",
    "GEMINI_API_KEY",
    "VNPAY_TMN_CODE",
    "VNPAY_HASH_SECRET",
    "VNPAY_RETURN_URL"
)

foreach ($name in $requiredVariables) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Thiếu biến staging bắt buộc: $name"
    }

    if ($values[$name] -match "(?i)REPLACE_ME|CHANGE_ME|journeyai_dev_password") {
        throw "Biến $name vẫn đang dùng placeholder hoặc thông tin local không an toàn."
    }
}

if ($values["JWT_SIGNING_SECRET"].Length -lt 32) {
    throw "JWT_SIGNING_SECRET phải có ít nhất 32 ký tự ngẫu nhiên."
}

foreach ($name in @("POSTGRES_PASSWORD", "MONGO_PASSWORD", "SMTP_PASSWORD", "VNPAY_HASH_SECRET")) {
    if ($values[$name].Length -lt 16) {
        throw "$name phải có ít nhất 16 ký tự trên staging."
    }
}

if ($values["EMAIL_ENABLED"] -ne "true") {
    throw "EMAIL_ENABLED phải là true trên staging để người dùng nhận được OTP."
}

if ($values["CONTAINER_REGISTRY"] -ne "ghcr.io") {
    throw "CONTAINER_REGISTRY phải là ghcr.io trong release MVP."
}

if ($values["CONTAINER_IMAGE_OWNER"] -notmatch "^[a-z0-9]+(?:[.-][a-z0-9]+)*$") {
    throw "CONTAINER_IMAGE_OWNER phải là GitHub owner viết thường hợp lệ."
}

if ($values["RELEASE_VERSION"] -notmatch "^(?:v[0-9]+\.[0-9]+\.[0-9]+(?:-rc\.[0-9]+)?|sha-[0-9a-f]{12})$") {
    throw "RELEASE_VERSION phải là SemVer release hoặc sha- kèm 12 ký tự commit."
}

foreach ($name in @("INFRA_BIND_HOST", "GATEWAY_BIND_HOST")) {
    if ($values[$name] -ne "127.0.0.1") {
        throw "$name phải là 127.0.0.1 trên staging để chỉ reverse proxy truy cập được."
    }
}

function Assert-HttpsUrl {
    param(
        [string]$Name,
        [string]$Value
    )

    $parsed = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$parsed) -or $parsed.Scheme -ne "https") {
        throw "$Name phải là URL HTTPS tuyệt đối."
    }

    if ($parsed.IsLoopback) {
        throw "$Name không được trỏ về localhost trên staging."
    }

    return $parsed
}

$frontendUrl = Assert-HttpsUrl -Name "FRONTEND_BASE_URL" -Value $values["FRONTEND_BASE_URL"]
$corsUrl = Assert-HttpsUrl -Name "CORS_ALLOWED_ORIGIN" -Value $values["CORS_ALLOWED_ORIGIN"]
$alternateCorsUrl = Assert-HttpsUrl -Name "CORS_ALLOWED_ORIGIN_ALT" -Value $values["CORS_ALLOWED_ORIGIN_ALT"]
$vnpayReturnUrl = Assert-HttpsUrl -Name "VNPAY_RETURN_URL" -Value $values["VNPAY_RETURN_URL"]

if ($frontendUrl.AbsolutePath -ne "/" -or $frontendUrl.Query -or $frontendUrl.Fragment) {
    throw "FRONTEND_BASE_URL phải là origin, không chứa path, query hoặc fragment."
}

if ($values["CORS_ALLOWED_ORIGIN"].TrimEnd("/") -ne $corsUrl.GetLeftPart([UriPartial]::Authority)) {
    throw "CORS_ALLOWED_ORIGIN phải chỉ chứa origin, không chứa path."
}

if ($values["CORS_ALLOWED_ORIGIN_ALT"].TrimEnd("/") -ne $alternateCorsUrl.GetLeftPart([UriPartial]::Authority)) {
    throw "CORS_ALLOWED_ORIGIN_ALT phải chỉ chứa origin, không chứa path."
}

if ($frontendUrl.GetLeftPart([UriPartial]::Authority) -ne $corsUrl.GetLeftPart([UriPartial]::Authority)) {
    throw "CORS_ALLOWED_ORIGIN phải trùng origin của FRONTEND_BASE_URL."
}

if ($frontendUrl.GetLeftPart([UriPartial]::Authority) -ne $alternateCorsUrl.GetLeftPart([UriPartial]::Authority)) {
    throw "CORS_ALLOWED_ORIGIN_ALT phải trùng origin của FRONTEND_BASE_URL trên staging."
}

if ($vnpayReturnUrl.AbsolutePath -ne "/v1/payments/vnpay-return") {
    throw "VNPAY_RETURN_URL phải kết thúc bằng /v1/payments/vnpay-return."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
docker compose `
    --project-directory $repoRoot `
    --env-file $resolvedEnvFile `
    -f (Join-Path $repoRoot "docker-compose.yml") `
    -f (Join-Path $repoRoot "docker-compose.release.yml") `
    config --quiet
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose không thể render cấu hình staging."
}

Write-Host "Staging preflight đạt: đủ biến bắt buộc, URL HTTPS hợp lệ và Compose render thành công."
