"""Checks the grammar lesson files (app/src/main/assets/grammar_<code>.txt) for format errors.

Usage: python3 -I tools/check_grammar.py <grammar file> [<grammar file> ...]

File format, one item per line; blank lines and lines starting with "#" are ignored:

    @lesson <id>          starts a lesson; id = a-z, 0-9 and "-", unique in the file, never renamed
                          (it is the progress key "g:<code>:<id>")
    title: <English title>
    level: A1 | A2
    intro: <one English sentence shown in the lesson list>
    p: <English paragraph; **bold** marks words to notice>
    table: <caption, may be empty>
    | header | header | ...         rows of the table just started; the first row is the header;
    | cell   | cell   | ...         every row has the same number of cells; **bold** allowed
    e: <sentence> | <reading> | <English> [| <text for the speech engine>]
    q: <prompt with ___ once> | <answer> | <wrong>; <wrong>; <wrong> | <English of the full sentence> [| <why> [| <speech text>]]

p:, table: and e: lines appear in the lesson in file order; q: lines are its practice questions.
Reading: Japanese = Hepburn romaji with macrons, particles as spoken (wa, e, o); Bulgarian = the app's
transliteration with the stress mark (véčer, săm); German = empty. Japanese sentences, answers and table
cells use kana only; the speech text (examples: 4th field; questions: 6th field, the finished sentence, "why"
may be left empty before it) may use kanji so the voice reads them naturally.
"""
import re
import sys

ID = re.compile(r'^[a-z0-9-]+$')
BLANK = re.compile(r'(?<!_)___(?!_)')
KANJI = re.compile(r'[㐀-䶿一-鿿豈-﫿]')
KEYS = {'title', 'level', 'intro', 'p', 'table', 'e', 'q'}


def check(path):
    code = re.search(r'grammar_([a-z]{2})\.txt$', path)
    code = code.group(1) if code else ''
    errors, lessons, lesson, in_table, width = [], {}, None, False, 0

    def err(n, msg):
        errors.append(f'{path}:{n}: {msg}')

    def no_kanji(n, what, text):
        if code == 'ja' and KANJI.search(text):
            err(n, f'kanji in {what} (kana only; put kanji in the speech text): {text}')

    for n, raw in enumerate(open(path, encoding='utf-8'), start=1):
        line = raw.rstrip('\n')
        if not line.strip() or line.startswith('#'):
            continue
        if line != line.rstrip():
            err(n, 'trailing spaces')
        if line.startswith('@lesson'):
            lid = line[len('@lesson'):].strip()
            if not ID.match(lid):
                err(n, f'bad lesson id {lid!r}')
            if lid in lessons:
                err(n, f'duplicate lesson id {lid}')
            lesson = lessons.setdefault(lid, {'line': n, 'title': 0, 'level': 0, 'intro': 0, 'p': 0, 'e': 0, 'q': 0, 'table': 0})
            in_table = False
            continue
        if lesson is None:
            err(n, 'text before the first @lesson')
            continue
        if line.startswith('|'):
            if not in_table:
                err(n, 'table row without a table: line')
                continue
            # One pipe off each end, as the app does: "|| x |" is an empty cell and "x".
            row = line.strip()
            row = row[1:] if row.startswith('|') else row
            row = row[:-1] if row.endswith('|') else row
            cells = [c.strip() for c in row.split('|')]
            if width and len(cells) != width:
                err(n, f'row has {len(cells)} cells, header has {width}')
            width = width or len(cells)
            for c in cells:
                no_kanji(n, 'table cell', c)
            continue
        key, sep, value = line.partition(':')
        if not sep or key not in KEYS:
            err(n, f'unknown line: {line[:60]}')
            continue
        value = value.strip()
        in_table = key == 'table'
        if key == 'table':
            width = 0
        lesson[key] += 1
        if key in ('title', 'intro', 'p') and not value:
            err(n, f'empty {key}')
        if key == 'level' and value not in ('A1', 'A2'):
            err(n, f'level must be A1 or A2, not {value!r}')
        if key == 'e':
            f = [x.strip() for x in value.split('|')]
            if len(f) not in (3, 4) or not f[0] or not f[2]:
                err(n, 'e: needs sentence | reading | English [| speech text]')
                continue
            if code in ('ja', 'bg') and not f[1]:
                err(n, 'missing reading')
            if code == 'de' and f[1]:
                err(n, 'German examples have no reading (leave it empty)')
            no_kanji(n, 'example', f[0])
        if key == 'q':
            f = [x.strip() for x in value.split('|')]
            if len(f) not in (4, 5, 6):
                err(n, 'q: needs prompt | answer | wrong; wrong; wrong | English [| why [| speech text]]')
                continue
            prompt, answer, wrongs, english = f[:4]
            wrongs = [w.strip() for w in wrongs.split(';')]
            if len(BLANK.findall(prompt)) != 1 or '____' in prompt:
                err(n, 'prompt must contain ___ (three underscores) exactly once')
            if not answer or not english:
                err(n, 'empty answer or English')
            if len(wrongs) != 3 or any(not w for w in wrongs):
                err(n, 'exactly three wrong options are needed')
            if len({answer, *wrongs}) != 1 + len(wrongs):
                err(n, 'options repeat or a wrong option equals the answer')
            for t in (prompt, answer, *wrongs):
                no_kanji(n, 'question', t)
    for lid, l in lessons.items():
        for key, least in (('title', 1), ('level', 1), ('intro', 1), ('p', 1), ('e', 3), ('q', 6)):
            if l[key] < least:
                err(l['line'], f'lesson {lid}: {l[key]} {key}: lines, needs at least {least}')
        for key in ('title', 'level', 'intro'):
            if l[key] > 1:
                err(l['line'], f'lesson {lid}: {key}: given {l[key]} times')
    return errors, len(lessons), sum(l['q'] for l in lessons.values())


if __name__ == '__main__':
    bad = 0
    for p in sys.argv[1:]:
        errors, count, questions = check(p)
        for e in errors:
            print(e)
        bad += len(errors)
        print(f'{p}: {count} lessons, {questions} questions, {len(errors)} problems')
    sys.exit(1 if bad else 0)
