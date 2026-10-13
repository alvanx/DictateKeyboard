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

import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * The paid flag: the last entitlement the server reported, cached so the app can decide what to show
 * without a network call (billing spec, section 10).
 *
 * What this decides is purely presentational — paywall or dictate button, "Plus" or "Free" on a
 * card. The server checks entitlement on every paid request on its own; a patched app that answers
 * `true` here gets a paywall from the server instead of one from the app. And nothing that runs on
 * the device or with the user's own key ever asks this (rule 1).
 *
 * Refreshed by [DictateCloud.refreshEntitlement] at app start, after a purchase and whenever the
 * server answers `not_entitled`.
 */
object Entitlements {

    private val prefs by FlorisPreferenceStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val state: MutableStateFlow<Entitlement> by lazy { MutableStateFlow(applyOverride(load())) }

    /** The cached record, with the debug override applied. One instance, so Compose collects it once. */
    val current: StateFlow<Entitlement> by lazy { state.asStateFlow() }

    /** For UI decisions only — see the class comment. */
    fun has(feature: PaidFeature): Boolean = state.value.has(feature, System.currentTimeMillis())

    /** Stores a fresh record from the server and publishes it. */
    suspend fun store(entitlement: Entitlement) {
        val stamped = entitlement.copy(fetchedAt = System.currentTimeMillis())
        prefs.dictate.cloudEntitlement.set(json.encodeToString(Entitlement.serializer(), stamped))
        // The payment nudge is owed again the next time a problem starts, not before.
        if (!stamped.needsPaymentFix) prefs.dictate.cloudLowCreditNudged.set(false)
        state.value = applyOverride(stamped)
    }

    /** Forgets the record — the account it described is gone from this device. */
    suspend fun clear() {
        prefs.dictate.cloudEntitlement.set("")
        state.value = applyOverride(Entitlement.NONE)
    }

    /**
     * Re-reads the cache from preferences. Called once the preference store has loaded, so a read
     * that happened earlier cannot leave the empty record in place for the life of the process, and
     * after a backup restore has replaced the stored record.
     */
    fun reload() {
        state.value = applyOverride(load())
    }

    /** Debug builds only: "plus", "free", or "" to follow the server. */
    suspend fun setDebugOverride(plan: String) {
        if (!BuildConfig.DEBUG) return
        prefs.dictate.cloudDebugPlan.set(plan)
        state.value = applyOverride(load())
    }

    fun debugOverride(): String = if (BuildConfig.DEBUG) prefs.dictate.cloudDebugPlan.get() else ""

    private fun load(): Entitlement {
        val raw = prefs.dictate.cloudEntitlement.get()
        if (raw.isBlank()) return Entitlement.NONE
        return runCatching { json.decodeFromString(Entitlement.serializer(), raw) }
            .getOrDefault(Entitlement.NONE)
    }

    private fun applyOverride(entitlement: Entitlement): Entitlement {
        val now = System.currentTimeMillis()
        return when (debugOverride()) {
            Entitlement.PLAN_PLUS -> Entitlement.forcedPlus(now)
            Entitlement.PLAN_FREE -> Entitlement.forcedFree(now)
            else -> entitlement
        }
    }
}
