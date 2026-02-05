#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)

AVD_NAME="${1:-Medium_Phone_API_36.0}"
EMULATOR_BIN="${EMULATOR_BIN:-$HOME/Library/Android/sdk/emulator/emulator}"
ADB_BIN="${ADB_BIN:-adb}"

if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
  echo "adb not found in PATH"
  exit 1
fi

if [ ! -x "$EMULATOR_BIN" ]; then
  echo "emulator not found: $EMULATOR_BIN"
  exit 1
fi

AVAILABLE_AVDS="$("$EMULATOR_BIN" -list-avds || true)"
if ! echo "$AVAILABLE_AVDS" | grep -Fxq "$AVD_NAME"; then
  echo "AVD not found: $AVD_NAME"
  echo "Available AVDs:"
  echo "$AVAILABLE_AVDS"
  exit 1
fi

running_emulator=$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {print $1; exit 0}')
if [ -z "$running_emulator" ]; then
  echo "Starting emulator: $AVD_NAME"
  nohup "$EMULATOR_BIN" -avd "$AVD_NAME" -netdelay none -netspeed full > /tmp/emulator.log 2>&1 &
else
  echo "Emulator already running: $running_emulator"
fi

"$ADB_BIN" wait-for-device

for _ in {1..60}; do
  boot_completed=$($ADB_BIN shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$boot_completed" = "1" ]; then
    break
  fi
  sleep 2
done

if ! JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null); then
  echo "Java 21 not found. Install JDK 21 and retry."
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

"$REPO_ROOT/android/gradlew" -p "$REPO_ROOT/android" :app:installDebug

"$ADB_BIN" shell am start -n com.chogm.discordapp/.WelcomeActivity

echo "Done."
