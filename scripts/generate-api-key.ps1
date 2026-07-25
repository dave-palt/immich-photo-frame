<#
.SYNOPSIS
    Creates or updates a least-privilege Immich API key for ImmichPhotoFrame.

.DESCRIPTION
    The key is scoped to only the 4 permissions the app needs:
      album.read  - list and get albums
      asset.read  - asset metadata
      asset.view  - view thumbnails and preview images
      user.read   - current user info (setup validation)

    If an existing "ImmichPhotoFrame" key is found, you'll be asked to
    update or recreate it. Otherwise a new key is created.

    For a standalone executable with the same functionality (no PowerShell
    required), download keymgr-windows-x64.exe from the Releases page.

.PARAMETER ServerUrl
    Your Immich server URL (e.g. https://photos.example.com:2283)

.PARAMETER Email
    Your Immich account email

.PARAMETER Password
    Optional. If omitted, you'll be prompted securely.

.EXAMPLE
    .\generate-api-key.ps1 https://photos.example.com:2283 user@example.com
#>
param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$ServerUrl,

    [Parameter(Mandatory=$true, Position=1)]
    [string]$Email,

    [Parameter(Position=2)]
    [string]$Password
)

$ErrorActionPreference = "Stop"
$Key_Name = "ImmichPhotoFrame"
$BaseUrl = $ServerUrl.TrimEnd('/')

# Prompt for password if not provided
if (-not $Password) {
    $securePassword = Read-Host "Password" -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    )
}

# --- Step 1: Login ---
Write-Host "Connecting to $BaseUrl ..." -ForegroundColor Yellow

try {
    $loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" `
        -Method Post `
        -ContentType "application/json" `
        -Body $loginBody `
        -ErrorAction Stop
} catch {
    Write-Host "Error: Login failed. Check URL, email, and password." -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor DarkGray
    exit 1
}

$accessToken = $loginResponse.accessToken
if (-not $accessToken) {
    Write-Host "Error: Could not parse access token from login response." -ForegroundColor Red
    exit 1
}

Write-Host "Login successful." -ForegroundColor Green
$headers = @{ Authorization = "Bearer $accessToken" }

# --- Step 2: Check for existing key ---
try {
    $existingKeys = Invoke-RestMethod -Uri "$BaseUrl/api/api-keys" -Headers $headers -ErrorAction Stop
} catch {
    $existingKeys = @()
}

$matchingKey = $existingKeys | Where-Object { $_.name -eq $Key_Name } | Select-Object -First 1

if ($matchingKey) {
    Write-Host "Found existing '$Key_Name' key." -ForegroundColor Yellow
    Write-Host "  Existing key ID: $($matchingKey.id)"

    $confirm = Read-Host "Delete and recreate it with fresh permissions? [y/N]"
    if ($confirm -match '^[Yy]') {
        Write-Host "Deleting old key..."
        try {
            Invoke-RestMethod -Uri "$BaseUrl/api/api-keys/$($matchingKey.id)" `
                -Method Delete -Headers $headers -ErrorAction Stop
        } catch {
            Write-Host "Error: Could not delete old key." -ForegroundColor Red
            exit 1
        }
        Write-Host "Old key deleted." -ForegroundColor Green
    } else {
        Write-Host "Keeping existing key. Done."
        exit 0
    }
}

# --- Step 3: Create scoped key ---
Write-Host "Creating scoped key..."

$keyBody = @{
    name = $Key_Name
    permissions = @("album.read", "asset.read", "asset.view", "user.read")
} | ConvertTo-Json -Depth 3

try {
    $keyResponse = Invoke-RestMethod -Uri "$BaseUrl/api/api-keys" `
        -Method Post `
        -ContentType "application/json" `
        -Headers $headers `
        -Body $keyBody `
        -ErrorAction Stop
} catch {
    Write-Host "Error: API key creation failed. Your Immich server may not support scoped keys (requires v1.135+)." -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor DarkGray
    exit 1
}

$apiKey = $keyResponse.secret
if (-not $apiKey) {
    Write-Host "Error: Could not parse API key from response." -ForegroundColor Red
    exit 1
}

# --- Done ---
Write-Host ""
Write-Host "================================================" -ForegroundColor White
Write-Host "  $Key_Name API Key (save this now)" -ForegroundColor White
Write-Host "================================================" -ForegroundColor White
Write-Host ""
Write-Host $apiKey -ForegroundColor Cyan
Write-Host ""
Write-Host "Permissions: album.read, asset.read, asset.view, user.read"
Write-Host "This key will NOT be shown again."
Write-Host ""
Write-Host "Enter it in the ImmichPhotoFrame app under Setup."
