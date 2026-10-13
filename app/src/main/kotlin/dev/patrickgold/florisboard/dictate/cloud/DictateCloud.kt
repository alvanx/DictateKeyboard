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

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Dictate Cloud, from the app's side: subscribing to Plus, reporting subscriptions to the server, and
 * keeping the entitlement somewhere the settings and the keyboard can read it (billing spec, section 9).
 *
 * The order of operations:
 *
 *  1. Play takes the money and hands back a purchase token.
 *  2. The server is asked to verify it. It checks with Google, acknowledges it and attaches it to an
 *     account, returning a device token the first time.
 *  3. The entitlement the server reports is cached in [Entitlements].
 *
 * Nothing is consumed — a subscription stays in Play's list for as long as it runs, and that list is
 * both the safety net and the restore path. [syncSubscription] sends whatever Play lists to the
 * server; verification is idempotent there, so it can run at every start.
 */
object DictateCloud {

    private val prefs by FlorisPreferenceStore

    /**
     * True while the subscription screen was opened from the setup flow rather than from settings.
     *
     * The screen offers a way back to "use my own provider", and where that leads has to differ:
     * during setup it belongs to the step the user is standing in, afterwards it is the provider
     * settings. A flag rather than a route argument because the route is also a deep link, and a
     * deep link carrying an onboarding flag would be a way to reach a half-state from outside.
     */
    var openedFromSetup: Boolean = false

    /**
     * Raised when that way back is taken, so the setup step can switch to its own-key branch as the
     * screen pops. Consumed by the reader.
     */
    val ownKeyRequested = MutableStateFlow(false)

    /** Why an account stopped being usable on this device. */
    enum class Gone {
        /**
         * The account itself no longer exists — deleted from the web page, from another device, or
         * by an administrator. Nothing kept here is worth anything any more, the recovery code
         * least of all: it would look like a way back to something that is not there.
         */
        DELETED,

        /**
         * Only *this* device was signed out; the account is alive. The recovery code is how the
         * user gets back in, so it is the one thing that must survive.
         */
        SIGNED_OUT,
    }

    /**
     * Raised when a refresh found the account gone, so the screen can say what happened rather than
     * silently losing a subscription the user was looking at. Consumed by the reader.
     */
    val gone = MutableStateFlow<Gone?>(null)

    /** What reporting a single subscription to the server turned into. */
    sealed interface Settlement {
        /** The server verified it and this device holds the account's token. */
        data class Verified(val entitlement: Entitlement?) : Settlement

        /** Paid for but not finalised by Google yet. Left alone; it will be picked up later. */
        data object Pending : Settlement

        /**
         * The server knows the subscription but gave this device no token, and it has none. Only the
         * recovery code reaches that account from here.
         */
        data object NeedsRecovery : Settlement

        /** Not our product. */
        data object Skipped : Settlement

        data class Failed(val error: DictateCloudException) : Settlement
    }

    sealed interface PurchaseResult {
        data class Subscribed(val entitlement: Entitlement?) : PurchaseResult
        data object Cancelled : PurchaseResult
        data object Pending : PurchaseResult
        data object NeedsRecovery : PurchaseResult

        /** Billing itself is not usable here — no Play install, sideloaded build, no store account. */
        data class Unavailable(val code: Int) : PurchaseResult

        /** Paid, but the server could not confirm it yet. Play keeps it; the next sync retries. */
        data class NotVerified(val error: DictateCloudException) : PurchaseResult

        data class Failed(val code: Int, val message: String) : PurchaseResult
    }

    /** One plan as Play offers it here — localised price and trial, ready to show. */
    data class PlanOffer(
        val plan: DictateCloudPlan,
        /** The recurring price, already in the buyer's own currency and formatting, from Play. */
        val formattedPrice: String,
        /** The same price in micros of [priceCurrency], for comparing the plans. Never displayed. */
        val priceMicros: Long = 0L,
        val priceCurrency: String = "",
        /** Length of the free trial on the chosen offer, or null when there is none to offer. */
        val trialDays: Int? = null,
        /** What [DictateCloudBilling.launch] needs to buy exactly this. */
        val offerToken: String = "",
        /** On the yearly plan: how much cheaper a month is than on monthly. See [yearlySavingsPercent]. */
        val savingsPercent: Int? = null,
    )

    sealed interface Shop {
        data class Ready(val plans: List<PlanOffer>) : Shop

        /**
         * Billing cannot be used here. Nearly always benign and worth saying plainly: the app was
         * installed as an APK rather than from Play, or there is no store account on the device.
         */
        data class Unavailable(val code: Int) : Shop
    }

    /**
     * Play's subscription page for Plus — cancel, change payment method, fix a failed payment.
     * Play has no separate link for payment problems; this page shows them first.
     */
    fun manageSubscriptionUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions" +
            "?sku=${DictateCloudPlan.PRODUCT_ID}&package=$packageName"

    /** This device's Dictate Cloud account, or an empty record when there is none yet. */
    fun account(): ProviderAccount =
        prefs.dictate.providerAccounts.get().getOrEmpty(ProviderRegistry.CLOUD.id)

    /** True once this device holds an account token. Says nothing about whether Plus is active. */
    fun isActive(): Boolean = account().hasWallet

    /** True when Dictate Cloud is the provider dictation and rewording currently run through. */
    fun isSelected(): Boolean =
        prefs.dictate.transcriptionProviderId.get() == ProviderRegistry.CLOUD.id

    /**
     * Makes Dictate Cloud the active provider for both dictation and rewording.
     *
     * Never done as a side effect of a purchase. Someone may subscribe while happily using their
     * own key — for a spare, or for the phone where the key is not set up — and silently
     * redirecting their dictation would be a decision made on their behalf.
     */
    suspend fun activate() {
        prefs.dictate.transcriptionProviderId.set(ProviderRegistry.CLOUD.id)
        prefs.dictate.rewordingProviderId.set(ProviderRegistry.CLOUD.id)
    }

    /**
     * Switches over after a purchase, but only when nothing else was set up.
     *
     * The rule above — never activate as a side effect — protects someone who already has a working
     * key. It must not be applied to someone who has none: they chose Dictate Cloud during setup,
     * subscribed, and would otherwise be left in front of a keyboard that refuses to use it.
     *
     * A keyless endpoint (Ollama, on-device) counts as set up and is left alone — same judgement the
     * onboarding makes when deciding whether the provider step is done.
     */
    private suspend fun activateIfNothingElseConfigured() {
        val currentId = prefs.dictate.transcriptionProviderId.get()
        if (currentId == ProviderRegistry.CLOUD.id) return
        if (prefs.dictate.providerAccounts.get().getOrEmpty(currentId).hasKey) return
        val preset = ProviderRegistry.byId(currentId)
        if (preset != null && preset.apiKeyUrl == null) return
        activate()
    }

    /** True when the subscribe button should pick the referral trial (see [pickOffer]). */
    private fun hasReferral(): Boolean = prefs.dictate.cloudReferralCode.get().isNotBlank()

    /** What Play has on offer, with local prices and the trial this buyer would get. */
    suspend fun shop(context: Context): Shop {
        val billing = DictateCloudBilling(context)
        try {
            val connection = billing.connect()
            if (connection != BillingResponseCode.OK) return Shop.Unavailable(connection)
            val products = billing.products()
            val details = products.plus ?: return Shop.Unavailable(products.code)
            val options = DictateCloudBilling.optionsOf(details)
            val referral = hasReferral()
            // Ordered by the plan list, not by what Play happened to return. A plan Play does not
            // offer here is left out rather than shown as a button that would fail.
            val plans = DictateCloudPlan.ordered.mapNotNull { plan ->
                val option = pickOffer(options, plan, referral) ?: return@mapNotNull null
                val offer = DictateCloudBilling.offerDetailsOf(details, option) ?: return@mapNotNull null
                val phases = offer.pricingPhases.pricingPhaseList
                // The recurring price is the last phase; a trial, if any, is a free phase before it.
                val recurring = phases.lastOrNull() ?: return@mapNotNull null
                val trial = phases.firstOrNull { it.priceAmountMicros == 0L && it !== recurring }
                PlanOffer(
                    plan = plan,
                    formattedPrice = recurring.formattedPrice,
                    priceMicros = recurring.priceAmountMicros,
                    priceCurrency = recurring.priceCurrencyCode,
                    trialDays = trial?.let { periodDays(it.billingPeriod) },
                    offerToken = option.offerToken,
                )
            }
            if (plans.isEmpty()) return Shop.Unavailable(products.code)
            val monthly = plans.firstOrNull { it.plan == DictateCloudPlan.MONTHLY }
            return Shop.Ready(
                plans.map { offer ->
                    if (offer.plan != DictateCloudPlan.YEARLY || monthly == null) return@map offer
                    offer.copy(
                        savingsPercent = yearlySavingsPercent(
                            monthlyMicros = monthly.priceMicros,
                            monthlyCurrency = monthly.priceCurrency,
                            yearlyMicros = offer.priceMicros,
                            yearlyCurrency = offer.priceCurrency,
                        ),
                    )
                },
            )
        } finally {
            billing.close()
        }
    }

    /**
     * Subscribes to [plan]: opens Play's sheet, waits for the answer and reports whatever comes back
     * to the server.
     *
     * [activity] must be a real, visible activity — Play draws its sheet on top of it.
     */
    suspend fun purchase(activity: Activity, plan: DictateCloudPlan): PurchaseResult {
        val billing = DictateCloudBilling(activity)
        try {
            val connection = billing.connect()
            if (connection != BillingResponseCode.OK) return PurchaseResult.Unavailable(connection)

            val products = billing.products()
            val details = products.plus ?: return PurchaseResult.Unavailable(products.code)
            // Picked again here rather than taken from the screen: eligibility can change between
            // showing the card and tapping it (a trial used up on another device, say).
            val option = pickOffer(DictateCloudBilling.optionsOf(details), plan, hasReferral())
                ?: return PurchaseResult.Unavailable(BillingResponseCode.ITEM_UNAVAILABLE)

            val launched = withContext(Dispatchers.Main) {
                billing.launch(activity, details, option.offerToken, account().walletId)
            }
            if (launched != BillingResponseCode.OK) {
                return PurchaseResult.Failed(launched, "launchBillingFlow returned $launched")
            }

            return when (val outcome = billing.awaitOutcome()) {
                is DictateCloudBilling.Outcome.Cancelled -> PurchaseResult.Cancelled
                is DictateCloudBilling.Outcome.Failed ->
                    PurchaseResult.Failed(outcome.code, outcome.message)
                is DictateCloudBilling.Outcome.Purchased -> {
                    val mine = outcome.purchases.filter { DictateCloudPlan.PRODUCT_ID in it.products }
                    val settled = mine.map { settle(it) }
                    settled.filterIsInstance<Settlement.Verified>().lastOrNull()
                        ?.let { return PurchaseResult.Subscribed(it.entitlement) }
                    when (val first = settled.firstOrNull()) {
                        is Settlement.Pending -> PurchaseResult.Pending
                        is Settlement.NeedsRecovery -> PurchaseResult.NeedsRecovery
                        is Settlement.Failed -> PurchaseResult.NotVerified(first.error)
                        else -> PurchaseResult.Failed(
                            BillingResponseCode.ERROR,
                            "No purchase for ${DictateCloudPlan.PRODUCT_ID} in the result",
                        )
                    }
                }
            }
        } finally {
            billing.close()
        }
    }

    /**
     * Sends every subscription Play lists to the server, then refreshes the entitlement.
     *
     * Run at app start, on opening the subscription screen and from its "Restore" button. Free
     * users with their own key never reach the server from here (rule 3): with no subscription in
     * Play and no account on the device, this ends after asking Play.
     *
     * Returns how many subscriptions the server verified on this run.
     */
    suspend fun syncSubscription(context: Context): Int {
        var verified = 0
        val billing = DictateCloudBilling(context)
        try {
            if (billing.connect() == BillingResponseCode.OK) {
                for (purchase in billing.purchases()) {
                    when (val settlement = settle(purchase)) {
                        is Settlement.Verified -> verified++
                        is Settlement.Failed -> flogError {
                            "Dictate Cloud: subscription not verified (${settlement.error.code}): ${settlement.error.message}"
                        }
                        else -> Unit
                    }
                }
            }
        } finally {
            billing.close()
        }
        if (verified > 0) flogInfo { "Dictate Cloud: verified $verified subscription(s)" }
        // Verify already returned an entitlement, but a separate read also covers an account whose
        // subscription lives on another Play account, and one that has just expired.
        refreshEntitlement()
        return verified
    }

    /**
     * Fetches the entitlement from the server and caches it; null when there is no account or no answer.
     *
     * Also the moment this device learns that its account has ended. An account can be deleted from
     * the web page or from a second phone, and a device can be signed out from the dashboard —
     * neither of which sends anything here. A refused token is therefore acted on rather than logged.
     *
     * Only an outright 401 counts. A blocked account answers 403, an unreachable server 0: both are
     * states to wait out. The cache is kept then, so a paying user is not shown a paywall because
     * our server is down (rule 6).
     */
    suspend fun refreshEntitlement(): Entitlement? {
        val current = account()
        if (!current.hasWallet) return null
        return try {
            DictateCloudApi.entitlement(current.apiKey).also { Entitlements.store(it) }
        } catch (e: DictateCloudException) {
            if (e.status == 401) {
                forget(
                    reason = if (e.code == DictateCloudApi.ErrorCode.DEVICE_REVOKED) {
                        Gone.SIGNED_OUT
                    } else {
                        Gone.DELETED
                    },
                    announce = true,
                )
            } else {
                flogError { "Dictate Cloud: entitlement unavailable (${e.code}): ${e.message}" }
            }
            null
        }
    }

    /**
     * Clears the account from this device.
     *
     * [reason] decides what survives. A deleted account leaves nothing behind — keeping the recovery
     * code would put a way back on screen that leads nowhere. A signed-out device keeps its code,
     * because the account is still there and that code is the only way onto it.
     *
     * [announce] is false when the user did this themselves and has already been told.
     */
    private suspend fun forget(reason: Gone, announce: Boolean) {
        edit {
            it.copy(
                walletId = "",
                apiKey = "",
                walletRecoveryCode = if (reason == Gone.SIGNED_OUT) it.walletRecoveryCode else "",
                balanceSeconds = -1,
                balanceRewords = -1,
                balanceCheckedAt = 0L,
            )
        }
        Entitlements.clear()
        prefs.dictate.cloudLowCreditNudged.set(false)
        if (announce) gone.value = reason
    }

    /**
     * Claims an existing account with its recovery code and stores it on this device.
     *
     * Throws [DictateCloudException] so the caller can tell "wrong code" apart from "no connection";
     * both look identical to a user staring at a spinner, and they need different advice.
     */
    suspend fun restore(code: String): DictateCloudRestore {
        val restored = DictateCloudApi.restore(code, label = DictateCloudApi.deviceLabel())
        edit {
            it.copy(
                walletId = restored.walletId,
                apiKey = restored.token,
                // The code the user just typed is the one that works — worth keeping so the settings
                // can show it again rather than asking them to have kept the paper.
                walletRecoveryCode = code.trim(),
            )
        }
        // Whoever types their code during setup expects to carry on from there, not to go back a
        // screen and skip the step they just completed.
        activateIfNothingElseConfigured()
        refreshEntitlement()
        return restored
    }

    /**
     * Signs one device out of an account, using its recovery code as the warrant.
     *
     * Only reached from the restore dialog when the device limit refused: the slot has to be freed
     * from *this* device, because the one being removed is usually the phone that is gone.
     */
    suspend fun revokeDevice(code: String, tokenHash: String): Boolean =
        DictateCloudApi.revokeDevice(code.trim(), tokenHash)

    /**
     * Reports one subscription to the server and stores what came back.
     *
     * A pending payment is not reported: Google has not taken the money yet, and the server would
     * refuse it anyway. It stays in Play's list and is picked up by a later sync.
     */
    private suspend fun settle(purchase: Purchase): Settlement {
        if (DictateCloudPlan.PRODUCT_ID !in purchase.products) return Settlement.Skipped
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return Settlement.Pending

        val current = account()
        val verified = try {
            DictateCloudApi.verifySubscription(
                purchaseToken = purchase.purchaseToken,
                walletId = current.walletId.takeIf { it.isNotBlank() },
                label = DictateCloudApi.deviceLabel(),
                token = current.apiKey.takeIf { current.hasWallet },
            )
        } catch (e: DictateCloudException) {
            return Settlement.Failed(e)
        }

        if (!store(verified)) return Settlement.NeedsRecovery
        verified.entitlement?.let { Entitlements.store(it) }
        activateIfNothingElseConfigured()
        return Settlement.Verified(verified.entitlement)
    }

    /**
     * Persists the account a verification returned. False when the reply carried no usable token and
     * this device has none either — only the recovery code reaches that account.
     *
     * The recovery code is sent exactly once, when the account is created, and the server keeps
     * only a hash of it. This write is the single moment it can be captured.
     */
    private suspend fun store(verified: DictateCloudVerify): Boolean {
        val current = account()
        // A token from the reply always wins; the device's own only counts if it is for this account.
        val token = verified.token?.takeIf { it.isNotBlank() }
            ?: current.apiKey.takeIf { current.walletId == verified.walletId }.orEmpty()
        if (token.isBlank()) return false
        edit {
            it.copy(
                walletId = verified.walletId,
                apiKey = token,
                // A code kept from a signed-out session (empty wallet id) is most likely this very
                // account's, and it is the user's only copy on screen; one from another account is not.
                walletRecoveryCode = verified.recoveryCode?.takeIf { code -> code.isNotBlank() }
                    ?: it.walletRecoveryCode.takeIf { _ ->
                        it.walletId.isBlank() || it.walletId == verified.walletId
                    }.orEmpty(),
            )
        }
        return true
    }

    /** Asks the server what deleting this account would affect, without deleting anything. */
    suspend fun previewDeletion(): DictateCloudDeletion? {
        val current = account()
        if (!current.hasWallet) return null
        return DictateCloudApi.previewDeletion(current.apiKey)
    }

    /**
     * Deletes the account on the server and wipes every trace of it from this device.
     *
     * The server goes first: if it refuses, the exception propagates and the local account stays,
     * so the user is not left holding a phone that has forgotten an account which still exists.
     *
     * This does not cancel a Play subscription — the dialog says so and links to Play first.
     */
    suspend fun deleteAccount(): DictateCloudDeletion {
        val current = account()
        val result = DictateCloudApi.deleteAccount(current.apiKey)
        // Not announced: the user is standing in front of the dialog that did it.
        forget(reason = Gone.DELETED, announce = false)
        return result
    }

    private suspend fun edit(block: (ProviderAccount) -> ProviderAccount) {
        val accounts = prefs.dictate.providerAccounts.get()
        prefs.dictate.providerAccounts.set(accounts.edit(ProviderRegistry.CLOUD.id, block))
    }
}
