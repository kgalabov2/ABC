package com.alphabetwriter

import java.text.Normalizer

/** A word that uses the letter. [reading] is its romanisation. */
data class Example(val word: String, val reading: String, val meaning: String)

/** A letter that is easy to confuse with this one. [text] may be outside the app (e.g. a kanji or a Latin letter). */
data class LookAlike(val text: String, val tip: String)

/** Everything the details page shows about a letter. */
data class LetterInfo(
    /** One line for the writing screen, e.g. "shi · [ɕi] · ши". */
    val summary: String,
    /** How to make the sound, one line per part. */
    val sounds: List<String>,
    val ipa: String?,
    /** Closest spelling in (Bulgarian) Cyrillic, or null when there is none. */
    val cyrillic: String?,
    val rhythm: String?,
    val examples: List<Example>,
    val lookAlikes: List<LookAlike>,
    val readingTips: List<String>,
    val origin: String?,
    /** The same letter in the other script or case, if the app has it. */
    val counterpart: Pair<Script, String>?,
    val counterpartNote: String?,
)

object LetterInfos {
    fun of(script: Script, letter: Letter): LetterInfo = when (script) {
        Script.HIRAGANA, Script.KATAKANA -> KanaInfo.of(script, letter)
        Script.GREEK -> GreekInfo.of(letter)
    }
}

private object KanaInfo {
    private val vowels = mapOf(
        'a' to Triple("a", "а", "a as in \"father\", short"),
        'i' to Triple("i", "и", "i as in \"machine\", short"),
        'u' to Triple("ɯ", "у", "u with relaxed, unrounded lips; often almost silent after k, s, t, h (as in です desu)"),
        'e' to Triple("e", "е", "e as in \"pet\""),
        'o' to Triple("o", "о", "o as in \"pot\", short and rounded"),
    )

    /** Consonant part of the romaji → IPA, Cyrillic, how to say it. */
    private val consonants = mapOf(
        "" to Triple("", "", null),
        "k" to Triple("k", "к", "k as in \"kit\""),
        "s" to Triple("s", "с", "s as in \"sun\""),
        "sh" to Triple("ɕ", "ш", "sh as in \"she\", with the tongue flatter and softer than Bulgarian ш"),
        "t" to Triple("t", "т", "t as in \"stop\""),
        "ch" to Triple("tɕ", "ч", "ch as in \"cheese\", softer than Bulgarian ч"),
        "ts" to Triple("ts", "ц", "ts as in \"cats\""),
        "n" to Triple("n", "н", "n as in \"no\""),
        "h" to Triple("h", "х", "h as in \"house\", a light breath"),
        "f" to Triple("ɸ", "ф", "a soft f made with both lips, as if blowing out a candle; between f and h"),
        "m" to Triple("m", "м", "m as in \"me\""),
        "y" to Triple("j", "й", "y as in \"yes\" (Bulgarian й)"),
        "r" to Triple("ɾ", "р", "one quick tap of the tongue, between r and l, like tt in American \"butter\""),
        "w" to Triple("ɰ", "у", "w as in \"water\", with the lips barely rounded"),
        "g" to Triple("ɡ", "г", "g as in \"go\"; inside words some speakers soften it to ng"),
        "z" to Triple("z", "з", "z as in \"zoo\"; at the start of a word closer to dz"),
        "j" to Triple("dʑ", "дж", "j as in \"jeep\", softer than Bulgarian дж"),
        "d" to Triple("d", "д", "d as in \"dog\""),
        "b" to Triple("b", "б", "b as in \"bed\""),
        "p" to Triple("p", "п", "p as in \"pen\""),
        "v" to Triple("v", "в", "v as in \"van\"; many Japanese speakers say b instead"),
        "ky" to Triple("kʲ", "к", "k blended with y into one sound"),
        "gy" to Triple("ɡʲ", "г", "g blended with y into one sound"),
        "ny" to Triple("ɲ", "н", "n blended with y, like Bulgarian нь in \"ньой\""),
        "hy" to Triple("ç", "х", "a breathy h with the tongue raised, like German \"ich\""),
        "my" to Triple("mʲ", "м", "m blended with y into one sound"),
        "ry" to Triple("ɾʲ", "р", "the tapped r blended with y"),
        "by" to Triple("bʲ", "б", "b blended with y into one sound"),
        "py" to Triple("pʲ", "п", "p blended with y into one sound"),
    )

    /** Hiragana origins: cursive forms of these kanji (man'yōgana). */
    private val hiraganaOrigin = mapOf(
        "あ" to "安", "い" to "以", "う" to "宇", "え" to "衣", "お" to "於",
        "か" to "加", "き" to "幾", "く" to "久", "け" to "計", "こ" to "己",
        "さ" to "左", "し" to "之", "す" to "寸", "せ" to "世", "そ" to "曽",
        "た" to "太", "ち" to "知", "つ" to "川", "て" to "天", "と" to "止",
        "な" to "奈", "に" to "仁", "ぬ" to "奴", "ね" to "祢", "の" to "乃",
        "は" to "波", "ひ" to "比", "ふ" to "不", "へ" to "部", "ほ" to "保",
        "ま" to "末", "み" to "美", "む" to "武", "め" to "女", "も" to "毛",
        "や" to "也", "ゆ" to "由", "よ" to "与",
        "ら" to "良", "り" to "利", "る" to "留", "れ" to "礼", "ろ" to "呂",
        "わ" to "和", "ゐ" to "為", "ゑ" to "恵", "を" to "遠", "ん" to "无",
    )

    /** Katakana origins: a part (or a simplified whole) of these kanji. */
    private val katakanaOrigin = mapOf(
        "ア" to "阿", "イ" to "伊", "ウ" to "宇", "エ" to "江", "オ" to "於",
        "カ" to "加", "キ" to "幾", "ク" to "久", "ケ" to "介", "コ" to "己",
        "サ" to "散", "シ" to "之", "ス" to "須", "セ" to "世", "ソ" to "曽",
        "タ" to "多", "チ" to "千", "ツ" to "川", "テ" to "天", "ト" to "止",
        "ナ" to "奈", "ニ" to "二", "ヌ" to "奴", "ネ" to "祢", "ノ" to "乃",
        "ハ" to "八", "ヒ" to "比", "フ" to "不", "ヘ" to "部", "ホ" to "保",
        "マ" to "末", "ミ" to "三", "ム" to "牟", "メ" to "女", "モ" to "毛",
        "ヤ" to "也", "ユ" to "由", "ヨ" to "与",
        "ラ" to "良", "リ" to "利", "ル" to "流", "レ" to "礼", "ロ" to "呂",
        "ワ" to "和", "ヰ" to "井", "ヱ" to "恵", "ヲ" to "乎",
    )

    private fun ex(word: String, reading: String, meaning: String) = Example(word, reading, meaning)

    private val examples: Map<String, Example> = mapOf(
        // Hiragana: native words.
        "あ" to ex("あめ", "ame", "rain"), "い" to ex("いぬ", "inu", "dog"), "う" to ex("うみ", "umi", "sea"),
        "え" to ex("えき", "eki", "station"), "お" to ex("おちゃ", "ocha", "green tea"),
        "か" to ex("かさ", "kasa", "umbrella"), "き" to ex("きた", "kita", "north"), "く" to ex("くつ", "kutsu", "shoes"),
        "け" to ex("けむり", "kemuri", "smoke"), "こ" to ex("こえ", "koe", "voice"),
        "さ" to ex("さくら", "sakura", "cherry blossom"), "し" to ex("しお", "shio", "salt"),
        "す" to ex("すし", "sushi", "sushi"), "せ" to ex("せかい", "sekai", "world"), "そ" to ex("そら", "sora", "sky"),
        "た" to ex("たまご", "tamago", "egg"), "ち" to ex("ちず", "chizu", "map"), "つ" to ex("つき", "tsuki", "moon"),
        "て" to ex("て", "te", "hand"), "と" to ex("とり", "tori", "bird"),
        "な" to ex("なつ", "natsu", "summer"), "に" to ex("にく", "niku", "meat"), "ぬ" to ex("ぬの", "nuno", "cloth"),
        "ね" to ex("ねこ", "neko", "cat"), "の" to ex("のり", "nori", "seaweed"),
        "は" to ex("はな", "hana", "flower"), "ひ" to ex("ひと", "hito", "person"), "ふ" to ex("ふゆ", "fuyu", "winter"),
        "へ" to ex("へや", "heya", "room"), "ほ" to ex("ほし", "hoshi", "star"),
        "ま" to ex("まど", "mado", "window"), "み" to ex("みず", "mizu", "water"), "む" to ex("むし", "mushi", "insect"),
        "め" to ex("め", "me", "eye"), "も" to ex("もり", "mori", "forest"),
        "や" to ex("やま", "yama", "mountain"), "ゆ" to ex("ゆき", "yuki", "snow"), "よ" to ex("よる", "yoru", "night"),
        "ら" to ex("らいねん", "rainen", "next year"), "り" to ex("りんご", "ringo", "apple"),
        "る" to ex("るす", "rusu", "not at home"), "れ" to ex("れきし", "rekishi", "history"),
        "ろ" to ex("ろうそく", "rōsoku", "candle"),
        "わ" to ex("わたし", "watashi", "I, me"), "を" to ex("ほんをよむ", "hon o yomu", "to read a book"),
        "ん" to ex("ほん", "hon", "book"),
        "が" to ex("がっこう", "gakkō", "school"), "ぎ" to ex("ぎんこう", "ginkō", "bank"),
        "ぐ" to ex("ぐあい", "guai", "condition, how you feel"), "げ" to ex("げんき", "genki", "healthy, well"),
        "ご" to ex("ごはん", "gohan", "rice, meal"),
        "ざ" to ex("ざっし", "zasshi", "magazine"), "じ" to ex("じかん", "jikan", "time"),
        "ず" to ex("ずっと", "zutto", "all the time"), "ぜ" to ex("ぜんぶ", "zenbu", "all"), "ぞ" to ex("ぞう", "zō", "elephant"),
        "だ" to ex("だいがく", "daigaku", "university"), "ぢ" to ex("はなぢ", "hanaji", "nosebleed"),
        "づ" to ex("つづく", "tsuzuku", "to continue"), "で" to ex("でんわ", "denwa", "telephone"),
        "ど" to ex("どこ", "doko", "where"),
        "ば" to ex("ばしょ", "basho", "place"), "び" to ex("くび", "kubi", "neck"), "ぶ" to ex("ぶた", "buta", "pig"),
        "べ" to ex("べんきょう", "benkyō", "study"), "ぼ" to ex("ぼうし", "bōshi", "hat"),
        "ぱ" to ex("かんぱい", "kanpai", "cheers!"), "ぴ" to ex("えんぴつ", "enpitsu", "pencil"),
        "ぷ" to ex("てんぷら", "tenpura", "tempura"), "ぺ" to ex("ぺこぺこ", "pekopeko", "starving"),
        "ぽ" to ex("さんぽ", "sanpo", "a walk"),
        "きゃ" to ex("きゃく", "kyaku", "guest"), "きゅ" to ex("きゅうり", "kyūri", "cucumber"),
        "きょ" to ex("きょう", "kyō", "today"),
        "しゃ" to ex("しゃしん", "shashin", "photo"), "しゅ" to ex("しゅくだい", "shukudai", "homework"),
        "しょ" to ex("しょくじ", "shokuji", "meal"),
        "ちゃ" to ex("おちゃ", "ocha", "green tea"), "ちゅ" to ex("ちゅうい", "chūi", "caution"),
        "ちょ" to ex("ちょっと", "chotto", "a little"),
        "にゃ" to ex("こんにゃく", "konnyaku", "konjac jelly"), "にゅ" to ex("ぎゅうにゅう", "gyūnyū", "milk"),
        "にょ" to ex("にょろにょろ", "nyoronyoro", "wriggling"),
        "ひゃ" to ex("ひゃく", "hyaku", "hundred"), "ひょ" to ex("ひょう", "hyō", "leopard; hail"),
        "みゃ" to ex("みゃく", "myaku", "pulse"), "みょ" to ex("みょうじ", "myōji", "family name"),
        "りゃ" to ex("りゃくご", "ryakugo", "abbreviation"), "りゅ" to ex("りゅう", "ryū", "dragon"),
        "りょ" to ex("りょこう", "ryokō", "trip"),
        "ぎゃ" to ex("ぎゃく", "gyaku", "opposite"), "ぎゅ" to ex("ぎゅうにく", "gyūniku", "beef"),
        "ぎょ" to ex("きんぎょ", "kingyo", "goldfish"),
        "じゃ" to ex("じゃま", "jama", "in the way"), "じゅ" to ex("じゅぎょう", "jugyō", "lesson"),
        "じょ" to ex("じょうず", "jōzu", "skilful"),
        "びゃ" to ex("さんびゃく", "sanbyaku", "three hundred"), "びょ" to ex("びょういん", "byōin", "hospital"),
        "ぴゃ" to ex("はっぴゃく", "happyaku", "eight hundred"), "ぴょ" to ex("ぴょんぴょん", "pyonpyon", "hopping"),
        "っ" to ex("きって", "kitte", "postage stamp"),
        // Katakana: loanwords.
        "ア" to ex("アイス", "aisu", "ice cream"), "イ" to ex("インク", "inku", "ink"),
        "ウ" to ex("ウイルス", "uirusu", "virus"), "エ" to ex("エアコン", "eakon", "air conditioner"),
        "オ" to ex("オレンジ", "orenji", "orange"),
        "カ" to ex("カメラ", "kamera", "camera"), "キ" to ex("キロ", "kiro", "kilo"), "ク" to ex("クラス", "kurasu", "class"),
        "ケ" to ex("ケーキ", "kēki", "cake"), "コ" to ex("コーヒー", "kōhī", "coffee"),
        "サ" to ex("サラダ", "sarada", "salad"), "シ" to ex("シーツ", "shītsu", "bed sheet"),
        "ス" to ex("スープ", "sūpu", "soup"), "セ" to ex("セーター", "sētā", "sweater"), "ソ" to ex("ソファ", "sofa", "sofa"),
        "タ" to ex("タオル", "taoru", "towel"), "チ" to ex("チーズ", "chīzu", "cheese"), "ツ" to ex("ツアー", "tsuā", "tour"),
        "テ" to ex("テレビ", "terebi", "TV"), "ト" to ex("トマト", "tomato", "tomato"),
        "ナ" to ex("ナイフ", "naifu", "knife"), "ニ" to ex("テニス", "tenisu", "tennis"), "ヌ" to ex("カヌー", "kanū", "canoe"),
        "ネ" to ex("ネクタイ", "nekutai", "necktie"), "ノ" to ex("ノート", "nōto", "notebook"),
        "ハ" to ex("ハンバーガー", "hanbāgā", "hamburger"), "ヒ" to ex("ヒーター", "hītā", "heater"),
        "フ" to ex("フランス", "Furansu", "France"), "ヘ" to ex("ヘリコプター", "herikoputā", "helicopter"),
        "ホ" to ex("ホテル", "hoteru", "hotel"),
        "マ" to ex("マスク", "masuku", "mask"), "ミ" to ex("ミルク", "miruku", "milk"), "ム" to ex("ゲーム", "gēmu", "game"),
        "メ" to ex("メール", "mēru", "e-mail"), "モ" to ex("モデル", "moderu", "model"),
        "ヤ" to ex("タイヤ", "taiya", "tyre"), "ユ" to ex("ユーロ", "yūro", "euro"), "ヨ" to ex("ヨーグルト", "yōguruto", "yogurt"),
        "ラ" to ex("ラジオ", "rajio", "radio"), "リ" to ex("リモコン", "rimokon", "remote control"),
        "ル" to ex("ルール", "rūru", "rule"), "レ" to ex("レモン", "remon", "lemon"), "ロ" to ex("ロボット", "robotto", "robot"),
        "ワ" to ex("ワイン", "wain", "wine"), "ン" to ex("パン", "pan", "bread"),
        "ガ" to ex("ガム", "gamu", "chewing gum"), "ギ" to ex("ギター", "gitā", "guitar"), "グ" to ex("グラス", "gurasu", "glass"),
        "ゲ" to ex("ゲーム", "gēmu", "game"), "ゴ" to ex("ゴルフ", "gorufu", "golf"),
        "ザ" to ex("デザート", "dezāto", "dessert"), "ジ" to ex("ジーンズ", "jīnzu", "jeans"),
        "ズ" to ex("チーズ", "chīzu", "cheese"), "ゼ" to ex("ゼロ", "zero", "zero"), "ゾ" to ex("ゾーン", "zōn", "zone"),
        "ダ" to ex("ダンス", "dansu", "dance"), "デ" to ex("デパート", "depāto", "department store"),
        "ド" to ex("ドア", "doa", "door"),
        "バ" to ex("バス", "basu", "bus"), "ビ" to ex("ビール", "bīru", "beer"), "ブ" to ex("ブログ", "burogu", "blog"),
        "ベ" to ex("ベッド", "beddo", "bed"), "ボ" to ex("ボール", "bōru", "ball"),
        "パ" to ex("パン", "pan", "bread"), "ピ" to ex("ピアノ", "piano", "piano"), "プ" to ex("プール", "pūru", "swimming pool"),
        "ペ" to ex("ペン", "pen", "pen"), "ポ" to ex("ポスト", "posuto", "postbox"),
        "キャ" to ex("キャンプ", "kyanpu", "camping"), "キュ" to ex("キュート", "kyūto", "cute"),
        "シャ" to ex("シャツ", "shatsu", "shirt"), "シュ" to ex("シューズ", "shūzu", "shoes"),
        "ショ" to ex("ショップ", "shoppu", "shop"),
        "チャ" to ex("チャンス", "chansu", "chance"), "チュ" to ex("チューブ", "chūbu", "tube"),
        "チョ" to ex("チョコレート", "chokorēto", "chocolate"),
        "ニュ" to ex("ニュース", "nyūsu", "news"), "ヒュ" to ex("ヒューズ", "hyūzu", "fuse"),
        "ミュ" to ex("ミュージック", "myūjikku", "music"), "リュ" to ex("リュック", "ryukku", "backpack"),
        "ギャ" to ex("ギャラリー", "gyararī", "gallery"),
        "ジャ" to ex("ジャム", "jamu", "jam"), "ジュ" to ex("ジュース", "jūsu", "juice"),
        "ジョ" to ex("ジョギング", "jogingu", "jogging"),
        "ビュ" to ex("ビュッフェ", "byuffe", "buffet"), "ピュ" to ex("コンピューター", "konpyūtā", "computer"),
        "ファ" to ex("ファン", "fan", "fan (supporter)"), "フィ" to ex("フィルム", "firumu", "film"),
        "フェ" to ex("カフェ", "kafe", "café"), "フォ" to ex("フォーク", "fōku", "fork"),
        "ヴァ" to ex("ヴァイオリン", "vaiorin", "violin"), "ヴィ" to ex("ヴィーナス", "Vīnasu", "Venus"),
        "ヴェ" to ex("ヴェール", "vēru", "veil"), "ヴォ" to ex("ヴォーカル", "vōkaru", "vocals"),
        "ウィ" to ex("ウィンドウ", "windō", "window (on a screen)"), "ウェ" to ex("ウェブ", "webu", "the web"),
        "ウォ" to ex("ウォッカ", "wokka", "vodka"),
        "ティ" to ex("パーティー", "pātī", "party"), "ディ" to ex("ディナー", "dinā", "dinner"),
        "トゥ" to ex("タトゥー", "tatū", "tattoo"),
        "シェ" to ex("シェフ", "shefu", "chef"), "ジェ" to ex("ジェット", "jetto", "jet"),
        "チェ" to ex("チェック", "chekku", "check"),
        "ッ" to ex("ロボット", "robotto", "robot"), "ー" to ex("コーヒー", "kōhī", "coffee"),
        "ヵ" to ex("一ヵ月", "ikkagetsu", "one month"), "ヶ" to ex("三ヶ月", "sankagetsu", "three months"),
    )

    private val lookAlikes: Map<String, List<LookAlike>> = buildMap<String, MutableList<LookAlike>> {
        fun pair(a: String, b: String, tip: String) {
            getOrPut(a) { mutableListOf() } += LookAlike(b, tip)
            getOrPut(b) { mutableListOf() } += LookAlike(a, tip)
        }
        fun kanji(kana: String, kanji: String, tip: String) {
            getOrPut(kana) { mutableListOf() } += LookAlike(kanji, tip)
        }
        pair("あ", "お", "お has a separate dot on the right; あ does not.")
        pair("い", "り", "り is taller: its right stroke is long and curves down; い's two strokes are short and apart.")
        pair("い", "こ", "Same two short strokes, but い stands (left and right), こ lies flat (top and bottom).")
        pair("う", "つ", "う has a short stroke on top; つ is a single curve.")
        pair("き", "さ", "き has two horizontal lines; さ has one.")
        pair("さ", "ち", "Near mirror images: ち's bottom bulges to the right like the number 5; さ's bottom is a separate stroke that bulges to the left.")
        pair("ぬ", "め", "ぬ ends with a small loop at the bottom right; め does not.")
        pair("ね", "れ", "Same first stroke; ね ends in a small loop, れ ends with a kick out to the right.")
        pair("ね", "わ", "Same first stroke; ね ends in a small loop, わ ends in a round curve.")
        pair("れ", "わ", "Same first stroke; れ ends with a kick out to the right, わ with a round curve.")
        pair("は", "ほ", "ほ has an extra horizontal line on top of the right-hand part.")
        pair("は", "け", "け has no loop at the bottom right.")
        pair("る", "ろ", "る ends with a small loop; ろ ends open.")
        pair("ま", "も", "ま ends with a loop at the bottom; も ends with an open hook curving right.")
        pair("シ", "ツ", "シ: the two dots sit on the left and the long stroke sweeps up from the bottom. ツ: the dots sit along the top and the long stroke comes down from the top right.")
        pair("ソ", "ン", "ソ: the first stroke is nearly vertical and the long stroke comes down from the top. ン: the first stroke is flatter and the long stroke sweeps up from the bottom.")
        pair("ク", "ケ", "ケ has a separate horizontal stroke reaching out to the right.")
        pair("ク", "タ", "タ has an extra short stroke inside.")
        pair("ウ", "ワ", "ウ has a short stroke on top; ワ does not.")
        pair("ワ", "フ", "ワ has a short vertical stroke on the left; フ does not.")
        pair("ス", "ヌ", "ヌ's short second stroke crosses the main stroke; ス's second stroke runs down-right from the bend.")
        pair("コ", "ロ", "ロ is closed on the left; コ is open.")
        pair("コ", "ユ", "ユ's bottom line sticks out past the right side.")
        pair("チ", "テ", "テ starts with a short flat line; チ starts with a short slanted stroke.")
        pair("マ", "ム", "マ's short stroke sits inside, under the bend; ム's is a short dot at the bottom right.")
        pair("ナ", "メ", "ナ has a flat horizontal line; メ is two crossing diagonals.")
        pair("ア", "マ", "ア's second stroke is a long leg down to the left; マ's is a short tick.")
        pair("ル", "レ", "ル has two strokes; レ is one.")
        pair("ヤ", "セ", "セ's second stroke curves along the bottom; ヤ's goes straight down to the left.")
        kanji("カ", "力", "Almost the same as the kanji 力 (chikara, power). Context tells them apart.")
        kanji("エ", "工", "Almost the same as the kanji 工 (craft). Context tells them apart.")
        kanji("ニ", "二", "Almost the same as the kanji 二 (two). Context tells them apart.")
        kanji("ロ", "口", "Almost the same as the kanji 口 (kuchi, mouth), which is drawn a little bigger.")
        kanji("ハ", "八", "Almost the same as the kanji 八 (eight). Context tells them apart.")
        kanji("タ", "夕", "Almost the same as the kanji 夕 (yū, evening). Context tells them apart.")
        kanji("ト", "卜", "Almost the same as the rare kanji 卜 (divination).")
    }

    /** Katakana that simplify a whole kanji rather than take a part of it. */
    private val katakanaFromWholeKanji = setOf("ハ", "ミ", "チ", "ニ", "ツ")

    /** Pairs where the hiragana and katakana look nearly the same. */
    private val similarAcrossScripts = setOf("へ", "り", "か", "き", "も", "せ", "や")

    fun of(script: Script, letter: Letter): LetterInfo {
        val text = letter.text
        val hira = toHiragana(text)
        val romaji = letter.label
        val special = specialSounds[hira]
        val parts = if (special == null) syllable(romaji) else null

        val ipa = special?.ipa ?: parts?.let { "[${it.ipa}]" }
        val cyrillic = special?.cyrillic ?: parts?.cyrillic
        val sounds = special?.sounds ?: parts?.sounds.orEmpty()
        val rhythm = when {
            hira == "っ" -> "One beat of silence: the mouth gets ready for the next consonant and holds it."
            hira == "ー" -> "One extra beat: the vowel before it lasts twice as long."
            hira.length == 1 && hira[0] in "ぁぃぅぇぉゃゅょゎ" ->
                "No beat of its own: it joins the kana before it into one beat (き + ゃ = きゃ, one beat)."
            text.length > 1 -> "One beat (mora), even though it is written with two kana."
            else -> "One beat (mora). Japanese gives every beat the same length."
        }

        val counterpartText = if (script == Script.HIRAGANA) Alphabets.toKatakana(text) else toHiragana(text)
        val otherScript = if (script == Script.HIRAGANA) Script.KATAKANA else Script.HIRAGANA
        val counterpart = counterpartText.takeIf { it != text && Alphabets.all(otherScript).any { l -> l.text == it } }
        val counterpartNote = counterpart?.let {
            if (hira in similarAcrossScripts) "The hiragana and katakana forms look almost the same." else null
        }

        return LetterInfo(
            summary = listOfNotNull(special?.summaryLabel, ipa, cyrillic?.let { "Cyrillic $it" }).joinToString(" · "),
            sounds = sounds,
            ipa = ipa,
            cyrillic = cyrillic,
            rhythm = rhythm,
            examples = listOfNotNull(examples[text]),
            lookAlikes = lookAlikes[text].orEmpty(),
            readingTips = emptyList(),
            origin = origin(script, text),
            counterpart = counterpart?.let { otherScript to it },
            counterpartNote = counterpartNote,
        )
    }

    private class Special(
        val ipa: String?,
        val cyrillic: String?,
        val sounds: List<String>,
        val summaryLabel: String? = null,
    )

    /** Kana whose sound does not follow from its romaji. Keyed by the hiragana form. */
    private val specialSounds = mapOf(
        "を" to Special("[o]", "о", listOf("Said exactly like お: o as in \"pot\". The w is written in romaji but silent today.")),
        "ん" to Special(
            "[ɴ]", "н",
            listOf(
                "n, m or ng depending on the next sound: m before m, b, p (さんぽ sanpo sounds sampo), ng before k and g.",
                "At the end of a word it is a nasal sound made at the back of the mouth.",
            ),
        ),
        "っ" to Special(
            null, null,
            listOf("No sound of its own: it doubles the next consonant, with a short pause before it (きって ki-t-te)."),
            summaryLabel = "doubles the next consonant",
        ),
        "ー" to Special(null, null, listOf("Lengthens the vowel before it: コ (ko) + ー = コー (kō)."), summaryLabel = "long vowel"),
        "ゐ" to Special("[i]", "и", listOf("Read i today. In old Japanese it was wi.")),
        "ゑ" to Special("[e]", "е", listOf("Read e today. In old Japanese it was we.")),
    )

    private class Syllable(val ipa: String, val cyrillic: String, val sounds: List<String>)

    /** Splits romaji such as "kyo" into consonant + vowel and builds IPA, Cyrillic and the how-to lines. */
    private fun syllable(romaji: String): Syllable? {
        val vowel = romaji.lastOrNull() ?: return null
        val v = vowels[vowel] ?: return null
        val c = consonants[romaji.dropLast(1)] ?: return null
        val cons = romaji.dropLast(1)
        var ipa = c.first + v.first
        var cyr = c.second + v.second
        when {
            // y-glides: きゃ кя, きゅ кю, きょ кьо; plain や я, ゆ ю, よ йо.
            cons.endsWith("y") && cons.length > 1 -> cyr = c.second + mapOf('a' to "я", 'u' to "ю", 'o' to "ьо")[vowel]
            cons == "y" -> cyr = mapOf('a' to "я", 'u' to "ю", 'o' to "йо")[vowel] ?: cyr
            cons == "n" && vowel == 'i' -> ipa = "ɲi"
            cons == "h" && vowel == 'i' -> ipa = "çi"
            cons == "w" && vowel != 'a' -> ipa = "w" + v.first // loanword ウィ ウェ ウォ
        }
        // Palatalised consonants already carry the y sound, so the vowel line is enough for them.
        val consonantLine = when {
            cons.isEmpty() -> null
            cons == "n" && vowel == 'i' -> "n softened by the i, like Bulgarian нь"
            cons == "h" && vowel == 'i' -> "a breathy h with the tongue raised, like German \"ich\""
            cons == "t" && vowel == 'i' -> "t as in \"tea\" (a loanword sound; native Japanese has chi)"
            cons == "t" && vowel == 'u' -> "t as in \"too\" (a loanword sound; native Japanese has tsu)"
            cons == "d" && vowel == 'i' -> "d as in \"deep\" (a loanword sound)"
            cons == "d" && vowel == 'u' -> "d as in \"do\" (a loanword sound)"
            else -> c.third
        }
        return Syllable(
            ipa, cyr,
            listOfNotNull(consonantLine?.let { "Consonant: $it" }, "Vowel: ${v.third}"),
        )
    }

    private fun origin(script: Script, text: String): String? {
        val table = if (script == Script.HIRAGANA) hiraganaOrigin else katakanaOrigin
        table[text]?.let { kanji ->
            return when {
                script == Script.HIRAGANA -> "Comes from a flowing, cursive way of writing the kanji $kanji."
                text in katakanaFromWholeKanji -> "Comes from the whole kanji $kanji, simplified."
                else -> "Comes from a part of the kanji $kanji."
            }
        }
        if (text == "ン") return "Its origin is uncertain; it may come from an old pronunciation mark."
        if (text == "ー") return "A plain horizontal line, used only for long vowels."
        if (text.length == 2) return "Written as ${text[0]} followed by a small ${text[1]}."
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        if (decomposed.length == 2) {
            val base = decomposed[0]
            return when (decomposed[1]) {
                '\u3099' -> if (base in "うウワヰヱヲ") {
                    "$base with the dakuten ゛ (two short strokes), which turns it into a v sound."
                } else {
                    "$base with the dakuten ゛ (two short strokes), which makes the consonant voiced: k→g, s→z, t→d, h→b."
                }
                '\u309A' -> "$base with the handakuten ゜ (a small circle), which turns h into p."
                else -> null
            }
        }
        val big = text[0] + 1
        if (text[0] in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮ") return "A smaller $big."
        if (text == "ゕ" || text == "ヵ") return "A smaller ${if (script == Script.HIRAGANA) "か" else "カ"}."
        if (text == "ゖ" || text == "ヶ") {
            return "A smaller ${if (script == Script.HIRAGANA) "け" else "ケ"}. In counters it actually comes from the kanji 箇."
        }
        return null
    }

    private fun toHiragana(text: String): String = buildString {
        for (c in text) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
    }
}

private object GreekInfo {
    private class Detail(
        val ipa: String,
        val cyrillic: String?,
        val sounds: List<String>,
        val example: Example,
        val origin: String,
        val lookAlikes: List<LookAlike> = emptyList(),
        val tips: List<String> = emptyList(),
    )

    private const val ACCENT_TIP = "With the accent mark (ά έ ή ί ό ύ ώ) the syllable is stressed. Every word with two or more syllables has one."

    /** Keyed by the small letter. */
    private val details = mapOf(
        "α" to Detail(
            "[a]", "а", listOf("a as in \"father\", short."), Example("αγάπη", "aghápi", "love"),
            "From the Phoenician letter aleph. It gave Latin A and Cyrillic А.",
            tips = listOf("αι is read e: και (ke) = and.", "αυ is read av, or af before p, t, k, f, s, th, ch: αυτό (aftó) = this.", ACCENT_TIP),
        ),
        "β" to Detail(
            "[v]", "в", listOf("v as in \"van\" (Bulgarian в), never b."), Example("βιβλίο", "vivlío", "book"),
            "From the Phoenician letter beth. It gave Latin B and both Cyrillic Б and В.",
            lookAlikes = listOf(LookAlike("B", "Looks like Latin B, but sounds v, like Cyrillic В.")),
            tips = listOf("For a b sound Greek writes μπ: μπύρα (bíra) = beer."),
        ),
        "γ" to Detail(
            "[ɣ] / [ʝ]", "г / й",
            listOf(
                "Before a, o, u and consonants: a soft g made without closing the throat, like a voiced Bulgarian х (or Ukrainian г).",
                "Before e and i sounds: y as in \"yes\" (Bulgarian й).",
            ),
            Example("γάλα", "ghála", "milk"),
            "From the Phoenician letter gimel. It gave Latin C and G, and Cyrillic Г.",
            lookAlikes = listOf(LookAlike("y", "The small γ looks a little like Latin y.")),
            tips = listOf("γγ is read ng: άγγελος (ángelos) = angel.", "γκ is read g at the start of a word: γκάζι (gázi) = gas."),
        ),
        "δ" to Detail(
            "[ð]", null, listOf("th as in \"this\": the tongue between the teeth, with voice. There is no Bulgarian equivalent; it is not д."),
            Example("δέντρο", "dhéndro", "tree"),
            "From the Phoenician letter daleth. It gave Latin D and Cyrillic Д.",
            tips = listOf("For a d sound Greek writes ντ: ντομάτα (domáta) = tomato."),
        ),
        "ε" to Detail(
            "[e]", "е", listOf("e as in \"pet\"."), Example("ελιά", "elyá", "olive"),
            "From the Phoenician letter he. It gave Latin E and Cyrillic Е.",
            tips = listOf("ει is read i: είμαι (íme) = I am.", "ευ is read ev or ef: ευχαριστώ (efharistó) = thank you.", ACCENT_TIP),
        ),
        "ζ" to Detail(
            "[z]", "з", listOf("z as in \"zoo\" (Bulgarian з)."), Example("ζάχαρη", "záhari", "sugar"),
            "From the Phoenician letter zayin. It gave Latin Z and Cyrillic З.",
        ),
        "η" to Detail(
            "[i]", "и", listOf("i as in \"machine\" (Bulgarian и). In modern Greek η, ι, υ, ει and οι all sound the same."),
            Example("ήλιος", "ílyos", "sun"),
            "From the Phoenician letter heth. It gave Latin H and Cyrillic И.",
            lookAlikes = listOf(
                LookAlike("H", "Capital Η looks like Latin H and Cyrillic Н, but sounds i."),
                LookAlike("n", "Small η looks like Latin n, but sounds i."),
            ),
            tips = listOf(ACCENT_TIP),
        ),
        "θ" to Detail(
            "[θ]", null, listOf("th as in \"thin\": the tongue between the teeth, without voice. There is no Bulgarian equivalent."),
            Example("θάλασσα", "thálasa", "sea"),
            "From the Phoenician letter teth. It gave the old Cyrillic letter Ѳ (fita).",
        ),
        "ι" to Detail(
            "[i]", "и", listOf("i as in \"machine\" (Bulgarian и)."), Example("ιστορία", "istoría", "history"),
            "From the Phoenician letter yodh. It gave Latin I and J, and Cyrillic І.",
            tips = listOf("Before a vowel it often glides into a y sound: ποιος (pyos) = who.", ACCENT_TIP),
        ),
        "κ" to Detail(
            "[k]", "к", listOf("k as in \"skip\", without a puff of air; softer (more like кь) before e and i."),
            Example("καφές", "kafés", "coffee"),
            "From the Phoenician letter kaph. It gave Latin K and Cyrillic К.",
        ),
        "λ" to Detail(
            "[l]", "л", listOf("l as in \"let\"; a clear l, lighter than Bulgarian л before a, o, u."),
            Example("λεμόνι", "lemóni", "lemon"),
            "From the Phoenician letter lamedh. It gave Latin L and Cyrillic Л.",
        ),
        "μ" to Detail(
            "[m]", "м", listOf("m as in \"me\"."), Example("μήλο", "mílo", "apple"),
            "From the Phoenician letter mem. It gave Latin M and Cyrillic М.",
            lookAlikes = listOf(LookAlike("u", "Small μ looks like Latin u with a tail, but sounds m.")),
            tips = listOf("μπ is read b at the start of a word (μπύρα, bíra = beer) and mb inside a word."),
        ),
        "ν" to Detail(
            "[n]", "н", listOf("n as in \"no\"."), Example("νερό", "neró", "water"),
            "From the Phoenician letter nun. It gave Latin N and Cyrillic Н.",
            lookAlikes = listOf(LookAlike("v", "Small ν looks like Latin v, but sounds n.")),
            tips = listOf("ντ is read d at the start of a word (ντομάτα, domáta = tomato) and nd inside a word."),
        ),
        "ξ" to Detail(
            "[ks]", "кс", listOf("ks as in \"box\"."), Example("ξύλο", "ksílo", "wood"),
            "From the Phoenician letter samekh. It gave the old Cyrillic letter Ѯ (ksi).",
        ),
        "ο" to Detail(
            "[o]", "о", listOf("o as in \"pot\", short. Sounds exactly like ω."), Example("όνομα", "ónoma", "name"),
            "From the Phoenician letter ayin. It gave Latin O and Cyrillic О.",
            tips = listOf("οι is read i: οικογένεια (ikoyénya) = family.", "ου is read u: ουρανός (uranós) = sky.", ACCENT_TIP),
        ),
        "π" to Detail(
            "[p]", "п", listOf("p as in \"spin\", without a puff of air."), Example("πόλη", "póli", "city"),
            "From the Phoenician letter pe. It gave Latin P and Cyrillic П.",
        ),
        "ρ" to Detail(
            "[r]", "р", listOf("A rolled or tapped r, like Bulgarian р."), Example("ρόδι", "ródhi", "pomegranate"),
            "From the Phoenician letter resh. It gave Latin R and Cyrillic Р.",
            lookAlikes = listOf(LookAlike("P", "Looks like Latin P/p, but sounds r, exactly like Cyrillic Р.")),
        ),
        "σ" to Detail(
            "[s]", "с", listOf("s as in \"sun\"; z before β, γ, δ, μ, ν, ρ (κόσμος, kózmos = world)."),
            Example("σπίτι", "spíti", "house"),
            "From the Phoenician letter shin. It gave Latin S and Cyrillic С (from the rounded form Ϲ).",
            tips = listOf("Inside a word it is σ; at the very end of a word it is ς: σεισμός (sizmós) = earthquake."),
        ),
        "ς" to Detail(
            "[s]", "с", listOf("s as in \"sun\"; the same sound as σ."), Example("λόγος", "lóghos", "word, reason"),
            "The end-of-word form of sigma, which grew out of the handwritten σ.",
            lookAlikes = listOf(LookAlike("σ", "σ is used everywhere else in the word; ς only as the last letter.")),
        ),
        "τ" to Detail(
            "[t]", "т", listOf("t as in \"stop\", without a puff of air."), Example("τυρί", "tirí", "cheese"),
            "From the Phoenician letter taw. It gave Latin T and Cyrillic Т.",
            tips = listOf("τσ is read ts: τσάι (tsái) = tea.", "τζ is read dz: τζάμι (dzámi) = window pane."),
        ),
        "υ" to Detail(
            "[i]", "и", listOf("i as in \"machine\" (Bulgarian и); in modern Greek it never sounds u on its own."),
            Example("ύπνος", "ípnos", "sleep"),
            "From the Phoenician letter waw. It gave Latin U, V, W and Y, and Cyrillic У (through ου) and the old Ѵ.",
            lookAlikes = listOf(
                LookAlike("u", "Small υ looks like Latin u, but sounds i."),
                LookAlike("У", "Capital Υ looks like Latin Y and Cyrillic У, but sounds i."),
            ),
            tips = listOf("ου is read u: ουρανός (uranós) = sky.", "αυ, ευ are read av/af and ev/ef.", ACCENT_TIP),
        ),
        "φ" to Detail(
            "[f]", "ф", listOf("f as in \"fish\" (Bulgarian ф)."), Example("φίλος", "fílos", "friend"),
            "A letter the Greeks added to the Phoenician alphabet. It gave Cyrillic Ф.",
        ),
        "χ" to Detail(
            "[x] / [ç]", "х",
            listOf(
                "Before a, o, u and consonants: like Bulgarian х, ch in Scottish \"loch\".",
                "Before e and i sounds: softer, like German \"ich\".",
            ),
            Example("χιόνι", "hyóni", "snow"),
            "A letter the Greeks added to the Phoenician alphabet. It gave Latin X (with a different sound) and Cyrillic Х.",
            lookAlikes = listOf(LookAlike("X", "Looks like Latin X, but sounds like Cyrillic Х.")),
        ),
        "ψ" to Detail(
            "[ps]", "пс", listOf("ps as in \"lapse\", even at the start of a word."), Example("ψωμί", "psomí", "bread"),
            "A letter the Greeks added to the Phoenician alphabet. It gave the old Cyrillic letter Ѱ (psi).",
        ),
        "ω" to Detail(
            "[o]", "о", listOf("o as in \"pot\", short. Sounds exactly like ο: the old long-o difference is gone."),
            Example("ώρα", "óra", "hour, time"),
            "A letter the Greeks added: \"o mega\", the big O. It gave the old Cyrillic letter Ѡ (omega).",
            lookAlikes = listOf(LookAlike("w", "Small ω looks like Latin w, but sounds o.")),
            tips = listOf(ACCENT_TIP),
        ),
    )

    fun of(letter: Letter): LetterInfo {
        val small = if (letter.text == "ς") "ς" else letter.text.lowercase()
        val d = details.getValue(small)
        val isCapital = letter.text != small
        val counterpart = when {
            letter.text == "ς" -> "Σ"
            isCapital -> small
            else -> letter.text.uppercase()
        }
        val lookAlikes = buildList {
            addAll(d.lookAlikes)
            if (!isCapital && small == "σ") add(LookAlike("ς", "ς is the same letter, used only at the end of a word."))
        }
        return LetterInfo(
            summary = listOfNotNull(
                d.ipa,
                d.cyrillic?.let { "Cyrillic $it" },
                d.sounds.first().substringBefore(';').substringBefore(" (").removeSuffix("."),
            ).joinToString(" · "),
            sounds = d.sounds,
            ipa = d.ipa,
            cyrillic = d.cyrillic,
            rhythm = null,
            examples = listOf(d.example),
            lookAlikes = lookAlikes,
            readingTips = d.tips,
            origin = d.origin,
            counterpart = Script.GREEK to counterpart,
            counterpartNote = if (isCapital) "Small form" else "Capital form",
        )
    }
}
