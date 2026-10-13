/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.overlay

/**
 * When to ask, once, whether the floating button should stay up over the Dictate keyboard too.
 *
 * By default it steps aside for the keyboard, which has a mic of its own. Somebody who has learned to
 * dictate with the floating button misses it there, though, and the setting that keeps it ("Also with the
 * Dictate keyboard") is three screens deep. So the keyboard asks — but only someone whose habit is plainly
 * the floating button: a handful of dictations through it, and at least twice as many as through the
 * keyboard's own mic.
 */
object KeepOverKeyboardTip {
    /** Dictations through the floating button before the question is worth asking. */
    const val MIN_BUTTON_DICTATIONS = 5

    fun shouldOffer(buttonDictations: Int, keyboardDictations: Int): Boolean =
        buttonDictations >= MIN_BUTTON_DICTATIONS && buttonDictations >= 2 * keyboardDictations
}
