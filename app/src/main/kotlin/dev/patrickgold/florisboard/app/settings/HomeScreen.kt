/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState as collectFlowAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.PreferenceRecovery
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.dictate.copyToClipboard
import dev.patrickgold.florisboard.dictate.ui.MovingMicCharm
import dev.patrickgold.florisboard.app.settings.style.charmLabel
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateMicCharm
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import dev.patrickgold.florisboard.dictate.data.stats.DictateStats
import dev.patrickgold.florisboard.dictate.importer.TranscribeShareActivity
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import androidx.compose.material3.IconButton
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import java.text.NumberFormat
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

/** How many transcripts the home screen previews before "See all". */
private const val RECENT_COUNT = 3

/** How long a failed dictation keeps its place on the home screen. */
private const val RETRY_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/**
 * The Home tab, built around what people come back for: the mic button and their recent dictations.
 * The other tabs hold the rest — Style the button's look, Dictionary the word lists, Settings everything
 * else — so this page stays short. Transcribing a file is the one action with a button of its own.
 * Until the first dictation it shows [FirstRunHome] instead.
 */
@Composable
fun HomeScreen() = FlorisScreen {
    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current
    val context = LocalContext.current

    // Null until loaded, so the first-run page never flashes up for somebody who has dictated.
    val entries: List<DictateHistoryEntry>? by remember { DictateHistoryStore.flow(context) }
        .collectFlowAsState(initial = null)
    val statDictations by prefs.dictate.statsDictations.collectAsState()
    // Before the first dictation Home is a short "two steps and you're talking" page with a heading of
    // its own (HomeFirstRun.kt), so the app bar stays empty then.
    val firstRun = isFirstRunHome(entries, statDictations)

    title = if (firstRun) "" else stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    // The keyboard test field lives with the settings: the keyboard is the secondary way in.
    previewFieldVisible = false

    // "Transcribe a file" (issue #301): the page's one action, so it gets the floating button.
    floatingActionButton {
        val transcribePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) context.startActivity(TranscribeShareActivity.intentFor(context, uri))
        }
        ExtendedFloatingActionButton(
            onClick = { transcribePicker.launch(TranscribeShareActivity.MIME_TYPES) },
            icon = { Icon(Icons.Default.AudioFile, contentDescription = null) },
            text = { Text(stringRes(R.string.home__transcribe_file)) },
        )
    }

    content {
        // "Try it here" on the first-run page (HomeFirstRun.kt). Set up out here, where it outlives that page.
        var tryItNote by rememberSaveable { mutableStateOf<String?>(null) }
        val tryIt = rememberTryItHere(onNote = { tryItNote = it })

        // The floating button is the main way in and the keyboard the second, so the button always has
        // its card here and the keyboard only asks for a row while it is off.
        val charm by prefs.dictate.floatingButtonCharm.collectAsState()
        val classicColor by prefs.dictate.floatingButtonColor.collectAsState()
        val charmColor by prefs.dictate.floatingButtonCharmColor.collectAsState()
        val animated by prefs.dictate.floatingButtonCharmAnimated.collectAsState()
        val buttonOn = rememberMicButtonOn()
        val keyboardOn = rememberKeyboardOn()
        val scope = rememberCoroutineScope()
        if (firstRun) {
            FirstRunHome(
                charm = charm,
                colorOf = { look ->
                    if (look == DictateMicCharm.CLASSIC) classicColor.toArgb()
                    else look.resolveColor(charmColor.toArgb())
                },
                buttonOn = buttonOn,
                keyboardOn = keyboardOn,
                onPickLook = { scope.launch { prefs.dictate.floatingButtonCharm.set(it) } },
                onMoreLooks = { navController.navigateToTab(MainTab.STYLE) },
                // "Turn on" switches the button on here; the floating-button screen then asks for the
                // accessibility permission, with its disclosure, as it does from the normal Home.
                onTurnOnButton = {
                    scope.launch { prefs.dictate.floatingButtonEnabled.set(true) }
                    navController.navigate(Routes.Settings.DictateFloatingButton)
                },
                onTurnOnKeyboard = { InputMethodUtils.showImeEnablerActivity(context) },
                onTryIt = tryIt,
            )
            // Room for the floating button, so it never covers the last line.
            Spacer(modifier = Modifier.height(88.dp))
            return@content
        }
        MicButtonCard(
            charm = charm,
            color = if (charm == DictateMicCharm.CLASSIC) classicColor.toArgb()
            else charm.resolveColor(charmColor.toArgb()),
            moving = animated && charm != DictateMicCharm.CLASSIC,
            on = buttonOn,
            onChangeLook = { navController.navigateToTab(MainTab.STYLE) },
            onTurnOn = { navController.navigate(Routes.Settings.DictateFloatingButton) },
        )
        if (!keyboardOn) {
            KeyboardRow(onTurnOn = { InputMethodUtils.showImeEnablerActivity(context) })
        }

        // While a dictation is in flight its placeholder row already reads as failed (issue #358); no
        // retry is offered then, the same rule DictateController.retranscribeHistoryEntry applies.
        val dictateState by DictateController.state.collectFlowAsState()
        val busy = dictateState is DictateController.UiState.Recording ||
            dictateState is DictateController.UiState.Transcribing ||
            dictateState is DictateController.UiState.Rewording
        // A note from "Try it here" is in the history like any dictation; only with the history off does it
        // need putting on the list by hand.
        val historyOn by prefs.dictate.historyEnabled.collectAsState()
        val shownNote = tryItNote?.takeUnless { historyOn }
        RecentDictationsCard(
            entries = remember(entries, shownNote) {
                val now = System.currentTimeMillis()
                val recent = recentEntries(entries.orEmpty(), now)
                if (shownNote == null) recent else (listOf(tryItEntry(shownNote, now)) + recent).take(RECENT_COUNT)
            },
            canRetry = !busy,
            onCopy = { entry ->
                copyToClipboard(context, entry.text)
                Toast.makeText(context, R.string.dictate__history_copied, Toast.LENGTH_SHORT).show()
            },
            onRetry = { entry ->
                entry.audioPath?.let { path ->
                    context.startActivity(TranscribeShareActivity.retryIntent(context, entry.id, path))
                }
            },
            onSeeAll = { navController.navigate(Routes.Settings.DictateHistory) },
        )

        // Settings that could not be read at start were kept instead of written over (#383). This is the
        // way back to them, offered until they are restored or discarded.
        val settingsUnreadable by PreferenceRecovery.pending.collectFlowAsState()
        var showRecoveryDialog by remember { mutableStateOf(false) }
        val recoveryScope = rememberCoroutineScope()
        if (settingsUnreadable) {
            FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.dictate__settings_unreadable_card),
                onClick = { showRecoveryDialog = true },
            )
        }
        if (showRecoveryDialog) {
            JetPrefAlertDialog(
                title = stringRes(R.string.dictate__settings_unreadable_title),
                confirmLabel = stringRes(R.string.action__restore),
                onConfirm = {
                    showRecoveryDialog = false
                    recoveryScope.launch {
                        PreferenceRecovery.restore(context).onFailure {
                            context.showLongToast(R.string.dictate__settings_restore_failed)
                        }
                    }
                },
                // Delete, not Discard: several translations of Discard read the same as Cancel beside it.
                neutralLabel = stringRes(R.string.action__delete),
                onNeutral = {
                    showRecoveryDialog = false
                    PreferenceRecovery.discard(context)
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { showRecoveryDialog = false },
            ) {
                Text(stringRes(R.string.dictate__settings_unreadable_message))
            }
        }

        // Passive dictation-stats summary (issue #142): appears once the user has dictated, taps through
        // to the full statistics screen. No interruption — just a glanceable "time saved".
        val statWords by prefs.dictate.statsWords.collectAsState()
        val statSpoken by prefs.dictate.statsSpokenSeconds.collectAsState()
        if (statDictations > 0L) {
            TimeSavedCard(
                savedSeconds = DictateStats.savedSeconds(statWords, statSpoken).toLong(),
                dictations = statDictations,
                onClick = { navController.navigate(Routes.Settings.DictateStats) },
            )
        }
        // Room for the floating button, so it never covers the last card.
        Spacer(modifier = Modifier.height(88.dp))

        // Milestone celebrations are shown on the keyboard (Smartbar nudge), consistent with rate/donate
        // (issue #142) — see DictateController.showMilestoneNudge. Not surfaced here.
    }
}

/** The mic button as it looks now, gently moving, with the way to change it or switch it on. */
@Composable
private fun MicButtonCard(
    charm: DictateMicCharm,
    color: Int,
    moving: Boolean,
    on: Boolean,
    onChangeLook: () -> Unit,
    onTurnOn: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Its own movement, at half speed: alive on the page that shows it off, never on top of other apps.
            MovingMicCharm(charm = charm, color = color, moving = moving, slowdown = 2, modifier = Modifier.size(64.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringRes(R.string.home__mic_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (on) stringRes(R.string.home__mic_on, "charm" to charmLabel(charm))
                    else stringRes(R.string.home__mic_off),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!on) {
                        Button(onClick = onTurnOn) { Text(stringRes(R.string.home__turn_on)) }
                    }
                    OutlinedButton(onClick = onChangeLook) { Text(stringRes(R.string.home__mic_change_look)) }
                }
            }
        }
    }
}

/** The keyboard, only while it is off: the second way to dictate, one tap from being on. */
@Composable
private fun KeyboardRow(onTurnOn: () -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Keyboard, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringRes(R.string.home__keyboard_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringRes(R.string.home__keyboard_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onTurnOn) { Text(stringRes(R.string.home__turn_on)) }
        }
    }
}

/** A failed dictation whose recording was kept, so it can be sent again. */
private fun DictateHistoryEntry.isRetryable(): Boolean = failed && !audioPath.isNullOrEmpty()

/**
 * The newest [RECENT_COUNT] dictations, newest first. A failed one from the last [RETRY_WINDOW_MS] whose
 * recording was kept always gets a row, taking the last slot if it is older than the rest: a lost
 * dictation is the one entry somebody opens the app to find.
 */
internal fun recentEntries(all: List<DictateHistoryEntry>, nowMs: Long): List<DictateHistoryEntry> {
    // The store floats pinned entries to the top; here it is about what was said last.
    val newest = all.sortedByDescending { it.createdAt }
    val shown = newest.take(RECENT_COUNT)
    if (shown.any { it.isRetryable() }) return shown
    val failed = newest.firstOrNull { it.isRetryable() && nowMs - it.createdAt <= RETRY_WINDOW_MS }
        ?: return shown
    return shown.take(RECENT_COUNT - 1) + failed
}

@Composable
private fun RecentDictationsCard(
    entries: List<DictateHistoryEntry>,
    canRetry: Boolean,
    onCopy: (DictateHistoryEntry) -> Unit,
    onRetry: (DictateHistoryEntry) -> Unit,
    onSeeAll: () -> Unit,
) {
    Card(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringRes(R.string.home__recent_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSeeAll) { Text(stringRes(R.string.home__recent_see_all)) }
        }
        if (entries.isEmpty()) {
            Text(
                stringRes(R.string.dictate__history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        } else {
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !entry.failed) { onCopy(entry) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (entry.failed) stringRes(R.string.home__recent_failed) else entry.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (entry.failed) MaterialTheme.colorScheme.error else Color.Unspecified,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            DateUtils.getRelativeTimeSpanString(
                                entry.createdAt,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                            ).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!entry.failed) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringRes(R.string.home__recent_copy),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (entry.isRetryable()) {
                        FilledTonalButton(onClick = { onRetry(entry) }, enabled = canRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringRes(R.string.home__recent_retry))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TimeSavedCard(savedSeconds: Long, dictations: Long, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.padding(8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
            // One line: "3h 12m  saved · 240 dictations".
            Text(
                homeDuration(savedSeconds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringRes(
                    R.string.home__stats_summary,
                    "count" to NumberFormat.getIntegerInstance().format(dictations),
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** Compact duration for the home stats card / milestone text: `3h`, `3h 12m`, `12m`, `45s`. */
private fun homeDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}
