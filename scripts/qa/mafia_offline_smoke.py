"""Fresh-install offline Mafia gate smoke test on an isolated Android emulator."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = Path('qa-mafia-offline')
OUT.mkdir(exist_ok=True)
PACKAGE = 'com.meysam.divanemtiaz.site.debug'

def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=30)

def snapshot(name='latest'):
    adb('shell', 'uiautomator', 'dump', '/sdcard/divan-window.xml')
    raw = adb('exec-out', 'cat', '/sdcard/divan-window.xml')
    (OUT / f'{name}.xml').write_bytes(raw)
    return ET.fromstring(raw)

def match(root, text):
    return next((n for n in root.iter('node') if n.get('text') == text or n.get('content-desc') == text), None)

def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def wait_for(text, scroll=False):
    for attempt in range(16):
        root = snapshot()
        consent = match(root, 'نه، ممنون')
        if consent is not None:
            tap(consent)
        else:
            found = match(root, text)
            if found is not None:
                return found
            if scroll and attempt >= 3:
                adb('shell', 'input', 'swipe', '540', '1500', '540', '600', '400')
        time.sleep(1)
    raise AssertionError(f'Expected UI text not found: {text}')

try:
    apk = next(Path('preview-apks').rglob('app-direct-debug.apk'))
    adb('install', '-r', str(apk))
    adb('shell', 'pm', 'clear', PACKAGE)
    adb('shell', 'wm', 'size', '1080x1920')
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    # A new process with no account/config must show the paywall, including after restart.
    for iteration in range(2):
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/com.meysam.divanemtiaz.MainActivity')
        tap(wait_for('راوی مافیا', scroll=True))
        wait_for('مافیا با Pro')
        wait_for('خرید اشتراک Pro')
        root = snapshot(f'offline-paywall-{iteration}')
        assert match(root, 'ثبت‌نام یا ورود') is not None
        (OUT / f'offline-paywall-{iteration}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    print('PASS: fresh offline launch and process restart both keep Mafia behind Pro/sign-in.')
finally:
    (OUT / 'logcat.txt').write_bytes(adb('logcat', '-d', '-t', '600'))
