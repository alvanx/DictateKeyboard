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

/** The choice that replaced the "Fade when idle" switch. */
class DictateFloatingButtonFadeTest {

    @Test
    fun `the old switch maps onto the new choice`() {
        assertEquals(DictateFloatingButtonFade.NEVER, DictateFloatingButtonFade.fromAutoDim("false"))
        // "On" moves to the gentler default rather than keeping the dot that prompted the change.
        assertEquals(DictateFloatingButtonFade.GENTLE, DictateFloatingButtonFade.fromAutoDim("true"))
        assertEquals(DictateFloatingButtonFade.GENTLE, DictateFloatingButtonFade.fromAutoDim(""))
    }

    @Test
    fun `gentle stays more visible and waits longer than strong`() {
        val gentle = DictateFloatingButtonFade.GENTLE
        val strong = DictateFloatingButtonFade.STRONG
        assertTrue(gentle.scale > strong.scale)
        assertTrue(gentle.alpha > strong.alpha)
        assertTrue(gentle.delayMs > strong.delayMs)
    }

    @Test
    fun `never does not fade`() {
        assertFalse(DictateFloatingButtonFade.NEVER.enabled)
        assertTrue(DictateFloatingButtonFade.GENTLE.enabled)
        assertTrue(DictateFloatingButtonFade.STRONG.enabled)
    }
}
