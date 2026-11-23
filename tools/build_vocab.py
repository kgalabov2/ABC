"""Build the five 2 000-word levels of one language: app/src/main/assets/vocab_<code>.tsv.

Usage:
  python3 -I tools/build_vocab.py <code> <kaikki .jsonl> <frequency list>      (el, bg, it, fr, de)
  python3 -I tools/build_vocab.py ja <kaikki .jsonl> <JMdict_e.gz>

Words are ranked by how common they are: subtitle word counts (FrequencyWords, OpenSubtitles 2018), with
inflected forms counted towards their dictionary form via Wiktionary's "form of" links; Japanese uses JMdict's
newspaper frequency bands (nf01–nf48). Meanings, readings, genders and topics come from English Wiktionary
(kaikki.org extracts). Rank 1–2000 is level 1, and so on up to level 5.

Output columns: level, topic, word, reading, meaning, kanji, note, speech text, picture (filled in later by
tools/match_pictures.py). Also writes <code>.nouns.txt beside the output: English nouns to find photos for.
All sources are CC BY-SA; see the app's About text.
"""
import collections, glob, gzip, json, os, re, sys
import xml.etree.ElementTree as ET

LEVEL_SIZE, LEVELS = 2000, 5
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Wiktionary topical category keywords -> app topic, checked in this order.
TOPIC_RULES = [
    ('colours', ['colors', 'colours']),
    ('numbers', ['cardinal numbers', 'ordinal numbers', 'numerals', 'number']),
    ('greetings', ['greetings', 'farewells', 'politeness']),
    ('animals', ['mammals', 'birds', 'fish', 'insects', 'reptiles', 'amphibians', 'dogs', 'cats', 'livestock',
                 'animals', 'pets', 'arachnids', 'cattle', 'horses', 'rodents', 'molluscs', 'crustaceans', 'primates']),
    ('food', ['foods', 'food', 'beverages', 'drinks', 'fruits', 'vegetables', 'meats', 'dairy', 'cooking', 'dishes',
              'breads', 'cheeses', 'spices', 'herbs', 'desserts', 'nuts', 'seafood', 'alcoholic', 'coffee', 'tea',
              'cuisine', 'meals', 'sweets', 'confectionery', 'soups', 'sauces', 'pasta', 'cakes', 'eating']),
    ('clothing', ['clothing', 'footwear', 'headwear', 'jewelry', 'jewellery', 'garments', 'fashion', 'textiles']),
    ('home', ['furniture', 'household', 'rooms', 'kitchenware', 'tools', 'containers', 'appliances', 'cleaning',
              'bedding', 'tableware', 'lighting', 'home', 'housing']),
    ('body', ['anatomy', 'body', 'medicine', 'diseases', 'health', 'illness', 'teeth', 'hair', 'organs', 'injuries',
              'pharmacology', 'hospital', 'disabilities', 'physiology', 'senses']),
    ('weather', ['weather', 'meteorology', 'climate', 'wind', 'rain', 'snow']),
    ('time', ['time', 'days of the week', 'months', 'seasons', 'calendar', 'times of day', 'units of time',
              'periods', 'holidays']),
    ('family', ['family', 'kinship', 'male people', 'female people', 'people', 'children', 'marriage', 'age']),
    ('travel', ['transport', 'vehicles', 'automobiles', 'aircraft', 'ships', 'watercraft', 'rail', 'roads',
                'travel', 'tourism', 'directions', 'traffic', 'aviation', 'nautical', 'cycling']),
    ('shopping', ['money', 'currency', 'commerce', 'shopping', 'retail', 'shops', 'trade', 'finance', 'banking',
                  'coins', 'prices', 'payment']),
    ('places', ['buildings', 'places', 'architecture', 'cities', 'urban', 'settlements', 'roads and streets',
                'religious buildings', 'shops and services']),
    ('nature', ['plants', 'trees', 'flowers', 'landforms', 'water', 'geology', 'minerals', 'astronomy', 'nature',
                'environment', 'botany', 'rivers', 'mountains', 'sky', 'fungi', 'gardening', 'agriculture',
                'elements', 'oceans', 'forests', 'light', 'fire']),
    ('work', ['occupations', 'education', 'school', 'business', 'office', 'employment', 'professions', 'jobs',
              'university', 'writing', 'mathematics', 'law', 'politics', 'government', 'economics']),
    ('leisure', ['sports', 'games', 'music', 'musical instruments', 'entertainment', 'art', 'hobbies', 'dance',
                 'film', 'theater', 'theatre', 'literature', 'toys', 'football', 'leisure', 'photography',
                 'television', 'radio']),
    ('tech', ['computing', 'technology', 'internet', 'electronics', 'telecommunications', 'software', 'computers',
              'mobile phones', 'electricity', 'machines']),
    ('feelings', ['emotions', 'feelings', 'personality', 'character', 'love', 'fear', 'anger', 'happiness',
                  'sadness', 'mental', 'psychology']),
]
POS_TOPIC = {
    'verb': 'verbs', 'adj': 'adjectives', 'adv': 'adverbs', 'num': 'numbers', 'intj': 'greetings',
    'phrase': 'greetings', 'pron': 'small', 'prep': 'small', 'conj': 'small', 'det': 'small', 'article': 'small',
    'particle': 'small', 'postp': 'small', 'contraction': 'small',
}
SKIP_POS = {'name', 'character', 'symbol', 'suffix', 'prefix', 'infix', 'interfix', 'affix', 'root', 'abbrev',
            'punct', 'romanization', 'circumfix', 'combining_form'}


def topic_of(pos, categories):
    """Topic from the word's main-sense categories; verbs, adjectives, adverbs and small words keep their
    grammar topic (a chess sense must not move "to eat" into Sport), except colours and numbers."""
    names = [c.lower() for c in categories]
    for topic, keys in TOPIC_RULES:
        if pos in POS_TOPIC and pos not in ('intj', 'phrase') and topic not in ('colours', 'numbers'):
            continue
        for name in names:
            # Whole-word match, so "fish" matches "Fish" and "Freshwater fish" but not "Fishing".
            if any(re.search(r'(^|\s)' + re.escape(k) + r'($|\s)', name) for k in keys):
                return topic
    return POS_TOPIC.get(pos, 'other')


def clean_gloss(gloss):
    """'dog (Canis lupus familiaris) (general term…)' -> 'dog'; keeps a short, readable meaning."""
    g = re.sub(r'\[[^\]]*\]', '', gloss)  # "[with dative] in"
    # "Used as a copula. to be": a grammar description followed by the translation; keep the translation.
    parts = g.split('. ')
    if len(parts) > 1 and GRAMMAR_GLOSS.match(g) and 0 < len(parts[-1].strip(' .')) <= 30 and parts[-1][:1].islower():
        g = parts[-1]
    g = re.split(r',? in the sense of|, used |\. ', g)[0]
    g = re.sub(r'\([^()]*\)', '', g)
    g = re.sub(r'\([^()]*\)', '', g)  # nested parentheses
    g = g.split(';')[0]
    g = re.sub(r'\s+', ' ', g).strip(' ,.:')
    if len(g) > 60:
        g = g[:60].rsplit(' ', 1)[0].rstrip(',') + '…'
    return g


# Bookkeeping categories ("Bulgarian entries with etymology trees") say nothing about the topic.
NOT_TOPICAL = re.compile(r'entries|terms|pages|etymolog|requests|lemmas|forms|words|derived|borrow|inherit|'
                         r'pronunciation|links|declension|conjugation|nouns|verbs|adjectives|adverbs|language|'
                         r'usage|templates|phrasebook|collocations|quotations|spellings|hyphenation|rhymes', re.I)


def categories_of(entry, senses=None):
    """Topical categories of the given senses (default: all), without Wiktionary's bookkeeping categories."""
    cats = []
    for sense in (entry.get('senses', []) if senses is None else senses):
        for c in sense.get('categories', []) or []:
            cats.append(c.get('name') if isinstance(c, dict) else c)
    return [c for c in cats if c and not NOT_TOPICAL.search(c)]


def all_categories(entry):
    cats = []
    for sense in entry.get('senses', []):
        for c in sense.get('categories', []) or []:
            cats.append(c.get('name') if isinstance(c, dict) else c)
    return [c for c in cats if c]


# Glosses that describe grammar rather than say what the word means ("forms the present perfect…").
GRAMMAR_GLOSS = re.compile(r'^(forms?|used|use|indicates?|expresses|denotes|introduces|auxiliary|\(auxiliary\)|'
                           r'the (definite|indefinite|most|inanimate|plural|singular|first|second|third)|marks?|'
                           r'as an? |refers? to|repeated|adverbial|prevocalic|an? (particle|word|suffix|prefix)|'
                           r'added|placed|in combination|with )', re.I)


def gloss_of(sense):
    """The sense's own meaning. Sub-senses carry their heading first ("As a copulative verb:", "to be")."""
    glosses = sense.get('glosses') or ['']
    return glosses[-1] if glosses[0].rstrip().endswith(':') else glosses[0]


def is_grammar(gloss):
    return bool(GRAMMAR_GLOSS.match(clean_gloss(gloss)))


def best_sense(senses):
    """The first sense that says what the word means; a grammar description only if there is nothing else."""
    return next((s for s in senses if not is_grammar(gloss_of(s))), senses[0])


def gender_of(entry):
    tags = set()
    for sense in entry.get('senses', [])[:2]:
        tags.update(sense.get('tags') or [])
    plural_only = 'plural-only' in tags
    for g in ('masculine', 'feminine', 'neuter'):
        if g in tags:
            return g, plural_only
    expansion = ' '.join(h.get('expansion', '') for h in entry.get('head_templates', []))
    m = re.search(r'\)\s+(m|f|n)(\s|$|\b)', expansion) or re.search(r'\s(m|f|n)\s*(\(|$)', expansion)
    if m:
        return {'m': 'masculine', 'f': 'feminine', 'n': 'neuter'}[m.group(1)], plural_only
    return None, plural_only


VOWELS = 'aeiouàèéìíòóùúâêîôûäöüœæ'


def with_article(code, word, gender, plural_only, aspirated_h=False):
    """The word as a learner should memorise it: with its article (der Hund, le chat, il cane)."""
    w = word.lower()
    w0 = w[:1]
    if code == 'de':
        if plural_only:
            return 'die ' + word
        return {'masculine': 'der ', 'feminine': 'die ', 'neuter': 'das '}.get(gender, '') + word
    if code == 'fr':
        if plural_only:
            return 'les ' + word
        # Elision before a vowel or a mute h (l'homme); an aspirated h keeps le/la (le héros).
        if w0 in VOWELS or (w0 == 'h' and not aspirated_h):
            return "l'" + word
        return {'masculine': 'le ', 'feminine': 'la '}.get(gender, '') + word
    if code == 'it':
        vowel = w0 in 'aeiouh'
        impure = bool(re.match(r'(s[^aeiou]|z|gn|ps|pn|x|y|i[aeiou])', w))
        if gender == 'feminine':
            return ('le ' if plural_only else ("l'" if vowel else 'la ')) + word
        if gender == 'masculine':
            if plural_only:
                return ('gli ' if vowel or impure else 'i ') + word
            if vowel:
                return "l'" + word
            return ('lo ' if impure else 'il ') + word
    return word


# When a word has entries for several parts of speech, verbs and function words win in this order (son: "his",
# not "sound"); between a noun and an adjective, the one with more senses (chien: "dog", not "stingy").
POS_PRIORITY = ['verb', 'article', 'pron', 'det', 'prep', 'conj', 'particle', 'adv', 'adj', 'noun', 'num',
                'intj', 'phrase', 'postp', 'contraction']


# "second-person singular imperfective future of ξέρω", "past participle of morire", "synonym of poiché".
FORM_GLOSS = re.compile(r'(?:person|participle|singular|plural|imperative|infinitive|gerund|form|spelling|variant|'
                        r'synonym|contraction|diminutive|dative|genitive|accusative|nominative|tense|future|present|'
                        r'past|perfect|imperfect|aorist|subjunctive|conditional|indicative|elision|clipping|'
                        r'nominalization)'
                        r'\s+of\s+([^\s:,;()]+)', re.I)
NOT_A_TARGET = {'the', 'a', 'an', 'this', 'that', 'its', 'his', 'her', 'their', 'one', 'some', 'address'}


def form_target(gloss):
    """The word an inflection glossed in words belongs to ("past participle of morire" -> "morire"), or None.
    "Short form of the first-person pronoun" is a description, not a form."""
    m = FORM_GLOSS.search(re.sub(r'\([^()]*\)', '', gloss or ''))  # not "time of day (clipping of Uhrzeit)"
    return m.group(1) if m and m.group(1).lower() not in NOT_A_TARGET else None


GREEK_ARTICLE = {'masculine': 'ο', 'feminine': 'η', 'neuter': 'το'}


# Senses that are not vocabulary: note names (Es: E-flat), letter names, initialisms and abbreviations.
NOT_A_WORD = re.compile(r'^[A-H](-flat|-sharp)?$|name of the .*letter|initialism of|abbreviation of|acronym of', re.I)


def load_fixes(code):
    """tools/vocab_fixes.tsv for one language: {lemma: (meaning, part of speech or '', written form or '')};
    meaning "-" leaves the word out."""
    fixes = {}
    for line in open(os.path.join(ROOT, 'tools', 'vocab_fixes.tsv'), encoding='utf-8'):
        if line.startswith('#') or not line.strip():
            continue
        lang, word, meaning, pos, text = (line.rstrip('\n').split('\t') + [''] * 5)[:5]
        if lang == code and word:
            fixes[word] = (meaning, pos, text)
    return fixes


def unstressed(word):
    """Bulgarian dictionary forms are written without the stress marks its inflection tables show (ка́жа).
    ѝ ("her") is its own precomposed letter and stays."""
    return word.replace('\u0301', '').replace('\u0300', '')


def build_from_frequency(code, kaikki_path, freq_path):
    fixes = load_fixes(code)
    norm = unstressed if code == 'bg' else (lambda w: w)
    entries = collections.defaultdict(list)   # word -> its lemma entries (one per part of speech)
    orphans = {}
    form_to_lemmas = collections.defaultdict(set)
    with open(kaikki_path, encoding='utf-8') as f:
        for line in f:
            e = json.loads(line)
            word, pos = e.get('word'), e.get('pos')
            if not word or pos in SKIP_POS or ' ' in word.strip() and pos not in ('phrase', 'intj'):
                continue
            senses = e.get('senses', [])
            word = norm(word)
            for t in {fo.get('word') for s in senses for fo in (s.get('form_of') or []) if fo.get('word')}:
                form_to_lemmas[word.lower()].add(norm(t))
            # Some inflections are glossed in words only ("θα ξέρεις: second-person singular … of ξέρω").
            for sense in senses:
                t = form_target(gloss_of(sense))
                if t and not sense.get('form_of'):
                    form_to_lemmas[word.lower()].add(norm(t))
            if fixes.get(word, ('',))[0] == '-':
                continue
            real = [s for s in senses if not s.get('form_of') and s.get('glosses')
                    and not form_target(gloss_of(s)) and not NOT_A_WORD.search(gloss_of(s))
                    and 'form-of' not in (s.get('tags') or []) and 'alt-of' not in (s.get('tags') or [])]
            if not real:
                # A form whose dictionary word has no entry (всичко "everything" of вси́чък) may be the only
                # place the word is explained: keep it in case the dictionary word turns out to be missing.
                extra = next((fo.get('extra', '') for s in senses for fo in (s.get('form_of') or []) if fo.get('extra')), '')
                # "(vsíčăk): everything, all" -> "everything, all"; a bare "keínos" is only a transliteration.
                gloss = clean_gloss(extra.split(':', 1)[1]) if ':' in extra else ''
                targets = {norm(fo['word']) for s in senses for fo in (s.get('form_of') or [])
                           if fo.get('word') and not re.search(r'[\s()\[\]]', fo['word'])}
                if gloss and targets and pos not in ('verb', 'noun', 'adj') and fixes.get(word, ('',))[0] != '-':
                    orphans.setdefault(word, dict(word=word, pos=pos, gloss=gloss, grammar=is_grammar(gloss), senses=1,
                                                  roman='', ipa='', gender=None, plural_only=False, aspirated=False,
                                                  cats=[], targets=targets))
                continue
            # Subtitles are full of swearing: leave out words whose main meaning is vulgar or offensive.
            if {'vulgar', 'offensive', 'derogatory', 'ethnic', 'slur'} & set(real[0].get('tags') or []):
                continue
            sense = best_sense(real)
            gloss = clean_gloss(gloss_of(sense))
            if not gloss:
                continue
            roman = next((x['form'] for x in e.get('forms', []) if 'romanization' in (x.get('tags') or [])), '')
            ipa = next((s['ipa'] for s in e.get('sounds', []) if s.get('ipa')), '')
            gender, plural_only = gender_of(e) if pos == 'noun' else (None, False)
            aspirated = any('aspirated h' in c.lower() or 'h aspiré' in c.lower() for c in all_categories(e))
            if any(r['pos'] == pos for r in entries[word]):
                continue  # a second noun spelled the same (Bulgarian маса "mass" after маса "table"): keep the first
            entries[word].append(dict(word=word, pos=pos, gloss=gloss, grammar=is_grammar(gloss), senses=len(real), roman=roman, ipa=ipa, gender=gender,
                                      plural_only=plural_only, aspirated=aspirated,
                                      cats=categories_of(e, [sense])))
    rank_of = {p: i for i, p in enumerate(POS_PRIORITY)}
    # An entry with a real meaning beats one that only describes grammar (que: "that", not "The inanimate…").
    nominal = ('noun', 'adj', 'intj', 'phrase')  # Tag: "day" (more senses), not "hello" (Guten Tag)

    def entry_key(r):
        if r['pos'] in nominal:
            return r['grammar'], 1, -r['senses'], rank_of.get(r['pos'], 99)
        return r['grammar'], 0, rank_of.get(r['pos'], 99), 0

    lemmas = {w: min(es, key=entry_key) for w, es in entries.items()}
    # A hand fix with a part of speech uses that entry; if the word has none (είναι "is" is only a noun "being"
    # in Wiktionary), the best entry is shown as that part of speech, without the noun's gender and topic.
    for w, (meaning, fix_pos, _) in fixes.items():
        if fix_pos and w in entries:
            match = [r for r in entries[w] if r['pos'] == fix_pos]
            lemmas[w] = min(match, key=entry_key) if match else dict(
                lemmas[w], pos=fix_pos, gender=None, plural_only=False, cats=[])
    by_lower = collections.defaultdict(set)
    for w in lemmas:
        by_lower[w.lower()].add(w)

    def resolve(targets, depth=2):
        """The lemmas behind form-of targets, following forms of forms (μιλήσω → μιλώ → μιλάω)."""
        found = set()
        for t in targets:
            if t in lemmas:
                found.add(t)
            elif depth and t.lower() in form_to_lemmas:
                found |= resolve(form_to_lemmas[t.lower()], depth - 1)
        return found

    for w, r in orphans.items():
        if w not in lemmas and not resolve(r['targets']):
            lemmas[w] = r
            by_lower[w.lower()].add(w)

    # Which lemmas each token of the frequency list can be: the word itself (direct) and the words it is a form of.
    tokens = []
    with open(freq_path, encoding='utf-8') as f:
        for line in f:
            parts = line.split()
            if len(parts) != 2 or not parts[1].isdigit():
                continue
            token, n = parts[0], int(parts[1])
            if any(ch.isdigit() for ch in token):
                continue
            # The lists are lower-case: in German "wasser" is Wasser, "zeit" is Zeit or zeit. Elsewhere a
            # capitalised word (Suo, Вяра) counts only when there is no lower-case one.
            direct = by_lower.get(token, set()) if code == 'de' or token not in lemmas else {token}
            forms = resolve(form_to_lemmas.get(token, set())) - direct
            if direct or forms:
                tokens.append((n, direct, forms))
    # How common each lemma is on its own evidence: tokens that can only be that lemma.
    prior = collections.Counter()
    for n, direct, forms in tokens:
        if len(direct | forms) == 1:
            prior[next(iter(direct | forms))] += n
    lexical = ('noun', 'adj', 'verb')
    counts = collections.Counter()

    def spread(n, words, boost=None, floor=None):
        """Shares n between words by evidence. [floor] words get at least 30% between them."""
        weights = {w: (prior[w] + 1) * (boost or {}).get(w, 1) for w in words}
        total = sum(weights.values())
        shares = {w: wt / total for w, wt in weights.items()}
        if floor and len(floor) < len(words):
            got = sum(shares[w] for w in floor)
            if got < 0.3:
                for w in shares:
                    shares[w] *= 0.3 / got if w in floor else 0.7 / (1 - got)
        for w, share in shares.items():
            counts[w] += n * share

    for n, direct, forms in tokens:
        # A token that can be several words is shared between them by how common each word is on its own evidence
        # (suis: être, not suivre; est: "is", not "east"; tagen: Tag, not the verb tagen; nuit: "night", not nuire),
        # with a head start for the word spelled exactly so.
        # A function word keeps nearly all of its own spelling (mal: "times", not a form of malen; ja: "yes",
        # not the noun das Ja).
        function = {w for w in direct if lemmas[w]['pos'] not in lexical}
        others = (direct - function) | forms
        if function and others:
            spread(n * 0.95, function)
            spread(n * 0.05, others)
        else:
            # A noun whose every spelling is also a verb form (Liebe / liebe, porte / porter) still gets a good
            # share of its own spelling; otherwise it would vanish.
            spread(n, direct | forms, {w: 3 for w in direct}, floor={w for w in direct if lemmas[w]['pos'] == 'noun'})
    ranked = [w for w, _ in counts.most_common()
              if len(w) > 1 or lemmas[w]['pos'] in ('conj', 'prep', 'pron', 'article', 'particle', 'det')]
    rows = []
    for rank, w in enumerate(ranked[:LEVEL_SIZE * LEVELS]):
        r = lemmas[w]
        topic = topic_of(r['pos'], r['cats'])
        text, note = w, ''
        if code in ('de', 'fr', 'it') and r['pos'] == 'noun':
            text = with_article(code, w, r['gender'], r['plural_only'], r['aspirated'])
        text = fixes.get(w, ('', '', ''))[2] or text
        if code == 'el' and r['pos'] == 'noun' and r['gender'] in GREEK_ARTICLE:
            note = f"{GREEK_ARTICLE[r['gender']]} {w} ({r['gender']})"
        if code == 'bg' and r['pos'] == 'noun' and r['gender']:
            note = r['gender']
        reading = r['roman'] if code in ('el', 'bg') else r['ipa']
        rows.append(dict(level=rank // LEVEL_SIZE + 1, topic=topic, text=text, reading=reading or w,
                         meaning=fixes.get(w, ('',))[0] or r['gloss'], kanji='', note=note, say=w, pos=r['pos']))
    return rows


# ---------------------------------------------------------------------------------------------- Japanese

ROMAJI = {}
_BASE = ('あa いi うu えe おo かka きki くku けke こko さsa しshi すsu せse そso たta ちchi つtsu てte とto '
         'なna にni ぬnu ねne のno はha ひhi ふfu へhe ほho まma みmi むmu めme もmo やya ゆyu よyo らra りri るru '
         'れre ろro わwa ゐi ゑe をo んn がga ぎgi ぐgu げge ごgo ざza じji ずzu ぜze ぞzo だda ぢji づzu でde どdo '
         'ばba びbi ぶbu べbe ぼbo ぱpa ぴpi ぷpu ぺpe ぽpo ゔvu ぁa ぃi ぅu ぇe ぉo ゎwa')
for item in _BASE.split():
    ROMAJI[item[0]] = item[1:]
_YOON = {'ゃ': 'a', 'ゅ': 'u', 'ょ': 'o'}


def kana_to_romaji(kana, verb=False):
    s = ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in kana)
    out, i = [], 0
    while i < len(s):
        c = s[i]
        nxt = s[i + 1] if i + 1 < len(s) else ''
        if c == 'っ':
            following = kana_to_romaji(s[i + 1:i + 3])[:1] if nxt else ''
            out.append('t' if following == 'c' else following)
            i += 1
            continue
        if c == 'ー':
            if out:
                out[-1] = macron(out[-1])
            i += 1
            continue
        if nxt in _YOON and c in ROMAJI:
            base = ROMAJI[c]
            stem = {'shi': 'sh', 'chi': 'ch', 'ji': 'j'}.get(base, base[:-1] + 'y')
            out.append(stem + _YOON[nxt])
            i += 2
            continue
        if nxt in 'ぁぃぅぇぉ' and c in ROMAJI and nxt:
            out.append(ROMAJI[c][:-1] + ROMAJI[nxt])
            i += 2
            continue
        if c == 'ん' and nxt and (nxt in 'あいうえおやゆよ'):
            out.append("n'")  # gen'in, not genin
            i += 1
            continue
        out.append(ROMAJI.get(c, c))
        i += 1
    text = ''.join(out)
    # Long vowels: ou/oo -> ō, uu -> ū (but keep a verb's final う: 思う omou).
    end = len(text) - (1 if verb and text.endswith('u') else 0)
    head, tail = text[:end], text[end:]
    head = re.sub(r'o[ou]', 'ō', head).replace('uu', 'ū')
    return head + tail


def macron(syllable):
    return syllable[:-1] + {'a': 'ā', 'i': 'ī', 'u': 'ū', 'e': 'ē', 'o': 'ō'}.get(syllable[-1:], syllable[-1:])


def jm_pos(descriptions):
    """JMdict part of speech, given as its description text ("Godan verb with 'u' ending", …)."""
    for d in descriptions:
        d = d.lower()
        if d.startswith('noun or participle which takes the aux. verb suru') or d.startswith('noun'):
            return 'noun'
        if 'verb' in d and 'adverb' not in d:
            return 'verb'
        if 'adjective' in d or 'adjectival' in d:
            return 'adj'
        if 'adverb' in d:
            return 'adv'
        for key, pos in (('particle', 'particle'), ('pronoun', 'pron'), ('conjunction', 'conj'),
                         ('interjection', 'intj'), ('counter', 'noun'), ('numeric', 'num'), ('expression', 'phrase'),
                         ('prefix', 'prefix'), ('suffix', 'suffix'), ('auxiliary', 'particle')):
            if key in d:
                return pos
    return 'noun'


def build_japanese(kaikki_path, jmdict_path):
    # English-gloss -> topic, learnt from the Japanese Wiktionary entries' categories.
    gloss_topic = {}
    with open(kaikki_path, encoding='utf-8') as f:
        for line in f:
            e = json.loads(line)
            cats = categories_of(e)
            topic = topic_of(e.get('pos'), cats)
            if topic in ('other', 'verbs', 'adjectives', 'adverbs', 'small'):
                continue
            for s in e.get('senses', [])[:1]:
                for g in s.get('glosses', [])[:1]:
                    gloss_topic.setdefault(clean_gloss(g).lower(), topic)
    # Also borrow topics from the other languages' level lists (same English meaning, same topic).
    for path in sorted(glob.glob(os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'vocab_*.tsv'))):
        if path.endswith('vocab_ja.tsv'):
            continue
        for line in open(path, encoding='utf-8'):
            f = line.rstrip('\n').split('\t')
            if len(f) > 4 and not line.startswith('#') and f[1] not in ('other', 'verbs', 'adjectives', 'adverbs', 'small'):
                gloss_topic.setdefault(f[4].lower(), f[1])
    entries = []
    parser = ET.iterparse(gzip.open(jmdict_path), events=('end',))
    for _, el in parser:
        if el.tag != 'entry':
            continue
        kebs = [(k.findtext('keb'), [p.text for p in k.findall('ke_pri')]) for k in el.findall('k_ele')]
        rebs = [(r.findtext('reb'), [p.text for p in r.findall('re_pri')]) for r in el.findall('r_ele')]
        pri = [p for _, ps in kebs + rebs for p in ps]
        nf = min((int(p[2:]) for p in pri if p.startswith('nf')), default=None)
        everyday = any(p in ('ichi1', 'spec1') for p in pri)
        if nf is None and not everyday:
            el.clear()
            continue
        nf = nf if nf is not None else 30  # everyday words without a newspaper band sort after the common ones
        senses = el.findall('sense')
        pos_raw = [p.text or '' for s in senses[:1] for p in s.findall('pos')]
        misc = [m.text or '' for s in senses[:1] for m in s.findall('misc')]
        gloss = next((g.text for s in senses for g in s.findall('gloss') if g.text), '')
        pos = jm_pos(pos_raw)
        kana = rebs[0][0]
        kanji = kebs[0][0] if kebs else ''
        usually_kana = any('usually written using kana' in m for m in misc)
        bonus = sum(1 for p in pri if p in ('news1', 'ichi1', 'spec1'))
        entries.append(dict(nf=nf, bonus=bonus, everyday=everyday, kana=kana, kanji=kanji, gloss=clean_gloss(gloss), pos=pos,
                            usually_kana=usually_kana))
        el.clear()
    # Everyday vocabulary first (JMdict's "ichi1"/"spec1" marks), then by newspaper frequency band.
    entries.sort(key=lambda r: (0 if r['everyday'] else 1, r['nf'], -r['bonus']))
    rows, seen = [], set()
    for r in entries:
        if r['pos'] in SKIP_POS or not r['gloss'] or r['kana'] in seen:
            continue
        seen.add(r['kana'])
        if r['pos'] in POS_TOPIC and r['pos'] not in ('intj', 'phrase', 'num'):
            topic = POS_TOPIC[r['pos']]  # verbs, adjectives, adverbs and small words keep their grammar topic
        else:
            topic = gloss_topic.get(r['gloss'].lower()) or POS_TOPIC.get(r['pos'], 'other')
        note = 'Usually written in kana.' if r['usually_kana'] and r['kanji'] else ''
        rows.append(dict(level=0, topic=topic, text=r['kana'], reading=kana_to_romaji(r['kana'], r['pos'] == 'verb'),
                         meaning=r['gloss'], kanji=r['kanji'], note=note,
                         say=r['kanji'] if r['kanji'] and not r['usually_kana'] else r['kana'], pos=r['pos']))
        if len(rows) == LEVEL_SIZE * LEVELS:
            break
    for i, row in enumerate(rows):
        row['level'] = i // LEVEL_SIZE + 1
    return rows


def main():
    code, kaikki, extra = sys.argv[1], sys.argv[2], sys.argv[3]
    rows = build_japanese(kaikki, extra) if code == 'ja' else build_from_frequency(code, kaikki, extra)
    out = os.path.join(ROOT, 'app', 'src', 'main', 'assets', f'vocab_{code}.tsv')
    with open(out, 'w', encoding='utf-8') as f:
        f.write(f'# {code} vocabulary, {len(rows)} words in levels of {LEVEL_SIZE} by frequency. Generated by tools/build_vocab.py.\n')
        f.write('# Sources: English Wiktionary via kaikki.org; FrequencyWords (OpenSubtitles 2018); JMdict (EDRDG). All CC BY-SA.\n')
        f.write('# level<TAB>topic<TAB>word<TAB>reading<TAB>meaning<TAB>kanji<TAB>note<TAB>speech text<TAB>picture\n')
        for r in rows:
            fields = [str(r['level']), r['topic'], r['text'], r['reading'], r['meaning'], r['kanji'], r['note'], r['say'], '']
            f.write('\t'.join(x.replace('\t', ' ').replace('\n', ' ') for x in fields) + '\n')
    nouns = sorted({(r['meaning'], r['topic']) for r in rows if r['pos'] == 'noun' and not r['meaning'].startswith('to ')})
    with open(os.path.join(os.path.dirname(kaikki), f'{code}.nouns.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(f'{m}\t{t}' for m, t in nouns) + '\n')
    by_level = collections.Counter(r['level'] for r in rows)
    by_topic = collections.Counter(r['topic'] for r in rows)
    print(f'{code}: {len(rows)} words, levels {dict(sorted(by_level.items()))}')
    print('  topics:', dict(by_topic.most_common()))


if __name__ == '__main__':
    main()
