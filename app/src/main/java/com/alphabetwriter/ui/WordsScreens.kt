package com.alphabetwriter.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.alphabetwriter.Animations
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.InputStream
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphabetwriter.FormSection
import com.alphabetwriter.FormsStore
import com.alphabetwriter.Glyph
import com.alphabetwriter.Language
import com.alphabetwriter.Levels
import com.alphabetwriter.PictureStore
import com.alphabetwriter.Letter
import com.alphabetwriter.Photo
import com.alphabetwriter.Progress
import com.alphabetwriter.ReviewSchedule
import com.alphabetwriter.Script
import com.alphabetwriter.Speaker
import com.alphabetwriter.Topic
import com.alphabetwriter.Word
import com.alphabetwriter.WordGlyphs
import com.alphabetwriter.WordLetters
import com.alphabetwriter.WordQuestion
import com.alphabetwriter.WordQuestionType
import com.alphabetwriter.WordQuiz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class HomeTab { WORDS, GRAMMAR, LETTERS }

/** Opens a web page (a photo's Commons page) in the browser, if the phone has one. */
private fun openPage(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser: nothing to open.
    }
}

/** Bottom navigation shared by the three home screens. */
@Composable
fun HomeNavBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == HomeTab.WORDS,
            onClick = { onSelect(HomeTab.WORDS) },
            icon = { Text("🍎", fontSize = 20.sp) },
            label = { Text("Words") },
        )
        NavigationBarItem(
            selected = selected == HomeTab.GRAMMAR,
            onClick = { onSelect(HomeTab.GRAMMAR) },
            icon = { Text("📖", fontSize = 20.sp) },
            label = { Text("Grammar") },
        )
        NavigationBarItem(
            selected = selected == HomeTab.LETTERS,
            onClick = { onSelect(HomeTab.LETTERS) },
            icon = { Text("あ", fontSize = 20.sp) },
            label = { Text("Letters") },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pictures

/** Decoded photos, shared by all screens; ~24 MB covers a topic's grid and a few full-size cards. */
private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** Decodes an image at the smallest power-of-two scale that still covers [maxPx]. */
private fun decodeSampled(maxPx: Int, open: () -> InputStream): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
    open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
} catch (_: Exception) {
    null
}

/**
 * A photo's bitmap: bundled photos come from the app's assets; the others are downloaded on first use and read
 * from the phone afterwards. Null while loading, or when a download is not possible (offline).
 */
@Composable
private fun rememberPhotoBitmap(photo: Photo, maxPx: Int): Pair<ImageBitmap?, Boolean> {
    val context = LocalContext.current
    val key = "${photo.cacheName}@$maxPx"
    // Keyed by the photo: when a slot (a quiz choice, the next word's card) switches to another photo, it must
    // start empty rather than keep showing the previous photo while the new one loads.
    val state = remember(key) { mutableStateOf(bitmapCache.get(key)) }
    // True when the photo could not be loaded (offline, or gone from Wikimedia); tried again next time it is shown.
    val failed = remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (state.value != null) return@LaunchedEffect
        val bitmap = if (photo.file != null) {
            withContext(Dispatchers.IO) { decodeSampled(maxPx) { context.assets.open("pictures/${photo.file}") } }
        } else {
            PictureStore.fetch(context, photo)?.let { file -> withContext(Dispatchers.IO) { decodeSampled(maxPx) { file.inputStream() } } }
        }
        if (bitmap != null) {
            bitmapCache.put(key, bitmap)
            state.value = bitmap
        } else {
            failed.value = true
        }
    }
    val bitmap = state.value
    return remember(bitmap) { bitmap?.asImageBitmap() } to failed.value
}

/** Colour words: the picture is a swatch of the colour. */
private val colourSwatches = mapOf(
    "🔴" to Color(0xFFD32F2F), "🔵" to Color(0xFF1E63D6), "🟢" to Color(0xFF2E9E44), "🟡" to Color(0xFFFFD52E),
    "⚫" to Color(0xFF141414), "⚪" to Color(0xFFFAFAFA), "🟣" to Color(0xFF8E44C9), "🟠" to Color(0xFFFF8A1F),
    "🟤" to Color(0xFF7B4A2A), "🩷" to Color(0xFFFF9EC7), "🩶" to Color(0xFF9E9E9E), "🩵" to Color(0xFF8FD3FF),
)

/**
 * The word's picture in a rounded box: its photo, a colour swatch, its emoji, or digits for numbers.
 * A word without any picture shows its meaning instead.
 */
@Composable
fun WordPicture(word: Word, photos: Map<String, Photo>, modifier: Modifier = Modifier, emojiSize: Int = 64, animate: Boolean = false) {
    val context = LocalContext.current
    // On a word's own card (animate), an animated emoji (a plane flying, a fish swimming) wins over a photo:
    // movement is easy to remember. Grids, covers and quiz choices stay still, so they are quick to scan.
    val animation = remember(word.image, animate) { if (animate) Animations.assetFor(context, word.image) else null }
    if (animation != null && word.image !in colourSwatches) {
        val cs = MaterialTheme.colorScheme
        Box(modifier.clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            val composition by rememberLottieComposition(LottieCompositionSpec.Asset(animation))
            // Noto's animations draw clouds, splashes and trails past their square canvas: let them, and clip to
            // the rounded picture box instead, so nothing stops at a hard square edge.
            LottieAnimation(
                composition, iterations = LottieConstants.IterateForever, clipToCompositionBounds = false,
                modifier = Modifier.fillMaxSize(0.8f),
            )
        }
        return
    }
    val photo = photos[word.image]
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val px = with(LocalDensity.current) { maxWidth.roundToPx() }
        val (bitmap, failed) = photo?.let { rememberPhotoBitmap(it, px.coerceAtLeast(64)) } ?: (null to false)
        val swatch = colourSwatches[word.image]
        when {
            // Colours are shown as the colour itself filling the picture, not as an icon.
            swatch != null -> Box(Modifier.fillMaxSize().background(swatch))
            bitmap != null -> Image(
                bitmap, contentDescription = word.meaning, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            photo != null && !failed -> Unit // still loading: leave the box empty
            // No picture, or a downloadable photo that could not be loaded: show what the word means.
            !word.hasPicture || word.image.startsWith("p:") -> Text(
                word.meaning, style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
            word.image.all { it.isDigit() } -> Text(
                word.image, fontSize = (emojiSize * if (word.image.length > 3) 0.55f else 0.8f).sp,
                fontWeight = FontWeight.Bold, color = cs.primary,
            )
            else -> Text(word.image, fontSize = emojiSize.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Words home and topic grid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordsHomeScreen(
    language: Language,
    onLanguage: (Language) -> Unit,
    level: Int,
    onLevel: (Int) -> Unit,
    words: List<Word>,
    photos: Map<String, Photo>,
    progress: Progress,
    onTopic: (Topic) -> Unit,
    onReview: () -> Unit,
    onLearn: () -> Unit,
    onBackup: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val levels = remember(words) { Levels.all.filter { l -> words.any { it.level == l } } }
    val inLevel = remember(words, level) { words.filter { it.level == level } }
    // A language has up to 10 000 words: count only when the words or the progress change, not on every redraw.
    val learnedKeys = progress.learned
    val hardKeys = progress.hard
    val reviews = progress.reviews
    val day = today()
    val learned = remember(words, learnedKeys) { words.count { it.key in learnedKeys } }
    val due = remember(words, learnedKeys, reviews, day) {
        ReviewSchedule.due(words, { it.key in learnedKeys }, { reviews[it.key] }, day).size
    }
    val fresh = remember(inLevel, learnedKeys) { inLevel.count { it.key !in learnedKeys } }
    val topicRows = remember(inLevel, learnedKeys, hardKeys) {
        inLevel.groupBy { it.topic }.toSortedMap().map { (topic, inTopic) ->
            TopicRow(
                topic, inTopic.size, inTopic.count { it.key in learnedKeys }, inTopic.count { it.key in hardKeys },
                inTopic.firstOrNull { it.hasPicture },
            )
        }
    }
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Words") },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Backup, pictures and moving phones") },
                                onClick = {
                                    menu = false
                                    onBackup()
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LanguagePicker(language, onLanguage)
                    if (levels.size > 1) LevelPicker(language, levels, level, onLevel)
                }
            }
            item { ReviewCard(learned, due, onReview) }
            item { LearnCard(fresh, Levels.title(level, language), onLearn) }
            item {
                Text(
                    "${Levels.title(level, language)}: ${Levels.subtitle(level)}. Pick a topic to browse its words, hear them and see how they are written.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(topicRows, key = { it.topic.id }) { row ->
                val (topic, size, known, hard, cover) = row
                Surface(
                    onClick = { onTopic(topic) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (cover != null) {
                            WordPicture(cover, photos, Modifier.size(56.dp), emojiSize = 32)
                        } else {
                            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Text(topic.emoji, fontSize = 32.sp) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(topic.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$size words · $known known" + if (hard > 0) " · $hard hard" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (hard > 0) HardDot()
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

/** One topic on the Words home: how many of its words there are, are known and are hard, and its cover word. */
private data class TopicRow(val topic: Topic, val size: Int, val known: Int, val hard: Int, val cover: Word?)

/** One button showing the current level; tapping it lists the levels this language has. */
@Composable
private fun LevelPicker(language: Language, levels: List<Int>, level: Int, onLevel: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(Levels.title(level, language))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose level")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            levels.forEach { l ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(Levels.title(l, language))
                            Text(Levels.subtitle(l), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        open = false
                        onLevel(l)
                    },
                    trailingIcon = if (l == level) {
                        { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** One button showing the current language; tapping it opens the list of all languages. */
@Composable
internal fun LanguagePicker(language: Language, onLanguage: (Language) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text("${language.flag}  ${language.nativeTitle} · ${language.title}")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose language")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Language.entries.forEach { l ->
                DropdownMenuItem(
                    text = { Text("${l.flag}  ${l.nativeTitle} · ${l.title}") },
                    onClick = {
                        open = false
                        onLanguage(l)
                    },
                    trailingIcon = if (l == language) {
                        { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    language: Language,
    topic: Topic,
    words: List<Word>,
    photos: Map<String, Photo>,
    progress: Progress,
    onWord: (Int) -> Unit,
    onQuiz: () -> Unit,
    onListen: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text("${topic.title} · ${language.title}") },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Listening is "pick the picture", so it needs at least four words with pictures.
                if (words.count { it.hasPicture } >= 4) {
                    ExtendedFloatingActionButton(
                        onClick = onListen,
                        icon = { Icon(AppIcons.VolumeUp, contentDescription = null) },
                        text = { Text("Listen") },
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = onQuiz,
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = { Text("Quiz") },
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 168.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(words, key = { _, w -> w.key }) { i, word ->
                WordTile(word, photos, progress.isLearned(word.key), progress.isHard(word.key)) { onWord(i) }
            }
        }
    }
}

@Composable
private fun WordTile(word: Word, photos: Map<String, Photo>, known: Boolean, hard: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (known) cs.primaryContainer else cs.surfaceContainer,
    ) {
        Box {
            Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WordPicture(word, photos, Modifier.fillMaxWidth().aspectRatio(1f), emojiSize = 48)
                Text(
                    word.text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    word.reading, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (known) {
                Icon(
                    Icons.Filled.Check, contentDescription = "Known", tint = cs.onPrimary,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(18.dp)
                        .background(cs.primary, CircleShape).padding(2.dp),
                )
            }
            if (hard) HardDot(Modifier.align(Alignment.TopEnd).padding(10.dp).semantics { contentDescription = "Hard" }, size = 12.dp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Word card

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordScreen(
    words: List<Word>,
    index: Int,
    photos: Map<String, Photo>,
    glyphs: Map<Char, Glyph>,
    progress: Progress,
    speaker: Speaker,
    spoken: Boolean,
    onSpoken: () -> Unit,
    onIndex: (Int) -> Unit,
    onLetter: (Script, Letter) -> Unit,
    onBack: () -> Unit,
) {
    val word = words[index]
    val context = LocalContext.current
    val language = word.language
    val canSpeak = speaker.canSpeak(language.languageTag)
    val wordGlyphs = remember(word.key) { WordGlyphs.of(word.text, glyphs) }
    val strokes = remember(word.key) { placeStrokes(wordGlyphs) }
    val demo = remember(word.key) { DemoState(strokes.size) }
    val letters = remember(word.key) { WordLetters.of(word) }
    val animated = remember(word.image) { Animations.assetFor(context, word.image) != null }
    val photo = photos[word.image].takeUnless { animated }
    // Stays open or closed while moving from word to word.
    var showForms by remember { mutableStateOf(false) }

    // Say the word when its card is opened, but not again when coming back to it from one of its letters.
    LaunchedEffect(word.key) {
        if (!spoken && canSpeak) speaker.speak(language.languageTag, word.say)
        onSpoken()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text(word.topic.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconToggleButton(
                        checked = progress.isHard(word.key),
                        onCheckedChange = { progress.toggleHard(word.key) },
                        modifier = Modifier.semantics {
                            contentDescription = if (progress.isHard(word.key)) "Hard word: tap to unmark" else "Mark as hard"
                        },
                    ) {
                        if (progress.isHard(word.key)) {
                            HardDot(size = 18.dp)
                        } else {
                            Box(Modifier.size(18.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                        }
                    }
                    FilledIconToggleButton(checked = progress.isLearned(word.key), onCheckedChange = { progress.toggle(word.key) }) {
                        Icon(Icons.Filled.Check, contentDescription = "Mark as known")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onIndex(index - 1) }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous word")
                        Text(words.getOrNull(index - 1)?.text.orEmpty(), maxLines = 1)
                    }
                    Text(
                        "${index + 1} / ${words.size}", style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onIndex(index + 1) }, enabled = index < words.lastIndex) {
                        Text(words.getOrNull(index + 1)?.text.orEmpty(), maxLines = 1)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next word")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 4:3, but at most 320 dp and 45% of the screen height, so in landscape the word stays in view.
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            if (word.hasPicture) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val height = minOf(maxWidth * 0.75f, 320.dp, screenHeight * 0.45f)
                    WordPicture(word, photos, Modifier.fillMaxWidth().height(height), emojiSize = if (height < 200.dp) 72 else 120, animate = true)
                }
            }
            if (animated) {
                Text(
                    "${Animations.CREDIT} ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { openPage(context, Animations.SOURCE) }.padding(vertical = 2.dp),
                )
            }
            photo?.let {
                Text(
                    "Photo: ${it.license} · ${it.artist.ifBlank { "unknown author" }} · Wikimedia Commons, resized ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { openPage(context, it.source) }.padding(vertical = 2.dp),
                )
            }
            Text(word.text, fontSize = 40.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                word.reading, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                word.meaning, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(),
            )
            if (canSpeak) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    FilledTonalButton(onClick = { speaker.speak(language.languageTag, word.say) }) {
                        Icon(AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Listen")
                    }
                    OutlinedButton(onClick = { speaker.speak(language.languageTag, word.say, slow = true) }) { Text("Slowly") }
                }
            } else {
                Text(
                    "No ${language.title} voice on this phone: install one in the text-to-speech settings to hear words.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { Speaker.openVoiceSettings(context) }) { Text("Open voice settings") }
            }
            word.kanji?.let {
                Text("Kanji: $it", style = MaterialTheme.typography.bodyMedium)
            }
            word.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            // Keyed state, so the next word never shows the previous word's forms while its own load.
            var forms by remember(language, word.text) {
                mutableStateOf(FormsStore.loaded(language)?.get(word.text).orEmpty())
            }
            LaunchedEffect(language, word.text) {
                forms = withContext(Dispatchers.IO) { FormsStore.forms(context, language)[word.text].orEmpty() }
            }
            if (forms.isNotEmpty()) {
                WordForms(forms, showForms, { showForms = it }, canSpeak) { speaker.speak(language.languageTag, it) }
            }

            if (strokes.isNotEmpty()) {
                Text("How to write it", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cells = wordGlyphs.size
                    val cell = (maxWidth / cells).coerceIn(56.dp, 104.dp)
                    val cellPx = with(LocalDensity.current) { cell.toPx() }
                    val scroll = remember(word.key) { ScrollState(0) }
                    // Long words overflow the screen: keep the square being written in view.
                    val writing = (strokes.getOrNull(demo.done) ?: strokes.last()).cell
                    LaunchedEffect(scroll, writing) { scroll.animateScrollTo(((writing - 1) * cellPx).toInt().coerceAtLeast(0)) }
                    Row(Modifier.horizontalScroll(scroll)) {
                        StrokeAnimation(strokes, cells, language.grid, demo, Modifier.height(cell))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = demo::replay) { Icon(AppIcons.Replay, contentDescription = "Replay") }
                    IconButton(onClick = demo::togglePlay) {
                        if (demo.playing) Icon(AppIcons.Pause, contentDescription = "Pause") else Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                    TextButton(onClick = demo::cycleSpeed) {
                        Text(when (demo.speed) { 0.5f -> "Slow"; 2f -> "Fast"; else -> "Normal" })
                    }
                }
            }
            if (letters.isNotEmpty()) {
                Text("Practise its letters", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    letters.forEach { (script, letter) ->
                        AssistChip(
                            onClick = { onLetter(script, letter) },
                            label = { Text("${letter.text}  ${letter.label}") },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** A word's conjugation or declension, folded away until opened; tapping a row says that form. */
@Composable
private fun WordForms(sections: List<FormSection>, open: Boolean, onOpen: (Boolean) -> Unit, canSpeak: Boolean, onSpeak: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(!open) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Forms", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            sections.joinToString(" · ") { it.title },
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = if (open) "Hide forms" else "Show forms",
            modifier = Modifier.rotate(if (open) 180f else 0f),
        )
    }
    if (!open) return
    sections.forEach { section ->
        Text(section.title, style = MaterialTheme.typography.labelLarge)
        Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                section.rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = canSpeak) { onSpeak(row.form) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                        Column(Modifier.weight(0.6f)) {
                            Text(row.form, style = MaterialTheme.typography.bodyLarge)
                            if (row.reading.isNotBlank()) {
                                Text(row.reading, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    Text(
        (if (canSpeak) "Tap a form to hear it. " else "") + "Forms: English Wiktionary (kaikki.org), CC BY-SA.",
        style = MaterialTheme.typography.labelSmall,
        color = cs.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------------------------
// Picture quiz

enum class WordQuizMode(val label: String, val types: Set<WordQuestionType>) {
    MIXED("Mixed", setOf(WordQuestionType.PICK_PICTURE, WordQuestionType.PICK_WORD)),
    PICTURE("Pick the picture", setOf(WordQuestionType.PICK_PICTURE)),
    WORD("Pick the word", setOf(WordQuestionType.PICK_WORD)),
    MEANING("Pick the meaning", setOf(WordQuestionType.PICK_MEANING)),
}

/** One word quiz in progress; one result per question, recorded in [progress] (wrong → hard). */
@Stable
class WordQuizRun(val language: Language, val topic: Topic, val questions: List<WordQuestion>, private val progress: Progress) {
    var index by mutableIntStateOf(0)
        private set
    var chosen by mutableStateOf<Word?>(null)
        private set
    private val results = mutableStateListOf<Boolean>()

    val finished: Boolean get() = index >= questions.size
    val current: WordQuestion get() = questions[index]
    val revealed: Boolean get() = chosen != null
    val score: Int get() = results.count { it }
    val missed: List<Word> get() = questions.filterIndexed { i, _ -> results.getOrNull(i) == false }.map { it.word }

    /** [question] is the index the tapped choice belonged to; a late tap on a replaced choice is ignored. */
    fun pick(word: Word, question: Int) {
        if (finished || revealed || question != index) return
        chosen = word
        val correct = word == current.word
        results += correct
        progress.recordAnswer(current.word.key, correct)
    }

    fun next() {
        if (finished || !revealed) return
        index++
        chosen = null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordQuizSetupScreen(
    language: Language,
    topic: Topic,
    wordCount: Int,
    hardCount: Int,
    mode: WordQuizMode,
    onMode: (WordQuizMode) -> Unit,
    count: Int,
    onCount: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text("Quiz · ${topic.title}") },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val asked = if (count == 0 || count >= wordCount) wordCount else count
            Text(
                (if (asked == wordCount) "Asks about all $wordCount" else "Asks about $asked of the $wordCount") +
                    " ${language.title} words of this topic" +
                    (if (hardCount > 0) ", with the $hardCount marked hard coming up more often." else "."),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Questions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WordQuizMode.entries.forEach { m -> FilterChip(selected = m == mode, onClick = { onMode(m) }, label = { Text(m.label) }) }
            }
            Text(
                when (mode) {
                    WordQuizMode.PICTURE -> "You see and hear a word and pick its picture out of four."
                    WordQuizMode.WORD -> "You see a picture and pick the written word out of four."
                    WordQuizMode.MEANING -> "You see and hear a word and pick what it means out of four."
                    WordQuizMode.MIXED -> "A mix of picture and word questions. Words without a picture are asked by meaning."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("How many", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = count == 10, onClick = { onCount(10) }, label = { Text("10") })
                FilterChip(selected = count == 0, onClick = { onCount(0) }, label = { Text("All ($wordCount)") })
            }
            Text(
                "A wrong answer marks the word hard (a red dot on its picture); a right answer clears it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start quiz") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordQuizScreen(run: WordQuizRun, photos: Map<String, Photo>, speaker: Speaker, onQuit: () -> Unit) {
    val question = run.current
    val word = question.word
    val tag = run.language.languageTag
    val canSpeak = speaker.canSpeak(tag)
    val cs = MaterialTheme.colorScheme
    val good = if (cs.surface.luminance() < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)

    // Picture questions start by saying the word; word questions say it once answered (it would give it away).
    LaunchedEffect(run.index) {
        if (canSpeak && question.type != WordQuestionType.PICK_WORD) speaker.speak(tag, word.say)
    }
    LaunchedEffect(run.index, run.revealed) {
        if (canSpeak && run.revealed && question.type == WordQuestionType.PICK_WORD) speaker.speak(tag, word.say)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onQuit) { Icon(Icons.Filled.Close, contentDescription = "Stop the quiz") } },
                title = { Text("Question ${run.index + 1} of ${run.questions.size}") },
                actions = { Text("${run.score} right", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()) {
            LinearProgressIndicator(progress = { run.index.toFloat() / run.questions.size }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            if (question.type != WordQuestionType.PICK_WORD) {
                Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (question.type == WordQuestionType.PICK_PICTURE) "Which picture is" else "What does this mean?",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                            )
                            Text(word.text, fontSize = 32.sp)
                            if (run.revealed) Text("${word.reading} · ${word.meaning}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (canSpeak) {
                            IconButton(onClick = { speaker.speak(tag, word.say) }) { Icon(AppIcons.VolumeUp, contentDescription = "Hear it") }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WordPicture(word, photos, Modifier.size(140.dp), emojiSize = 80)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Which word is this?", style = MaterialTheme.typography.titleMedium)
                        if (run.revealed) Text("${word.text} · ${word.reading} · ${word.meaning}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Two by two in portrait; one row of four on a wide (landscape) screen, where rows are too short.
            val configuration = LocalConfiguration.current
            val perRow = if (configuration.screenWidthDp > configuration.screenHeightDp) 4 else 2
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.choices.chunked(perRow).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { choice -> key(run.index, choice.key) {
                            val border = when {
                                !run.revealed -> null
                                choice == word -> BorderStroke(4.dp, good)
                                choice == run.chosen -> BorderStroke(4.dp, cs.error)
                                else -> null
                            }
                            val asked = run.index
                            Surface(
                                onClick = { run.pick(choice, asked) },
                                enabled = !run.revealed,
                                shape = RoundedCornerShape(16.dp),
                                color = cs.surfaceContainerHigh,
                                border = border,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    if (question.type == WordQuestionType.PICK_PICTURE) {
                                        WordPicture(choice, photos, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true), emojiSize = 56)
                                        if (run.revealed) {
                                            Text(choice.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(choice.meaning, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    } else if (question.type == WordQuestionType.PICK_MEANING) {
                                        Text(choice.meaning, fontSize = 20.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        if (run.revealed) {
                                            Text(choice.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    } else {
                                        Text(choice.text, fontSize = 26.sp, textAlign = TextAlign.Center, maxLines = 2)
                                        if (run.revealed) {
                                            Text(choice.reading, style = MaterialTheme.typography.bodyMedium)
                                            Text(choice.meaning, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        } }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (run.revealed) {
                    val right = run.chosen == word
                    Text(
                        if (right) "Right!" else "Not quite. Marked as hard.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (right) good else cs.error,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = run::next) { Text(if (run.index == run.questions.lastIndex) "See results" else "Next") }
                } else {
                    Text(
                        when (question.type) {
                            WordQuestionType.PICK_PICTURE -> "Tap the matching picture."
                            WordQuestionType.PICK_MEANING -> "Tap the matching meaning."
                            WordQuestionType.PICK_WORD -> "Tap the matching word."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordQuizResultsScreen(
    run: WordQuizRun,
    photos: Map<String, Photo>,
    onWord: (Word) -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val total = run.questions.size
    val percent = if (total == 0) 0 else run.score * 100 / total
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Done") } },
                title = { Text("Quiz results · ${run.topic.title}") },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${run.score} / $total", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium)
            Text(
                when {
                    percent == 100 -> "Perfect: every word right."
                    percent >= 80 -> "$percent% — well done."
                    percent >= 50 -> "$percent% — getting there."
                    else -> "$percent% — keep practising."
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (run.missed.isNotEmpty()) {
                Text(
                    "Marked as hard. Tap one to look at it again:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                run.missed.forEach { w ->
                    Surface(
                        onClick = { onWord(w) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            WordPicture(w, photos, Modifier.size(56.dp), emojiSize = 32)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(w.text, fontSize = 22.sp)
                                Text("${w.reading} · ${w.meaning}", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
                Button(onClick = onAgain, modifier = Modifier.weight(1f)) { Text("New quiz") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Listening practice

/**
 * Endless listening rounds over a topic: four pictures, one word said aloud, pick its picture.
 * Wrong answers mark the word hard (and hard words come up more often), like the quiz.
 */
@Stable
class ListenRun(
    val language: Language,
    val topic: Topic,
    private val words: List<Word>,
    private val allWords: List<Word>,
    private val progress: Progress,
) {
    var round by mutableIntStateOf(0)
        private set
    var target by mutableStateOf(pickTarget(null))
        private set
    var choices by mutableStateOf(WordQuiz.choices(target, words, allWords))
        private set
    var chosen by mutableStateOf<Word?>(null)
        private set
    var right by mutableIntStateOf(0)
        private set
    var answered by mutableIntStateOf(0)
        private set

    val revealed: Boolean get() = chosen != null

    private fun pickTarget(previous: Word?) =
        WordQuiz.nextTarget(words, previous, weight = { if (progress.isHard(it.key)) 4 else 1 })

    /** [forRound] is the round the tapped picture belonged to; a late tap on a replaced picture is ignored. */
    fun pick(word: Word, forRound: Int) {
        if (revealed || forRound != round) return
        chosen = word
        answered++
        val correct = word == target
        if (correct) right++
        progress.recordAnswer(target.key, correct)
    }

    fun next() {
        if (!revealed) return
        val t = pickTarget(target)
        target = t
        choices = WordQuiz.choices(t, words, allWords)
        chosen = null
        round++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    run: ListenRun,
    photos: Map<String, Photo>,
    speaker: Speaker,
    showTranslations: Boolean,
    onShowTranslations: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val target = run.target
    val tag = run.language.languageTag
    val canSpeak = speaker.canSpeak(tag)
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val good = if (cs.surface.luminance() < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)

    // Every new round starts by saying the word.
    LaunchedEffect(run.round) { if (canSpeak) speaker.speak(tag, target.say) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text("Listen · ${run.topic.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (run.answered > 0) {
                        Text("${run.right} / ${run.answered}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Translations", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Show translations under the pictures" },
                )
                Switch(checked = showTranslations, onCheckedChange = onShowTranslations)
            }
            if (!canSpeak) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "No ${run.language.title} voice on this phone, so the words can't be said aloud.",
                        style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { Speaker.openVoiceSettings(context) }) { Text("Voice settings") }
                }
            }
            Spacer(Modifier.height(8.dp))
            // The word to find: tap it to hear it again.
            Surface(
                onClick = { if (canSpeak) speaker.speak(tag, target.say) },
                shape = RoundedCornerShape(16.dp),
                color = cs.secondaryContainer,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Say the word again" },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(40.dp), tint = cs.onSecondaryContainer)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(target.text, fontSize = 32.sp, color = cs.onSecondaryContainer)
                        Text(
                            if (run.revealed) "${target.reading} · ${target.meaning}" else "Tap to hear it again",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSecondaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val configuration = LocalConfiguration.current
            val perRow = if (configuration.screenWidthDp > configuration.screenHeightDp) 4 else 2
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                run.choices.chunked(perRow).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { choice -> key(run.round, choice.key) {
                            val forRound = run.round
                            val border = when {
                                !run.revealed -> null
                                choice == target -> BorderStroke(4.dp, good)
                                choice == run.chosen -> BorderStroke(4.dp, cs.error)
                                else -> null
                            }
                            Surface(
                                onClick = { run.pick(choice, forRound) },
                                enabled = !run.revealed,
                                shape = RoundedCornerShape(16.dp),
                                color = cs.surfaceContainerHigh,
                                border = border,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    WordPicture(
                                        choice, photos,
                                        Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true),
                                        emojiSize = 56,
                                    )
                                    if (run.revealed) {
                                        Text(choice.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (showTranslations || run.revealed) {
                                        Text(
                                            choice.meaning, style = MaterialTheme.typography.labelMedium,
                                            color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        } }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (run.revealed) {
                    val correct = run.chosen == target
                    Text(
                        if (correct) "Right!" else "Not quite. Marked as hard.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (correct) good else cs.error,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = run::next) { Text("Next") }
                } else {
                    Text("Tap the picture of the word you hear.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
