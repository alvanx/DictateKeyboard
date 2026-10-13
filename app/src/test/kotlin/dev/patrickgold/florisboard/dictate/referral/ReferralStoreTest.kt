/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.referral

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferralStoreTest {

    private class FakeStorage : ReferralStorage {
        var code: String? = null
        var captured = false
        override fun readCode() = code
        override fun writeCode(code: String?) { this.code = code }
        override fun hasCaptured() = captured
        override fun markCaptured() { captured = true }
    }

    @Test fun validHandle() = assertEquals("anna_k-7", parseReferrer("ref=anna_k-7"))
    @Test fun uppercaseIsLowered() = assertEquals("anna", parseReferrer("ref=ANNA"))
    @Test fun extraParamsIgnored() =
        assertEquals("anna", parseReferrer("utm_source=google-play&ref=anna&gclid=abc123"))
    @Test fun urlEncoded() = assertEquals("anna", parseReferrer("ref%3Danna"))
    @Test fun urlEncodedWithExtras() =
        assertEquals("anna", parseReferrer("utm_source%3Dx%26ref%3Danna%26utm_medium%3Dy"))
    @Test fun tooShort() = assertNull(parseReferrer("ref=ab"))
    @Test fun tooLong() = assertNull(parseReferrer("ref=" + "a".repeat(33)))
    @Test fun maxLengthOk() = assertEquals("a".repeat(32), parseReferrer("ref=" + "a".repeat(32)))
    @Test fun badCharacters() {
        assertNull(parseReferrer("ref=ann@!"))
        assertNull(parseReferrer("ref=an na"))
        assertNull(parseReferrer("ref=anna.k"))
    }
    @Test fun otherKeyIsNotRef() = assertNull(parseReferrer("xref=anna&reference=anna"))
    @Test fun empty() {
        assertNull(parseReferrer(""))
        assertNull(parseReferrer("   "))
        assertNull(parseReferrer("ref="))
    }
    @Test fun nullInput() = assertNull(parseReferrer(null))

    @Test fun setManuallyValid() {
        val storage = FakeStorage()
        val store = ReferralStore(storage)
        assertTrue(store.setManually("  Friend-01 "))
        assertEquals("friend-01", store.currentCode)
        assertEquals("friend-01", storage.code)
    }

    @Test fun setManuallyInvalidKeepsOldCode() {
        val storage = FakeStorage()
        val store = ReferralStore(storage)
        store.setManually("anna")
        assertFalse(store.setManually("no"))
        assertFalse(store.setManually("bad code!"))
        assertFalse(store.setManually(""))
        assertEquals("anna", store.currentCode)
        assertEquals("anna", storage.code)
    }

    @Test fun clearRemovesCode() {
        val storage = FakeStorage()
        val store = ReferralStore(storage)
        store.setManually("anna")
        store.clear()
        assertNull(store.currentCode)
        assertNull(storage.code)
    }

    @Test fun recordCaptureStoresCodeAndMarksDone() {
        val storage = FakeStorage()
        val store = ReferralStore(storage)
        store.recordCapture("utm_source=x&ref=Anna")
        assertEquals("anna", store.currentCode)
        assertTrue(storage.captured)
    }

    @Test fun recordCaptureWithoutReferrerStillMarksDone() {
        val storage = FakeStorage()
        val store = ReferralStore(storage)
        store.recordCapture(null)
        assertNull(store.currentCode)
        assertTrue(storage.captured)
    }

    @Test fun manualCodeBeatsInstallLink() {
        val storage = FakeStorage()
        val store = ReferralStore(storage)
        store.setManually("typed")
        store.recordCapture("ref=fromlink")
        assertEquals("typed", store.currentCode)
    }
}
