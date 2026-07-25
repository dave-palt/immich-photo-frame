#!/usr/bin/env bash
#
# build.sh — Cross-compile the ImmichFrame key manager into standalone
# executables for macOS, Linux, and Windows using Bun's --compile feature.
#
# Output goes to dist/. Each binary is a self-contained executable with
# no runtime dependencies.
#
# Usage:
#   ./build.sh           # build all targets
#   ./build.sh host      # build only for the current platform
#
set -euo pipefail

cd "$(dirname "$0")/.."

SOURCE="scripts/keymgr.ts"
DIST="dist"
mkdir -p "$DIST"

# ── Target matrix ─────────────────────────────────────────────────
# Format: target|output-name
TARGETS=(
  "bun-darwin-arm64|keymgr-darwin-arm64"
  "bun-darwin-x64|keymgr-darwin-x64"
  "bun-linux-arm64|keymgr-linux-arm64"
  "bun-linux-x64|keymgr-linux-x64"
  "bun-windows-x64|keymgr-windows-x64.exe"
)

# ── Build host-only target if requested ──────────────────────────
if [ "${1:-all}" = "host" ]; then
  case "$(uname -s)-$(uname -m)" in
    Darwin-arm64)  HOST_TARGET="bun-darwin-arm64|keymgr" ;;
    Darwin-x86_64) HOST_TARGET="bun-darwin-x64|keymgr" ;;
    Linux-aarch64) HOST_TARGET="bun-linux-arm64|keymgr" ;;
    Linux-x86_64)  HOST_TARGET="bun-linux-x64|keymgr" ;;
    *) echo "Unknown host platform: $(uname -s)-$(uname -m)"; exit 1 ;;
  esac
  TARGETS=("$HOST_TARGET")
fi

# ── Compile ───────────────────────────────────────────────────────
echo "Building ${#TARGETS[@]} target(s)..."

FAILED=0
for entry in "${TARGETS[@]}"; do
  target="${entry%%|*}"
  output="${DIST}/${entry#*|}"

  echo -n "  $target → $output ... "

  if bun build --compile --target="$target" "$SOURCE" --outfile "$output" >/dev/null 2>&1; then
    # Strip macOS/Linux binaries (smaller size); skip on Windows
    case "$output" in
      *.exe) ;;
      *) chmod +x "$output"; strip "$output" 2>/dev/null || true ;;
    esac
    SIZE=$(du -h "$output" | cut -f1)
    echo "OK (${SIZE})"
  else
    echo "FAILED"
    FAILED=$((FAILED + 1))
  fi
done

echo ""
if [ "$FAILED" -gt 0 ]; then
  echo "ERROR: $FAILED target(s) failed to build."
  exit 1
fi

echo "All binaries in $DIST/"
