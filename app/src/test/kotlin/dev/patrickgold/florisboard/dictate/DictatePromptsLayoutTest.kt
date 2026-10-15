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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DictatePromptsLayoutTest {
    private val whenUseful = DictatePromptsLayout.WHEN_USEFUL

    @Test
    fun `when useful shows the chips right after a dictation`() {
        assertTrue(whenUseful.chipsInBar(hasSelection = false, afterDictation = true, hasPrompts = true))
    }

    @Test
    fun `when useful shows the chips on a selection`() {
        assertTrue(whenUseful.chipsInBar(hasSelection = true, afterDictation = false, hasPrompts = true))
    }

    @Test
    fun `when useful leaves the bar to the suggestions otherwise`() {
        assertFalse(whenUseful.chipsInBar(hasSelection = false, afterDictation = false, hasPrompts = true))
    }

    @Test
    fun `when useful still offers Ask by voice without saved prompts`() {
        assertTrue(whenUseful.chipsInBar(hasSelection = false, afterDictation = true, hasPrompts = false))
        assertTrue(whenUseful.liveChipInBar)
    }

    @Test
    fun `the panel layout keeps its selection strip and nothing more`() {
        val panel = DictatePromptsLayout.PANEL
        assertTrue(panel.chipsInBar(hasSelection = true, afterDictation = false, hasPrompts = true))
        assertFalse(panel.chipsInBar(hasSelection = true, afterDictation = false, hasPrompts = false))
        assertFalse(panel.chipsInBar(hasSelection = false, afterDictation = true, hasPrompts = true))
        assertFalse(panel.liveChipInBar)
    }

    @Test
    fun `the always-on row never doubles up in the bar`() {
        val row = DictatePromptsLayout.ROW
        assertFalse(row.chipsInBar(hasSelection = true, afterDictation = true, hasPrompts = true))
    }

    @Test
    fun `the move to when useful takes the row and leaves a picked panel alone`() {
        assertEquals(whenUseful, DictatePromptsLayout.movedToWhenUseful(DictatePromptsLayout.ROW))
        assertEquals(DictatePromptsLayout.PANEL, DictatePromptsLayout.movedToWhenUseful(DictatePromptsLayout.PANEL))
        assertEquals(whenUseful, DictatePromptsLayout.movedToWhenUseful(whenUseful))
    }
}
