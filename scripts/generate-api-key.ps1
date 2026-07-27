<#
.SYNOPSIS
    Generate an Immich API key scoped to the 4 permissions Immich Media Frame needs.
    
.DESCRIPTION
    Creates or updates an "Immich Media Frame" API key on your Immich server with the exact
    permissions required by the Immich Media Frame Android app:
      - album.read   (list albums)
      - asset.read   (asset metadata)
      - asset.view   (view thumbnails & previews)
      - asset.download (download originals for video playback)
      - user.read    (validate key via /users/me)
      
    Requires Immich v1.135+ for scoped API keys.
    
.PARAMETER ServerUrl
    Base URL of your Immich server (e.g. https://photos.example.com:2283)
    
.PARAMETER Email
    Your Immich account email.
    
.PARAMETER Password
    Your Immich password. If omitted, you'll be prompted securely.

.EXAMPLE
    .\generate-api-key.ps1 https://photos.example.com user@example.com
    
.EXAMPLE
    .\generate-api-key.ps1 https://photos.example.com user@example.com "mypassword"
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true, Position=0)]
    [ValidateNotNullOrEmpty()]
    [string]$ServerUrl,
    
    [Parameter(Mandatory=$true, Position=1)]
    [ValidateNotNullOrEmpty()]
    [string]$Email,
    
    [Parameter(Mandatory=$false, Position=2)]
    [string]$Password
)

$KEY_NAME = "ImmichMediaFrame"
$REQUIRED_PERMS = @("album.read", "asset.read", "asset.view", "asset.download", "user.read")
$ServerUrl = $ServerUrl.TrimEnd('/')

function Write-Info { param([string]$msg) Write-Host "ℹ $msg" -ForegroundColor Cyan }
function Write-Success { param([string]$msg) Write-Host "✓ $msg" -ForegroundColor Green }
function Write-Warn { param([string]$msg) Write-Host "⚠ $msg" -ForegroundColor Yellow }
function Write-ErrorMsg { param([string]$msg) Write-Host "✗ $msg" -ForegroundColor Red }
function Write-Step { param([string]$msg) Write-Host "▸ $msg" -ForegroundColor Cyan }

function Read-Password {
    $secure = Read-Host -AsSecureString "Password"
    [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    )
}

function Invoke-ImmichRequest {
    param(
        [string]$Url,
        [string]$Method = "GET",
        [string]$Body,
        [string]$Token,
        [string]$ApiKey
    )
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($ApiKey) { $headers["x-api-key"] = $ApiKey }
    
    try {
        $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -Body $Body -ErrorAction Stop
        return $response
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $body = if ($_.Exception.Response) { 
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $reader.ReadToEnd() 
        } else { $_.Exception.Message }
        throw [PSCustomObject]@{ StatusCode = $statusCode; Body = $body }
    }
}

try {
    Write-Info "Immich Media Frame API Key Generator"
    Write-Info "Server: $ServerUrl"
    Write-Info "Account: $Email"
    Write-Host ""
    
    # Step 1: Login
    Write-Step "Logging in to $ServerUrl as $Email..."
    $loginRes = Invoke-ImmichRequest -Url "$ServerUrl/api/auth/login" -Method POST `
        -Body (ConvertTo-Json @{ email = $Email; password = $(if ($Password) { $Password } else { Read-Password }) })
    $token = $loginRes.accessToken
    if (-not $token) { throw "Login succeeded but no access token returned" }
    Write-Success "Login successful"
    
    # Step 2: Check for existing key
    Write-Step "Checking for existing '$KEY_NAME' API key..."
    $keys = Invoke-ImmichRequest -Url "$ServerUrl/api/api-keys" -Token $token
    $existing = $keys | Where-Object { $_.name -eq $KEY_NAME }
    
    if ($existing) {
        Write-Warn "Found existing '$KEY_NAME' key (ID: $($existing.id))"
        $confirm = Read-Host "Update permissions on this key? [Y/n]"
        if ($confirm -notmatch '^n$') {
            Write-Step "Updating permissions on existing key..."
            Invoke-ImmichRequest -Url "$ServerUrl/api/api-keys/$($existing.id)" -Method PUT -Token $token `
                -Body (ConvertTo-Json @{ name = $KEY_NAME; permissions = $REQUIRED_PERMS } -Depth 3)
            Write-Success "'$KEY_NAME' key updated with $($REQUIRED_PERMS.Count) permissions"
            Write-Host ""
            Write-Info "The key value is unchanged — no need to re-enter it in Immich Media Frame."
            Write-Info "Run `.\check-api-key.ps1 $ServerUrl <your-api-key>` to verify."
            exit 0
        } else {
            Write-Info "Keeping existing key as-is."
            exit 0
        }
    }
    
    # Step 3: Create new key
    Write-Step "Creating API key '$KEY_NAME' with $($REQUIRED_PERMS.Count) permissions..."
    $createRes = Invoke-ImmichRequest -Url "$ServerUrl/api/api-keys" -Method POST -Token $token `
        -Body (ConvertTo-Json @{ name = $KEY_NAME; permissions = $REQUIRED_PERMS } -Depth 3)
    
    $apiKey = $createRes.apiKey
    if (-not $apiKey) { throw "API key created but no key returned" }
    
    Write-Host ""
    Write-Success "Done! Your Immich Media Frame API key:"
    Write-Host "  $apiKey" -ForegroundColor Green -BackgroundColor DarkGray
    Write-Host ""
    Write-Info "Copy this key into Immich Media Frame Settings → API Key"
    Write-Info "Run `.\check-api-key.ps1 $ServerUrl $apiKey` to verify it works."
}
catch {
    $err = $_
    if ($err -is [PSCustomObject] -and $err.StatusCode) {
        Write-ErrorMsg "Request failed (HTTP $($err.StatusCode)): $($err.Body)"
    } else {
        Write-ErrorMsg $err.Exception.Message
    }
    exit 1
}
finally {
    # Invalidate the login session so this device doesn't linger in Immich's
    # "Authorized Devices". The API key is independent of the session and survives.
    if ($token) {
        try { Invoke-ImmichRequest -Url "$ServerUrl/api/auth/logout" -Method POST -Token $token | Out-Null }
        catch { Write-Warn "Could not log out (session may linger)." }
    }
}