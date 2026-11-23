# Alphabet Writer (shown on the phone as "ABC")

[![Build](https://github.com/kgalabov2/ABC/actions/workflows/build.yml/badge.svg)](https://github.com/kgalabov2/ABC/actions/workflows/build.yml)

Android app for learning languages: words, grammar and writing foreign alphabets by hand. It opens on the
Words tab; the bottom bar has Words, Grammar and Letters. The phone shows it as **ABC**; the package
(`com.alphabetwriter`), the progress file format and the class names keep the old name, so progress and
backups carry over.

Letters: writing foreign alphabets by hand. It currently covers Japanese hiragana and katakana
(including voiced kana, combinations such as きゃ, small kana, loanword sounds and historical kana) and Greek
(small and capital letters).

For every letter:

- **Watch:** the letter is drawn stroke by stroke. Each stroke grows in red from a green square at its start,
  with stroke numbers. Below are numbered step thumbnails; tap one to pause on that step. Replay, pause and
  speed (Slow / Normal / Fast) sit under the animation, and tapping the animation also pauses it.
- **Write:** a free-hand pad. **Hint** animates the next stroke to write (start square, direction arrow,
  number), counting the strokes already drawn. **Guide** shows the whole letter faintly behind your writing.
  Undo and Clear remove strokes.
- **Details:** pronunciation (description, IPA and the nearest Cyrillic spelling), Listen / Slowly through
  the phone's text-to-speech, an example word, look-alikes, the hiragana/katakana or capital/small
  counterpart, origin, and reading tips for Greek letter pairs.
- The ✓ button marks a letter as learned; the home tables show learned letters highlighted.
- **Hard letters:** the circle next to ✓ marks a letter as hard (problematic). Hard letters show a red dot in
  the tables. A wrong exam answer marks a letter hard; a right one clears it.
- **Exam** (button on the home screen): tests the letters marked as learned in the current tab. Choose
  Mixed, Pick from 4 (see a sound or letter name, tap its letter; graded at once) or Write it (write the
  letter, reveal it over your writing with its stroke order, then answer "I knew it" or "Not yet learned"),
  and 10, 20 or all questions. Hard letters come up more often. Results list the missed letters, each one tap
  from practice. Wrong choices never sound the same as the answer (を/お, じ/ぢ, σ/ς).
- **Words** (bottom tab): Japanese or Greek vocabulary by topic (casual conversation, numbers, shopping,
  food and drink, travel and directions, time and days, family and people, colours, weather, animals). A topic
  shows a grid of pictures with each word written under its picture; a word's card shows the photo, the word,
  its reading and meaning, says it aloud (text-to-speech, normal or slow), animates how it is written letter by
  letter (Greek accents included) and links each letter to its practice page. ✓ marks a word as known; the
  topic **Quiz** asks "Pick the picture" (see and hear the word) or "Pick the word" (see the picture), and
  wrong answers mark the word hard (red dot). Word lists: `app/src/main/assets/words_*.tsv`.
- **Word forms:** the card of a Bulgarian, German or Japanese verb, noun or adjective has a folded **Forms**
  section: conjugation (present, past, perfect, polite/plain…) or declension (article, plural, definite forms),
  from English Wiktionary; tapping a form says it.
- **Grammar** (bottom tab): beginner (A1–A2) lessons for Bulgarian, German and Japanese, for the language chosen
  in Words. A lesson has a short explanation, tables, spoken examples and a practice of gap-fill questions with
  four options (the sentence's English is shown; the finished sentence is said aloud). 80% or more right marks
  the lesson done (✓); a failed practice marks it hard (red dot). Lesson progress is backed up with the rest.
- **Back up and restore** (⋮ menu): "Save to a file…" picks a file through Android's file picker (choose
  Google Drive there); the app rewrites it after every change. After a reinstall, "Restore from a file…"
  reads it back and keeps it updated again. Android's own Google backup also covers the progress
  (`res/xml/*backup*` excludes only the file permission, which cannot survive a reinstall). The file format
  is plain text, see `ProgressFile.kt`.

If the speaker buttons are missing, the phone has no Japanese or Greek voice installed. The details page
links to the text-to-speech settings (Samsung: Settings → General management → Text-to-speech).

## Build and install

Needs JDK 17 and the Android SDK with platform 37 (`gradle.properties` turns off automatic SDK downloads, so
install missing parts with the SDK manager). Gradle 9.8.2 comes through the wrapper.

```bash
./gradlew assembleDebug testDebugUnitTest
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions (`.github/workflows/build.yml`) does the same on every push and pull request and keeps the
APK and the test report as the run's artifacts. The build also runs the unit tests (`app/src/test`): stroke data coverage and stroke counts, word lists and
levels, photo and animation data, grammar lessons and word forms, and the practice and quiz logic.

## Stroke data

All strokes live in `app/src/main/assets/strokes_*.txt`, one character per line, in a 109 × 109 box:
`character<TAB>stroke 1<TAB>stroke 2 ...`, each stroke being SVG path data plus `@x,y` for its number label.

- `strokes_kana.txt` is converted from [KanjiVG](https://kanjivg.tagaini.net) (© Ulrich Apel, CC BY-SA 3.0)
  with `tools/kanjivg_to_asset.py <svg dir> <out file>`. The file is shared under the same licence.
- `strokes_greek.txt` is generated by `tools/strokes_greek.py`, which holds the hand-drawn Greek strokes.
  Writing lines: capital/ascender y=22, x-height y=47, baseline y=77, descender y=98.
- `tools/preview.py <asset> <out.html> [lines|square]` renders an asset as a contact sheet for checking
  shapes and stroke order by eye.

## Word levels (about 10 000 words per language)

Each language has the hand-made **Essentials** plus five levels of 2 000 words, most common first
(Level 1 ≈ CEFR A1–A2 … Level 5 ≈ C2; JLPT N5–N1 for Japanese). **Learn new words** shows the next unseen words
of a level ("Learn it" / "Already know"); they then come back in **Review** (spaced repetition). Words without a
picture are quizzed by meaning.

Built by `tools/build_vocab.py <code> <kaikki jsonl> <frequency list | JMdict_e.gz>` into
`app/src/main/assets/vocab_<code>.tsv`, from English Wiktionary (kaikki.org extracts), FrequencyWords
(OpenSubtitles 2018) and, for Japanese, JMdict's newspaper frequency bands — all CC BY-SA. Inflected forms are
counted towards their dictionary form; a spelling that can be several words (French *est*: "is" or "east") is
shared between them by how common each is on its own evidence; vulgar senses are left out; nouns of German, French,
Italian carry their article, Greek nouns their article in the note. Wiktionary describes function words by their
grammar, so the meanings of the most common words are checked by hand in `tools/vocab_fixes.tsv` (a "-" there
leaves a bogus word out). `tools/match_pictures.py` then gives nouns the lead photo of the
English Wikipedia article named by their meaning (free licences only, no maps, flags, logos or drawings) and writes
`photos_remote.tsv`; the phone downloads those photos on first view and keeps them.

**Every photo is checked by eye before the app may use it.** `match_pictures.py` lists candidates; contact sheets
(photo + the meanings that would use it) are judged KEEP/REJECT into `tools/photo_review.tsv`, and only KEEP photos
reach `photos_remote.tsv`. The bar: a learner seeing only the photo would name the word. Of 3 917 candidates, 1 283
passed (abstract words, paintings, posters, text, unclear roles such as "father" were rejected). Words whose meaning
has an unmistakable emoji (`tools/word_icons.tsv`: clothes, food, animals, vehicles…) show that emoji; numbers show
digits and colours a swatch. A word's own card, Learn and Review play the emoji's animation when Google's Noto
animated emoji has one (`assets/anim`, Lottie, CC BY 4.0; 173 bundled); grids and quizzes stay still.
`dist/alphabet-writer-pictures.zip` holds the approved photos for "Import pictures and progress".

Photo downloads use Android's HTTP/2 client (`HttpEngine`, Android 14+): Wikimedia refuses about half of all
HTTP/1.1 requests with "429 Too many requests", whatever the pace. Every request waits its turn in `Pace`: at
most 1 500 an hour (counted across restarts in `files/photo-requests.log`) and a random pause before each one,
0.4–1.5 s while browsing and 2.5–6 s during **Download all** (about 850 an hour, so all ~4 100 photos take about
five hours with the app open).

Known gaps: Greek (~8 750) and Bulgarian (~6 650) have fewer than 10 000 words because the 50 000-entry frequency
lists run out of distinct dictionary words; Japanese is ranked by newspaper frequency, so level 1 leans to news
vocabulary; many words land in "Other words" where Wiktionary has no topic category.

## App icon

One letter per corner, each from a different language and found only in that script: あ (Japanese), λ (Greek),
Ж (Bulgarian) and Ü (German), drawn as pen strokes. `tools/make_icon.py . preview.png` writes the vector drawable
and the background colour and renders a circle-masked preview.

## Grammar lessons and word forms

Lessons: `app/src/main/assets/grammar_<code>.txt`, one file per language, in a line format described and checked
by `tools/check_grammar.py` (`python3 -I tools/check_grammar.py app/src/main/assets/grammar_*.txt`). A lesson id
is its progress key (`g:<code>:<id>`), so never rename one. Japanese text is kana only (the speech text after the
last `|` of an example may use kanji so the voice reads it naturally). The lessons were written for this app and
checked for correctness by a separate reviewer; a language gets the tab as soon as its file exists.

Word forms: `tools/build_forms.py` builds `app/src/main/assets/forms_<code>.tsv` from the kaikki.org extracts and
the word lists (one row per form: word, section, label, form, reading).

## Moving to another phone

Backup, pictures, moving phones (⋮ menu): **Share the app** sends the installed app through the share sheet;
**Share pictures** packs the downloaded photos and the progress into one zip; on the other phone, install the app
and use **Import pictures and progress**. **Download all pictures** fetches every photo for offline use. Android's
own phone-to-phone transfer also carries the photos (they are left out of the 25 MB cloud backup).

## Word photos

`tools/fetch_pictures.py <download dir>` fetches the lead image of an English Wikipedia article for each picture
(the `ARTICLES` table, chosen after reviewing the photos), with its Commons licence and author. Images are
downloaded with `curl` (upload.wikimedia.org throttles Python's HTTP client). `tools/pictures_to_assets.py
<download dir>` shrinks them into `app/src/main/assets/pictures/` and writes `credits.tsv`. Words without a photo
show their emoji.

## Adding an alphabet

1. Write its strokes in a generator like `tools/strokes_greek.py` and produce `strokes_<name>.txt`.
2. Add a `Script` entry (title, native title, TTS language tag, grid style, asset) in `Alphabets.kt`, and its
   letter groups.
3. Add its pronunciation details in `LetterInfo.kt`.
4. The unit tests check that every letter has stroke data and pronunciation details.

## Licence

The app's code (Kotlin sources, build files and `tools/*.py`) is under the MIT licence in `LICENSE`. The bundled
data keeps its own licences: the stroke data, word lists and word forms are CC BY-SA, the animations CC BY 4.0 and
each photo its own Commons licence. `THIRD_PARTY_NOTICES.md` lists them with their authors.
