#!/usr/bin/env python3
"""Render the Divan 5.7 mark at Android and PWA icon sizes. Requires Pillow; build-time independent."""
from pathlib import Path
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
BG = '#091827'
ACCENT = '#72DDF1'
INK = '#F2F7FC'

def render(size, transparent=False, adaptive=False):
    scale = 4
    img = Image.new('RGBA', (size * scale, size * scale), (0, 0, 0, 0) if transparent else BG)
    draw = ImageDraw.Draw(img)
    k = size * scale / 100
    if adaptive:
        # Foreground remains inside Android's adaptive-icon safe zone.
        k *= .62
    offset = (size * scale - 100 * k) / 2
    def box(coords): return tuple(offset + v * k for v in coords)
    draw.rounded_rectangle(box((24, 23, 37, 76)), radius=6 * k, fill=ACCENT)
    draw.arc(box((23, 23, 78, 76)), start=270, end=90, fill=ACCENT, width=round(12 * k))
    for x in (70, 83): draw.ellipse(box((x - 4, 76, x + 4, 84)), fill=INK)
    return img.resize((size, size), Image.Resampling.LANCZOS)

if __name__ == '__main__':
    res = ROOT / 'app/src/main/res'
    for dpi, size, adaptive in [('mdpi',48,108),('hdpi',72,162),('xhdpi',96,216),('xxhdpi',144,324),('xxxhdpi',192,432)]:
        folder = res / ('mipmap-' + dpi)
        render(size).save(folder / 'ic_launcher.png')
        render(adaptive, transparent=True, adaptive=True).save(folder / 'ic_launcher_foreground.png')
        Image.new('RGB',(adaptive,adaptive),BG).save(folder / 'ic_launcher_background.png')
    web = ROOT / 'webapp/web/icons'
    for name, size in [('favicon',48),('apple-touch-icon',180),('icon-192',192),('icon-512',512),('maskable-192',192),('maskable-512',512)]:
        render(size, adaptive=name.startswith('maskable')).save(web / (name + '.png'))
