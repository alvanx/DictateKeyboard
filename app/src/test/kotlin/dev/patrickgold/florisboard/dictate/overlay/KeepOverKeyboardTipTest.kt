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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeepOverKeyboardTipTest {
    @Test
    fun `asks someone who mostly uses the floating button`() {
        assertTrue(KeepOverKeyboardTip.shouldOffer(buttonDictations = 6, keyboardDictations = 1))
        assertTrue(KeepOverKeyboardTip.shouldOffer(buttonDictations = 5, keyboardDictations = 0))
    }

    @Test
    fun `waits for a habit to form`() {
        assertFalse(KeepOverKeyboardTip.shouldOffer(buttonDictations = 4, keyboardDictations = 0))
    }

    @Test
    fun `leaves keyboard dictators alone`() {
        assertFalse(KeepOverKeyboardTip.shouldOffer(buttonDictations = 8, keyboardDictations = 5))
    }
}
