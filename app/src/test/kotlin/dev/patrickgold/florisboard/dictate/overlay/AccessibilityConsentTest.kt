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

/** The consent record behind the accessibility disclosure: valid only for the text version agreed to. */
class AccessibilityConsentTest {

    @Test
    fun `nothing agreed yet is not consent`() {
        assertFalse(AccessibilityConsent.isValid(AccessibilityConsent.NONE_VERSION, 0L))
    }

    @Test
    fun `agreeing to the current version is consent`() {
        assertTrue(AccessibilityConsent.isValid(1, 1_700_000_000_000L, currentVersion = 1))
    }

    @Test
    fun `an older version must be shown again`() {
        assertFalse(AccessibilityConsent.isValid(1, 1_700_000_000_000L, currentVersion = 2))
    }

    @Test
    fun `a version without a timestamp is not consent`() {
        assertFalse(AccessibilityConsent.isValid(1, 0L, currentVersion = 1))
    }

    @Test
    fun `a newer stored version still counts`() {
        assertTrue(AccessibilityConsent.isValid(3, 1_700_000_000_000L, currentVersion = 2))
    }
}
