/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.cloud.DictateCloud
import dev.patrickgold.florisboard.dictate.cloud.DictateCloudApi
import dev.patrickgold.florisboard.dictate.cloud.DictateCloudDeletion
import dev.patrickgold.florisboard.dictate.cloud.DictateCloudDevice
import dev.patrickgold.florisboard.dictate.cloud.DictateCloudDeviceLimitException
import dev.patrickgold.florisboard.dictate.cloud.DictateCloudException
import dev.patrickgold.florisboard.dictate.cloud.DictateCloudPlan
import dev.patrickgold.florisboard.dictate.cloud.Entitlement
import dev.patrickgold.florisboard.dictate.cloud.EntitlementState
import dev.patrickgold.florisboard.dictate.cloud.Entitlements
import dev.patrickgold.florisboard.dictate.cloud.PaidFeature
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import java.text.DateFormat
import java.util.Date

/**
 * Dictate Cloud: the Plus subscription — what it costs, whether it is active, and getting an account
 * back onto a new device (billing spec, section 9).
 *
 * The screen is written to be readable by someone who has not decided yet. Dictate Cloud is one of
 * two equal ways to use the app, not the intended one — so the alternative is named here rather
 * than tucked away, and what the cloud path *cannot* do is stated on the same screen that sells it.
 * A user who finds that out after paying has been misled, however true the small print was. For the
 * same reason the trial length, the renewal price and how to cancel sit right next to the buttons.
 */
@Composable
fun DictateCloudScreen() = FlorisScreen {
    title = stringRes(R.string.dictate__cloud_title)
    previewFieldVisible = false
    iconSpaceReserved = false

    val prefs by FlorisPreferenceStore

    content {
        val context = LocalContext.current
        val activity = LocalActivity.current
        val navController = LocalNavController.current
        val scope = rememberCoroutineScope()

        // Where "use my own provider" leads depends on how this screen was reached: back into the
        // setup step it was opened from, or into the provider settings. Sending someone mid-setup to
        // a settings screen strands them — they then have to find their own way back and skip the
        // step they were in the middle of.
        val backToOwnProvider: () -> Unit = if (DictateCloud.openedFromSetup) {
            {
                DictateCloud.ownKeyRequested.value = true
                navController.popBackStack()
            }
        } else {
            { navController.navigate(Routes.Settings.DictateProviders) }
        }

        val accounts by prefs.dictate.providerAccounts.collectAsState()
        val activeProviderId by prefs.dictate.transcriptionProviderId.collectAsState()
        val account = accounts.getOrEmpty(ProviderRegistry.CLOUD.id)
        val isSelected = activeProviderId == ProviderRegistry.CLOUD.id
        val entitlement by Entitlements.current.collectAsState()
        val entitled = entitlement.has(PaidFeature.MANAGED_TRANSCRIPTION, System.currentTimeMillis())
        val manageUrl = DictateCloud.manageSubscriptionUrl(context.packageName)

        var shop by remember { mutableStateOf<DictateCloud.Shop?>(null) }
        var buying by remember { mutableStateOf<DictateCloudPlan?>(null) }
        var refreshing by remember { mutableStateOf(false) }
        var notice by remember { mutableStateOf<String?>(null) }
        var showRestore by remember { mutableStateOf(false) }
        var showDelete by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf(false) }
        var deletePreview by remember { mutableStateOf<DictateCloudDeletion?>(null) }

        val noticeSubscribed = stringRes(R.string.dictate__cloud_notice_subscribed)
        val noticePending = stringRes(R.string.dictate__cloud_notice_sub_pending)
        val noticeNotVerified = stringRes(R.string.dictate__cloud_notice_not_verified)
        val noticeNeedsRecovery = stringRes(R.string.dictate__cloud_notice_sub_needs_recovery)
        val noticeFailed = stringRes(R.string.dictate__cloud_notice_failed)
        val noticeDeleted = stringRes(R.string.dictate__cloud_notice_account_removed)
        val noticeGoneDeleted = stringRes(R.string.dictate__cloud_notice_account_deleted)
        val noticeGoneSignedOut = stringRes(R.string.dictate__cloud_notice_account_signed_out)
        val noticeRestoreNone = stringRes(R.string.dictate__cloud_restore_purchase_none)
        val noticeRestoreDone = stringRes(R.string.dictate__cloud_restore_purchase_done)

        // An account can end somewhere else entirely — deleted from the web page or from a second
        // phone, this device signed out from the dashboard. The refresh below clears it either way,
        // and this is what keeps that from happening in silence.
        val gone by DictateCloud.gone.collectAsState()
        LaunchedEffect(gone) {
            when (gone) {
                DictateCloud.Gone.DELETED -> notice = noticeGoneDeleted
                DictateCloud.Gone.SIGNED_OUT -> notice = noticeGoneSignedOut
                null -> return@LaunchedEffect
            }
            DictateCloud.gone.value = null
        }

        // Reporting whatever Play lists comes first: if a payment went through but never reached the
        // server, this is where it is put right, before anything is drawn that would say "Free".
        // Play's prices load alongside, not after: they do not depend on our server, and a slow or
        // unreachable server must not keep the plans behind a spinner.
        LaunchedEffect(Unit) {
            launch { shop = DictateCloud.shop(context) }
            DictateCloud.syncSubscription(context)
        }

        fun openPlay() {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(manageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        fun buy(plan: DictateCloudPlan) {
            val host = activity ?: return
            buying = plan
            scope.launch {
                notice = when (val result = DictateCloud.purchase(host, plan)) {
                    is DictateCloud.PurchaseResult.Subscribed -> noticeSubscribed
                    is DictateCloud.PurchaseResult.Pending -> noticePending
                    is DictateCloud.PurchaseResult.NeedsRecovery -> noticeNeedsRecovery
                    is DictateCloud.PurchaseResult.NotVerified -> noticeNotVerified
                    is DictateCloud.PurchaseResult.Cancelled -> null
                    is DictateCloud.PurchaseResult.Unavailable -> noticeFailed
                    is DictateCloud.PurchaseResult.Failed -> noticeFailed
                }
                buying = null
                DictateCloud.refreshEntitlement()
            }
        }

        fun restorePurchase() {
            refreshing = true
            scope.launch {
                val settled = DictateCloud.syncSubscription(context)
                refreshing = false
                notice = when {
                    settled.any { it is DictateCloud.Settlement.Verified } -> noticeRestoreDone
                    settled.any { it is DictateCloud.Settlement.NeedsRecovery } -> noticeNeedsRecovery
                    settled.any { it is DictateCloud.Settlement.Pending } -> noticePending
                    settled.any { it is DictateCloud.Settlement.Failed } -> noticeNotVerified
                    else -> noticeRestoreNone
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

            if (account.hasWallet) {
                StatusCard(
                    entitlement = entitlement,
                    entitled = entitled,
                    refreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            DictateCloud.syncSubscription(context)
                            refreshing = false
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))

                if (entitlement.needsPaymentFix) {
                    PaymentCard(onFix = ::openPlay)
                    Spacer(Modifier.height(12.dp))
                }

                if (entitled && !isSelected) {
                    ActivateCard(onActivate = { scope.launch { DictateCloud.activate() } })
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                IntroCard(onOwnProvider = backToOwnProvider)
                Spacer(Modifier.height(12.dp))
            }

            notice?.let { text ->
                NoticeCard(text = text, onDismiss = { notice = null })
                Spacer(Modifier.height(12.dp))
            }

            // The plans are the paywall: shown whenever Plus is not active. Someone on hold is not
            // shown them — their subscription still exists, and the fix is the payment, not a second
            // purchase.
            if (!entitled && !entitlement.needsPaymentFix) {
                SectionTitle(stringRes(R.string.dictate__cloud_plans_title))
                Spacer(Modifier.height(4.dp))
                BodyText(stringRes(R.string.dictate__cloud_plans_includes))
                Spacer(Modifier.height(8.dp))

                when (val state = shop) {
                    null -> Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }

                    is DictateCloud.Shop.Unavailable -> BodyText(
                        stringRes(R.string.dictate__cloud_shop_unavailable_plus),
                    )

                    is DictateCloud.Shop.Ready -> {
                        state.plans.forEach { offer ->
                            PlanCard(
                                offer = offer,
                                busy = buying == offer.plan,
                                anyBusy = buying != null,
                                onBuy = { buy(offer.plan) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = stringRes(R.string.dictate__cloud_plan_disclosure),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Stacked rather than side by side: both labels grow long in translation.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (account.hasWallet && entitlement.isPlus) {
                    OutlinedButton(onClick = ::openPlay) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringRes(R.string.dictate__cloud_manage_btn))
                    }
                }
                OutlinedButton(onClick = ::restorePurchase, enabled = !refreshing) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringRes(R.string.dictate__cloud_restore_purchase_btn))
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle(stringRes(R.string.dictate__cloud_recovery_title))
            Spacer(Modifier.height(4.dp))
            BodyText(stringRes(R.string.dictate__cloud_recovery_explain_plus))

            if (account.walletRecoveryCode.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                RecoveryCodeCard(code = account.walletRecoveryCode, context = context)
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showRestore = true }) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringRes(R.string.dictate__cloud_recovery_enter_btn))
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle(stringRes(R.string.dictate__cloud_limits_title))
            Spacer(Modifier.height(4.dp))
            BodyText(stringRes(R.string.dictate__cloud_limits_body_plus))

            Spacer(Modifier.height(16.dp))
            SectionTitle(stringRes(R.string.dictate__cloud_privacy_title))
            Spacer(Modifier.height(4.dp))
            BodyText(stringRes(R.string.dictate__cloud_privacy_body_plus))

            // Deleting sits below everything else on purpose: it is the one action here that cannot
            // be undone, and it should not share a resting place with subscribing.
            if (account.hasWallet) {
                Spacer(Modifier.height(20.dp))
                SectionTitle(stringRes(R.string.dictate__cloud_delete_title_plus))
                Spacer(Modifier.height(4.dp))
                BodyText(stringRes(R.string.dictate__cloud_delete_explain_plus))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        deleting = true
                        scope.launch {
                            deletePreview = runCatching { DictateCloud.previewDeletion() }.getOrNull()
                            deleting = false
                            if (deletePreview != null) showDelete = true else notice = noticeFailed
                        }
                    },
                    enabled = !deleting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringRes(R.string.dictate__cloud_delete_btn))
                }
            }

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(20.dp))
                DebugOverrideCard(scope = scope)
            }

            Spacer(Modifier.height(16.dp))
            OtherWayCard(onOwnProvider = backToOwnProvider)
            Spacer(Modifier.height(24.dp))
        }

        if (showRestore) {
            RestoreDialog(
                onDismiss = { showRestore = false },
                onRestore = { code, onResult ->
                    scope.launch {
                        val result = runCatching { DictateCloud.restore(code) }
                        onResult(
                            when (val error = result.exceptionOrNull()) {
                                null -> RestoreOutcome.Success
                                is DictateCloudDeviceLimitException ->
                                    RestoreOutcome.TooManyDevices(error.limit, error.devices)
                                is DictateCloudException ->
                                    if (error.code == DictateCloudApi.ErrorCode.WALLET_NOT_FOUND) {
                                        RestoreOutcome.NotFound
                                    } else {
                                        RestoreOutcome.Error
                                    }
                                else -> RestoreOutcome.Error
                            },
                        )
                    }
                },
                onSignOutDevice = { code, tokenHash, onDone ->
                    scope.launch {
                        onDone(runCatching { DictateCloud.revokeDevice(code, tokenHash) }.getOrDefault(false))
                    }
                },
            )
        }

        deletePreview?.let {
            if (showDelete) {
                DeleteAccountDialog(
                    subscriptionActive = entitlement.isPlus && entitlement.autoRenewing,
                    busy = deleting,
                    onCancelSubscription = ::openPlay,
                    onDismiss = { if (!deleting) { showDelete = false; deletePreview = null } },
                    onConfirm = {
                        deleting = true
                        scope.launch {
                            val result = runCatching { DictateCloud.deleteAccount() }
                            deleting = false
                            showDelete = false
                            deletePreview = null
                            notice = if (result.isSuccess) noticeDeleted else noticeFailed
                        }
                    },
                )
            }
        }
    }
}

/**
 * The confirmation before an account is destroyed.
 *
 * Deleting the account does not cancel a Play subscription — only Google can — so while one is
 * still renewing the dialog says so in the error colour and links to Play's page first (billing
 * spec, section 6). Someone who deletes and keeps being charged has been let down by this dialog.
 */
@Composable
private fun DeleteAccountDialog(
    subscriptionActive: Boolean,
    busy: Boolean,
    onCancelSubscription: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringRes(R.string.dictate__cloud_delete_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringRes(R.string.dictate__cloud_delete_dialog_body_plus),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (subscriptionActive) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringRes(R.string.dictate__cloud_delete_dialog_sub_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onCancelSubscription) {
                        Text(stringRes(R.string.dictate__cloud_delete_dialog_cancel_sub_btn))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringRes(R.string.dictate__cloud_delete_dialog_records_plus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(
                    text = stringRes(R.string.dictate__cloud_delete_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringRes(R.string.dictate__cloud_delete_dialog_cancel))
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The plan, as the first thing on the screen once there is an account: Plus or Free, what state the
 * subscription is in and until when, and how much of today's allowance is used.
 */
@Composable
private fun StatusCard(
    entitlement: Entitlement,
    entitled: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val date = dateFormat.format(Date(entitlement.expiresAt))
    val stateLine = when (entitlement.state) {
        EntitlementState.TRIAL -> stringRes(R.string.dictate__cloud_state_trial, "date" to date)
        EntitlementState.ACTIVE -> stringRes(R.string.dictate__cloud_state_active, "date" to date)
        EntitlementState.CANCELED_ACTIVE -> stringRes(R.string.dictate__cloud_state_canceled, "date" to date)
        EntitlementState.GRACE -> stringRes(R.string.dictate__cloud_state_grace, "date" to date)
        EntitlementState.ON_HOLD -> stringRes(R.string.dictate__cloud_state_on_hold)
        EntitlementState.EXPIRED -> stringRes(R.string.dictate__cloud_state_expired)
        EntitlementState.PENDING -> stringRes(R.string.dictate__cloud_state_pending)
        else -> stringRes(R.string.dictate__cloud_state_none)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entitled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringRes(R.string.dictate__cloud_plus_status_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringRes(
                        if (entitlement.isPlus) R.string.dictate__cloud_plus_name
                        else R.string.dictate__cloud_free_name,
                    ),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (entitlement.isKnown) {
                    Text(text = stateLine, style = MaterialTheme.typography.bodyMedium)
                }
                val daily = entitlement.daily
                if (entitled && daily != null && daily.allowanceSeconds > 0) {
                    Text(
                        text = stringRes(
                            R.string.dictate__cloud_daily_usage,
                            "used" to (daily.usedSeconds / 60).toString(),
                            "total" to (daily.allowanceSeconds / 60).toString(),
                            "time" to timeFormat.format(Date(daily.resetsAt)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (entitlement.isKnown) {
                    Text(
                        text = stringRes(
                            R.string.dictate__cloud_checked,
                            "time" to DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(entitlement.fetchedAt)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringRes(R.string.dictate__cloud_refresh))
                }
            }
        }
    }
}

/** Grace period or account hold: Google could not charge, and only Play's settings can fix that. */
@Composable
private fun PaymentCard(onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringRes(R.string.dictate__cloud_payment_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.dictate__cloud_payment_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onFix) {
                Text(stringRes(R.string.dictate__cloud_open_play_btn))
            }
        }
    }
}

/**
 * Debug builds only: pretend the server said Plus or Free, to walk through the paywall states
 * without a license tester (billing spec, section 10). Never compiled into a decision in release:
 * [Entitlements] ignores the setting there, and this card is not drawn.
 */
@Composable
private fun DebugOverrideCard(scope: CoroutineScope) {
    var current by remember { mutableStateOf(Entitlements.debugOverride()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Developer override (debug builds only)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "Server", Entitlement.PLAN_PLUS to "Force Plus", Entitlement.PLAN_FREE to "Force free")
                    .forEach { (value, label) ->
                        FilterChip(
                            selected = current == value,
                            onClick = {
                                current = value
                                scope.launch { Entitlements.setDebugOverride(value) }
                            },
                            label = { Text(label) },
                        )
                    }
            }
        }
    }
}

/**
 * Shown before there is an account: what Dictate Cloud is, and — deliberately in the same card —
 * that an own API key remains a full alternative.
 */
@Composable
private fun IntroCard(onOwnProvider: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = providerIcon(ProviderRegistry.CLOUD.id),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringRes(R.string.dictate__cloud_intro_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            BodyText(stringRes(R.string.dictate__cloud_plus_intro_body))
            Spacer(Modifier.height(8.dp))
            BodyText(stringRes(R.string.dictate__cloud_intro_alternative))
            TextButton(onClick = onOwnProvider) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringRes(R.string.dictate__cloud_use_own_provider_btn))
            }
        }
    }
}

/** Plus is active but dictation still runs through another provider — offer to switch, do not switch. */
@Composable
private fun ActivateCard(onActivate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.dictate__cloud_activate_plus_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.dictate__cloud_activate_plus_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onActivate) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringRes(R.string.dictate__cloud_activate_btn))
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.dictate__cloud_notice_dismiss))
            }
        }
    }
}

/**
 * One plan on the paywall: the recurring price from Play, the trial if this buyer gets one, and on
 * the yearly plan how much cheaper a month comes out. The button says what tapping it starts — a
 * trial or a paid subscription — because "Subscribe" on a free trial undersells it and "Start free
 * trial" on a paid one misleads.
 */
@Composable
private fun PlanCard(
    offer: DictateCloud.PlanOffer,
    busy: Boolean,
    anyBusy: Boolean,
    onBuy: () -> Unit,
) {
    val yearly = offer.plan == DictateCloudPlan.YEARLY
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringRes(
                            if (yearly) R.string.dictate__cloud_plan_yearly else R.string.dictate__cloud_plan_monthly,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    offer.savingsPercent?.let {
                        Spacer(Modifier.width(8.dp))
                        SavingsPill(percent = it)
                    }
                }
                Text(
                    text = stringRes(
                        if (yearly) R.string.dictate__cloud_plan_price_yearly
                        else R.string.dictate__cloud_plan_price_monthly,
                        "price" to offer.formattedPrice,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                offer.trialDays?.let { days ->
                    Text(
                        text = stringRes(R.string.dictate__cloud_plan_trial, "days" to days.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = onBuy, enabled = !anyBusy) {
                    Text(
                        stringRes(
                            if (offer.trialDays != null) R.string.dictate__cloud_plan_btn_trial
                            else R.string.dictate__cloud_plan_btn_subscribe,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * "37 % cheaper" — how much less a month costs on the yearly plan than on the monthly one.
 *
 * Same shape as the category pill in the prompt library, so the two read as the same kind of
 * marking rather than as two designers passing through.
 */
@Composable
private fun SavingsPill(percent: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            text = stringRes(
                R.string.dictate__cloud_plan_savings,
                "percent" to percent.toString(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun RecoveryCodeCard(code: String, context: Context) {
    val copied = stringRes(R.string.dictate__cloud_recovery_copied)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("Dictate Cloud", code))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringRes(R.string.dictate__cloud_recovery_copy),
                )
            }
        }
    }
}

/** The closing reminder that the other path is still open, phrased as information rather than a warning. */
@Composable
private fun OtherWayCard(onOwnProvider: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringRes(R.string.dictate__cloud_other_way_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            BodyText(stringRes(R.string.dictate__cloud_other_way_body_plus))
            TextButton(onClick = onOwnProvider) {
                Text(stringRes(R.string.dictate__cloud_use_own_provider_btn))
            }
        }
    }
}

/**
 * One signed-in device, with the button that makes room for this one.
 *
 * The last-used time is what makes the choice possible: without it every row reads the same, and a
 * device name alone ("Phone · Android 13") does not say which phone is the one in your hand.
 */
@Composable
private fun DeviceRow(
    device: DictateCloudDevice,
    enabled: Boolean,
    onSignOut: () -> Unit,
) {
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.label?.takeIf { it.isNotBlank() }
                    ?: stringRes(R.string.dictate__cloud_devices_unnamed),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = device.lastSeenAt?.takeIf { it > 0 }?.let {
                    stringRes(R.string.dictate__cloud_devices_last_used, "time" to format.format(Date(it)))
                } ?: stringRes(R.string.dictate__cloud_devices_never_used),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(enabled = enabled, onClick = onSignOut) {
            Text(stringRes(R.string.dictate__cloud_devices_sign_out))
        }
    }
}

/**
 * How a restore ended.
 *
 * [TooManyDevices] is not a failure but a fork, which is why it carries the devices: the account
 * has all the sign-ins it may have, and the way on is to pick one and sign it out. Answering with a
 * bare error would strand exactly the case the limit has to keep serving — a new phone taking over
 * from one that is lost, broken or wiped, and therefore cannot sign itself out.
 */
private sealed interface RestoreOutcome {
    data object Success : RestoreOutcome
    data object NotFound : RestoreOutcome
    data object Error : RestoreOutcome
    data class TooManyDevices(val limit: Int, val devices: List<DictateCloudDevice>) : RestoreOutcome
}

@Composable
private fun RestoreDialog(
    onDismiss: () -> Unit,
    onRestore: (String, (RestoreOutcome) -> Unit) -> Unit,
    onSignOutDevice: (String, String, (Boolean) -> Unit) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Non-null once the account turned out to be full. The dialog then stops asking for a code —
    // it has one that works — and asks which device should make room instead.
    var full by remember { mutableStateOf<RestoreOutcome.TooManyDevices?>(null) }

    val notFound = stringRes(R.string.dictate__cloud_recovery_not_found_plus)
    val failed = stringRes(R.string.dictate__cloud_recovery_error)

    fun attempt() {
        busy = true
        onRestore(code) { outcome ->
            busy = false
            when (outcome) {
                RestoreOutcome.Success -> onDismiss()
                RestoreOutcome.NotFound -> error = notFound
                RestoreOutcome.Error -> error = failed
                is RestoreOutcome.TooManyDevices -> { full = outcome; error = null }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                stringRes(
                    if (full != null) R.string.dictate__cloud_devices_full_title
                    else R.string.dictate__cloud_recovery_enter_btn
                ),
            )
        },
        text = {
            val limit = full
            Column {
                if (limit == null) {
                    BodyText(stringRes(R.string.dictate__cloud_recovery_dialog_body_plus))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = code,
                        onValueChange = { code = it; error = null },
                        singleLine = true,
                        enabled = !busy,
                        isError = error != null,
                        label = { Text(stringRes(R.string.dictate__cloud_recovery_field)) },
                    )
                } else {
                    BodyText(
                        stringRes(
                            R.string.dictate__cloud_devices_full_body_plus,
                            "count" to limit.devices.size.toString(),
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    limit.devices.forEach { device ->
                        DeviceRow(
                            device = device,
                            enabled = !busy,
                            onSignOut = {
                                busy = true
                                onSignOutDevice(code, device.tokenHash) { ok ->
                                    if (ok) {
                                        full = null
                                        attempt()
                                    } else {
                                        busy = false
                                        error = failed
                                    }
                                }
                            },
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            // Nothing to confirm once the list is showing: each row is its own action, and a
            // second "OK" would be a button that does not know which device was meant.
            if (full == null) {
                TextButton(
                    enabled = !busy && code.isNotBlank(),
                    onClick = { attempt() },
                ) {
                    Text(stringRes(R.string.dictate__cloud_recovery_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringRes(android.R.string.cancel))
            }
        },
    )
}
