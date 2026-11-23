"""Hand-drawn stroke data for the Greek alphabet, written to app/src/main/assets/strokes_greek.txt.

Usage: python3 -I tools/strokes_greek.py

Coordinates use the same 109 x 109 box as the KanjiVG kana data. Writing lines:
  ascender / capital height y=22, x-height y=47, baseline y=77, descender y=98.
Each stroke is SVG path data, listed in writing order and in the direction the pen moves.
Stroke-number label positions are placed automatically next to each stroke's start.
"""
import os, re

ASC, XH, BASE, DESC = 22, 47, 77, 98
K = 0.5523  # cubic Bezier circle constant


def ellipse(cx, cy, rx, ry):
    """Closed ellipse, starting at the top and going counter-clockwise (the usual pen direction for O)."""
    kx, ky = K * rx, K * ry
    f = lambda v: f'{v:.2f}'.rstrip('0').rstrip('.')
    return (f'M{f(cx)},{f(cy - ry)} '
            f'C{f(cx - kx)},{f(cy - ry)} {f(cx - rx)},{f(cy - ky)} {f(cx - rx)},{f(cy)} '
            f'C{f(cx - rx)},{f(cy + ky)} {f(cx - kx)},{f(cy + ry)} {f(cx)},{f(cy + ry)} '
            f'C{f(cx + kx)},{f(cy + ry)} {f(cx + rx)},{f(cy + ky)} {f(cx + rx)},{f(cy)} '
            f'C{f(cx + rx)},{f(cy - ky)} {f(cx + kx)},{f(cy - ry)} {f(cx)},{f(cy - ry)}')


CAPITALS = {
    'Α': ['M54,22 L31,77', 'M54,22 L77,77', 'M39,58 L69,58'],
    'Β': ['M36,22 L36,77', 'M36,22 L56,22 C71,22 71,48 56,48 L36,48',
          'M36,48 L59,48 C75,48 75,77 59,77 L36,77'],
    'Γ': ['M38,22 L38,77', 'M38,22 L74,22'],
    'Δ': ['M54,22 L30,77', 'M54,22 L78,77', 'M30,77 L78,77'],
    'Ε': ['M38,22 L38,77', 'M38,22 L72,22', 'M38,49.5 L66,49.5', 'M38,77 L72,77'],
    'Ζ': ['M34,22 L74,22 L34,77 L75,77'],
    'Η': ['M34,22 L34,77', 'M74,22 L74,77', 'M34,49.5 L74,49.5'],
    'Θ': [ellipse(54, 49.5, 23, 27.5), 'M44,49.5 L64,49.5'],
    'Ι': ['M54,22 L54,77'],
    'Κ': ['M37,22 L37,77', 'M73,22 L37,57', 'M48,46.3 L74,77'],
    'Λ': ['M54,22 L30,77', 'M54,22 L78,77'],
    'Μ': ['M30,22 L30,77', 'M30,22 L54,62 L78,22 L78,77'],
    'Ν': ['M34,22 L34,77', 'M34,22 L74,77 L74,22'],
    'Ξ': ['M34,22 L74,22', 'M40,49.5 L68,49.5', 'M33,77 L75,77'],
    'Ο': [ellipse(54, 49.5, 23, 27.5)],
    'Π': ['M34,22 L34,77', 'M34,22 L74,22 L74,77'],
    'Ρ': ['M36,22 L36,77', 'M36,22 L57,22 C74,22 74,52 57,52 L36,52'],
    'Σ': ['M74,22 L34,22 L57,49.5 L34,77 L75,77'],
    'Τ': ['M32,22 L76,22', 'M54,22 L54,77'],
    'Υ': ['M32,22 L54,51', 'M76,22 L54,51', 'M54,51 L54,77'],
    'Φ': [ellipse(54, 49.5, 24, 17), 'M54,22 L54,77'],
    'Χ': ['M32,22 L76,77', 'M76,22 L32,77'],
    'Ψ': ['M30,22 C30,48 38,58 54,58 C70,58 78,48 78,22', 'M54,22 L54,77'],
    'Ω': ['M30,77 L44,77 C33,70 30,58 30,48 C30,32 41,22 54,22 C67,22 78,32 78,48 '
          'C78,58 75,70 64,77 L78,77'],
}

SMALL = {
    'α': ['M68,54 C65,49 59,47 53,47 C42,47 35,54 35,63 C35,71 41,77 50,77 C59,77 65,69 70,47 '
          'C68,60 70,77 78,77'],
    'β': ['M38,98 L38,36 C38,26 44,22 52,22 C61,22 66,28 66,35 C66,42 60,47 50,47 '
          'C64,47 70,55 70,63 C70,71 63,77 54,77 C47,77 41,74 38,70'],
    'γ': ['M32,50 C40,45 46,56 53,78 C56,88 56,97 51,97 C46,97 47,88 52,78 C58,66 66,54 76,47'],
    'δ': ['M68,26 C62,21 48,21 47,28 C46,35 56,39 62,44 C68,49 71,55 71,62 C71,71 63,77 53,77 '
          'C43,77 35,71 35,62 C35,53 44,47 56,44'],
    'ε': ['M68,52 C64,48 59,47 53,47 C44,47 39,51 39,55 C39,60 44,62 52,62 C42,62 36,66 36,70 '
          'C36,75 43,77 52,77 C59,77 65,75 69,71'],
    'ζ': ['M44,24 C50,27 60,26 66,23 C52,34 36,48 36,62 C36,72 44,77 56,78 C66,79 68,84 66,90 '
          'C64,95 60,97 56,97'],
    'η': ['M34,50 C37,48 38,52 38,56 L38,77 L38,60 C40,51 47,47 54,47 C63,47 68,53 68,60 L68,98'],
    'θ': [ellipse(54, 49.5, 16, 27.5), 'M38,49.5 L70,49.5'],
    'ι': ['M52,47 L52,70 C52,75 55,77 61,76'],
    'κ': ['M38,47 L38,77', 'M68,47 L39,63 L69,77'],
    'λ': ['M38,23 C44,21 48,27 52,37 L71,77', 'M54.85,43 L35,77'],
    'μ': ['M38,47 L38,98 L38,64 C38,73 44,77 51,77 C59,77 66,71 66,62 L66,47 L66,71 '
          'C66,76 69,78 74,76'],
    'ν': ['M36,48 C40,46 43,50 53,77 C64,66 72,57 70,47'],
    'ξ': ['M44,24 C50,27 60,26 66,23 C54,26 44,30 44,36 C44,42 52,45 60,45 C48,45 38,52 38,61 '
          'C38,71 46,77 56,78 C66,79 68,84 66,90 C64,95 60,97 56,97'],
    'ο': [ellipse(54, 62, 16, 15)],
    'π': ['M32,49 C44,47 62,47 77,47', 'M44,48 L42,77', 'M64,48 C64,62 64,72 70,77'],
    'ρ': ['M38,98 L38,62 C38,53 45,47 54,47 C63,47 70,54 70,62 C70,70 63,77 54,77 '
          'C47,77 41,74 38,70'],
    'σ': ['M58,47 C46,47 38,54 38,62 C38,71 45,77 54,77 C63,77 70,71 70,62 C70,54 64,47 56,47 L77,47'],
    'ς': ['M70,50 C66,47 60,47 55,47 C44,47 37,55 37,63 C37,71 44,75 54,77 C62,79 64,84 62,89 '
          'C60,94 56,96 52,95'],
    'τ': ['M34,47 L74,47', 'M54,47 L54,71 C54,75 57,77 62,76'],
    'υ': ['M36,50 C38,48 40,49 40,54 L40,64 C40,72 46,77 54,77 C64,77 70,67 70,56 C70,51 69,48 67,47'],
    'φ': [ellipse(54, 62, 20, 15), 'M54,32 L54,98'],
    'χ': ['M36,47 L72,98', 'M72,47 L36,98'],
    'ψ': ['M34,48 C36,52 36,56 36,60 C36,70 44,76 54,76 C64,76 72,70 72,60 C72,54 71,50 70,47',
          'M54,32 L54,98'],
    'ω': ['M42,49 C34,55 34,65 37,71 C40,77 48,79 52,72 C53,70 54,66 54,62 C54,66 55,70 56,72 '
          'C60,79 68,77 71,71 C74,65 74,55 66,49'],
}


def start_of(path):
    x, y = re.match(r'M\s*([-\d.]+)[ ,]([-\d.]+)', path).groups()
    return float(x), float(y)


def place_labels(paths):
    """Put each stroke number (font size 8, baseline-left anchored) beside its start, avoiding earlier labels."""
    placed = []
    for p in paths:
        sx, sy = start_of(p)
        for dx, dy in ((-8, -1), (3, -1), (-8, 8), (3, 8), (-12, 3), (6, 3)):
            lx, ly = sx + dx, sy + dy
            if not (1 <= lx <= 103 and 8 <= ly <= 107):
                continue
            if all(abs(lx - px) > 6 or abs(ly - py) > 8 for px, py in placed):
                break
        placed.append((lx, ly))
    return placed


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, 'app', 'src', 'main', 'assets', 'strokes_greek.txt')
    lines = [
        '# Greek stroke-order data, drawn for this app in the 109 x 109 box used by the kana data.',
        '# Writing lines: capital/ascender y=22, x-height y=47, baseline y=77, descender y=98.',
        '# Generated by tools/strokes_greek.py; edit that file, not this one.',
        '# Format: character<TAB>stroke 1<TAB>stroke 2 ..., each stroke = SVG path data@stroke-number-label x,y',
    ]
    for table in (CAPITALS, SMALL):
        for ch, paths in table.items():
            labels = place_labels(paths)
            lines.append(ch + '\t' + '\t'.join(
                ' '.join(p.split()) + f'@{lx:g},{ly:g}' for p, (lx, ly) in zip(paths, labels)))
    with open(out, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
    print(f'{len(CAPITALS) + len(SMALL)} characters written to {out}')


if __name__ == '__main__':
    main()
