package com.alphabetwriter

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Holds the objects that must exist once per process, not once per activity (each owns state or a thread). */
class AlphabetWriterApp : Application() {
    val progress by lazy { Progress(this) }
    val sync by lazy { ProgressSync(this, progress) }
    /** Work that outlives a screen, such as downloading all pictures. */
    val scope = CoroutineScope(SupervisorJob())
}
