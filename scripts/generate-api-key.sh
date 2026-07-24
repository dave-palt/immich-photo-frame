#!/usr/bin/env bash
#
# generate-api-key.sh
#
# Creates a least-privilege Immich API key for ImmichPhotoFrame.
# The key is scoped to only the 4 permissions the app needs:
#
#   album.read   — list and get albums
#   asset.read   — asset metadata
#   asset.view   — view thumbnails and preview images
#   user.read    — current user info (setup validation)
#
# Usage:
#   ./generate-api-key.sh <server-url> <email> [password]
#
#   ./generate-api-key.sh https://photos.example.com:2283 user@example.com
#
# If password is omitted, you'll be prompted securely.
# The script never stores credentials — the login token is used in-memory
# to create the key, then discarded.
#
set -euo pipefail

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

# --- Step 2: Create scoped API key ---

KEY_RESPONSE=$(curl -sf -X POST "${SERVER_URL}/api/api-keys" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d '{
        "name": "ImmichPhotoFrame",
        "permissions": [
            "album.read",
            "asset.read",
            "asset.view",
            "user.read"
        ]
    }') || {
    echo "Error: API key creation failed. Your Immich server may not support scoped keys (requires v1.135+)."
    exit 1
}

API_KEY=$(echo "$KEY_RESPONSE" | grep -o '"secret"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"secret"[[:space:]]*:[[:space:]]*"//;s/"$//')

if [ -z "$API_KEY" ]; then
    echo "Error: Could not parse API key from response."
    echo "Raw response: $KEY_RESPONSE"
    exit 1
fi

# --- Done ---

echo ""
echo "================================================"
echo "  ImmichPhotoFrame API Key (save this now)"
echo "================================================"
echo ""
echo "$API_KEY"
echo ""
echo "Permissions: album.read, asset.read, asset.view, user.read"
echo "This key will NOT be shown again."
echo ""
echo "Enter it in the ImmichPhotoFrame app under Setup."
