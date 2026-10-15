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
import kotlin.test.assertTrue

class DictateMicCharmTest {
    private val lilac = 0xFFC3A8FF.toInt()

    @Test
    fun `a tintable look takes the picked colour`() {
        assertEquals(lilac, DictateMicCharm.DAISY.resolveColor(lilac))
    }

    @Test
    fun `no pick means the look's own colour`() {
        assertEquals(DictateMicCharm.DAISY.defaultColor, DictateMicCharm.DAISY.resolveColor(0))
    }

    @Test
    fun `a look with its own colours ignores the pick`() {
        assertEquals(DictateMicCharm.SUNFLOWER.defaultColor, DictateMicCharm.SUNFLOWER.resolveColor(lilac))
        assertEquals(DictateMicCharm.ON_AIR.defaultColor, DictateMicCharm.ON_AIR.resolveColor(lilac))
    }

    @Test
    fun `every collection has looks and every movement has a loop`() {
        DictateMicCollection.entries.forEach { c ->
            assertTrue(DictateMicCharm.entries.any { it.collection == c }, "$c is empty")
        }
        DictateMicMove.entries.forEach { assertTrue(it.periodMs > 0) }
    }
}
