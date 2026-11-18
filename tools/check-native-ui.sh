#!/usr/bin/env bash
# Run only on the disposable CI emulator; capture diagnostics even when instrumentation fails.
set +e
gradle --no-daemon -Pdivan.api=https://example.test/divan/api/index.php connectedBazaarDebugAndroidTest 2>&1 | tee native-ui-tests.log
result=${PIPESTATUS[0]}
adb logcat -d -s AndroidRuntime:E > native-ui-crashes.log
adb pull /sdcard/Download/divan-ui-smoke ui-screenshots
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
files=glob.glob('app/build/outputs/androidTest-results/**/TEST-*.xml',recursive=True)
count=sum(int(ET.parse(path).getroot().get('tests',0)) for path in files)
print('Native instrumentation tests actually executed:', count)
if count<4: raise SystemExit('Expected all four native tests, not an empty instrumentation run')
PY
coverage=$?
if [ "$result" != 0 ]; then exit "$result"; fi
exit "$coverage"
