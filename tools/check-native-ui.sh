#!/usr/bin/env bash
# Run only on the disposable CI emulator; capture diagnostics even when instrumentation fails.
set +e
gradle --no-daemon -Pdivan.api=https://example.test/divan/api/index.php connectedBazaarDebugAndroidTest :admin-app:connectedDebugAndroidTest 2>&1 | tee native-ui-tests.log
result=${PIPESTATUS[0]}
adb logcat -d > native-ui-crashes.log
adb pull /sdcard/Download/divan-ui-smoke ui-screenshots
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
files=glob.glob('app/build/outputs/androidTest-results/**/TEST-*.xml',recursive=True)
count=sum(int(ET.parse(path).getroot().get('tests',0)) for path in files)
print('Native instrumentation tests actually executed:', count)
if count<12: raise SystemExit('Expected twelve player-app instrumentation tests')
admin=glob.glob('admin-app/build/outputs/androidTest-results/**/TEST-*.xml',recursive=True)
admin_count=sum(int(ET.parse(path).getroot().get('tests',0)) for path in admin)
print('Admin instrumentation tests actually executed:', admin_count)
if admin_count<2: raise SystemExit('Expected two admin instrumentation tests')
PY
coverage=$?
if [ "$result" != 0 ]; then exit "$result"; fi
exit "$coverage"
