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

import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.dictateProxyConfig
import dev.patrickgold.florisboard.dictate.provider.CloudAvailability
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.Proxy
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The account half of Dictate Cloud: verifying a subscription, reading the entitlement, moving an
 * account to a new device (billing spec, section 6).
 *
 * Only these account endpoints live here. Dictation and rewording deliberately do not — the server
 * speaks the ordinary OpenAI formats for those, so they run through `OpenAiCompatibleClient` like
 * every other provider, with the wallet token in place of an API key.
 *
 * Nothing here decides anything about entitlement. The app cannot unlock itself, and a reply that
 * says otherwise changes nothing upstream: every paid request is checked where the money is.
 */
object DictateCloudApi {

    /** Codes the server sends in `error.code`, for the cases a caller must react to differently. */
    object ErrorCode {
        /** Payment started but not finished (cash at a counter, for instance). Ask again later. */
        const val PURCHASE_PENDING = "purchase_pending"
        /** Google has no record of this purchase. */
        const val PURCHASE_UNKNOWN = "purchase_unknown"
        /** The purchase was refunded — credit was clawed back. */
        const val PURCHASE_VOIDED = "purchase_voided"
        /** Google could not be asked right now. Our problem, not the purchase's: retry. */
        const val VERIFY_UNAVAILABLE = "verify_unavailable"

        /** Same as [VERIFY_UNAVAILABLE], under the name `/v1/subscription/verify` uses. */
        const val GOOGLE_UNAVAILABLE = "google_unavailable"

        /**
         * A paid route was called without an active Plus subscription (HTTP 402). The remedy is the
         * paywall, so the app refreshes its cached entitlement and offers the subscription screen.
         */
        const val NOT_ENTITLED = "not_entitled"

        /**
         * Plus is active but today's allowance is used up (HTTP 429). Not something to buy or retry
         * now: it resets at 00:00 UTC, which the reply carries as `resets_at`.
         */
        const val DAILY_LIMIT = "daily_limit"
        /** No account for this recovery code (also returned for blocked ones, on purpose). */
        const val WALLET_NOT_FOUND = "wallet_not_found"
        /** The stored token is not (or no longer) valid — including because the account is gone. */
        const val INVALID_TOKEN = "invalid_token"

        /**
         * This device was signed out from the dashboard, but the account is still there. Only the
         * account reads (`GET /wallet`, `GET /entitlement`) tell this apart from [INVALID_TOKEN];
         * elsewhere the two are one error, because nothing else can act on the difference. Here it
         * decides whether the recovery code is thrown away with the token or is the one thing kept.
         */
        const val DEVICE_REVOKED = "device_revoked"

        /**
         * The account already holds as many signed-in devices as it may. Not a plain failure but a
         * fork: the reply lists them, and signing one out is the way on — see
         * [DictateCloudDeviceLimitException].
         */
        const val DEVICE_LIMIT = "device_limit"

        /**
         * Out of credit — the credit-pack server's answer, kept while servers that still send it
         * are around. Treated like [NOT_ENTITLED]: the remedy is a button in this app rather than
         * something to sort out at a provider.
         */
        const val INSUFFICIENT_CREDITS = "insufficient_credits"
    }

    private val base = ProviderRegistry.CLOUD.baseUrl.trimEnd('/')

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val prefs by FlorisPreferenceStore

    /**
     * Reports a Play subscription to the server, which checks it with Google, acknowledges it and
     * attaches it to an account (`POST /v1/subscription/verify`).
     *
     * Safe to call repeatedly with the same [purchaseToken]: the server keys subscriptions on it, so
     * a second call returns the same account. That is the design — the app calls this at every
     * start for whatever Play lists, which is also how a reinstall gets its account back.
     *
     * [token] is this device's own token when it has one, which attaches a new subscription to the
     * existing account. Without one the server either finds the account the token already belongs
     * to and issues a device token for it, or creates an account and returns its token and recovery
     * code, once and only once.
     *
     * [label] names the device in the ledger.
     */
    suspend fun verifySubscription(
        purchaseToken: String,
        productId: String = DictateCloudPlan.PRODUCT_ID,
        walletId: String? = null,
        label: String? = null,
        token: String? = null,
    ): DictateCloudVerify {
        val body = json.encodeToString(
            VerifyRequest(
                purchaseToken = purchaseToken,
                productId = productId,
                walletId = walletId?.takeIf { it.isNotBlank() },
                label = label,
            ),
        )
        val request = Request.Builder()
            .url("$base/subscription/verify")
            .apply { if (!token.isNullOrBlank()) header("authorization", "Bearer $token") }
            .post(body.toRequestBody(jsonMedia))
            .build()
        return call(request)
    }

    /** The current entitlement for [token] (`GET /v1/entitlement`). */
    suspend fun entitlement(token: String): Entitlement {
        val request = Request.Builder()
            .url("$base/entitlement")
            .header("authorization", "Bearer $token")
            .get()
            .build()
        return call(request)
    }

    /**
     * How this device names itself in the ledger, e.g. `SM-A556B · Android 15 · 5.4.0`.
     *
     * Deliberately a description, not an identifier: model, OS and app version are what makes a
     * support message answerable, and none of it says who anybody is. The server truncates to 60
     * characters.
     */
    fun deviceLabel(): String = listOf(
        android.os.Build.MODEL,
        "Android ${android.os.Build.VERSION.RELEASE}",
        dev.patrickgold.florisboard.BuildConfig.VERSION_NAME,
    ).filter { it.isNotBlank() }.joinToString(" · ")

    /**
     * Claims an existing account on this device using its recovery code.
     *
     * Issues an additional token rather than replacing the old one, so recovering onto a new phone
     * does not silently lock out the old one — a shared account across two devices is a feature, and
     * cutting one off without being asked to would be a surprise.
     */
    suspend fun restore(code: String, label: String? = null): DictateCloudRestore = post(
        path = "/wallet/restore",
        body = json.encodeToString(RestoreRequest(code = code, label = label)),
    )

    /**
     * Signs one device out of an account, authorised by its recovery code.
     *
     * The way past the device limit, and it has to work from *another* device: the one being
     * removed is typically the one that cannot ask for anything any more — lost, broken, wiped.
     * It grants nothing new, since whoever holds the code can already spend the balance or delete
     * the account outright.
     */
    suspend fun revokeDevice(code: String, tokenHash: String): Boolean {
        val reply: DictateCloudRevoke = post(
            path = "/wallet/devices/revoke",
            body = json.encodeToString(RevokeDeviceRequest(code = code, tokenHash = tokenHash)),
        )
        return reply.revoked
    }

    /**
     * Asks what deleting this account would affect, without deleting it. Nothing changes on the
     * server until [deleteAccount] is called with the confirmation.
     */
    suspend fun previewDeletion(token: String): DictateCloudDeletion = postAuthed(
        path = "/wallet/delete",
        token = token,
        body = json.encodeToString(DeleteRequest(confirm = false)),
    )

    /**
     * Deletes the account for good. It does **not** cancel a Play subscription — only Google can —
     * which is why the dialog links to Play's subscription page first (billing spec, section 6).
     */
    suspend fun deleteAccount(token: String): DictateCloudDeletion = postAuthed(
        path = "/wallet/delete",
        token = token,
        body = json.encodeToString(DeleteRequest(confirm = true)),
    )

    private suspend inline fun <reified T> postAuthed(path: String, token: String, body: String): T {
        val request = Request.Builder()
            .url("$base$path")
            .header("authorization", "Bearer $token")
            .post(body.toRequestBody(jsonMedia))
            .build()
        return call(request)
    }

    private suspend inline fun <reified T> post(path: String, body: String): T {
        val request = Request.Builder()
            .url("$base$path")
            .post(body.toRequestBody(jsonMedia))
            .build()
        return call(request)
    }

    private suspend inline fun <reified T> call(request: Request): T {
        // The one door to the Cloud server. Shut while the service is switched off, so nothing can
        // reach the placeholder address however it got here.
        if (!CloudAvailability.ENABLED) throw DictateCloudException(0, "unavailable", "Cloud is not available")
        val response = try {
            client().newCall(request).await()
        } catch (e: IOException) {
            throw DictateCloudException(0, "unreachable", e.message ?: "Network error")
        }
        val body = response.use { it.body.string() }
        if (!response.isSuccessful) throw errorFrom(response.code, body)
        return try {
            json.decodeFromString<T>(body)
        } catch (e: Exception) {
            throw DictateCloudException(response.code, "bad_response", e.message ?: "Unreadable reply")
        }
    }

    /**
     * The server answers errors in OpenAI's envelope, which is exactly why it does: the same shape
     * carries the dictation and rewording failures, so one parser covers all of it.
     */
    private fun errorFrom(status: Int, body: String): DictateCloudException {
        val parsed = runCatching { json.decodeFromString<ErrorEnvelope>(body).error }.getOrNull()
        val code = parsed?.code.orEmpty()
        val message = parsed?.message?.takeIf { it.isNotBlank() } ?: "HTTP $status"

        // The one refusal that carries more than a reason: the device limit sends the devices
        // holding the slots, because the only useful next step is to pick one and sign it out.
        // Flattening it into a plain error would leave the user told "no" with nowhere to go.
        if (code == ErrorCode.DEVICE_LIMIT) {
            val envelope = runCatching { json.decodeFromString<DeviceLimitEnvelope>(body) }.getOrNull()
            if (envelope != null) {
                return DictateCloudDeviceLimitException(
                    status = status,
                    code = code,
                    message = message,
                    limit = envelope.limit,
                    devices = envelope.devices,
                )
            }
        }
        return DictateCloudException(status = status, code = code, message = message)
    }

    /**
     * Built per call rather than kept around: these endpoints are used a handful of times in
     * an install's life, and rebuilding costs nothing next to holding a client that would miss a
     * proxy setting changed in the meantime.
     *
     * User-installed CA certificates are not honoured here, unlike for provider calls (#137). This
     * endpoint is ours and presents a public certificate; there is no self-hosted variant of it to
     * accommodate, and trusting a user CA for the wallet would only widen what can intercept it.
     */
    private fun client(): OkHttpClient {
        val timeout = Duration.ofSeconds(30)
        val builder = OkHttpClient.Builder()
            .callTimeout(timeout)
            .connectTimeout(Duration.ofSeconds(10))
            .fastFallback(true)
            .readTimeout(timeout)
            .writeTimeout(timeout)
        prefs.dictate.dictateProxyConfig()?.let { proxy ->
            builder.proxy(proxy.toJavaProxy())
            if (proxy.type == Proxy.Type.HTTP && proxy.hasCredentials) {
                builder.proxyAuthenticator { _, response ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(proxy.username!!, proxy.password!!))
                        .build()
                }
            }
        }
        return builder.build()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }

    @Serializable
    private data class VerifyRequest(
        val purchaseToken: String,
        val productId: String,
        val walletId: String? = null,
        val label: String? = null,
    )

    @Serializable
    private data class RestoreRequest(
        val code: String,
        val label: String? = null,
    )

    @Serializable
    private data class DeleteRequest(val confirm: Boolean)

    @Serializable
    private data class RevokeDeviceRequest(val code: String, val tokenHash: String)

    @Serializable
    private data class DeviceLimitEnvelope(
        val limit: Int = 0,
        val devices: List<DictateCloudDevice> = emptyList(),
    )

    @Serializable
    private data class ErrorEnvelope(val error: ErrorBody? = null)

    @Serializable
    private data class ErrorBody(
        val message: String = "",
        val code: String = "",
        val type: String = "",
    )
}

/**
 * A failed wallet call. [status] is 0 when the server was never reached, [code] the server's own
 * error code (see [DictateCloudApi.ErrorCode]) or empty when it did not send one.
 */
open class DictateCloudException(
    val status: Int,
    val code: String,
    message: String,
) : IOException(message)

/**
 * The account is signed in on as many devices as it may be.
 *
 * Carries the devices holding the slots, because "no" on its own would strand the case this rule
 * exists for: a new phone replacing one that is lost, broken or wiped. That old device cannot sign
 * itself out, so the choice has to be offered here.
 */
class DictateCloudDeviceLimitException(
    status: Int,
    code: String,
    message: String,
    val limit: Int,
    val devices: List<DictateCloudDevice>,
) : DictateCloudException(status, code, message)

/** One signed-in device, as the server names it. [tokenHash] is the handle for signing it out. */
@Serializable
data class DictateCloudDevice(
    @SerialName("token_hash") val tokenHash: String,
    val label: String? = null,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("last_seen_at") val lastSeenAt: Long? = null,
)

@Serializable
data class DictateCloudRevoke(
    val revoked: Boolean = false,
    @SerialName("devices_left") val devicesLeft: Int = 0,
)

/**
 * Reply to `POST /v1/subscription/verify`. [token] is present when this device was given a new one
 * (first purchase, reinstall, new phone); [recoveryCode] only when the account was just created.
 */
@Serializable
data class DictateCloudVerify(
    @SerialName("wallet_id") val walletId: String,
    val token: String? = null,
    @SerialName("recovery_code") val recoveryCode: String? = null,
    val entitlement: Entitlement? = null,
)

/**
 * Reply to both steps of deleting an account. One shape for both so the caller does not have to
 * branch on which call it made. The minute fields are the credit-pack server's and read 0 otherwise.
 */
@Serializable
data class DictateCloudDeletion(
    val confirmed: Boolean = false,
    val deleted: Boolean = false,
    @SerialName("minutes_left") val minutesLeft: Int = 0,
    @SerialName("rewords_left") val rewordsLeft: Int = 0,
    val purchases: Int = 0,
    @SerialName("forfeited_minutes") val forfeitedMinutes: Int = 0,
    @SerialName("purchases_retained") val purchasesRetained: Int = 0,
)

@Serializable
data class DictateCloudRestore(
    @SerialName("wallet_id") val walletId: String,
    val token: String,
    @SerialName("seconds_left") val secondsLeft: Int = 0,
    @SerialName("minutes_left") val minutesLeft: Int = 0,
    @SerialName("rewords_left") val rewordsLeft: Int = 0,
)
