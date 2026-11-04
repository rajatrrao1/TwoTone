package com.hmj.twotone

import kotlinx.coroutines.flow.MutableSharedFlow

/** Volume-key presses turned into page turns (+1 next page, -1 previous page). */
object PageKeys {
    val turns = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** How many paged lists are on screen; keys are only taken over when one is. */
    var listening = 0
}
