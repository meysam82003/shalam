#!/usr/bin/env python3
"""Assembles the web version (PWA, also packed into the Windows and iPhone versions) into build/play/.

    python3 build-play.py [--api URL] [--channel web|windows|ios]

Runs TeaVM on the app's own sources, then copies the page, the app's assets and sounds, fonts and icons, and writes
the offline cache list. The result is a static folder: upload it as /play/ next to the site.
"""
import argparse, json, os, re, shutil, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
APP = os.path.join(ROOT, 'app')

ap = argparse.ArgumentParser()
ap.add_argument('--api', default='https://httpuser87890.ir/divan/api/index.php')
ap.add_argument('--channel', default='web')
ap.add_argument('--out', default=os.path.join(HERE, 'build', 'play'))
ap.add_argument('--skip-teavm', action='store_true')
args = ap.parse_args()

gradle = open(os.path.join(APP, 'build.gradle'), encoding='utf-8').read()
# The same pinned certificate key as the Android builds (public; forged certificates are refused).
LICENSE_KEY = os.environ.get('DIVAN_LICENSE_KEY') or re.search(r"setting\('divan.licenseKey', 'DIVAN_LICENSE_KEY'\) \?: '([A-Za-z0-9+/=]+)'", gradle).group(1)
version = re.search(r"versionName\s+'([^']+)'", gradle).group(1)
code = re.search(r"versionCode\s+(\d+)", gradle).group(1)

if not args.skip_teavm:
    subprocess.check_call(['gradle', '--no-daemon', '-q', 'generateJavaScript'], cwd=HERE)

out = args.out
if os.path.isdir(out):
    shutil.rmtree(out)
os.makedirs(out)

def copy(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy2(src, dst)

web = os.path.join(HERE, 'web')
for name in ('host.js', 'play.css'):
    copy(os.path.join(web, name), os.path.join(out, name))
shutil.copytree(os.path.join(web, 'icons'), os.path.join(out, 'icons'))
shutil.copytree(os.path.join(web, 'vendor'), os.path.join(out, 'vendor'))
copy(os.path.join(HERE, 'build', 'generated', 'teavm', 'js', 'divan.js'), os.path.join(out, 'divan.js'))
fonts = os.path.join(ROOT, 'site', 'site-assets', 'fonts')
for f in ('Vazirmatn-Regular.woff2', 'Vazirmatn-Bold.woff2'):
    copy(os.path.join(fonts, f), os.path.join(out, 'fonts', f))

# the app's assets (fonts are drawn with the page's own copy) and raw sounds
assets = []
src_assets = os.path.join(APP, 'src', 'main', 'assets')
for dirpath, _, files in os.walk(src_assets):
    for f in files:
        rel = os.path.relpath(os.path.join(dirpath, f), src_assets).replace(os.sep, '/')
        if rel.startswith('fonts/'):
            continue
        copy(os.path.join(dirpath, f), os.path.join(out, 'assets', rel))
        assets.append(rel)
raw = []
src_raw = os.path.join(APP, 'src', 'main', 'res', 'raw')
for f in sorted(os.listdir(src_raw)):
    if f.endswith('.mp3'):
        copy(os.path.join(src_raw, f), os.path.join(out, 'res', 'raw', f))
        raw.append(f[:-4])

config = {'package': 'com.meysam.divanemtiaz.site', 'versionName': version, 'versionCode': code, 'channel': args.channel,
          'store': 'direct', 'api': args.api, 'licenseKey': LICENSE_KEY, 'assets': sorted(assets), 'raw': raw}
html = open(os.path.join(web, 'index.template.html'), encoding='utf-8').read()
html = html.replace('__CONFIG__', json.dumps(config, ensure_ascii=False)).replace('__VERSION__', version).replace('__HTMLCLASS__', 'app-window' if args.channel == 'windows' else '')
open(os.path.join(out, 'index.html'), 'w', encoding='utf-8').write(html)
copy(os.path.join(web, 'manifest.template.webmanifest'), os.path.join(out, 'manifest.webmanifest'))

core = ['./', 'index.html', 'host.js?v=' + version, 'play.css?v=' + version, 'divan.js?v=' + version, 'vendor/polygon-clipping.min.js',
        'manifest.webmanifest', 'fonts/Vazirmatn-Regular.woff2', 'fonts/Vazirmatn-Bold.woff2', 'icons/icon-192.png', 'icons/favicon.png',
        'icons/backdrop.webp'] + ['assets/' + a for a in sorted(assets)]
lazy = ['res/raw/%s.mp3' % r for r in raw] + ['icons/icon-512.png', 'icons/maskable-192.png', 'icons/maskable-512.png', 'icons/apple-touch-icon.png']
sw = open(os.path.join(web, 'sw.template.js'), encoding='utf-8').read()
sw = sw.replace('__VERSION__', version + '-' + code).replace('__CORE__', json.dumps(core)).replace('__LAZY__', json.dumps(lazy))
open(os.path.join(out, 'sw.js'), 'w', encoding='utf-8').write(sw)
open(os.path.join(out, '.htaccess'), 'w').write(
    "AddType application/manifest+json .webmanifest\nAddType font/woff2 .woff2\nAddType image/webp .webp\n"
    "<IfModule mod_deflate.c>\nAddOutputFilterByType DEFLATE text/html text/css application/javascript application/manifest+json text/tab-separated-values application/json\n</IfModule>\n"
    "<Files \"sw.js\">\nHeader set Cache-Control \"no-cache\"\n</Files>\n<Files \"index.html\">\nHeader set Cache-Control \"no-cache\"\n</Files>\n")
total = sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(out) for f in fs)
print('built %s (version %s, %d files, %.1f MB)' % (out, version, sum(len(fs) for _, _, fs in os.walk(out)), total / 1e6))
