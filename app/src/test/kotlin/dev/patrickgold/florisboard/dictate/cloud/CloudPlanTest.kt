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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which Play offer the subscribe button buys (billing spec, section 9), how a trial period reads, and
 * the "cheaper per month" badge on the yearly plan.
 */
class CloudPlanTest {

    private fun opt(plan: String, offer: String?) = DictateCloudPlanOption(plan, offer, "$plan/${offer ?: "base"}")

    private val everything = listOf(
        opt("monthly", null), opt("monthly", "trial7"), opt("monthly", "trial30"),
        opt("yearly", null), opt("yearly", "trial7"), opt("yearly", "trial30"),
    )

    @Test
    fun `default is the 7-day trial`() {
        assertEquals("trial7", pickOffer(everything, DictateCloudPlan.MONTHLY, hasReferral = false)?.offerId)
        assertEquals("yearly", pickOffer(everything, DictateCloudPlan.YEARLY, hasReferral = false)?.basePlanId)
    }

    @Test
    fun `referral picks the 30-day trial`() {
        assertEquals("trial30", pickOffer(everything, DictateCloudPlan.YEARLY, hasReferral = true)?.offerId)
    }

    @Test
    fun `30-day trial is never picked without a referral, even when it is the only offer`() {
        val options = listOf(opt("monthly", null), opt("monthly", "trial30"))
        assertNull(pickOffer(options, DictateCloudPlan.MONTHLY, hasReferral = false)?.offerId)
    }

    @Test
    fun `referral falls back to the 7-day trial when Play does not list the 30-day one`() {
        val options = listOf(opt("monthly", null), opt("monthly", "trial7"))
        assertEquals("trial7", pickOffer(options, DictateCloudPlan.MONTHLY, hasReferral = true)?.offerId)
    }

    @Test
    fun `no trial left means the plain base plan`() {
        val options = listOf(opt("monthly", null), opt("yearly", null))
        val picked = pickOffer(options, DictateCloudPlan.MONTHLY, hasReferral = false)
        assertEquals("monthly", picked?.basePlanId)
        assertNull(picked?.offerId)
    }

    @Test
    fun `unknown offers are ignored and a missing plan gives nothing`() {
        val options = listOf(opt("monthly", "spring_promo"))
        assertNull(pickOffer(options, DictateCloudPlan.MONTHLY, hasReferral = false))
        assertNull(pickOffer(listOf(opt("monthly", null)), DictateCloudPlan.YEARLY, hasReferral = false))
    }

    @Test
    fun `trial periods in days`() {
        assertEquals(7, periodDays("P7D"))
        assertEquals(7, periodDays("P1W"))
        assertEquals(30, periodDays("P1M"))
        assertEquals(30, periodDays("P30D"))
        assertNull(periodDays("P"))
        assertNull(periodDays("7 days"))
    }

    private fun micros(amount: Double) = (amount * 1_000_000).toLong()

    @Test
    fun `yearly saving at the list prices`() {
        // $3.99 a month against $29.99 a year: $2.50 a month, 37.4 % less, rounded down.
        assertEquals(37, yearlySavingsPercent(micros(3.99), "USD", micros(29.99), "USD"))
    }

    @Test
    fun `no badge across currencies, without a saving, or for a trivial one`() {
        assertNull(yearlySavingsPercent(micros(3.99), "USD", micros(29.99), "EUR"))
        assertNull(yearlySavingsPercent(micros(3.99), "USD", micros(47.88), "USD"))
        assertNull(yearlySavingsPercent(micros(3.99), "USD", micros(45.00), "USD"))
        assertNull(yearlySavingsPercent(0L, "USD", micros(29.99), "USD"))
    }
}
