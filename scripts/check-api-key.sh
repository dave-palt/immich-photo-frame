#!/usr/bin/env bash
#
# check-api-key.sh — Diagnose an Immich API key's permissions
#
# Tests the exact endpoints ImmichPhotoFrame uses, in order:
#   1. GET  /server/ping
#   2. GET  /users/me
#   3. GET  /albums
#   4. POST /search/metadata (album assets — same as the app)
#   5. GET  /assets/{id}/thumbnail?size=preview
#
# Usage:
#   ./scripts/check-api-key.sh <server-url> <api-key>
#
set -uo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; }
info() { echo -e "${YELLOW}→${NC} $1"; }

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <server-url> <api-key>"
    echo ""
    echo "Example:"
    echo "  $0 https://photos.example.com:2283 myApiKey123"
    exit 1
fi

SERVER_URL="${1%/}"
API_KEY="$2"
BASE="${SERVER_URL}/api"

echo "========================================"
echo "  Immich API Key Diagnostics"
echo "========================================"
echo "Server: $SERVER_URL"
echo ""

# --- 1. Ping ---
info "Testing GET /server/ping ..."
PING=$(curl -sf -w "\n%{http_code}" "${BASE}/server/ping" -H "x-api-key: ${API_KEY}" 2>&1) || {
    fail "Server unreachable at ${BASE}/server/ping"
    exit 1
}
PING_CODE=$(echo "$PING" | tail -1)
PING_BODY=$(echo "$PING" | sed '$d')
if [ "$PING_CODE" = "200" ]; then
    pass "Ping OK (HTTP 200) — $PING_BODY"
else
    fail "Ping failed (HTTP $PING_CODE): $PING_BODY"
    exit 1
fi
echo ""

# --- 2. User ---
info "Testing GET /users/me ..."
USER=$(curl -sf -w "\n%{http_code}" "${BASE}/users/me" -H "x-api-key: ${API_KEY}" 2>&1) || true
USER_CODE=$(echo "$USER" | tail -1)
USER_BODY=$(echo "$USER" | sed '$d')
if [ "$USER_CODE" = "200" ]; then
    EMAIL=$(echo "$USER_BODY" | grep -o '"email"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"email"[[:space:]]*:[[:space:]]*"//;s/"$//')
    pass "User authenticated as: $EMAIL"
else
    fail "GET /users/me failed (HTTP $USER_CODE)"
    if [ "$USER_CODE" = "403" ]; then
        echo "  → Key is missing 'user.read' permission"
    fi
    exit 1
fi
echo ""

# --- 3. Albums ---
info "Testing GET /albums ..."
ALBUMS=$(curl -sf -w "\n%{http_code}" "${BASE}/albums" -H "x-api-key: ${API_KEY}" 2>&1) || true
ALBUMS_CODE=$(echo "$ALBUMS" | tail -1)
ALBUMS_BODY=$(echo "$ALBUMS" | sed '$d')
if [ "$ALBUMS_CODE" = "200" ]; then
    ALBUM_COUNT=$(echo "$ALBUMS_BODY" | grep -o '"id"' | wc -l | tr -d ' ')
    pass "Found $ALBUM_COUNT album(s)"
else
    fail "GET /albums failed (HTTP $ALBUMS_CODE)"
    if [ "$ALBUMS_CODE" = "403" ]; then
        echo "  → Key is missing 'album.read' permission"
    fi
    exit 1
fi
echo ""

# --- 4. Album assets via POST /search/metadata ---
if [ "$ALBUM_COUNT" = "0" ]; then
    fail "No albums found — cannot test album assets"
    exit 0
fi

FIRST_ALBUM_ID=$(echo "$ALBUMS_BODY" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"//;s/"$//')
info "Testing POST /search/metadata (album: $FIRST_ALBUM_ID) ..."

SEARCH_RESULT=$(curl -sf -w "\n%{http_code}" -X POST "${BASE}/search/metadata" \
    -H "x-api-key: ${API_KEY}" \
    -H "Content-Type: application/json" \
    -d "{\"albumIds\":[\"${FIRST_ALBUM_ID}\"],\"type\":\"IMAGE\",\"size\":100}" 2>&1) || true
SEARCH_CODE=$(echo "$SEARCH_RESULT" | tail -1)
SEARCH_BODY=$(echo "$SEARCH_RESULT" | sed '$d')
if [ "$SEARCH_CODE" = "200" ]; then
    ASSET_COUNT=$(echo "$SEARCH_BODY" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['assets']['items']))" 2>/dev/null || echo "0")
    pass "Album has $ASSET_COUNT image asset(s)"
    if [ "$ASSET_COUNT" = "0" ]; then
        info "No assets to test thumbnails with."
    fi
else
    fail "POST /search/metadata failed (HTTP $SEARCH_CODE)"
    if [ "$SEARCH_CODE" = "403" ]; then
        echo "  → Key is missing 'asset.read' permission"
    fi
    exit 1
fi
echo ""

# --- 5. Thumbnail ---
if [ "${ASSET_COUNT:-0}" -gt 0 ]; then
    FIRST_ASSET_ID=$(echo "$SEARCH_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['assets']['items'][0]['id'])" 2>/dev/null || echo "")
    if [ -n "$FIRST_ASSET_ID" ]; then
        info "Testing thumbnail: GET /assets/$FIRST_ASSET_ID/thumbnail?size=preview ..."
        THUMB_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "${BASE}/assets/${FIRST_ASSET_ID}/thumbnail?size=preview" -H "x-api-key: ${API_KEY}" 2>&1) || true
        if [ "$THUMB_CODE" = "200" ]; then
            pass "Thumbnail loaded (HTTP 200)"
        else
            fail "Thumbnail failed (HTTP $THUMB_CODE)"
            if [ "$THUMB_CODE" = "403" ]; then
                echo "  → Key is missing 'asset.view' permission"
            fi
        fi
    fi
fi

echo ""
echo "========================================"
pass "Diagnostics complete"
echo "========================================"
