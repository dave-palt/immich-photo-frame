#!/usr/bin/env bash
#
# generate-api-key.sh
#
# Creates or updates a least-privilege Immich API key for Immich Media Frame.
# The key is scoped to only the 4 permissions the app needs:
#
#   album.read   — list and get albums
#   asset.read   — search/list assets in albums
#   asset.view   — view thumbnails and previews
#   asset.download — download original files (video playback)
#   user.read    — validate API key (GET /users/me)
#
# Usage:
#   ./generate-api-key.sh <server-url> <email> [password]
#
#   ./generate-api-key.sh https://photos.example.com:2283 user@example.com
#
# If password is omitted, you'll be prompted securely.
# If an existing "Immich Media Frame" key is found, you'll be asked to update
# or recreate it. Otherwise a new key is created.
#
set -euo pipefail

KEY_NAME="ImmichMediaFrame"

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <server-url> <email> [password]"
    echo ""
    echo "Example:"
    echo "  $0 https://photos.example.com:2283 user@example.com"
    exit 1
fi

SERVER_URL="${1%/}"
EMAIL="$2"

if [ "$#" -ge 3 ]; then
    PASSWORD="$3"
else
    read -s -p "Password: " PASSWORD
    echo
fi

# --- Step 1: Login to get access token ---

echo "Connecting to $SERVER_URL ..."

LOGIN_RESPONSE=$(curl -sf -X POST "${SERVER_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}" 2>&1) || {
    echo "Error: Login failed. Check URL, email, and password."
    exit 1
}

ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"accessToken"[[:space:]]*:[[:space:]]*"//;s/"$//')

if [ -z "$ACCESS_TOKEN" ]; then
    echo "Error: Could not parse access token from login response."
    echo "Raw response: $LOGIN_RESPONSE"
    exit 1
fi

echo "Login successful."

# --- Step 2: Check for existing Immich Media Frame key ---

EXISTING_KEYS=$(curl -sf "${SERVER_URL}/api/api-keys" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" 2>&1) || EXISTING_KEYS="[]"

# Check if any key has our name (|| true prevents pipefail exit when grep finds no match)
KEY_NAMES=$(echo "$EXISTING_KEYS" | grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"name"[[:space:]]*:[[:space:]]*"//;s/"$//' || true)

if [ -n "$KEY_NAMES" ] && echo "$KEY_NAMES" | grep -q "$KEY_NAME"; then
    echo "Found existing '$KEY_NAME' key."

    # Extract the ID of the matching key
    MATCHING_ID=$(echo "$EXISTING_KEYS" | tr ',' '\n' | grep -A1 "\"name\".*\"$KEY_NAME\"" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"//;s/"$//' || true)
    # Fallback: try python3 for robust JSON parsing
    if [ -z "$MATCHING_ID" ]; then
        MATCHING_ID=$(echo "$EXISTING_KEYS" | python3 -c "
import sys, json
for k in json.load(sys.stdin):
    if k.get('name') == '${KEY_NAME}':
        print(k['id'])
        break
" 2>/dev/null || true)
    fi

    echo "  Existing key ID: $MATCHING_ID"
    echo ""
    read -p "Update permissions on this key? [Y/n] " CONFIRM
    if [[ ! "$CONFIRM" =~ ^[Nn] ]]; then
        echo "Updating permissions on existing key..."
        UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "${SERVER_URL}/api/api-keys/${MATCHING_ID}" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer ${ACCESS_TOKEN}" \
            -d "{
                \"name\": \"${KEY_NAME}\",
                \"permissions\": [
                    \"album.read\",
                    \"asset.read\",
                    \"asset.view\",
                    \"asset.download\",
                    \"user.read\"
                ]
            }" 2>&1)

        UPDATE_HTTP_CODE=$(echo "$UPDATE_RESPONSE" | tail -1)
        UPDATE_BODY=$(echo "$UPDATE_RESPONSE" | sed '$d')

        if [ "$UPDATE_HTTP_CODE" != "200" ]; then
            echo "Error: Failed to update key (HTTP $UPDATE_HTTP_CODE)."
            echo "Server response: $UPDATE_BODY"
            exit 1
        fi

        echo ""
        echo "================================================"
        echo "  ${KEY_NAME} key updated successfully"
        echo "================================================"
        echo ""
        echo "The key value is unchanged — no need to re-enter it in Immich Media Frame."
        echo "Permissions: album.read, asset.read, asset.view, asset.download, user.read"
        echo ""
        echo "Run ./scripts/check-api-key.sh to verify."
        exit 0
    else
        echo "Keeping existing key as-is. Done."
        exit 0
    fi
fi

# --- Step 3: Create scoped API key ---

echo "Creating scoped key..."

# Use -s (silent) but NOT -f (fail) so we capture the response body on error
KEY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${SERVER_URL}/api/api-keys" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{
        \"name\": \"${KEY_NAME}\",
        \"permissions\": [
            \"album.read\",
            \"asset.read\",
            \"asset.view\",
            \"asset.download\",
            \"user.read\"
        ]
    }" 2>&1)

# Split response body and HTTP status code
KEY_HTTP_CODE=$(echo "$KEY_RESPONSE" | tail -1)
KEY_BODY=$(echo "$KEY_RESPONSE" | sed '$d')

if [ "$KEY_HTTP_CODE" != "200" ]; then
    echo "Error: API key creation failed (HTTP $KEY_HTTP_CODE)."
    echo "Server response: $KEY_BODY"
    echo ""
    echo "Your Immich server may not support scoped keys (requires v1.135+)."
    exit 1
fi

API_KEY=$(echo "$KEY_BODY" | grep -o '"apiKey"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"apiKey"[[:space:]]*:[[:space:]]*"//;s/"$//')

if [ -z "$API_KEY" ]; then
    echo "Error: Could not parse API key from response."
    echo "Raw response: $KEY_BODY"
    exit 1
fi

# --- Done ---

echo ""
echo "================================================"
echo "  ${KEY_NAME} API Key (save this now)"
echo "================================================"
echo ""
echo "$API_KEY"
echo ""
echo "Permissions: album.read, asset.read, asset.view, asset.download, user.read"
echo "This key will NOT be shown again."
echo ""
echo "Enter it in the Immich Media Frame app under Setup."
