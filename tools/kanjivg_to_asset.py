"""Convert KanjiVG kana SVGs into the app's stroke asset.

Usage: python3 -I kvg_to_asset.py <svg dir> <output file>
Output: one line per character: char<TAB>path@numX,numY<TAB>path@numX,numY ...
"""
import os, re, sys

src, out = sys.argv[1], sys.argv[2]
path_tag = re.compile(r'<path\b[^>]*>')
attr = lambda name, tag: (re.search(r'\s' + re.escape(name) + r'="([^"]*)"', tag) or [None, None])[1]
num_tag = re.compile(r'<text transform="matrix\(1 0 0 1 ([-\d.]+) ([-\d.]+)\)">(\d+)</text>')
lines = []
for fn in sorted(os.listdir(src)):
    if not fn.endswith('.svg'):
        continue
    code = fn[:-4]
    text = open(os.path.join(src, fn), encoding='utf-8').read()
    strokes = {}
    for tag in path_tag.findall(text):
        pid, d = attr('id', tag), attr('d', tag)
        m = re.fullmatch(r'kvg:' + code + r'-s(\d+)', pid or '')
        if not m or not d:
            sys.exit(f'{fn}: unexpected path tag {tag[:80]}')
        n = int(m.group(1))
        if n in strokes:
            sys.exit(f'{fn}: duplicate stroke {n}')
        if re.search(r'[^Mc\d.,\s-]', d):
            sys.exit(f'{fn}: unexpected path command in {d}')
        strokes[n] = ' '.join(d.split())
    nums = {int(n): (x, y) for x, y, n in num_tag.findall(text)}
    if sorted(strokes) != list(range(1, len(strokes) + 1)):
        sys.exit(f'{fn}: stroke numbers not 1..n: {sorted(strokes)}')
    if sorted(nums) != sorted(strokes):
        sys.exit(f'{fn}: stroke-number labels {sorted(nums)} != strokes {sorted(strokes)}')
    ch = chr(int(code, 16))
    lines.append(ch + '\t' + '\t'.join(f'{strokes[i]}@{nums[i][0]},{nums[i][1]}' for i in range(1, len(strokes) + 1)))

header = [
    '# Kana stroke-order data converted from KanjiVG (https://kanjivg.tagaini.net),',
    '# Copyright (C) 2009-2011 Ulrich Apel, licensed under CC BY-SA 3.0',
    '# (https://creativecommons.org/licenses/by-sa/3.0/). This file is shared under the same licence.',
    '# Coordinates are in KanjiVG\'s 109 x 109 box.',
    '# Format: character<TAB>stroke 1<TAB>stroke 2 ..., each stroke = SVG path data@stroke-number-label x,y',
]
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, 'w', encoding='utf-8') as f:
    f.write('\n'.join(header + lines) + '\n')
print(f'{len(lines)} characters written to {out}')
