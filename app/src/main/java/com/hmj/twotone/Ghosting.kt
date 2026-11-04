package com.hmj.twotone

import androidx.compose.runtime.mutableIntStateOf

/**
 * Counts page turns and asks for a clean refresh (one black frame, one white)
 * every so often, to wipe away E-ink ghosting.
 */
object Ghosting {
    /** Page turns between clean refreshes; 0 = only after other apps (or never). */
    var everyTurns = 0
    private var turns = 0

    /** Goes up each time a clean refresh is wanted; the activity watches it. */
    val requests = mutableIntStateOf(0)

    fun pageTurned() {
        if (everyTurns <= 0) return
        turns++
        if (turns >= everyTurns) { turns = 0; requests.intValue++ }
    }

    fun now() { turns = 0; requests.intValue++ }
}
