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
 * How the rewording prompt chips (including the live-prompt chip) are surfaced on the keyboard
 * (user choice in the Dictate rewording settings):
 *
 *  - [WHEN_USEFUL]: in the Smartbar itself, in place of the suggestions, only at the moments they are
 *    useful — right after a dictation landed, and while text is selected — with "Ask by voice" last.
 *    The rest of the time the suggestions own the bar. The default.
 *  - [PANEL]: a dedicated full-width panel (own [dev.patrickgold.florisboard.ime.ImeUiMode.DICTATE]),
 *    opened via the magic-wand Smartbar action. Chips flow left-to-right, top-to-bottom. A selection
 *    still brings the saved prompts into the bar.
 *  - [ROW]: an always-on extra row pinned above the Smartbar, so the prompts are permanently visible
 *    without opening a panel. The live-prompt chip is the last entry.
 */
enum class DictatePromptsLayout {
    PANEL,
    ROW,
    WHEN_USEFUL;

    /**
     * Whether the prompt chips take the Smartbar's middle, in place of the suggestions, right now. Only
     * asked while no dictation is running; while one is, the bar shows the dictation.
     *
     * @param hasSelection text is selected in the field.
     * @param afterDictation a dictation just landed in the field and nothing has been typed since.
     * @param hasPrompts the user has saved prompts. [WHEN_USEFUL] shows its chips without any, since
     *  "Ask by voice" is always there; the PANEL strip has only the saved ones.
     */
    fun chipsInBar(hasSelection: Boolean, afterDictation: Boolean, hasPrompts: Boolean): Boolean = when (this) {
        WHEN_USEFUL -> hasSelection || afterDictation
        PANEL -> hasSelection && hasPrompts
        // The row above the bar already shows every prompt, all the time.
        ROW -> false
    }

    /** Whether "Ask by voice" ends the chips in the bar. The PANEL layout keeps it in its panel. */
    val liveChipInBar: Boolean
        get() = this == WHEN_USEFUL

    companion object {
        /**
         * Where a stored layout lands when the chips become "show when useful" (the one-time move in
         * DictateLegacyMigrator). The always-on row is where an earlier update moved nearly everyone, so
         * it moves on; the panel could only have been picked by hand since then, so it stays.
         */
        fun movedToWhenUseful(current: DictatePromptsLayout): DictatePromptsLayout =
            if (current == ROW) WHEN_USEFUL else current
    }
}
