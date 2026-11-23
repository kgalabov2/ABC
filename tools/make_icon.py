"""Draws the launcher icon: four letters, one per corner, each from a different language and found in only that
script: あ (Japanese), λ (Greek), Ж (Bulgarian) and Ü (German), drawn as pen strokes, one colour per letter.
(ß was tried first, but drawn as strokes it reads as Greek β.)

Usage: python3 -I tools/make_icon.py <project root> [<preview.png>]

Writes app/src/main/res/drawable/ic_launcher_foreground.xml (an adaptive-icon foreground, 108 × 108 dp with the
letters inside the 66 dp safe circle) and the launcher background colour, and, when asked, a PNG preview masked
to a circle (needs Pillow). あ and λ are the stroke data the app itself uses (あ from KanjiVG, CC BY-SA 3.0;
λ drawn for the app); Ж and Ü are drawn here.
"""
import math
import os
import re
import sys

STROKE = 3.0
BACKGROUND = '#FF1E2B4A'
CENTRE, OFFSET, HALF = 54.0, 13.0, 10.0  # cell centres at 54 ± 13, cells 20 × 20 dp: 6 dp apart, inside the circle
# (letter, colour, corner): あ red (Japan), λ blue (Greece), Ж green (Bulgaria), Ü gold (Germany).
LETTERS = [('あ', '#FFFF6B5E', (-1, -1)), ('λ', '#FF5CC8FF', (1, -1)), ('Ж', '#FF34D399', (-1, 1)), ('Ü', '#FFFFC83D', (1, 1))]


def parse(d):
    """SVG path data (M, L, H, C, S, absolute or relative) → list of absolute cubic segments."""
    tokens = re.findall(r'[A-Za-z]|-?\d*\.?\d+(?:e-?\d+)?', d)
    i, cmd, pos, last_ctrl, segs = 0, None, (0.0, 0.0), None, []

    def num():
        nonlocal i
        v = float(tokens[i])
        i += 1
        return v

    while i < len(tokens):
        if re.match(r'[A-Za-z]', tokens[i]):
            cmd = tokens[i]
            i += 1
        rel = cmd.islower()
        ox, oy = pos if rel else (0.0, 0.0)
        c = cmd.upper()
        if c == 'M':
            pos = (ox + num(), oy + num())
            cmd = 'l' if rel else 'L'
            last_ctrl = None
        elif c == 'L':
            p = (ox + num(), oy + num())
            segs.append((pos, pos, p, p))
            pos, last_ctrl = p, None
        elif c == 'H':
            p = ((pos[0] if rel else 0.0) + num(), pos[1])
            segs.append((pos, pos, p, p))
            pos, last_ctrl = p, None
        elif c == 'C':
            c1 = (ox + num(), oy + num())
            c2 = (ox + num(), oy + num())
            p = (ox + num(), oy + num())
            segs.append((pos, c1, c2, p))
            pos, last_ctrl = p, c2
        elif c == 'S':
            c1 = (2 * pos[0] - last_ctrl[0], 2 * pos[1] - last_ctrl[1]) if last_ctrl else pos
            c2 = (ox + num(), oy + num())
            p = (ox + num(), oy + num())
            segs.append((pos, c1, c2, p))
            pos, last_ctrl = p, c2
        else:
            raise ValueError(f'unsupported path command {cmd}')
    return segs


def fmt(segs):
    out, pos = [], None
    for p0, c1, c2, p in segs:
        if pos != p0:
            out.append(f'M{p0[0]:.2f},{p0[1]:.2f}')
        if c1 == p0 and c2 == p:
            out.append(f'L{p[0]:.2f},{p[1]:.2f}')
        else:
            out.append(f'C{c1[0]:.2f},{c1[1]:.2f} {c2[0]:.2f},{c2[1]:.2f} {p[0]:.2f},{p[1]:.2f}')
        pos = p
    return ''.join(out)


def bezier(seg, t):
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = seg
    u = 1 - t
    return (u ** 3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t ** 3 * x3,
            u ** 3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t ** 3 * y3)


def bounds(strokes):
    pts = [bezier(s, k / 20) for segs in strokes for s in segs for k in range(21)]
    return min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts)


def fit(strokes, box):
    """Scales strokes uniformly into box = (x0, y0, x1, y1), centred."""
    x0, y0, x1, y1 = bounds(strokes)
    bx0, by0, bx1, by1 = box
    k = min((bx1 - bx0) / (x1 - x0), (by1 - by0) / (y1 - y0))
    dx = bx0 + ((bx1 - bx0) - (x1 - x0) * k) / 2 - x0 * k
    dy = by0 + ((by1 - by0) - (y1 - y0) * k) / 2 - y0 * k
    tr = lambda p: (p[0] * k + dx, p[1] * k + dy)
    return [[tuple(tr(p) for p in seg) for seg in segs] for segs in strokes]


# Ж and Ü in a 100-unit design box (y down), in the order and direction they are usually handwritten.
ZHE = [
    'M10,0 L50,50 L10,100',   # left arms: top-left in to the middle, out to bottom-left
    'M50,0 L50,100',          # stem
    'M90,0 L50,50 L90,100',   # right arms
]
U_UMLAUT = [
    'M15,26 L15,70 C15,89 29,100 50,100 C71,100 85,89 85,70 L85,26',  # the U
    'M33,4 L33,4.5',  # the two dots: a very short line with a round cap
    'M67,4 L67,4.5',
]


def strokes_of(root, asset, char):
    for line_ in open(os.path.join(root, 'app/src/main/assets', asset), encoding='utf-8'):
        f = line_.rstrip('\n').split('\t')
        if f[0] == char:
            return [parse(s.split('@')[0]) for s in f[1:]]
    raise SystemExit(f'{char} not found in {asset}')


def letters(root):
    source = {
        'あ': lambda: strokes_of(root, 'strokes_kana.txt', 'あ'),
        'λ': lambda: strokes_of(root, 'strokes_greek.txt', 'λ'),
        'Ж': lambda: [parse(d) for d in ZHE],
        'Ü': lambda: [parse(d) for d in U_UMLAUT],
    }
    drawn = []
    for char, colour, (sx, sy) in LETTERS:
        cx, cy = CENTRE + sx * OFFSET, CENTRE + sy * OFFSET
        drawn.append((colour, fit(source[char](), (cx - HALF, cy - HALF, cx + HALF, cy + HALF))))
    return drawn


def write_vector(root, drawn):
    paths = []
    for colour, strokes in drawn:
        for segs in strokes:
            paths.append(
                '    <path\n'
                f'        android:pathData="{fmt(segs)}"\n'
                f'        android:strokeColor="{colour}"\n'
                f'        android:strokeWidth="{STROKE}"\n'
                '        android:strokeLineCap="round"\n'
                '        android:strokeLineJoin="round" />'
            )
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- One letter per corner, each from a different script: あ (strokes from KanjiVG, CC BY-SA 3.0,\n'
        '     https://kanjivg.tagaini.net), λ, Ж and Ü. Generated by tools/make_icon.py; edit that, not this file. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n' + '\n'.join(paths) + '\n</vector>\n'
    )
    open(os.path.join(root, 'app/src/main/res/drawable/ic_launcher_foreground.xml'), 'w', encoding='utf-8').write(xml)
    colours = os.path.join(root, 'app/src/main/res/values/colors.xml')
    text = open(colours, encoding='utf-8').read()
    text = re.sub(r'(<color name="launcher_background">)[^<]*(</color>)', rf'\g<1>{BACKGROUND}\g<2>', text)
    open(colours, 'w', encoding='utf-8').write(text)


def preview(drawn, out, size=1024):
    from PIL import Image, ImageDraw
    s = size / 108
    rgb = lambda c: '#' + c[3:]
    big = Image.new('RGB', (size, size), rgb(BACKGROUND))
    d = ImageDraw.Draw(big)
    w = STROKE * s
    for colour, strokes in drawn:
        for segs in strokes:
            pts = [(x * s, y * s) for seg in segs for x, y in (bezier(seg, k / 60) for k in range(61))]
            # Round pen: a disc at every sample point, so curves and joins come out smooth.
            for x, y in pts:
                d.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=rgb(colour))
            for a, b in zip(pts, pts[1:]):
                d.line((a, b), fill=rgb(colour), width=round(w))
    # The launcher shows the middle 72 dp of the 108 dp layer, masked; a circle is the strictest mask.
    crop = big.crop((round(18 * s), round(18 * s), round(90 * s), round(90 * s)))
    mask = Image.new('L', crop.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, crop.size[0] - 1, crop.size[1] - 1), fill=255)
    result = Image.new('RGB', crop.size, 'white')
    result.paste(crop, (0, 0), mask)
    result.resize((384, 384), Image.LANCZOS).save(out)


if __name__ == '__main__':
    root = sys.argv[1]
    drawn = letters(root)
    write_vector(root, drawn)
    if len(sys.argv) > 2:
        preview(drawn, sys.argv[2])
    ink = max(math.hypot(x - CENTRE, y - CENTRE) for _, strokes in drawn for segs in strokes for seg in segs
              for x, y in (bezier(seg, k / 40) for k in range(41))) + STROKE / 2
    print(f'farthest ink {ink:.1f} dp from the centre (must stay within the 33 dp safe circle)')
    if ink > 33:
        raise SystemExit('the letters leave the safe circle: make them smaller')
