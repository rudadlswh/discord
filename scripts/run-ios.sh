#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)
IOS_DIR="$REPO_ROOT/ios"

SIMULATOR_NAME="${1:-iPhone 16}"
DERIVED_DATA="$IOS_DIR/build"
PROJECT_FILE="$IOS_DIR/DiscordLite.xcodeproj"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen not found. Install with: brew install xcodegen"
  exit 1
fi

if [ ! -f "$IOS_DIR/project.yml" ]; then
  echo "Missing iOS project.yml at $IOS_DIR/project.yml"
  exit 1
fi

(cd "$IOS_DIR" && xcodegen)

xcodebuild \
  -project "$PROJECT_FILE" \
  -scheme DiscordLite \
  -sdk iphonesimulator \
  -configuration Debug \
  -derivedDataPath "$DERIVED_DATA" \
  build

DEVICE_ID=$(xcrun simctl list devices available | awk -v name="$SIMULATOR_NAME" '
  $0 ~ name && $0 ~ /\([0-9A-F-]+\)/ {
    match($0, /\(([0-9A-F-]+)\)/, m)
    print m[1]
    exit
  }')

if [ -z "$DEVICE_ID" ]; then
  echo "Simulator not found: $SIMULATOR_NAME"
  echo "Available devices:"
  xcrun simctl list devices available | sed -n '1,120p'
  exit 1
fi

xcrun simctl boot "$DEVICE_ID" >/dev/null 2>&1 || true
xcrun simctl bootstatus "$DEVICE_ID" -b

APP_PATH="$DERIVED_DATA/Build/Products/Debug-iphonesimulator/DiscordLite.app"
if [ ! -d "$APP_PATH" ]; then
  echo "Built app not found at $APP_PATH"
  exit 1
fi

xcrun simctl install "$DEVICE_ID" "$APP_PATH"
xcrun simctl launch "$DEVICE_ID" "com.chogm.DiscordLite"

echo "Done."
