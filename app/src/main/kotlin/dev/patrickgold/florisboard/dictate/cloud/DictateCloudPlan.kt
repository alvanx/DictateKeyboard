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

import kotlin.math.floor

/**
 * The two base plans of the one subscription, "Plus", mirroring the Play Console (billing spec,
 * section 4).
 *
 * Prices are never kept here. Play converts them per country and the app shows exactly what Play
 * says; a figure in this file would be wrong everywhere but in the currency it was written in.
 * [months] exists only to compare the two plans with each other.
 */
enum class DictateCloudPlan(val basePlanId: String, val months: Int) {
    MONTHLY("monthly", 1),
    YEARLY("yearly", 12);

    companion object {
        /** The subscription product id in Play, and what `/v1/subscription/verify` expects. */
        const val PRODUCT_ID = "plus"

        /** Free trial for new customers; the default. */
        const val OFFER_TRIAL7 = "trial7"

        /**
         * The longer trial, for someone who arrived with a referral code. Play decides eligibility
         * only for the default offer, so the app has to keep this one to itself unless a code is
         * stored — see [pickOffer].
         */
        const val OFFER_TRIAL30 = "trial30"

        /** Display order: monthly first, so the yearly card reads as the saving it is. */
        val ordered: List<DictateCloudPlan> = listOf(MONTHLY, YEARLY)

        fun byBasePlanId(id: String): DictateCloudPlan? = entries.firstOrNull { it.basePlanId == id }
    }
}

/**
 * One way to buy a plan, as Play lists it: a base plan, optionally with an offer on top.
 *
 * Play only lists offers the buyer is eligible for, so someone who has had a trial before simply
 * gets no trial option and [pickOffer] falls back to the plain base plan.
 */
data class DictateCloudPlanOption(
    val basePlanId: String,
    /** Null for the base plan itself. */
    val offerId: String?,
    val offerToken: String,
)

/**
 * Chooses what to buy for [plan]: `trial30` when a referral code is stored, otherwise `trial7`,
 * otherwise the base plan with no offer.
 *
 * `trial30` is never chosen without a referral, even if Play lists it — its eligibility is ours to
 * decide, not Play's. Any other offer in the list is ignored, so a promotion added in the console
 * later cannot be picked up by accident.
 */
fun pickOffer(
    options: List<DictateCloudPlanOption>,
    plan: DictateCloudPlan,
    hasReferral: Boolean,
): DictateCloudPlanOption? {
    val forPlan = options.filter { it.basePlanId == plan.basePlanId }
    fun offer(id: String) = forPlan.firstOrNull { it.offerId == id }
    return (if (hasReferral) offer(DictateCloudPlan.OFFER_TRIAL30) else null)
        ?: offer(DictateCloudPlan.OFFER_TRIAL7)
        ?: forPlan.firstOrNull { it.offerId == null }
}

/**
 * Length in days of an ISO 8601 period as Play writes them (`P7D`, `P1W`, `P1M`, `P1Y`), or null.
 *
 * Months and years are counted as 30 and 365 days. This is only ever used to say "7-day free trial",
 * where a month-long trial reading as "30-day" is what people expect anyway.
 */
fun periodDays(period: String): Int? {
    val match = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""").matchEntire(period.trim())
        ?: return null
    val (y, m, w, d) = match.destructured
    if (y.isEmpty() && m.isEmpty() && w.isEmpty() && d.isEmpty()) return null
    fun n(s: String) = s.toIntOrNull() ?: 0
    return n(y) * 365 + n(m) * 30 + n(w) * 7 + n(d)
}

/**
 * Below this, an advertised saving reads as a rounding artefact rather than a reason to choose.
 */
private const val MIN_SAVINGS_PERCENT = 10

/**
 * How much cheaper a month is on [yearly] than on [monthly], as a whole percentage.
 *
 * Worked out from Play's own amounts, since Play's per-country prices are not a conversion of ours.
 * Rounded **down**, so the badge never promises more than the real saving, and null wherever the
 * claim would not hold: different currencies, a missing amount, no saving, or one too small to print.
 */
fun yearlySavingsPercent(
    monthlyMicros: Long,
    monthlyCurrency: String,
    yearlyMicros: Long,
    yearlyCurrency: String,
): Int? {
    if (monthlyCurrency != yearlyCurrency) return null
    if (monthlyMicros <= 0L || yearlyMicros <= 0L) return null
    val perMonthYearly = yearlyMicros.toDouble() / DictateCloudPlan.YEARLY.months
    val perMonthMonthly = monthlyMicros.toDouble() / DictateCloudPlan.MONTHLY.months
    if (perMonthYearly >= perMonthMonthly) return null
    val percent = floor((1.0 - perMonthYearly / perMonthMonthly) * 100).toInt()
    return percent.takeIf { it >= MIN_SAVINGS_PERCENT }
}
