#!/usr/bin/env python3
"""Writes iPhone.mobileconfig: a Web Clip only (no MDM, certificate, VPN or proxy) that adds «دیوان امتیاز» to the
home screen and opens the web version (/play/) full screen, like an app. It is the web version, not a native iOS app.

    python3 build-iphone.py [--url https://httpuser87890.ir/play/] [--out build/iPhone.mobileconfig]

Unsigned profiles show «Not Verified» on install; sign on the server with the site's own certificate:
    openssl smime -sign -signer cert.pem -inkey key.pem -certfile chain.pem -nodetach -outform der \
        -in iPhone.mobileconfig -out iPhone-signed.mobileconfig
"""
import argparse, base64, os, plistlib, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ap = argparse.ArgumentParser()
ap.add_argument('--url', default='https://httpuser87890.ir/play/')
ap.add_argument('--out', default=os.path.join(HERE, 'build', 'iPhone.mobileconfig'))
args = ap.parse_args()

icon = open(os.path.join(HERE, 'web', 'icons', 'apple-touch-icon.png'), 'rb').read()
# Fixed identifiers: installing a newer profile replaces the old one instead of adding a second icon.
NS = uuid.UUID('6f0d8a52-5d0e-4c1b-9a43-2f0d3c9b7e11')
clip = {
    'PayloadType': 'com.apple.webClip.managed',
    'PayloadVersion': 1,
    'PayloadIdentifier': 'com.meysam.divanemtiaz.webclip',
    'PayloadUUID': str(uuid.uuid5(NS, 'webclip')).upper(),
    'PayloadDisplayName': 'دیوان امتیاز',
    'PayloadDescription': 'میان‌بُر دیوان امتیاز روی صفحهٔ اصلی',
    'Label': 'دیوان امتیاز',
    'URL': args.url,
    'Icon': icon,
    'Precomposed': True,
    'FullScreen': True,
    'IsRemovable': True,
}
profile = {
    'PayloadType': 'Configuration',
    'PayloadVersion': 1,
    'PayloadIdentifier': 'com.meysam.divanemtiaz.profile',
    'PayloadUUID': str(uuid.uuid5(NS, 'profile')).upper(),
    'PayloadDisplayName': 'دیوان امتیاز',
    'PayloadDescription': 'فقط یک میان‌بُر (Web Clip) برای اجرای نسخهٔ وب دیوان امتیاز به‌صورت تمام‌صفحه اضافه می‌کند. هیچ تنظیم دیگری روی دستگاه تغییر نمی‌کند و هر وقت بخواهید از «تنظیمات ← عمومی ← VPN و مدیریت دستگاه» حذف می‌شود.',
    'PayloadOrganization': 'دیوان امتیاز',
    'PayloadRemovalDisallowed': False,
    'PayloadContent': [clip],
}
os.makedirs(os.path.dirname(args.out), exist_ok=True)
with open(args.out, 'wb') as f:
    plistlib.dump(profile, f, fmt=plistlib.FMT_XML)
print('wrote', args.out, os.path.getsize(args.out), 'bytes')
