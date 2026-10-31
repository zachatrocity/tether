#!/usr/bin/env python3
"""Reproduce Tether's vector Orbit mark and density-specific launcher assets.

Production geometry redrawn from the selected Orbit concept; no generated raster is
embedded in the APK. Requires Pillow only for pre-adaptive Android launcher assets.
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
TEAL, MINT = '#073D39', '#A5F3C7'


def point(angle):
    a = math.radians(angle)
    return 54 + 23 * math.cos(a), 54 + 23 * math.sin(a)


def paths(color):
    result = []
    for start, end in [(185, 270), (5, 90)]:
        x, y = point(start)
        ex, ey = point(end)
        result.append(f'<path android:fillColor="#00000000" android:strokeColor="{color}" '
                      f'android:strokeWidth="5" android:strokeLineCap="round" '
                      f'android:pathData="M{x:.4f},{y:.4f} A23,23 0,0 1,{ex:.4f},{ey:.4f}"/>')
    for angle, radius in [(315, 7), (135, 9)]:
        x, y = point(angle)
        result.append(f'<path android:fillColor="{color}" android:pathData="M{x-radius:.4f},{y:.4f} '
                      f'a{radius},{radius} 0,1 0,{2*radius},0 a{radius},{radius} 0,1 0,{-2*radius},0"/>')
    return '\n    '.join(result)


def vector(color, background=False):
    bg = f'<path android:fillColor="{TEAL}" android:pathData="M0,0h108v108H0z"/>' if background else ''
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:width="108dp" android:height="108dp" '
            'android:viewportWidth="108" android:viewportHeight="108">\n    '
            + bg + paths(color) + '\n</vector>\n')


def render(size, background=False, round_icon=False):
    scale = 8
    n = size * scale
    image = Image.new('RGBA', (n, n))
    draw = ImageDraw.Draw(image)
    if background:
        if round_icon:
            draw.ellipse((0, 0, n-1, n-1), fill=TEAL)
        else:
            draw.rounded_rectangle((0, 0, n-1, n-1), radius=n*0.23, fill=TEAL)
    def xy(x, y):
        return x*n/108, y*n/108
    for start, end in [(185, 270), (5, 90)]:
        curve = [xy(*point(start + (end-start)*i/160)) for i in range(161)]
        draw.line(curve, fill=MINT, width=round(5*n/108), joint="curve")
        for a in (start, end):
            x, y = point(a)
            draw.ellipse((*xy(x-2.5, y-2.5), *xy(x+2.5, y+2.5)), fill=MINT)
    for a, r in [(315, 7), (135, 9)]:
        x, y = point(a)
        draw.ellipse((*xy(x-r, y-r), *xy(x+r, y+r)), fill=MINT)
    return image.resize((size, size), Image.Resampling.LANCZOS)


if __name__ == '__main__':
    for name, color in [('ic_launcher_foreground', MINT), ('ic_launcher_monochrome', '#FFFFFF'),
                        ('opentagviewer_icon', TEAL), ('opentagviewer_icon_themed', '?attr/colorPrimary')]:
        (RES / 'drawable' / f'{name}.xml').write_text(vector(color))
    (RES / 'drawable-night/opentagviewer_icon_themed.xml').write_text(vector('?attr/colorPrimary'))
    (RES / 'drawable/ic_launcher_background.xml').write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android">'
        f'<solid android:color="{TEAL}"/></shape>\n')
    for dpi, multiplier in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
        folder = RES / f'mipmap-{dpi}'
        for name, bg, rnd in [('ic_launcher', True, False), ('ic_launcher_round', True, True),
                              ('ic_launcher_foreground', False, False)]:
            size = round((48 if bg else 108) * multiplier)
            render(size, bg, rnd).save(folder / f'{name}.webp', lossless=True)
    out = ROOT / 'branding'
    out.mkdir(exist_ok=True)
    render(512, True).save(out / 'tether-orbit.png')
