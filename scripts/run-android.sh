#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="${ENV_FILE:-$REPO_ROOT/.env}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

AVD_NAME="${1:-Medium_Phone_API_36.0}"
EMULATOR_BIN="${EMULATOR_BIN:-$HOME/Library/Android/sdk/emulator/emulator}"
ADB_BIN="${ADB_BIN:-adb}"
TURN_ENABLED="${TURN_ENABLED:-1}"
TURN_URLS_DEFAULT="turn:10.0.2.2:3478?transport=udp,turn:10.0.2.2:3478?transport=tcp"
TURN_URLS="${TURN_URLS:-$TURN_URLS_DEFAULT}"
TURN_USERNAME="${TURN_USERNAME:-}"
TURN_PASSWORD="${TURN_PASSWORD:-}"

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

resolve_target_serial() {
  if [ -n "${ANDROID_SERIAL:-}" ]; then
    echo "$ANDROID_SERIAL"
    return 0
  fi
  if [ -n "${ADB_SERIAL:-}" ]; then
    echo "$ADB_SERIAL"
    return 0
  fi

  local devices serial
  devices=$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1}')
  for serial in $devices; do
    if "$ADB_BIN" -s "$serial" emu avd name 2>/dev/null | grep -Fxq "$AVD_NAME"; then
      echo "$serial"
      return 0
    fi
  done

  if [ -n "$devices" ] && [ "$(echo "$devices" | wc -l | tr -d ' ')" = "1" ]; then
    echo "$devices"
    return 0
  fi

  return 1
}

target_serial=""
for _ in {1..60}; do
  if target_serial=$(resolve_target_serial); then
    break
  fi
  sleep 2
done

if [ -z "$target_serial" ]; then
  echo "Multiple devices/emulators detected. Set ANDROID_SERIAL or ADB_SERIAL."
  "$ADB_BIN" devices
  exit 1
fi

export ANDROID_SERIAL="$target_serial"

"$ADB_BIN" -s "$target_serial" wait-for-device

for _ in {1..60}; do
  boot_completed=$($ADB_BIN -s "$target_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
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

if ! command -v gradle >/dev/null 2>&1; then
  echo "gradle not found in PATH"
  exit 1
fi

if [ "$TURN_ENABLED" = "1" ]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "docker not found in PATH (set TURN_ENABLED=0 to skip TURN)"
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "Docker daemon not running (set TURN_ENABLED=0 to skip TURN)"
    exit 1
  fi
  if [ -z "$TURN_USERNAME" ] || [ -z "$TURN_PASSWORD" ]; then
    echo "TURN_USERNAME or TURN_PASSWORD not set (set in .env or env vars, or set TURN_ENABLED=0)"
    exit 1
  fi
  echo "Starting TURN server..."
  docker compose -f "$REPO_ROOT/docker-compose.yml" up -d turn
fi

echo "Cleaning server build..."
JAVA_HOME="$JAVA_HOME" gradle -p "$REPO_ROOT/server" clean

echo "Starting server..."
server_env=(env JAVA_HOME="$JAVA_HOME")
if [ "$TURN_ENABLED" = "1" ]; then
  server_env+=(
    TURN_URLS="$TURN_URLS"
    TURN_USERNAME="$TURN_USERNAME"
    TURN_PASSWORD="$TURN_PASSWORD"
  )
fi
nohup "${server_env[@]}" gradle -p "$REPO_ROOT/server" run --no-daemon > /tmp/discord_server.log 2>&1 &

"$REPO_ROOT/android/gradlew" -p "$REPO_ROOT/android" :app:installDebug

"$ADB_BIN" -s "$target_serial" shell am start -n com.chogm.discordapp/.WelcomeActivity

echo "Done."
