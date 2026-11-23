# Third-party content

The code is under the MIT licence (`LICENSE`). These data files come from, or are derived from, other works
and keep their licences. Files derived from CC BY-SA works are shared under CC BY-SA 4.0 (or the source's
version where it says so), so anyone reusing them must keep that licence and the attribution below.

| Files | Source | Licence |
|---|---|---|
| `app/src/main/assets/strokes_kana.txt` | Converted from [KanjiVG](https://kanjivg.tagaini.net), © Ulrich Apel | CC BY-SA 3.0 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` (the app icon) | The strokes of あ are from KanjiVG, © Ulrich Apel; the other letters are drawn for this app (`tools/make_icon.py`) | CC BY-SA 3.0 |
| `app/src/main/assets/vocab_*.tsv` | Built from English Wiktionary (extracted by [kaikki.org](https://kaikki.org)), [FrequencyWords](https://github.com/hermitdave/FrequencyWords) by Hermit Dave (OpenSubtitles 2018) and, for Japanese, [JMdict](https://www.edrdg.org/jmdict/j_jmdict.html) by the Electronic Dictionary Research and Development Group | CC BY-SA 4.0 |
| `app/src/main/assets/forms_*.tsv` | Word forms from English Wiktionary, extracted by kaikki.org | CC BY-SA 4.0 |
| `app/src/main/assets/anim/*.json` | [Noto Emoji Animation](https://googlefonts.github.io/noto-emoji-animation/), © Google | CC BY 4.0 |
| `app/src/main/assets/pictures/*.jpg` | Photos from [Wikimedia Commons](https://commons.wikimedia.org), resized. Author, licence and source page of each are in `app/src/main/assets/pictures/credits.tsv` | Each photo's own licence (CC BY-SA, CC BY, CC0 or public domain) |
| `app/src/main/assets/photos_remote.tsv` | Links to Wikimedia Commons photos that the app downloads on the phone, with each photo's author, licence and source page. The photos themselves are not in this repository | Each photo's own licence |

Written for this project and covered by the project licence: the Greek stroke data (`strokes_greek.txt`,
`tools/strokes_greek.py`), the Essentials word lists (`words_*.tsv`), the grammar lessons (`grammar_*.txt`) and the
hand-checked tables in `tools/` (`vocab_fixes.tsv`, `word_icons.tsv`, `photo_review.tsv`).

The built app also contains libraries pulled in by Gradle (not stored here), among them Kotlin, AndroidX /
Jetpack Compose and [Lottie](https://github.com/airbnb/lottie-android), all under the Apache License 2.0.
