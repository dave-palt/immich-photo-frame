<#
.SYNOPSIS
    Diagnoses an Immich API key's permissions.

.DESCRIPTION
    Tests the exact endpoints ImmichPhotoFrame uses, in order:
      1. GET  /server/ping
      2. GET  /users/me
      3. GET  /albums
      4. POST /search/metadata (album assets - same as the app)
      5. GET  /assets/{id}/thumbnail?size=preview

    For a standalone executable with the same functionality (no PowerShell
    required), download keymgr-windows-x64.exe from the Releases page.

.PARAMETER ServerUrl
    Your Immich server URL (e.g. https://photos.example.com:2283)

.PARAMETER ApiKey
    The Immich API key to test

.EXAMPLE
    .\check-api-key.ps1 https://photos.example.com:2283 myApiKey123
#>
param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$ServerUrl,

    [Parameter(Mandatory=$true, Position=1)]
    [string]$ApiKey
)

$ErrorActionPreference = "Stop"
$BaseUrl = $ServerUrl.TrimEnd('/')
$ApiBase = "$BaseUrl/api"
$headers = @{ "x-api-key" = $ApiKey }

function Write-Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }
function Write-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Yellow }

Write-Host "========================================"
Write-Host "  Immich API Key Diagnostics"
Write-Host "========================================"
Write-Host "Server: $BaseUrl"
Write-Host ""

# --- 1. Ping ---
Write-Info "Testing GET /server/ping ..."
try {
    $ping = Invoke-WebRequest -Uri "$ApiBase/server/ping" -Headers $headers -UseBasicParsing -ErrorAction Stop
    Write-Pass "Ping OK (HTTP $($ping.StatusCode)) - $($ping.Content)"
} catch {
    Write-Fail "Server unreachable at $ApiBase/server/ping"
    exit 1
}
Write-Host ""

# --- 2. User ---
Write-Info "Testing GET /users/me ..."
try {
    $user = Invoke-RestMethod -Uri "$ApiBase/users/me" -Headers $headers -ErrorAction Stop
    Write-Pass "User authenticated as: $($user.email)"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Fail "GET /users/me failed (HTTP $code)"
    if ($code -eq 403) {
        Write-Host "  -> Key is missing 'user.read' permission"
    }
    exit 1
}
Write-Host ""

# --- 3. Albums ---
Write-Info "Testing GET /albums ..."
try {
    $albums = Invoke-RestMethod -Uri "$ApiBase/albums" -Headers $headers -ErrorAction Stop
    $albumCount = $albums.Count
    Write-Pass "Found $albumCount album(s)"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Fail "GET /albums failed (HTTP $code)"
    if ($code -eq 403) {
        Write-Host "  -> Key is missing 'album.read' permission"
    }
    exit 1
}
Write-Host ""

if ($albumCount -eq 0) {
    Write-Fail "No albums found - cannot test album assets"
    exit 0
}

$firstAlbumId = $albums[0].id

# --- 4. Album assets via POST /search/metadata ---
Write-Info "Testing POST /search/metadata (album: $firstAlbumId) ..."
$searchBody = @{
    albumIds = @($firstAlbumId)
    type = "IMAGE"
    size = 100
} | ConvertTo-Json -Depth 3

try {
    $searchResult = Invoke-RestMethod -Uri "$ApiBase/search/metadata" `
        -Method Post `
        -ContentType "application/json" `
        -Headers $headers `
        -Body $searchBody `
        -ErrorAction Stop
    $assetCount = $searchResult.assets.items.Count
    Write-Pass "Album has $assetCount image asset(s)"
    if ($assetCount -eq 0) {
        Write-Info "No assets to test thumbnails with."
    }
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Fail "POST /search/metadata failed (HTTP $code)"
    if ($code -eq 403) {
        Write-Host "  -> Key is missing 'asset.read' permission"
    }
    exit 1
}
Write-Host ""

# --- 5. Thumbnail ---
if ($assetCount -gt 0) {
    $firstAssetId = $searchResult.assets.items[0].id
    if ($firstAssetId) {
        Write-Info "Testing thumbnail: GET /assets/$firstAssetId/thumbnail?size=preview ..."
        try {
            $thumb = Invoke-WebRequest -Uri "$ApiBase/assets/$firstAssetId/thumbnail?size=preview" `
                -Headers $headers -UseBasicParsing -ErrorAction Stop
            Write-Pass "Thumbnail loaded (HTTP $($thumb.StatusCode))"
        } catch {
            $code = $_.Exception.Response.StatusCode.value__
            Write-Fail "Thumbnail failed (HTTP $code)"
            if ($code -eq 403) {
                Write-Host "  -> Key is missing 'asset.view' permission"
            }
        }
    }
}

Write-Host ""
Write-Host "========================================"
Write-Pass "Diagnostics complete"
Write-Host "========================================"
