#!/usr/bin/env bash
#
# check-api-key.sh — Diagnose an Immich API key's permissions
#
# Tests the exact endpoints ImmichPhotoFrame uses, in order:
#   1. GET /server/ping
#   2. GET /users/me
#   3. GET /albums
#   4. GET /albums/{id} (picks first album, checks assets)
#   5. GET /assets/{id}/thumbnail (if album has assets)
#
# Usage:
#   ./scripts/check-api-key.sh <server-url> <api-key>
#
#   ./scripts/check-api-key.sh https://photos.example.com:2283 myApiKey123
#
set -euo pipefail

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
PING_BODY=$(echo "$PING" | head -n -1)
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
USER_BODY=$(echo "$USER" | head -n -1)
if [ "$USER_CODE" = "200" ]; then
    EMAIL=$(echo "$USER_BODY" | grep -o '"email"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"email"[[:space:]]*:[[:space:]]*"//;s/"$//')
    pass "User authenticated as: $EMAIL"
else
    fail "GET /users/me failed (HTTP $USER_CODE)"
    echo "  Response: $USER_BODY"
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
ALBUMS_BODY=$(echo "$ALBUMS" | head -n -1)
if [ "$ALBUMS_CODE" = "200" ]; then
    ALBUM_COUNT=$(echo "$ALBUMS_BODY" | grep -o '"id"' | wc -l | tr -d ' ')
    pass "Found $ALBUM_COUNT album(s)"
else
    fail "GET /albums failed (HTTP $ALBUMS_CODE)"
    echo "  Response: $ALBUMS_BODY"
    if [ "$ALBUMS_CODE" = "403" ]; then
        echo "  → Key is missing 'album.read' permission"
    fi
    exit 1
fi
echo ""

# --- 4. First album assets ---
if [ "$ALBUM_COUNT" = "0" ]; then
    fail "No albums found — cannot test album assets"
    exit 0
fi

FIRST_ALBUM_ID=$(echo "$ALBUMS_BODY" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"//;s/"$//')
info "Testing GET /albums/$FIRST_ALBUM_ID ..."

ALBUM_DETAIL=$(curl -sf -w "\n%{http_code}" "${BASE}/albums/${FIRST_ALBUM_ID}" -H "x-api-key: ${API_KEY}" 2>&1) || true
ALBUM_CODE=$(echo "$ALBUM_DETAIL" | tail -1)
ALBUM_BODY=$(echo "$ALBUM_DETAIL" | head -n -1)
if [ "$ALBUM_CODE" = "200" ]; then
    ASSET_COUNT=$(echo "$ALBUM_BODY" | grep -o '"id"' | wc -l | tr -d ' ')
    # Subtract the album's own id from the count
    ASSET_COUNT=$((ASSET_COUNT > 0 ? ASSET_COUNT - 1 : 0))
    pass "Album has $ASSET_COUNT asset(s)"
    if [ "$ASSET_COUNT" -gt 0 ]; then
        # Show types
        TYPES=$(echo "$ALBUM_BODY" | grep -o '"type"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"type"[[:space:]]*:[[:space:]]*"//;s/"$//' | sort | uniq -c | tr '\n' ' ')
        info "Asset types: $TYPES"
    fi
else
    fail "GET /albums/$FIRST_ALBUM_ID failed (HTTP $ALBUM_CODE)"
    echo "  Response: $ALBUM_BODY"
    exit 1
fi
echo ""

# --- 5. Thumbnail (if assets exist) ---
if [ "$ASSET_COUNT" -gt 0 ]; then
    FIRST_ASSET_ID=$(echo "$ALBUM_BODY" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | sed -n '2p' | sed 's/.*"id"[[:space:]]*:[[:space:]]*"//;s/"$//')
    if [ -n "$FIRST_ASSET_ID" ]; then
        info "Testing thumbnail: GET /assets/$FIRST_ASSET_ID/thumbnail ..."
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
