#!/usr/bin/env python3
"""Render the Divan 5.7 mark at Android and PWA icon sizes. Requires Pillow; build-time independent."""
from pathlib import Path
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
BG = '#021715'

def render(size, transparent=False, adaptive=False):
    scale = 4
    img = Image.new('RGBA', (size * scale, size * scale), (0, 0, 0, 0) if transparent else BG)
    k = size * scale / 100 * (.62 if adaptive else .9)
    ox = (size * scale - 100 * k) / 2
    oy = (size * scale - 76 * k) / 2
    def pt(x, y): return (ox + x*k, oy + y*k)
    mask = Image.new('L', img.size, 0)
    m = ImageDraw.Draw(mask)
    m.polygon([pt(x,y) for x,y in [(20,53),(12,19),(34,34),(50,10),(66,34),(88,19),(80,53)]],fill=255)
    m.rounded_rectangle((*pt(19,55),*pt(81,64)),radius=4*k,fill=255)
    for x,y in [(12,17),(50,8),(88,17)]:m.ellipse((*pt(x-4,y-4),*pt(x+4,y+4)),fill=255)
    metal = Image.new('RGBA', img.size)
    pixels = metal.load()
    stops = [(255,240,185),(235,182,71),(166,104,19),(255,218,139)]
    for x in range(metal.width):
        at=x/max(1,metal.width-1)*3; i=min(2,int(at)); f=at-i
        color=tuple(round(stops[i][j]*(1-f)+stops[i+1][j]*f) for j in range(3))+(255,)
        for y in range(metal.height): pixels[x,y]=color
    img.paste(metal,(0,0),mask)
    draw=ImageDraw.Draw(img)
    draw.ellipse((*pt(46,38),*pt(54,46)),fill='#21876A')
    draw.line([pt(21,55),pt(79,55)],fill='#FFF2BE',width=max(1,round(k)))
    return img.resize((size,size),Image.Resampling.LANCZOS)

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
