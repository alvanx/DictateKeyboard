/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

/**
 * How far the floating dictation button steps back while it is not being used.
 *
 * This used to be an on/off switch whose "on" shrank the button to half its size at under half opacity after
 * 3.5 seconds — few enough that it was usually a dot by the time the user looked for it in the field they had
 * just selected. [GENTLE] is the new default; [STRONG] keeps that old look for those who want the button out
 * of the way, though with the same wake-up rules as [GENTLE].
 */
enum class DictateFloatingButtonFade(
    /** How long the button stays full size after the last thing that woke it, in ms. */
    val delayMs: Long,
    /** The faded button's size, as a share of its full size. */
    val scale: Float,
    /** The faded button's opacity. */
    val alpha: Float,
) {
    /** Always full size and fully opaque. */
    NEVER(0L, 1f, 1f),

    /** Steps back a little, and only after a while — still easy to spot. */
    GENTLE(15_000L, 0.85f, 0.8f),

    /** The old behaviour: a small, see-through dot after a few seconds. */
    STRONG(4_000L, 0.55f, 0.5f);

    /** Whether the button fades at all. */
    val enabled: Boolean
        get() = this != NEVER

    companion object {
        /**
         * The value the old `dictate__floating_button_auto_dim` switch stood for, from its raw stored form.
         * Off was "never"; on, or anything unreadable, the new default — the old look was the complaint.
         */
        fun fromAutoDim(rawValue: String): DictateFloatingButtonFade =
            if (rawValue == "false") NEVER else GENTLE
    }
}
