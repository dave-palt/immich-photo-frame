#!/usr/bin/env bash
#
# generate-api-key.sh
#
# Creates or updates a least-privilege Immich API key for ImmichFrame.
# The key is scoped to only the 4 permissions the app needs:
#
#   album.read   — list and get albums
#   asset.read   — search/list assets in albums
#   asset.view   — view thumbnails, previews, and originals
#   user.read    — validate API key (GET /users/me)
#
# Usage:
#   ./generate-api-key.sh <server-url> <email> [password]
#
#   ./generate-api-key.sh https://photos.example.com:2283 user@example.com
#
# If password is omitted, you'll be prompted securely.
# If an existing "ImmichFrame" key is found, you'll be asked to update
# or recreate it. Otherwise a new key is created.
#
set -euo pipefail

KEY_NAME="ImmichFrame"

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

# --- Step 2: Check for existing ImmichFrame key ---

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
    read -p "Delete and recreate it with fresh permissions? [y/N] " CONFIRM
    if [[ "$CONFIRM" =~ ^[Yy] ]]; then
        echo "Deleting old key..."
        curl -sf -X DELETE "${SERVER_URL}/api/api-keys/${MATCHING_ID}" \
            -H "Authorization: Bearer ${ACCESS_TOKEN}" || {
            echo "Error: Could not delete old key."
            exit 1
        }
        echo "Old key deleted."
    else
        echo "Keeping existing key. Done."
        exit 0
    fi
fi

# --- Step 3: Create scoped API key ---

echo "Creating scoped key..."

KEY_RESPONSE=$(curl -sf -X POST "${SERVER_URL}/api/api-keys" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{
        \"name\": \"${KEY_NAME}\",
        \"permissions\": [
            \"album.read\",
            \"asset.read\",
            \"asset.view\",
            \"user.read\"
        ]
    }") || {
    echo "Error: API key creation failed. Your Immich server may not support scoped keys (requires v1.135+)."
    exit 1
}

API_KEY=$(echo "$KEY_RESPONSE" | grep -o '"apiKey"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"apiKey"[[:space:]]*:[[:space:]]*"//;s/"$//')

if [ -z "$API_KEY" ]; then
    echo "Error: Could not parse API key from response."
    echo "Raw response: $KEY_RESPONSE"
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
echo "Permissions: album.read, asset.read, asset.view, user.read"
echo "This key will NOT be shown again."
echo ""
echo "Enter it in the ImmichFrame app under Setup."
