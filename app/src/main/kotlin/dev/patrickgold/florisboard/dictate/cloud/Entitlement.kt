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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the paid plan unlocks, by the names the server uses in `features`.
 *
 * Only ever used to decide what to **show** (billing spec, rule 2 and section 10): the server checks
 * entitlement on every paid request itself, and nothing that runs on the device or with the user's
 * own key is ever gated by this (rule 1).
 */
enum class PaidFeature(val wireName: String) {
    MANAGED_TRANSCRIPTION("managed_transcription"),
    MANAGED_CLEANUP("managed_cleanup"),
    DICTIONARY_SYNC("dictionary_sync");

    companion object {
        /** Null for a name this version of the app does not know — those are ignored. */
        fun byWireName(name: String): PaidFeature? = entries.firstOrNull { it.wireName == name }
    }
}

/** Our subscription states (billing spec, section 7). */
object EntitlementState {
    const val TRIAL = "trial"
    const val ACTIVE = "active"
    const val CANCELED_ACTIVE = "canceled_active"
    const val GRACE = "grace"
    const val ON_HOLD = "on_hold"
    const val EXPIRED = "expired"
    const val PENDING = "pending"
    const val NONE = "none"

    /** The states Google still grants access in. */
    val grantsAccess: Set<String> = setOf(TRIAL, ACTIVE, CANCELED_ACTIVE, GRACE)

    /** The states in which the app should point at Play's payment settings. */
    val needsPaymentFix: Set<String> = setOf(GRACE, ON_HOLD)
}

/** The daily allowance, as `GET /v1/entitlement` reports it. */
@Serializable
data class DailyAllowance(
    @SerialName("allowance_seconds") val allowanceSeconds: Int = 0,
    @SerialName("used_seconds") val usedSeconds: Int = 0,
    @SerialName("resets_at") val resetsAt: Long = 0L,
)

/**
 * The entitlement record from `GET /v1/entitlement` (billing spec, section 6), also returned by
 * `POST /v1/subscription/verify`. Cached on the device as-is, plus [fetchedAt].
 */
@Serializable
data class Entitlement(
    val plan: String = PLAN_FREE,
    val state: String = EntitlementState.NONE,
    @SerialName("expires_at") val expiresAt: Long = 0L,
    @SerialName("auto_renewing") val autoRenewing: Boolean = false,
    val source: String = "",
    val daily: DailyAllowance? = null,
    val features: List<String> = emptyList(),
    /** When this device last got the record from the server; 0 means never. Not sent by the server. */
    @SerialName("fetched_at") val fetchedAt: Long = 0L,
) {
    /** True once the record came from the server at least once, rather than being the empty default. */
    val isKnown: Boolean get() = fetchedAt > 0L

    val isPlus: Boolean get() = plan == PLAN_PLUS

    val isTrial: Boolean get() = state == EntitlementState.TRIAL

    val needsPaymentFix: Boolean get() = state in EntitlementState.needsPaymentFix

    /** The features the server listed that this version knows; unknown names are dropped. */
    val knownFeatures: Set<PaidFeature>
        get() = features.mapNotNull { PaidFeature.byWireName(it) }.toSet()

    /**
     * Whether the cached record still says [feature] is unlocked at [now].
     *
     * Offline, the cache is trusted until [expiresAt] plus [OFFLINE_GRACE_MS] (billing spec,
     * section 10). That margin is what keeps a paying user from being shown a paywall because a
     * renewal happened while the phone had no connection.
     */
    fun has(feature: PaidFeature, now: Long): Boolean =
        isPlus &&
            state in EntitlementState.grantsAccess &&
            now < expiresAt + OFFLINE_GRACE_MS &&
            feature in knownFeatures

    companion object {
        const val PLAN_PLUS = "plus"
        const val PLAN_FREE = "free"

        /** How long past [expiresAt] a cached record is still believed. */
        const val OFFLINE_GRACE_MS = 3L * 24 * 60 * 60 * 1000

        /** The record before anything is known: free, nothing unlocked. */
        val NONE = Entitlement()

        /** What the debug override "force Plus" pretends the server said. */
        fun forcedPlus(now: Long): Entitlement = Entitlement(
            plan = PLAN_PLUS,
            state = EntitlementState.ACTIVE,
            expiresAt = now + 30L * 24 * 60 * 60 * 1000,
            autoRenewing = true,
            source = "debug",
            features = PaidFeature.entries.map { it.wireName },
            fetchedAt = now,
        )

        /** What the debug override "force free" pretends the server said. */
        fun forcedFree(now: Long): Entitlement = Entitlement(
            plan = PLAN_FREE,
            state = EntitlementState.EXPIRED,
            source = "debug",
            fetchedAt = now,
        )
    }
}
