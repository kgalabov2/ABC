"""Fetch word photos: the lead image of an English Wikipedia article per picture, with licence data.

Usage: python3 -I tools/fetch_pictures.py <download dir> [emoji=Article ...]

Without overrides it uses ARTICLES below. Writes <download dir>/pictures.json and one image per entry,
named by the picture's code points (e.g. 1f436.jpg). Only freely licensed images are kept: Wikipedia's
page images API returns free images by default, and the licence is read back from Commons.
"""
import json, os, subprocess, sys, time, urllib.error, urllib.parse, urllib.request

UA = 'AlphabetWriter/1.0 (personal language-learning app; picture fetch script)'

# Picture (the word's emoji) -> English Wikipedia article whose lead photo shows it. Chosen after reviewing
# the photos; receipt and grandmother keep their emoji (no fitting lead photo).
ARTICLES = {
    # Greetings and small talk (reviewed: thank you, sorry, OK and the meal grace keep their emoji)
    '👋': 'Wave (gesture)', '🌅': 'Dawn', '🌙': 'Moon', '😴': 'Sleep', '🚶': 'Walking', '😊': 'Smile',
    '🤝': 'Handshake', '📛': 'Name tag',
    # Shopping: the price tag (expensive, cheap, size and receipt keep their emoji: no clear lead photo)
    '🏷️': 'Price tag',
    # People and weather (person, noon, sunny, hot and tasty keep their emoji)
    '👴': 'Old age', '👨': 'Man', '👩': 'Woman', '🌧️': 'Raindrop', '💨': 'Windsock', '🥶': 'Frost',
    # Shopping
    '💴': 'Banknotes of the Japanese yen', '💶': 'Euro banknotes', '🛒': 'Shopping', '🏪': 'Retail', '💳': 'Credit card',
    '👕': 'Shirt', '👟': 'Sneakers', '👜': 'Handbag', '🛍️': 'Shopping bag',
    # Food and drink
    '💧': 'Drinking water', '🍵': 'Green tea', '☕': 'Coffee', '🍺': 'Beer', '🍷': 'Wine', '🍚': 'Cooked rice',
    '🍞': 'Bread', '🐟': 'Fish as food', '🥩': 'Meat', '🥚': 'Egg as food', '🍎': 'Fuji (apple)', '🍣': 'Sushi',
    '🍜': 'Ramen', '🥢': 'Chopsticks', '🧀': 'Cheese', '🫒': 'Olive', '🥗': 'Greek salad',
    # Travel
    '✈️': 'Airliner', '🚆': 'Train', '🚌': 'Bus', '🚕': 'Taxi', '🚉': 'Train station', '🛫': 'Airport terminal',
    '🏨': 'Hotel', '🎫': 'Ticket (admission)', '🛂': 'Passport', '🧳': 'Suitcase', '🗺️': 'Map', '🚻': 'Toilet',
    '🚢': 'Ferry', '🏖️': 'Beach',
    # Time
    '🌄': 'Sunrise', '🌆': 'Dusk', '🌃': 'Night', '⏰': 'Clock',
    # Family and people
    # Father, mother and grandfather use the Man, Woman and Old age photos (listed above): the Father, Mother and
    # Grandparent articles' photos all include a baby, which clashes with the baby picture in the picture quiz.
    '👦': 'Boy', '👧': 'Girl', '👶': 'Infant', '👫': 'Friendship', '👪': 'Family', '🧑‍🏫': 'Teacher',
    # Weather
    # Sun, rain and wind keep their emoji: the articles' lead images (space, wet pavement, a branch) don't read well.
    '❄️': 'Snowflake', '☁️': 'Overcast', '☂️': 'Umbrella',
    '⛈️': 'Thunderstorm', '🌈': 'Rainbow',
    # Animals
    '🐶': 'Dog', '🐱': 'Cat', '🐦': 'Bird', '🐴': 'Horse', '🐮': 'Cattle', '🐷': 'Pig', '🐭': 'House mouse',
    '🐰': 'Rabbit', '🐻': 'Brown bear', '🐵': 'Monkey', '🐘': 'African elephant',
}


def key(emoji):
    return '-'.join(f'{ord(c):x}' for c in emoji if ord(c) != 0xFE0F)


def get(url, tries=6):
    """GET with polite retries: Wikimedia answers 429 when fetched too fast."""
    for attempt in range(tries):
        req = urllib.request.Request(url, headers={'User-Agent': UA})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == tries - 1:
                raise
            wait = max(int(e.headers.get('Retry-After') or 0), 30 * (attempt + 1))
            print(f'429, waiting {wait}s', flush=True)
            time.sleep(wait)


def api(base, params):
    params = dict(params, format='json', formatversion='2')
    return json.loads(get(base + '?' + urllib.parse.urlencode(params)))


def main():
    out = sys.argv[1]
    articles = dict(ARTICLES)
    for arg in sys.argv[2:]:
        e, t = arg.split('=', 1)
        articles[e] = t
    os.makedirs(out, exist_ok=True)
    entries = {}
    titles = list(articles.values())
    for i in range(0, len(titles), 40):
        batch = titles[i:i + 40]
        res = api('https://en.wikipedia.org/w/api.php', {
            'action': 'query', 'titles': '|'.join(batch), 'prop': 'pageimages',
            'piprop': 'thumbnail|name', 'pithumbsize': '960', 'redirects': '1',  # a standard thumbnail width
        })
        q = res['query']
        redirects = {r['from']: r['to'] for r in q.get('redirects', [])}
        normalized = {n['from']: n['to'] for n in q.get('normalized', [])}
        pages = {p['title']: p for p in q['pages']}
        for t in batch:
            final = redirects.get(normalized.get(t, t), normalized.get(t, t))
            p = pages.get(final, {})
            if 'thumbnail' in p and 'pageimage' in p:
                entries[t] = {'article': final, 'file': p['pageimage'], 'thumb': p['thumbnail']['source']}
        time.sleep(1)
    files = sorted({e['file'] for e in entries.values()})
    meta = {}
    for i in range(0, len(files), 40):
        res = api('https://commons.wikimedia.org/w/api.php', {
            'action': 'query', 'titles': '|'.join('File:' + f for f in files[i:i + 40]),
            'prop': 'imageinfo', 'iiprop': 'extmetadata|url',
        })
        for p in res['query']['pages']:
            info = (p.get('imageinfo') or [{}])[0]
            md = info.get('extmetadata', {})
            val = lambda k: (md.get(k) or {}).get('value', '')
            meta[p['title'].removeprefix('File:').replace(' ', '_')] = {
                'license': val('LicenseShortName'), 'license_url': val('LicenseUrl'),
                'artist': val('Artist'), 'page': info.get('descriptionurl', ''),
                'nonfree': val('NonFree'),
            }
        time.sleep(1)
    result = []
    for emoji, title in articles.items():
        e = entries.get(title)
        if not e:
            print(f'no image: {emoji} {title}')
            continue
        m = meta.get(e['file'].replace(' ', '_'))
        if not m or m['nonfree'] in ('true', 'True') or not m['license']:
            print(f'not on Commons or not free: {emoji} {title} {e["file"]}')
            continue
        if m['license'].upper().startswith('GFDL'):
            # GFDL wants its full text shipped with every copy; pick a CC or public-domain photo instead.
            print(f'GFDL only, skipped: {emoji} {title} {e["file"]}')
            continue
        # Thumbnail links carry a tracking query (?utm_source=...): take the extension from the path only.
        name = key(emoji) + os.path.splitext(urllib.parse.urlsplit(e['thumb']).path)[1].lower()
        path = os.path.join(out, name)
        if not os.path.exists(path) or os.path.getsize(path) == 0:  # resumable
            # Images through curl: upload.wikimedia.org answers Python's urllib with 429 even when idle.
            subprocess.run(
                ['curl', '-sSf', '--retry', '4', '--retry-delay', '10', '-A', UA, '-o', path, e['thumb']],
                check=True,
            )
            time.sleep(1)
        result.append(dict(emoji=emoji, image=name, **e, **m))
    with open(os.path.join(out, 'pictures.json'), 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print(f'{len(result)} of {len(articles)} pictures downloaded')


if __name__ == '__main__':
    main()
