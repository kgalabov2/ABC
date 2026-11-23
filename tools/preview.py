"""Render stroke assets as an HTML contact sheet, for checking shapes and stroke order by eye.

Usage: python3 -I tools/preview.py <asset.txt> <out.html> [lines|square]

Each cell shows the typed character (system font) next to the stroke data: strokes coloured
from red (first) to blue (last), a dot at each stroke's start, and the stroke-number labels.
"""
import html, sys

asset, out = sys.argv[1], sys.argv[2]
grid = sys.argv[3] if len(sys.argv) > 3 else 'square'
COLORS = ['#d7263d', '#f46036', '#c5a000', '#1b998b', '#2e86ab', '#5c4bb5', '#a23b72', '#555']

cells = []
for line in open(asset, encoding='utf-8'):
    if not line.strip() or line.startswith('#'):
        continue
    ch, *strokes = line.rstrip('\n').split('\t')
    g = []
    if grid == 'lines':
        for y, dash in ((22, '2,2'), (47, '2,2'), (77, ''), (98, '2,2')):
            g.append(f'<line x1="0" x2="109" y1="{y}" y2="{y}" stroke="#bbb" stroke-dasharray="{dash}"/>')
    else:
        g.append('<line x1="54.5" x2="54.5" y1="0" y2="109" stroke="#ccc" stroke-dasharray="2,2"/>')
        g.append('<line y1="54.5" y2="54.5" x1="0" x2="109" stroke="#ccc" stroke-dasharray="2,2"/>')
    for i, s in enumerate(strokes):
        d, label = s.rsplit('@', 1)
        lx, ly = label.split(',')
        c = COLORS[i % len(COLORS)]
        sx, sy = d[1:].replace(',', ' ').split()[:2]
        g.append(f'<path d="{d}" fill="none" stroke="{c}" stroke-width="4" stroke-linecap="round" '
                 f'stroke-linejoin="round" opacity="0.85"/>')
        g.append(f'<circle cx="{sx}" cy="{sy}" r="2.6" fill="{c}"/>')
        g.append(f'<text x="{lx}" y="{ly}" font-size="8" fill="{c}" font-family="sans-serif">{i + 1}</text>')
    cells.append(
        f'<div class="cell"><div class="font">{html.escape(ch)}</div>'
        f'<svg viewBox="0 0 109 109" width="130" height="130"><rect width="109" height="109" fill="#fff" '
        f'stroke="#999"/>{"".join(g)}</svg></div>')

with open(out, 'w', encoding='utf-8') as f:
    f.write('<!doctype html><meta charset="utf-8"><style>'
            'body{font-family:sans-serif;margin:8px;background:#eee}'
            '.cell{display:inline-flex;align-items:center;gap:4px;margin:3px;background:#fafafa;padding:3px}'
            '.font{font-size:64px;width:72px;text-align:center}</style>'
            + ''.join(cells))
print(f'{len(cells)} cells written to {out}')
