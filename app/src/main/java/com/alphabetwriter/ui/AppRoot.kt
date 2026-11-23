package com.alphabetwriter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.alphabetwriter.Alphabets
import com.alphabetwriter.Exam
import com.alphabetwriter.Glyph
import com.alphabetwriter.GrammarPractice
import com.alphabetwriter.Language
import com.alphabetwriter.Lesson
import com.alphabetwriter.Levels
import kotlinx.coroutines.CoroutineScope
import com.alphabetwriter.Photo
import com.alphabetwriter.Progress
import com.alphabetwriter.ReviewSchedule
import com.alphabetwriter.ProgressSync
import com.alphabetwriter.Script
import com.alphabetwriter.Speaker
import com.alphabetwriter.Topic
import com.alphabetwriter.Word
import com.alphabetwriter.WordQuiz

/** Screens other than the letter/details pair, which sit on top of whichever of these is open. */
private enum class Section {
    HOME, BACKUP, EXAM_SETUP, EXAM, TOPIC, WORD, WORD_QUIZ_SETUP, WORD_QUIZ, LISTEN, REVIEW, LEARN, GRAMMAR_LESSON, GRAMMAR_PRACTICE,
}

/**
 * Words: home (topics) → topic (picture grid) → word card (→ a letter of it); topic → quiz setup → quiz → results.
 * Grammar: home (lessons of the Words language) → lesson → practice → results.
 * Letters: home (letter tables) → letter (watch + write) → details; home → exam setup → exam → results.
 * Every home → back up and restore.
 */
@Composable
fun AppRoot(
    glyphs: Map<Char, Glyph>,
    wordsOf: (Language) -> List<Word>,
    lessonsOf: (Language) -> List<Lesson>,
    grammarLanguages: List<Language>,
    photos: Map<String, Photo>,
    progress: Progress,
    sync: ProgressSync,
    speaker: Speaker,
    appScope: CoroutineScope,
) {
    var scriptIndex by rememberSaveable { mutableIntStateOf(0) }
    var openText by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf(false) }
    // Details pages reached from other details pages (counterpart, look-alike links), newest last,
    // each "scriptIndex<TAB>text", so Back retraces them.
    var detailsHistory by rememberSaveable { mutableStateOf("") }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showHint by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var homeTab by rememberSaveable { mutableStateOf(HomeTab.WORDS) }
    var examMode by rememberSaveable { mutableStateOf(ExamMode.MIXED) }
    var examCount by rememberSaveable { mutableIntStateOf(10) }
    var wordLanguage by rememberSaveable { mutableStateOf(Language.JAPANESE) }
    var level by rememberSaveable { mutableIntStateOf(0) }
    var topic by rememberSaveable { mutableStateOf(Topic.GREETINGS) }
    var wordIndex by rememberSaveable { mutableIntStateOf(0) }
    var wordFromQuiz by rememberSaveable { mutableStateOf(false) }
    // The word whose card last said it aloud, so returning from one of its letters does not repeat it.
    var spokenWord by rememberSaveable { mutableStateOf<String?>(null) }
    var wordQuizMode by rememberSaveable { mutableStateOf(WordQuizMode.MIXED) }
    var wordQuizCount by rememberSaveable { mutableIntStateOf(10) }
    // Not saved: a quiz does not survive the app being killed in the background (it reopens at its start).
    var examRun by remember { mutableStateOf<ExamRun?>(null) }
    var wordQuiz by remember { mutableStateOf<WordQuizRun?>(null) }
    var listenRun by remember { mutableStateOf<ListenRun?>(null) }
    var reviewRun by remember { mutableStateOf<ReviewRun?>(null) }
    var learnRun by remember { mutableStateOf<LearnRun?>(null) }
    var grammarRun by remember { mutableStateOf<GrammarPracticeRun?>(null) }
    var lessonIndex by rememberSaveable { mutableIntStateOf(0) }
    var showTranslations by rememberSaveable { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    val listStates = Script.entries.map { rememberLazyListState() }

    val script = Script.entries[scriptIndex]
    val letters = remember(script) { Alphabets.all(script) }
    val index = openText?.let { text -> letters.indexOfFirst { it.text == text } }?.takeIf { it >= 0 }
    val languageWords = remember(wordLanguage) { wordsOf(wordLanguage) }
    // A level the language does not have (only Essentials before its level lists are built) falls back to 0.
    if (languageWords.none { it.level == level }) level = 0
    val levelWords = remember(languageWords, level) { languageWords.filter { it.level == level } }
    val topicWords = remember(levelWords, topic) { levelWords.filter { it.topic == topic } }
    val lessons = remember(wordLanguage) { lessonsOf(wordLanguage) }
    if (section == Section.GRAMMAR_PRACTICE && grammarRun == null) section = Section.GRAMMAR_LESSON
    if (section == Section.GRAMMAR_LESSON && lessonIndex !in lessons.indices) section = Section.HOME
    if (section == Section.EXAM && examRun == null) section = Section.HOME
    if (section == Section.WORD_QUIZ && wordQuiz == null) section = Section.TOPIC
    if (section == Section.LISTEN && listenRun == null) section = Section.TOPIC
    if (section == Section.REVIEW && reviewRun == null) section = Section.HOME
    if (section == Section.LEARN && learnRun == null) section = Section.HOME
    if (section == Section.WORD && wordIndex !in topicWords.indices) section = Section.TOPIC
    if (openText != null && index == null) openText = null // a letter of another script; never leave it pending

    fun closeDetails() {
        if (detailsHistory.isEmpty()) {
            details = false
            return
        }
        val entries = detailsHistory.split('\n')
        val (previousScript, previousText) = entries.last().split('\t')
        detailsHistory = entries.dropLast(1).joinToString("\n")
        scriptIndex = previousScript.toInt()
        openText = previousText
    }

    fun openLetter(s: Script, text: String) {
        scriptIndex = s.ordinal
        openText = text
        details = false
        detailsHistory = ""
    }

    fun startExam() {
        val pool = learnedLetters(script, progress)
        if (pool.isEmpty()) return
        val questions = Exam.build(
            script, pool,
            count = if (examCount == 0) pool.size else examCount,
            types = examMode.types,
            weight = { if (progress.isHard(it)) 4 else 1 },
        )
        examRun = ExamRun(script, questions, progress)
        section = Section.EXAM
    }

    fun leaveExam() {
        examRun = null
        section = Section.HOME
    }

    fun startWordQuiz() {
        if (topicWords.isEmpty()) return
        val questions = WordQuiz.build(
            topicWords, levelWords,
            count = if (wordQuizCount == 0) topicWords.size else wordQuizCount,
            types = wordQuizMode.types,
            weight = { if (progress.isHard(it.key)) 4 else 1 },
        )
        wordQuiz = WordQuizRun(wordLanguage, topic, questions, progress)
        section = Section.WORD_QUIZ
    }

    fun leaveWordQuiz() {
        wordQuiz = null
        section = Section.TOPIC
    }

    fun startGrammarPractice() {
        val lesson = lessons.getOrNull(lessonIndex) ?: return
        if (lesson.questions.isEmpty()) return
        grammarRun = GrammarPracticeRun(wordLanguage, lesson, GrammarPractice.build(lesson), progress)
        section = Section.GRAMMAR_PRACTICE
    }

    fun leaveGrammarPractice() {
        grammarRun = null
        section = Section.GRAMMAR_LESSON
    }

    BackHandler(enabled = index != null || section != Section.HOME) {
        when {
            index != null && details -> closeDetails()
            index != null -> openText = null
            section == Section.EXAM && examRun?.finished == false -> confirmQuit = true
            section == Section.EXAM -> leaveExam()
            section == Section.WORD_QUIZ && wordQuiz?.finished == false -> confirmQuit = true
            section == Section.WORD_QUIZ -> leaveWordQuiz()
            section == Section.GRAMMAR_PRACTICE && grammarRun?.finished == false -> confirmQuit = true
            section == Section.GRAMMAR_PRACTICE -> leaveGrammarPractice()
            section == Section.WORD && wordFromQuiz && wordQuiz != null -> section = Section.WORD_QUIZ
            section == Section.LEARN -> {
                learnRun = null
                section = Section.HOME
            }
            section == Section.REVIEW -> {
                reviewRun = null
                section = Section.HOME
            }
            section == Section.LISTEN -> {
                listenRun = null
                section = Section.TOPIC
            }
            section == Section.WORD || section == Section.WORD_QUIZ_SETUP -> section = Section.TOPIC
            else -> section = Section.HOME
        }
    }

    if (confirmQuit) {
        val quitting = section
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = {
                Text(
                    when (quitting) {
                        Section.WORD_QUIZ -> "Stop the quiz?"
                        Section.GRAMMAR_PRACTICE -> "Stop the practice?"
                        else -> "Stop the exam?"
                    },
                )
            },
            text = {
                Text(
                    if (quitting == Section.GRAMMAR_PRACTICE) {
                        "The lesson is marked done only when a practice is finished with ${GrammarPractice.PASS_PERCENT}% or more right."
                    } else {
                        "Answers given so far are kept (missed ones stay marked hard)."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmQuit = false
                    when (quitting) {
                        Section.WORD_QUIZ -> leaveWordQuiz()
                        Section.GRAMMAR_PRACTICE -> leaveGrammarPractice()
                        else -> leaveExam()
                    }
                }) { Text("Stop") }
            },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep going") } },
        )
    }

    if (index == null) {
        val run = examRun
        val quiz = wordQuiz
        val navBar = @Composable { HomeNavBar(homeTab) { homeTab = it } }
        when (section) {
            Section.HOME -> if (homeTab == HomeTab.GRAMMAR) {
                GrammarHomeScreen(
                    language = wordLanguage,
                    onLanguage = { wordLanguage = it },
                    lessons = lessons,
                    languages = grammarLanguages,
                    progress = progress,
                    onLesson = {
                        lessonIndex = it
                        section = Section.GRAMMAR_LESSON
                    },
                    onBackup = { section = Section.BACKUP },
                    bottomBar = navBar,
                )
            } else if (homeTab == HomeTab.LETTERS) {
                HomeScreen(
                    script = script,
                    listState = listStates[scriptIndex],
                    progress = progress,
                    onScript = { scriptIndex = it.ordinal },
                    onOpen = { openLetter(script, it.text) },
                    onExam = { section = Section.EXAM_SETUP },
                    onBackup = { section = Section.BACKUP },
                    backupFailing = sync.failing,
                    bottomBar = navBar,
                )
            } else {
                WordsHomeScreen(
                    language = wordLanguage,
                    onLanguage = { wordLanguage = it },
                    level = level,
                    onLevel = { level = it },
                    words = languageWords,
                    photos = photos,
                    progress = progress,
                    onTopic = {
                        topic = it
                        section = Section.TOPIC
                    },
                    onLearn = {
                        val fresh = levelWords.filter { !progress.isLearned(it.key) }.take(LEARN_BATCH)
                        if (fresh.isNotEmpty()) {
                            learnRun = LearnRun(wordLanguage, Levels.title(level, wordLanguage), fresh, progress)
                            section = Section.LEARN
                        }
                    },
                    onBackup = { section = Section.BACKUP },
                    onReview = {
                        val day = today()
                        val due = ReviewSchedule.due(
                            languageWords, { progress.isLearned(it.key) }, { progress.reviewState(it.key) }, day,
                        )
                        if (due.isNotEmpty()) {
                            // At most 50 a session, most overdue first.
                            reviewRun = ReviewRun(wordLanguage, due.take(50), progress, day)
                            section = Section.REVIEW
                        }
                    },
                    bottomBar = navBar,
                )
            }
            Section.GRAMMAR_LESSON -> lessons.getOrNull(lessonIndex)?.let { lesson ->
                // Keyed so each lesson starts scrolled to the top.
                key(wordLanguage, lesson.id) {
                    LessonScreen(
                        language = wordLanguage,
                        lesson = lesson,
                        number = lessonIndex + 1,
                        count = lessons.size,
                        progress = progress,
                        speaker = speaker,
                        onPractise = ::startGrammarPractice,
                        onNext = if (lessonIndex < lessons.lastIndex) ({ lessonIndex++ }) else null,
                        onBack = { section = Section.HOME },
                    )
                }
            }
            Section.GRAMMAR_PRACTICE -> grammarRun?.let { practice ->
                if (practice.finished) {
                    GrammarResultsScreen(
                        run = practice,
                        onAgain = ::startGrammarPractice,
                        onLesson = ::leaveGrammarPractice,
                        onDone = {
                            grammarRun = null
                            section = Section.HOME
                        },
                    )
                } else {
                    GrammarPracticeScreen(practice, speaker, onQuit = { confirmQuit = true })
                }
            }
            Section.BACKUP -> BackupScreen(
                progress, sync,
                downloadablePhotos = remember(photos) { photos.values.filter { it.url != null } },
                appScope = appScope,
                onBack = { section = Section.HOME },
            )
            Section.LEARN -> learnRun?.let { learn ->
                LearnScreen(learn, photos, speaker, onBack = {
                    learnRun = null
                    section = Section.HOME
                })
            }
            Section.EXAM_SETUP -> ExamSetupScreen(
                script = script,
                progress = progress,
                mode = examMode,
                onMode = { examMode = it },
                count = examCount,
                onCount = { examCount = it },
                onStart = ::startExam,
                onBack = { section = Section.HOME },
            )
            Section.EXAM -> if (run != null) {
                if (run.finished) {
                    ExamResultsScreen(
                        run = run,
                        onPractise = { openLetter(run.script, it.text) },
                        onAgain = {
                            scriptIndex = run.script.ordinal // a missed letter may have led to another script
                            section = Section.EXAM_SETUP
                        },
                        onDone = ::leaveExam,
                    )
                } else {
                    ExamQuestionScreen(run, glyphs, speaker, onQuit = { confirmQuit = true })
                }
            }
            Section.TOPIC -> TopicScreen(
                language = wordLanguage,
                topic = topic,
                words = topicWords,
                photos = photos,
                progress = progress,
                onWord = {
                    wordIndex = it
                    wordFromQuiz = false
                    spokenWord = null
                    section = Section.WORD
                },
                onQuiz = { section = Section.WORD_QUIZ_SETUP },
                onListen = {
                    val withPictures = topicWords.filter { it.hasPicture }
                    listenRun = ListenRun(wordLanguage, topic, withPictures, levelWords.filter { it.hasPicture }, progress)
                    section = Section.LISTEN
                },
                onBack = { section = Section.HOME },
            )
            Section.REVIEW -> reviewRun?.let { review ->
                ReviewScreen(review, photos, speaker, onBack = {
                    reviewRun = null
                    section = Section.HOME
                })
            }
            Section.LISTEN -> listenRun?.let { listen ->
                ListenScreen(
                    run = listen,
                    photos = photos,
                    speaker = speaker,
                    showTranslations = showTranslations,
                    onShowTranslations = { showTranslations = it },
                    onBack = {
                        listenRun = null
                        section = Section.TOPIC
                    },
                )
            }
            Section.WORD -> WordScreen(
                words = topicWords,
                index = wordIndex,
                photos = photos,
                glyphs = glyphs,
                progress = progress,
                speaker = speaker,
                spoken = spokenWord == topicWords.getOrNull(wordIndex)?.key,
                onSpoken = { spokenWord = topicWords.getOrNull(wordIndex)?.key },
                onIndex = { wordIndex = it },
                onLetter = { s, letter -> openLetter(s, letter.text) },
                onBack = { section = if (wordFromQuiz && wordQuiz != null) Section.WORD_QUIZ else Section.TOPIC },
            )
            Section.WORD_QUIZ_SETUP -> WordQuizSetupScreen(
                language = wordLanguage,
                topic = topic,
                wordCount = topicWords.size,
                hardCount = topicWords.count { progress.isHard(it.key) },
                mode = wordQuizMode,
                onMode = { wordQuizMode = it },
                count = wordQuizCount,
                onCount = { wordQuizCount = it },
                onStart = ::startWordQuiz,
                onBack = { section = Section.TOPIC },
            )
            Section.WORD_QUIZ -> if (quiz != null) {
                if (quiz.finished) {
                    WordQuizResultsScreen(
                        run = quiz,
                        photos = photos,
                        onWord = { w ->
                            wordIndex = topicWords.indexOf(w).coerceAtLeast(0)
                            wordFromQuiz = true
                            spokenWord = null
                            section = Section.WORD
                        },
                        onAgain = { section = Section.WORD_QUIZ_SETUP },
                        onDone = ::leaveWordQuiz,
                    )
                } else {
                    WordQuizScreen(quiz, photos, speaker, onQuit = { confirmQuit = true })
                }
            }
        }
        return
    }

    val letter = letters[index]
    val strokes = remember(letter.text) { placeStrokes(letter.text.mapNotNull { glyphs[it] }) }
    // Kept here so the writing survives a visit to the details page.
    val ink = remember(script, letter.text) { mutableStateListOf<List<Offset>>() }

    if (details) {
        // Keyed so each letter's page starts scrolled to the top.
        key(script, letter.text) {
            DetailsScreen(
                script = script,
                letter = letter,
                strokes = strokes,
                speaker = speaker,
                onBack = ::closeDetails,
                onPractise = {
                    details = false
                    detailsHistory = ""
                },
                onOpen = { otherScript, text ->
                    val entry = "$scriptIndex\t${letter.text}"
                    detailsHistory = if (detailsHistory.isEmpty()) entry else detailsHistory + "\n" + entry
                    scriptIndex = otherScript.ordinal
                    openText = text
                },
            )
        }
    } else {
        LetterScreen(
            script = script,
            letters = letters,
            index = index,
            strokes = strokes,
            ink = ink,
            progress = progress,
            speaker = speaker,
            showGuide = showGuide,
            showHint = showHint,
            onShowGuide = { showGuide = it },
            onShowHint = { showHint = it },
            onOpen = { openText = letters[it].text },
            onDetails = {
                details = true
                detailsHistory = ""
            },
            onBack = { openText = null },
        )
    }
}
