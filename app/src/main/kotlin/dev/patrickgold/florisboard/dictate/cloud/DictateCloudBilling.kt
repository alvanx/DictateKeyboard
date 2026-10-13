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
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A coroutine-shaped wrapper around Google Play Billing, kept deliberately thin: it connects, lists
 * the Plus subscription, starts a purchase and reports what came back. It knows nothing about
 * entitlement — what a purchase unlocks is decided by the server, and the two never meet in this file.
 *
 * Nothing is acknowledged or consumed here. Subscriptions are acknowledged by the server when it
 * verifies the token (billing spec, section 6), so a purchase the app never managed to report is
 * still visible to [purchases] and gets reported on the next run.
 *
 * One instance covers one errand and is then closed. Billing connections are cheap next to how
 * rarely this runs (a purchase, or one sweep at startup), and a short-lived client removes a whole
 * class of questions about stale state and reconnection.
 *
 * Requires Billing 8 or newer, which Play makes mandatory for uploads from 31.08.2026. Two things
 * changed there that shape the code below: purchase results arrive through [QueryProductDetailsResult]
 * rather than a bare list, and nothing can be bought without an **offer token** — for a subscription
 * that is the token of a base plan or of an offer on it (see [optionsOf] and [pickOffer]).
 */
class DictateCloudBilling(context: Context) {

    /** What a purchase attempt turned into. */
    sealed interface Outcome {
        data class Purchased(val purchases: List<Purchase>) : Outcome
        data object Cancelled : Outcome
        data class Failed(val code: Int, val message: String) : Outcome
    }

    /**
     * Buffered rather than a [kotlinx.coroutines.flow.SharedFlow]: Play may report the result before
     * the caller gets around to waiting for it, and a channel holds it instead of dropping it for
     * want of a subscriber. Losing that message would mean a paid-for purchase nobody redeems.
     */
    private val outcomes = Channel<Outcome>(Channel.BUFFERED)

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            outcomes.trySend(
                when (result.responseCode) {
                    BillingResponseCode.OK -> Outcome.Purchased(purchases.orEmpty())
                    BillingResponseCode.USER_CANCELED -> Outcome.Cancelled
                    else -> Outcome.Failed(result.responseCode, result.debugMessage)
                },
            )
        }
        .enableAutoServiceReconnection()
        // Required since Billing 6. A subscription can be paid for in ways that do not complete at
        // once (cash at a counter, for one); such a purchase arrives PENDING and is not reported to
        // the server until it settles — see how the caller treats the state.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Connects, returning a [BillingResponseCode]. [BillingResponseCode.OK] means ready. */
    suspend fun connect(): Int = suspendCancellableCoroutine { cont ->
        if (client.isReady) {
            cont.resume(BillingResponseCode.OK)
            return@suspendCancellableCoroutine
        }
        var settled = false
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (!settled) {
                    settled = true
                    cont.resume(result.responseCode)
                }
            }

            override fun onBillingServiceDisconnected() {
                // Only interesting while still waiting for the first answer; afterwards the client
                // reconnects on its own (see enableAutoServiceReconnection above).
                if (!settled) {
                    settled = true
                    cont.resume(BillingResponseCode.SERVICE_DISCONNECTED)
                }
            }
        })
    }

    /** The Plus subscription as Play knows it — localised name, base plans, offers and prices. */
    suspend fun products(): Products = suspendCancellableCoroutine { cont ->
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(DictateCloudPlan.PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            cont.resume(Products(result.responseCode, details.productDetailsList.orEmpty()))
        }
    }

    data class Products(val code: Int, val details: List<ProductDetails>) {
        /** The Plus subscription, or null when Play did not return it. */
        val plus: ProductDetails?
            get() = details.firstOrNull { it.productId == DictateCloudPlan.PRODUCT_ID }
    }

    /**
     * Subscriptions Play considers current on this Play account — active, in grace, or not yet
     * acknowledged.
     *
     * This is the safety net of the whole flow and also the restore path: a new phone or a
     * reinstall finds the subscription here, and sending its token to the server brings the
     * account back without the recovery code (billing spec, section 11).
     */
    suspend fun purchases(): List<Purchase> = suspendCancellableCoroutine { cont ->
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            cont.resume(if (result.responseCode == BillingResponseCode.OK) purchases else emptyList())
        }
    }

    /**
     * Opens Play's purchase sheet. **Must be called on the main thread.** The result does not arrive
     * here but through [awaitOutcome]; this only says whether the sheet could be opened.
     *
     * [walletId] travels along as Play's `obfuscatedAccountId` and comes back to the server on the
     * order. The server matches on it when someone resubscribes after their plan expired, so the
     * new token lands on the same account (billing spec, section 11). An empty [walletId] attaches
     * nothing: a first purchase creates its account on the server instead.
     */
    fun launch(
        activity: Activity,
        details: ProductDetails,
        offerToken: String,
        walletId: String,
    ): Int {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(product))
            .apply { if (walletId.isNotBlank()) setObfuscatedAccountId(walletId) }
            .build()
        return client.launchBillingFlow(activity, flow).responseCode
    }

    /** Suspends until Play reports how the purchase went. */
    suspend fun awaitOutcome(): Outcome = outcomes.receive()

    fun close() {
        outcomes.close()
        client.endConnection()
    }

    companion object {
        /** Every base plan and offer Play lists for [details], in the shape [pickOffer] reads. */
        fun optionsOf(details: ProductDetails): List<DictateCloudPlanOption> =
            details.subscriptionOfferDetails.orEmpty().map {
                DictateCloudPlanOption(
                    basePlanId = it.basePlanId,
                    offerId = it.offerId,
                    offerToken = it.offerToken,
                )
            }

        /** Play's full record for the option [pickOffer] chose, to read its prices from. */
        fun offerDetailsOf(
            details: ProductDetails,
            option: DictateCloudPlanOption,
        ): ProductDetails.SubscriptionOfferDetails? =
            details.subscriptionOfferDetails?.firstOrNull { it.offerToken == option.offerToken }
    }
}
