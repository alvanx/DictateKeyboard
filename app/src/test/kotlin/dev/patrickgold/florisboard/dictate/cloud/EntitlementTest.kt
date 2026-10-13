/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.cloud

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cached paid flag (billing spec, section 10): which states unlock, how long the cache is
 * believed offline, and that the server's record parses as the spec writes it.
 */
class EntitlementTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_767_225_600_000L

    private fun plus(state: String, expiresAt: Long = now + day) = Entitlement(
        plan = Entitlement.PLAN_PLUS,
        state = state,
        expiresAt = expiresAt,
        features = listOf("managed_transcription", "managed_cleanup", "dictionary_sync"),
        fetchedAt = now,
    )

    @Test
    fun `access states unlock, the others do not`() {
        for (state in listOf("trial", "active", "canceled_active", "grace")) {
            assertTrue(plus(state).has(PaidFeature.MANAGED_TRANSCRIPTION, now), state)
        }
        for (state in listOf("on_hold", "expired", "pending")) {
            assertFalse(plus(state).has(PaidFeature.MANAGED_TRANSCRIPTION, now), state)
        }
    }

    @Test
    fun `cache is trusted until three days past expiry`() {
        val record = plus("active", expiresAt = now)
        assertTrue(record.has(PaidFeature.MANAGED_CLEANUP, now + 3 * day - 1))
        assertFalse(record.has(PaidFeature.MANAGED_CLEANUP, now + 3 * day))
    }

    @Test
    fun `free plan and the empty record unlock nothing`() {
        assertFalse(plus("active").copy(plan = Entitlement.PLAN_FREE).has(PaidFeature.DICTIONARY_SYNC, now))
        assertFalse(Entitlement.NONE.has(PaidFeature.MANAGED_TRANSCRIPTION, now))
        assertFalse(Entitlement.NONE.isKnown)
    }

    @Test
    fun `only listed features unlock`() {
        val record = plus("active").copy(features = listOf("managed_transcription"))
        assertTrue(record.has(PaidFeature.MANAGED_TRANSCRIPTION, now))
        assertFalse(record.has(PaidFeature.DICTIONARY_SYNC, now))
    }

    @Test
    fun `parses the server record and ignores unknown features and fields`() {
        val body = """
            {
              "plan": "plus",
              "state": "active",
              "expires_at": 1767225600000,
              "auto_renewing": true,
              "source": "google_play",
              "daily": { "allowance_seconds": 5400, "used_seconds": 1200, "resets_at": 1767312000000 },
              "features": ["managed_transcription", "managed_cleanup", "dictionary_sync", "teleport"],
              "something_new": 1
            }
        """.trimIndent()
        val record = Json { ignoreUnknownKeys = true }.decodeFromString(Entitlement.serializer(), body)
        assertEquals(5400, record.daily?.allowanceSeconds)
        assertEquals(1200, record.daily?.usedSeconds)
        assertEquals(PaidFeature.entries.toSet(), record.knownFeatures)
        assertTrue(record.has(PaidFeature.DICTIONARY_SYNC, 1767225600000L - 1))
        assertFalse(record.isKnown)
    }

    @Test
    fun `grace and hold ask for a payment fix`() {
        assertTrue(plus("grace").needsPaymentFix)
        assertTrue(plus("on_hold").needsPaymentFix)
        assertFalse(plus("active").needsPaymentFix)
    }
}
