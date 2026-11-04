package com.hmj.twotone

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the launcher is on screen. Every clock, progress bar and status check
 * stops while it isn't (screen off, or another app in front) and starts again
 * when it is, so nothing wakes the phone for nothing.
 */
object Visible {
    var on by mutableStateOf(true)
}
