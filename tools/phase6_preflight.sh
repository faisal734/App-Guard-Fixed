#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
failed=0
printf '%s\n' '=== App Guard Flutter Phase 6: local preflight ==='
if command -v flutter >/dev/null 2>&1; then flutter --version | head -n 2; else echo 'BLOCKED: Flutter SDK not found'; failed=1; fi
if command -v dart >/dev/null 2>&1; then dart --version 2>&1; else echo 'BLOCKED: Dart SDK not found'; failed=1; fi
if command -v java >/dev/null 2>&1; then java -version 2>&1 | head -n 1; else echo 'BLOCKED: Java runtime not found'; failed=1; fi
if python3 tools/validate_flutter_bridge.py; then :; else failed=1; fi
if bash -n tools/prepare_flutter_phase4.sh; then echo 'PASS: phase 4 preparation script syntax'; else failed=1; fi
if python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path('.')
manifest=root/'app/src/main/AndroidManifest.xml'
ET.parse(manifest)
required=[
 'flutter_ui/lib/main.dart',
 'flutter_ui/lib/native_bridge.dart',
 'flutter_ui/android_host/com/example/flutterbridge/AppGuardFlutterBridge.kt',
 'flutter_ui/android_host/com/example/flutterbridge/AppGuardFlutterActivity.kt',
 'flutter_ui/android_host/com/example/flutterbridge/FlutterHostEntryActivity.kt',
]
missing=[p for p in required if not (root/p).is_file()]
if missing:
 print('FAIL: missing integration files: '+', '.join(missing)); raise SystemExit(1)
print('PASS: manifest and integration source files present')
PY
then :; else failed=1; fi
if [[ "$failed" -eq 0 ]]; then
 echo 'PASS: preflight completed; this is not a build or device test.'
else
 echo 'INCOMPLETE: resolve BLOCKED/FAIL items before attempting Android build.'
 exit 2
fi
