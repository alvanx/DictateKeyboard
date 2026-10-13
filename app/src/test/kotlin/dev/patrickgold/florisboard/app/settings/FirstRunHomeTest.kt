/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings

import dev.patrickgold.florisboard.dictate.DictateMicCharm
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When Home shows its first-run page, and which looks that page offers. */
class FirstRunHomeTest {

    private val entry = DictateHistoryEntry(
        id = 1L,
        text = "hello",
        createdAt = 0L,
        providerId = "openai",
        providerName = "OpenAI",
        model = "",
        language = "",
        durationSecs = 3L,
        audioPath = null,
        audioBytes = 0L,
        source = "keyboard",
        reworded = false,
        pinned = false,
    )

    @Test
    fun `shows only once history has loaded empty and nothing was ever dictated`() {
        assertTrue(isFirstRunHome(emptyList(), dictations = 0L))
        // Still loading: never flash the first-run page.
        assertFalse(isFirstRunHome(null, dictations = 0L))
        assertFalse(isFirstRunHome(listOf(entry), dictations = 0L))
        // History off or cleared, but they have dictated before.
        assertFalse(isFirstRunHome(emptyList(), dictations = 5L))
    }

    @Test
    fun `offers the default looks when nothing or one of them is picked`() {
        val default = listOf(DictateMicCharm.DAISY, DictateMicCharm.BUTTERFLY, DictateMicCharm.HEART)
        assertEquals(default, firstRunLooks(DictateMicCharm.CLASSIC))
        assertEquals(default, firstRunLooks(DictateMicCharm.BUTTERFLY))
    }

    @Test
    fun `a look picked in the Style tab takes the last place`() {
        assertEquals(
            listOf(DictateMicCharm.DAISY, DictateMicCharm.BUTTERFLY, DictateMicCharm.MOON),
            firstRunLooks(DictateMicCharm.MOON),
        )
    }
}
