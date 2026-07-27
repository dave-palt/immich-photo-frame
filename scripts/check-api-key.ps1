<#
.SYNOPSIS
    Test an Immich API key against all endpoints Immich Media Frame uses.
    
.DESCRIPTION
    Validates that an API key has the 5 required permissions by testing each
    endpoint the app calls. Reports pass/fail with helpful hints for missing
    permissions.
    
    Tests (in order):
      1. GET  /api/server/ping       — Server reachable
      2. GET  /api/users/me          — user.read permission
      3. GET  /api/albums            — album.read permission
      4. POST /api/search/metadata   — asset.read permission
      5. GET  /api/assets/{id}/thumbnail?size=preview — asset.view permission
      6. GET  /api/assets/{id}/original              — asset.download permission

.PARAMETER ServerUrl
    Base URL of your Immich server (e.g. https://photos.example.com:2283)
    
.PARAMETER ApiKey
    The Immich API key to test (e.g. immich_apikey_abc123)

.EXAMPLE
    .\check-api-key.ps1 https://photos.example.com immich_apikey_abc123
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true, Position=0)]
    [ValidateNotNullOrEmpty()]
    [string]$ServerUrl,
    
    [Parameter(Mandatory=$true, Position=1)]
    [ValidateNotNullOrEmpty()]
    [string]$ApiKey
)

$ServerUrl = $ServerUrl.TrimEnd('/')
$Base = "$ServerUrl/api"

function Write-Info { param([string]$msg) Write-Host "ℹ $msg" -ForegroundColor Cyan }
function Write-Success { param([string]$msg) Write-Host "✓ $msg" -ForegroundColor Green }
function Write-Warn { param([string]$msg) Write-Host "⚠ $msg" -ForegroundColor Yellow }
function Write-ErrorMsg { param([string]$msg) Write-Host "✗ $msg" -ForegroundColor Red }
function Write-Step { param([string]$msg) Write-Host "▸ $msg" -ForegroundColor Cyan }

function Test-Endpoint {
    param(
        [string]$Path,
        [string]$Method = "GET",
        [string]$Body,
        [string]$Permission,
        [string]$Description,
        [switch]$UseQueryAuth
    )

    # When -UseQueryAuth is set, append the API key as ?apiKey= instead of
    # using the x-api-key header. This mirrors how the app loads media via
    # Coil/ExoPlayer. See check-api-key.sh for the same logic.
    if ($UseQueryAuth) {
        $sep = if ($Path.Contains("?")) { "&" } else { "?" }
        $Path = "${Path}${sep}apiKey=$ApiKey"
        $headers = @{ "Content-Type" = "application/json" }
    } else {
        $headers = @{
            "Content-Type" = "application/json"
            "x-api-key"    = $ApiKey
        }
    }
    
    try {
        $params = @{
            Uri         = "$Base$Path"
            Method      = $Method
            Headers     = $headers
            ErrorAction = 'Stop'
        }
        if ($Body) { $params.Body = $Body }
        
        $response = Invoke-RestMethod @params
        return @{ ok = $true; status = 200; data = $response }
    }
    catch {
        $statusCode = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.value__ } else { 0 }
        $body = if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $reader.ReadToEnd()
        } else { $_.Exception.Message }
        return @{ ok = $false; status = $statusCode; error = $body }
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Immich API Key Diagnostics" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Server: $ServerUrl" -ForegroundColor Cyan
Write-Host ""

# --- 1. Ping ---
Write-Step "Testing GET /server/ping ..."
$ping = Test-Endpoint -Path "/server/ping" -Permission "none" -Description "Server reachable"
if ($ping.ok) {
    Write-Success "Ping OK (HTTP 200) — $($ping.data.resp)"
} else {
    Write-ErrorMsg "Server unreachable at $Base/server/ping (HTTP $($ping.status))"
    exit 1
}
Write-Host ""

# --- 2. User ---
Write-Step "Testing GET /users/me ..."
$user = Test-Endpoint -Path "/users/me" -Permission "user.read" -Description "Validate API key"
if ($user.ok) {
    Write-Success "Authenticated as: $($user.data.email)"
} else {
    Write-ErrorMsg "GET /users/me failed (HTTP $($user.status))"
    if ($user.status -eq 403) { Write-Host "  → Key is missing 'user.read' permission" -ForegroundColor Yellow }
    exit 1
}
Write-Host ""

# --- 3. Albums ---
Write-Step "Testing GET /albums ..."
$albums = Test-Endpoint -Path "/albums" -Permission "album.read" -Description "List albums"
if ($albums.ok) {
    $count = ($albums.data | Measure-Object).Count
    Write-Success "Found $count album(s)"
} else {
    Write-ErrorMsg "GET /albums failed (HTTP $($albums.status))"
    if ($albums.status -eq 403) { Write-Host "  → Key is missing 'album.read' permission" -ForegroundColor Yellow }
    exit 1
}
Write-Host ""

# --- 4. Album assets via POST /search/metadata ---
if ($count -eq 0) {
    Write-Warn "No albums found — cannot test album assets"
    exit 0
}

$firstAlbumId = $albums.data[0].id
Write-Step "Testing POST /search/metadata (album: $firstAlbumId) ..."
$searchBody = ConvertTo-Json @{ albumIds = @($firstAlbumId); type = "IMAGE"; size = 100 } -Depth 3
$search = Test-Endpoint -Path "/search/metadata" -Method POST -Body $searchBody -Permission "asset.read" -Description "Search assets in album"
if ($search.ok) {
    $assetCount = $search.data.assets.items.Count
    Write-Success "Album has $assetCount image asset(s)"
    
    if ($assetCount -gt 0) {
        $firstAssetId = $search.data.assets.items[0].id
        
        # --- 5. Thumbnail (?apiKey= query param — same as Coil image loading) ---
        Write-Step "Testing thumbnail: GET /assets/$firstAssetId/thumbnail?size=preview&apiKey=*** ..."
        $thumb = Test-Endpoint -Path "/assets/$firstAssetId/thumbnail?size=preview" -Permission "asset.view" -Description "Download thumbnail" -UseQueryAuth
        if ($thumb.ok) {
            Write-Success "Thumbnail loaded (HTTP 200)"
        } else {
            Write-ErrorMsg "Thumbnail failed (HTTP $($thumb.status))"
            if ($thumb.status -eq 403) { Write-Host "  → Key is missing 'asset.view' permission" -ForegroundColor Yellow }
        }

        # --- 6. Download original (?apiKey= query param — same as ExoPlayer video loading) ---
        Write-Step "Testing download: GET /assets/$firstAssetId/original?apiKey=*** ..."
        $download = Test-Endpoint -Path "/assets/$firstAssetId/original" -Permission "asset.download" -Description "Download original" -UseQueryAuth
        if ($download.ok) {
            Write-Success "Original download OK (HTTP 200)"
        } else {
            Write-ErrorMsg "Download failed (HTTP $($download.status))"
            if ($download.status -eq 403) { Write-Host "  → Key is missing 'asset.download' permission (required for video playback)" -ForegroundColor Yellow }
        }
    } else {
        Write-Warn "No assets to test thumbnails with"
    }
} else {
    Write-ErrorMsg "POST /search/metadata failed (HTTP $($search.status))"
    if ($search.status -eq 403) { Write-Host "  → Key is missing 'asset.read' permission" -ForegroundColor Yellow }
    exit 1
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Success "Diagnostics complete"
Write-Host "========================================" -ForegroundColor Green