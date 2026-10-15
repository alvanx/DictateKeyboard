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

import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which dictations the home screen lists: the three newest, except that a recent failed one whose
 * recording was kept always gets a row, so it can be retried without opening the full history.
 */
class RecentEntriesTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100L * day

    private fun entry(id: Long, ageMs: Long, failed: Boolean = false, audio: Boolean = failed, pinned: Boolean = false) =
        DictateHistoryEntry(
            id = id,
            text = "text $id",
            createdAt = now - ageMs,
            providerId = "openai",
            providerName = "OpenAI",
            model = "",
            language = "",
            durationSecs = 3L,
            audioPath = if (audio) "/history/$id.wav" else null,
            audioBytes = if (audio) 1000L else 0L,
            source = "keyboard",
            reworded = false,
            pinned = pinned,
            failed = failed,
        )

    private fun ids(list: List<DictateHistoryEntry>) = list.map { it.id }

    @Test
    fun `newest three, newest first, pinned or not`() {
        val all = listOf(entry(1, 5 * day, pinned = true), entry(2, 1000), entry(3, 2000), entry(4, 3000))
        assertEquals(listOf(2L, 3L, 4L), ids(recentEntries(all, now)))
    }

    @Test
    fun `an older failed recording takes the last row`() {
        val all = listOf(entry(1, 1000), entry(2, 2000), entry(3, 3000), entry(4, 2 * day, failed = true))
        assertEquals(listOf(1L, 2L, 4L), ids(recentEntries(all, now)))
    }

    @Test
    fun `a failed recording already in the newest three is not repeated`() {
        val all = listOf(entry(1, 1000, failed = true), entry(2, 2000), entry(3, 3000), entry(4, 4000))
        assertEquals(listOf(1L, 2L, 3L), ids(recentEntries(all, now)))
    }

    @Test
    fun `a failure with no recording, or over a week old, is left to the history`() {
        val noAudio = listOf(entry(1, 1000), entry(2, 2000), entry(3, 3000), entry(4, day, failed = true, audio = false))
        assertEquals(listOf(1L, 2L, 3L), ids(recentEntries(noAudio, now)))
        val old = listOf(entry(1, 1000), entry(2, 2000), entry(3, 3000), entry(4, 8 * day, failed = true))
        assertEquals(listOf(1L, 2L, 3L), ids(recentEntries(old, now)))
    }
}
