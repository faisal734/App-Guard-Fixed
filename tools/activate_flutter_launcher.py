#!/usr/bin/env python3
"""Configure conditional Flutter handoff while preserving native PIN/onboarding entry."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
build = root / 'app/build.gradle.kts'
text = build.read_text()
if 'FLUTTER_HOST_ENABLED' not in text:
    needle = '  defaultConfig {\n'
    if needle not in text:
        raise SystemExit('defaultConfig block not found; refusing to edit Gradle file')
    text = text.replace(
        needle,
        needle + '    buildConfigField("boolean", "FLUTTER_HOST_ENABLED", (providers.gradleProperty("enableFlutterHost").orNull == "true").toString())\n',
        1,
    )
    build.write_text(text)

# MainActivity deliberately remains the Android entry point for PIN and onboarding.
# Once those native security gates pass, it routes to AppGuardFlutterActivity.
manifest = root / 'app/src/main/AndroidManifest.xml'
manifest_text = manifest.read_text()
if 'android:name=".flutterbridge.AppGuardFlutterActivity"' not in manifest_text:
    raise SystemExit('Flutter host activity is not registered; run the Flutter preparation script first')
print('Flutter handoff configured. MainActivity keeps PIN/onboarding; Flutter hosts the main dashboard.')
