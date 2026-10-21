#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v flutter >/dev/null 2>&1; then
  echo 'ERROR: Flutter SDK is required (stable channel).' >&2
  exit 2
fi
if ! command -v java >/dev/null 2>&1; then
  echo 'ERROR: Java is required; use JDK 17 or 21.' >&2
  exit 2
fi

flutter --version
java -version
bash tools/prepare_flutter_phase4.sh
(
  cd flutter_module
  flutter pub get
  flutter analyze
  flutter test
)

# Compile Android host with Flutter add-to-app enabled. MainActivity stays native.
bash ./gradlew -PenableFlutterHost=true :app:assembleDebug
printf '\nBuild succeeded. APK output:\n%s\n' "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
