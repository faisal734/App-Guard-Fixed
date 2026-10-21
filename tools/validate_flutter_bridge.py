#!/usr/bin/env python3
"""Offline static checks for the staged App Guard Flutter bridge."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
dart = (root / "flutter_ui/lib/native_bridge.dart").read_text(encoding="utf-8")
kotlin = (root / "flutter_ui/android_host/com/example/flutterbridge/AppGuardFlutterBridge.kt").read_text(encoding="utf-8")
manifest = root / "app/src/main/AndroidManifest.xml"

# Parse only invokeMethod/invokeListMethod/invokeMapMethod calls, not the ready getter.
dart_methods = set(re.findall(r"_channel\.invoke(?:Method|ListMethod|MapMethod)(?:<[^>]+>)?\(\s*'([^']+)'", dart))
kotlin_methods = set(re.findall(r'"([A-Za-z][A-Za-z0-9]+)"\s*->', kotlin))
missing = sorted(dart_methods - kotlin_methods)
extra = sorted(kotlin_methods - dart_methods - {"isProtectionBridgeReady"})
errors = []
if missing:
    errors.append("Dart methods missing from Kotlin bridge: " + ", ".join(missing))
if not manifest.is_file():
    errors.append("Android manifest missing")
else:
    try:
        ET.parse(manifest)
    except ET.ParseError as exc:
        errors.append(f"Android manifest XML invalid: {exc}")

if errors:
    print("FAIL")
    for error in errors:
        print(" - " + error)
    sys.exit(1)
print("PASS: Android manifest XML parses")
print(f"PASS: {len(dart_methods)} Dart MethodChannel operations have matching Kotlin handlers")
if extra:
    print("INFO: Kotlin handlers not directly called from Dart: " + ", ".join(extra))
print("NOTE: Static checks only; not a Dart/Kotlin compile or device security test.")
