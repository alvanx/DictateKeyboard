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

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the referral handle and the "already looked" flag live. A small seam so the logic can be tested
 * without a phone.
 */
interface ReferralStorage {
    fun readCode(): String?
    fun writeCode(code: String?)
    fun hasCaptured(): Boolean
    fun markCaptured()
}

/**
 * The referral handle from a friend's invite link (`...&referrer=ref%3D<handle>`), read once from the Play
 * install referrer and kept on this device (see docs/specs/referrals.md).
 *
 * Stored in its own `SharedPreferences` file rather than in the JetPref store on purpose: the backup rules
 * only include the JetPref store, so a separate file never goes to Google's cloud backup, and the settings
 * export (which is a dump of the JetPref store) cannot contain it. A code must not travel between devices.
 *
 * Nothing here talks to a server.
 */
class ReferralStore(private val storage: ReferralStorage) {

    private val state = MutableStateFlow(runCatching { storage.readCode() }.getOrNull())

    /** The stored handle, or null. */
    val code: Flow<String?> = state.asStateFlow()

    val currentCode: String? get() = state.value

    /**
     * Reads the Play install referrer on first launch only. Whatever happens (no referrer, service missing,
     * timeout, error) the answer is recorded and the attempt is never repeated. Never throws.
     */
    suspend fun captureOnce(context: Context, timeoutMs: Long = TIMEOUT_MS) {
        if (runCatching { storage.hasCaptured() }.getOrDefault(true)) return
        val raw = withContext(Dispatchers.IO) {
            runCatching { withTimeoutOrNull(timeoutMs) { readInstallReferrer(context) } }.getOrNull()
        }
        recordCapture(raw)
    }

    /** Stores the outcome of the one-time read. [raw] is the referrer string, or null for "no code". */
    internal fun recordCapture(raw: String?) {
        runCatching {
            val parsed = raw?.let(::parseReferrer)
            // A code typed in by hand before the read finished wins over the install link.
            if (parsed != null && state.value == null) {
                storage.writeCode(parsed)
                state.value = parsed
            }
            storage.markCaptured()
        }
    }

    /** Saves a handle typed in by hand. False (and nothing saved) if it is not a valid handle. */
    fun setManually(code: String): Boolean {
        val handle = normalizeHandle(code) ?: return false
        return runCatching {
            storage.writeCode(handle)
            state.value = handle
            true
        }.getOrDefault(false)
    }

    fun clear() {
        runCatching { storage.writeCode(null) }
        state.value = null
    }

    private suspend fun readInstallReferrer(context: Context): String? =
        suspendCancellableCoroutine { cont ->
            val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
            fun finish(value: String?) {
                runCatching { client.endConnection() }
                if (cont.isActive) cont.resume(value) {}
            }
            cont.invokeOnCancellation { runCatching { client.endConnection() } }
            try {
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        val value = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            runCatching { client.installReferrer.installReferrer }.getOrNull()
                        } else {
                            // FEATURE_NOT_SUPPORTED, SERVICE_UNAVAILABLE, DEVELOPER_ERROR, anything else.
                            null
                        }
                        finish(value)
                    }

                    override fun onInstallReferrerServiceDisconnected() = finish(null)
                })
            } catch (_: Exception) {
                finish(null)
            }
        }

    companion object {
        const val TIMEOUT_MS = 10_000L
        private val HANDLE = Regex("^[a-z0-9_-]{3,32}$")

        /** [raw] trimmed and lowercased if it is a valid handle, else null. */
        internal fun normalizeHandle(raw: String): String? =
            raw.trim().lowercase().takeIf { HANDLE.matches(it) }

        /**
         * The referral handle in a Play install referrer string, or null. Accepts `ref=<handle>` anywhere in
         * the (URL-decoded) query string and ignores everything else (utm_*, gclid, ...).
         */
        fun parseReferrer(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            // The string may be encoded once (`ref%3Dabc`) or arrive as plain `ref=abc&utm_source=x`.
            val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: return null
            for (part in decoded.split('&', '?')) {
                val eq = part.indexOf('=')
                if (eq < 0) continue
                if (part.substring(0, eq).trim().lowercase() != "ref") continue
                normalizeHandle(part.substring(eq + 1))?.let { return it }
            }
            return null
        }
    }
}

/** Top-level alias so callers and tests can write `parseReferrer(raw)`. */
fun parseReferrer(raw: String?): String? = ReferralStore.parseReferrer(raw)

class SharedPrefsReferralStorage(context: Context) : ReferralStorage {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    override fun readCode(): String? = prefs.getString(KEY_CODE, null)
    override fun writeCode(code: String?) {
        prefs.edit().apply { if (code == null) remove(KEY_CODE) else putString(KEY_CODE, code) }.apply()
    }
    override fun hasCaptured(): Boolean = prefs.getBoolean(KEY_CAPTURED, false)
    override fun markCaptured() {
        prefs.edit().putBoolean(KEY_CAPTURED, true).apply()
    }

    private companion object {
        const val FILE_NAME = "dictate_referral"
        const val KEY_CODE = "code"
        const val KEY_CAPTURED = "captured"
    }
}

object Referral {
    @Volatile private var instance: ReferralStore? = null

    fun get(context: Context): ReferralStore = instance ?: synchronized(this) {
        instance ?: ReferralStore(SharedPrefsReferralStorage(context)).also { instance = it }
    }
}
