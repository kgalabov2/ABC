"""Give level words a photo: the lead image of the English Wikipedia article named by the word's meaning.

Usage: python3 -I tools/match_pictures.py <work dir> <nouns file> [<nouns file> ...]

The nouns files are the <code>.nouns.txt lists written by build_vocab.py (English meanings of nouns). For each
meaning it asks Wikipedia for the article's lead image (a standard 500 px thumbnail), skips drawings, maps, flags,
logos and diagrams, and keeps only freely licensed photos (not GFDL-only), with their credits from Commons.

Writes app/src/main/assets/photos_remote.tsv (picture key, image URL, article, artist, licence, Commons page,
licence link) and fills the picture column of every app/src/main/assets/vocab_*.tsv. The phone downloads the
photos itself the first time it shows them. <work dir> caches the API answers so a rerun is quick.
"""
import glob, html, json, os, re, sys, time, urllib.parse, urllib.request

UA = 'AlphabetWriter/1.0 (personal language-learning app; picture matching script)'
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'app', 'src', 'main', 'assets')
# Words in a file name that mean it is not a photo of the thing (whole words: "Orange" and "Plane" stay).
NOT_A_PHOTO = {'map', 'maps', 'flag', 'flags', 'logo', 'coat', 'seal', 'diagram', 'chart', 'locator', 'symbol',
               'icon', 'graph', 'schema', 'schematic', 'emblem', 'blank', 'plan', 'scheme', 'arms', 'structure',
               'formula', 'wordmark', 'signature', 'score', 'text', 'location', 'orthographic', 'projection',
               'distribution', 'range', 'cladogram', 'svg', 'drawing', 'illustration', 'sketch', 'chemical'}
# English words that name very different things; one photo cannot serve all their meanings.
HOMONYMS = {'pupil', 'match', 'glass', 'plant', 'bank', 'bat', 'bow', 'light', 'letter', 'note', 'spring', 'fall',
            'watch', 'ring', 'date', 'fan', 'iron', 'key', 'organ', 'palm', 'pitcher', 'star', 'suit', 'tie', 'trunk',
            'wave', 'row', 'rock', 'scale', 'club', 'cell', 'chest', 'court', 'crane', 'deck', 'draft', 'figure', 'jam',
            'mine', 'nail', 'park', 'pound', 'present', 'record', 'sink', 'square', 'stamp', 'tank', 'tire', 'bar',
            'bill', 'board', 'case', 'change', 'charge', 'check', 'class', 'current', 'degree', 'line', 'mark', 'order',
            'party', 'pen', 'point', 'post', 'pool', 'race', 'rest', 'right', 'round', 'set', 'sign', 'space', 'stage',
            'state', 'step', 'stick', 'table', 'term', 'top', 'trip', 'turn', 'type', 'kind', 'mean', 'patient',
            'plane', 'port', 'press', 'spirit', 'story', 'train', 'trunk', 'volume', 'well', 'yard', 'film',
            'sheet', 'band', 'block', 'cast', 'chip', 'coach', 'crown', 'cross', 'fair', 'fire', 'head', 'hand'}


# Numbers and colours get digits and colour swatches (the app draws those itself), only in their own topics.
NUMBERS = {w: str(i) for i, w in enumerate(
    'zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen '
    'seventeen eighteen nineteen twenty'.split())}
NUMBERS.update({'thirty': '30', 'forty': '40', 'fifty': '50', 'sixty': '60', 'seventy': '70', 'eighty': '80',
                'ninety': '90', 'hundred': '100', 'one hundred': '100', 'thousand': '1000', 'one thousand': '1000',
                'ten thousand': '10000', 'million': '1000000'})
COLOURS = {'red': '🔴', 'blue': '🔵', 'green': '🟢', 'yellow': '🟡', 'black': '⚫', 'white': '⚪', 'purple': '🟣',
           'violet': '🟣', 'orange': '🟠', 'brown': '🟤', 'pink': '🩷', 'grey': '🩶', 'gray': '🩶', 'light blue': '🩵'}
NO_ICON_TOPICS = ('verbs', 'adjectives', 'adverbs', 'small')


def load_review():
    """tools/photo_review.tsv: Commons file name -> KEEP or REJECT, from looking at every photo next to the word
    meanings that use it. Only KEEP photos are used: a photo must show the word unmistakably."""
    path = os.path.join(ROOT, 'tools', 'photo_review.tsv')
    verdicts = {}
    if os.path.exists(path):
        for line in open(path, encoding='utf-8'):
            if line.strip() and not line.startswith('#'):
                f = line.rstrip('\n').split('\t')
                verdicts[f[0]] = f[1]
    return verdicts


def load_icons():
    """tools/word_icons.tsv: English meaning -> emoji that cannot be mistaken for anything else."""
    icons = {}
    for line in open(os.path.join(ROOT, 'tools', 'word_icons.tsv'), encoding='utf-8'):
        if line.strip() and not line.startswith('#'):
            meaning, emoji = line.rstrip('\n').split('\t')
            icons[meaning] = emoji
    return icons


def icon_key(meaning):
    """'Trousers, pants (clothing)' -> 'trousers'; 'the sun' -> 'sun'."""
    m = re.sub(r'\([^)]*\)', '', meaning).split(';')[0].split(',')[0].strip().strip('…').strip().lower()
    return re.sub(r'^(a|an|the)\s+', '', m)


def icon_for(meaning, topic, icons):
    key = icon_key(meaning)
    if topic == 'numbers':
        return NUMBERS.get(key)
    if topic == 'colours':
        return COLOURS.get(key)
    if topic in NO_ICON_TOPICS or key.startswith('to '):
        return None
    return icons.get(key)


def api(base, params):
    params = dict(params, format='json', formatversion='2')
    req = urllib.request.Request(base + '?' + urllib.parse.urlencode(params), headers={'User-Agent': UA})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            if e.code not in (429, 503) or attempt == 4:
                raise
            time.sleep(int(e.headers.get('Retry-After') or 10 * (attempt + 1)))


def title_for(meaning):
    """'dog' -> 'Dog'; 'cup of coffee' -> 'Cup of coffee'; None for meanings that cannot be an article."""
    m = meaning.split(',')[0].split(' or ')[0].strip().strip('…').strip()
    m = re.sub(r'^(a|an|the)\s+', '', m, flags=re.I)
    if not m or len(m.split()) > 3 or m.startswith('to ') or not re.match(r"^[A-Za-z][A-Za-z '\-]*$", m):
        return None
    return m[0].upper() + m[1:]


def thumb_url(url):
    """The thumbnail address without tracking parameters, on Wikimedia's usual image host."""
    return url.split('?')[0].replace('https://thumb.wikimedia.org/', 'https://upload.wikimedia.org/')


def plain(text):
    text = re.sub(r'<[^>]+>', '', text or '')
    text = ' '.join(html.unescape(text).split())
    text = re.sub(r'\s*\(talk\)', '', text)
    text = re.sub(r'^(.+?)\s*\1$', r'\1', text)  # "Jane DoeJane Doe" -> "Jane Doe" (not "CC BY" -> "C BY")
    return text[:70].rsplit(' ', 1)[0] + '…' if len(text) > 70 else text


def main():
    work = sys.argv[1]
    os.makedirs(work, exist_ok=True)
    meanings, topics_of_title = set(), {}
    for path in sys.argv[2:]:
        for line in open(path, encoding='utf-8'):
            if not line.strip():
                continue
            meaning, _, topic = line.rstrip('\n').partition('\t')
            meanings.add(meaning)
            t = title_for(meaning)
            if t and topic not in ('', 'other'):
                topics_of_title.setdefault(t, set()).add(topic)
    titles = sorted({t for t in map(title_for, meanings) if t})
    # One English title used for words of different topics (bank: money / river) is probably a homonym.
    ambiguous = {t for t, ts in topics_of_title.items() if len(ts) > 1} | {t for t in titles if t.lower() in HOMONYMS}
    print(f'{len(ambiguous)} ambiguous titles skipped')
    print(f'{len(meanings)} noun meanings, {len(titles)} possible article titles')

    cache_path = os.path.join(work, 'pageimages.json')
    pages = json.load(open(cache_path)) if os.path.exists(cache_path) else {}
    todo = [t for t in titles if t not in pages]
    for i in range(0, len(todo), 50):
        batch = todo[i:i + 50]
        res = api('https://en.wikipedia.org/w/api.php', {
            'action': 'query', 'titles': '|'.join(batch), 'prop': 'pageimages', 'piprop': 'thumbnail|name',
            'pithumbsize': '500', 'redirects': '1',
        })
        q = res.get('query', {})
        norm = {n['from']: n['to'] for n in q.get('normalized', [])}
        redir = {r['from']: r['to'] for r in q.get('redirects', [])}
        by_title = {p['title']: p for p in q.get('pages', [])}
        for t in batch:
            final = redir.get(norm.get(t, t), norm.get(t, t))
            p = by_title.get(final, {})
            pages[t] = {'file': p.get('pageimage'), 'thumb': (p.get('thumbnail') or {}).get('source'), 'article': final}
        json.dump(pages, open(cache_path, 'w'))
        print(f'  pages {min(i + 50, len(todo))}/{len(todo)}', flush=True)
        time.sleep(1)

    def looks_like_photo(name):
        return not (set(re.split(r'[^a-z]+', name.lower())) & NOT_A_PHOTO)

    files = sorted({p['file'] for p in pages.values() if p.get('file') and p.get('thumb') and looks_like_photo(p['file'])})
    meta_path = os.path.join(work, 'licences.json')
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    todo = [f for f in files if f not in meta]
    for i in range(0, len(todo), 50):
        batch = todo[i:i + 50]
        res = api('https://commons.wikimedia.org/w/api.php', {
            'action': 'query', 'titles': '|'.join('File:' + f for f in batch), 'prop': 'imageinfo', 'iiprop': 'extmetadata|url|mime',
        })
        got = {}
        for p in res.get('query', {}).get('pages', []):
            info = (p.get('imageinfo') or [{}])[0]
            md = info.get('extmetadata', {})
            val = lambda k: (md.get(k) or {}).get('value', '')
            got[p['title'].removeprefix('File:').replace(' ', '_')] = {
                'license': plain(val('LicenseShortName')), 'license_url': val('LicenseUrl'), 'artist': plain(val('Artist')),
                'page': info.get('descriptionurl', ''), 'nonfree': val('NonFree'), 'mime': info.get('mime', ''),
            }
        for f in batch:
            meta[f] = got.get(f.replace(' ', '_'), {})
        json.dump(meta, open(meta_path, 'w'))
        print(f'  licences {min(i + 50, len(todo))}/{len(todo)}', flush=True)
        time.sleep(1)

    # Usable photos: free (not GFDL-only), a photo (JPEG), on Commons. They are candidates; the app gets only the
    # ones approved in the photo review.
    candidates = {}
    for t, p in pages.items():
        if t in ambiguous or not looks_like_photo(p.get('file') or ''):
            continue
        m = meta.get(p.get('file') or '')
        if not m or not m.get('license') or m.get('nonfree') in ('true', 'True'):
            continue
        if m['license'].upper().startswith('GFDL') or m.get('mime') != 'image/jpeg' or not m.get('page'):
            continue
        candidates[t] = dict(key='p:' + p['file'], file=p['file'], url=thumb_url(p['thumb']), article=p['article'], **m)
    review = load_review()
    photo_by_title = {t: c for t, c in candidates.items() if review.get(c['file']) == 'KEEP'}
    print(f'{len(candidates)} candidate photos, {len(review)} reviewed, {len(photo_by_title)} titles with an approved photo')

    icons = load_icons()
    rows = {}
    for path in glob.glob(os.path.join(ASSETS, 'vocab_*.tsv')):
        lines = open(path, encoding='utf-8').read().split('\n')
        out, matched, iconed = [], 0, 0
        for line in lines:
            if not line or line.startswith('#'):
                out.append(line)
                continue
            f = line.split('\t')
            while len(f) < 9:
                f.append('')
            title = title_for(f[4])
            photo = photo_by_title.get(title) if title and f[4] in meanings else None
            icon = icon_for(f[4], f[1], icons)
            if icon:
                f[8] = icon  # unmistakable: an emoji (or the app's checked photo for it), digits or a swatch
                iconed += 1
            elif photo and f[1] not in NO_ICON_TOPICS:
                f[8] = photo['key']
                rows[photo['key']] = photo
                matched += 1
            else:
                f[8] = ''
            out.append('\t'.join(f))
        open(path, 'w', encoding='utf-8').write('\n'.join(out))
        print(f'{os.path.basename(path)}: {matched} words with a photo, {iconed} with an icon, digits or colour')

    # Candidates still waiting for review, with the meanings that would use them, for the contact sheets.
    uses = {}
    for path in glob.glob(os.path.join(ASSETS, 'vocab_*.tsv')):
        for line in open(path, encoding='utf-8'):
            f = line.rstrip('\n').split('\t')
            if line.startswith('#') or len(f) < 5 or f[1] in NO_ICON_TOPICS or icon_for(f[4], f[1], icons):
                continue
            c = candidates.get(title_for(f[4]) or '') if f[4] in meanings else None
            if c and c['file'] not in review:
                uses.setdefault(c['key'], (c, set()))[1].add(f[4])
    with open(os.path.join(work, 'candidates.tsv'), 'w', encoding='utf-8') as f:
        for key in sorted(uses):
            c, ms = uses[key]
            f.write('\t'.join([key, c['url'], c['article'], c['file'], ' / '.join(sorted(ms, key=len))]) + '\n')
    print(f'{len(uses)} photos waiting for review in {os.path.join(work, "candidates.tsv")}')

    with open(os.path.join(ASSETS, 'photos_remote.tsv'), 'w', encoding='utf-8') as f:
        f.write('# Photos the phone downloads on first use: lead images of English Wikipedia articles, from Wikimedia Commons.\n')
        f.write('# Generated by tools/match_pictures.py. Each keeps its own licence (see the Commons page); shown resized.\n')
        f.write('# picture key<TAB>image URL<TAB>article<TAB>artist<TAB>licence<TAB>Commons page<TAB>licence link\n')
        for key in sorted(rows):
            p = rows[key]
            fields = [key, p['url'], p['article'], p['artist'] or 'Unknown author', p['license'], p['page'], p['license_url']]
            f.write('\t'.join(x.replace('\t', ' ') for x in fields) + '\n')
    print(f'{len(rows)} photos in photos_remote.tsv')


if __name__ == '__main__':
    main()
