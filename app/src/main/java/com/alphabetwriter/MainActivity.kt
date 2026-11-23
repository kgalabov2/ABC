package com.alphabetwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alphabetwriter.ui.AlphabetWriterTheme
import com.alphabetwriter.ui.AppRoot

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val glyphs = GlyphStore.get(this)
        val photos = WordStore.photos(this)
        val app = application as AlphabetWriterApp
        val progress = app.progress
        val sync = app.sync
        speaker = Speaker(this)
        setContent {
            AlphabetWriterTheme {
                AppRoot(
                    glyphs, { WordStore.words(this, it) }, { GrammarStore.lessons(this, it) }, GrammarStore.languages(this),
                    photos, progress, sync, speaker, app.scope,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        speaker.refresh() // a voice may have been installed while the app was in the background
    }

    override fun onStop() {
        (application as AlphabetWriterApp).sync.flush() // the process may be killed in the background
        super.onStop()
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
