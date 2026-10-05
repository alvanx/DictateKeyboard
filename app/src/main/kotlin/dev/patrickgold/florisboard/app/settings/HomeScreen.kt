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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState as collectFlowAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.dictate.copyToClipboard
import dev.patrickgold.florisboard.app.settings.dictate.isOverlayServiceEnabled
import dev.patrickgold.florisboard.app.settings.dictate.providerDisplayName
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import dev.patrickgold.florisboard.dictate.data.stats.DictateStats
import dev.patrickgold.florisboard.dictate.importer.ImportTranscriber
import dev.patrickgold.florisboard.dictate.importer.TranscribeShareActivity
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import java.text.NumberFormat
import org.florisboard.lib.compose.stringRes

/** How many transcripts the home screen previews before "See all". */
private const val RECENT_COUNT = 3

/** How long a failed dictation keeps its place on the home screen. */
private const val RETRY_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/**
 * The app's landing page, built around what people come back for: their recent dictations and their word
 * lists, with the other things worth one tap right under them. Every settings category sits one tap
 * away behind the gear, in [AllSettingsScreen], so this page stays short.
 */
@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    // The keyboard test field lives with the settings now: the keyboard is the secondary way in.
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    actions {
        IconButton(onClick = { navController.navigate(Routes.Settings.All) }) {
            Icon(Icons.Outlined.Settings, contentDescription = stringRes(R.string.settings__all__title))
        }
    }

    content {
        // Setup, only while there is no way to dictate at all. The floating button is the main way in
        // now and the keyboard the second, so either one being ready is enough — the old "not selected
        // as default keyboard" nag would only bother somebody who dictates through the button.
        val lifecycleOwner = LocalLifecycleOwner.current
        val isImeEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val bubbleEnabled by prefs.dictate.floatingButtonEnabled.collectAsState()
        var bubbleServiceEnabled by remember { mutableStateOf(isOverlayServiceEnabled(context)) }
        DisposableEffect(lifecycleOwner) {
            // The accessibility service is switched on in system settings, so re-check on return.
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) bubbleServiceEnabled = isOverlayServiceEnabled(context)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        if (!isImeEnabled && !(bubbleEnabled && bubbleServiceEnabled)) {
            SetupCard(
                onFloatingButton = { navController.navigate(Routes.Settings.DictateFloatingButton) },
                onKeyboard = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        }

        val entries by remember { DictateHistoryStore.flow(context) }.collectFlowAsState(initial = emptyList())
        // While a dictation is in flight its placeholder row already reads as failed (issue #358); no
        // retry is offered then, the same rule DictateController.retranscribeHistoryEntry applies.
        val dictateState by DictateController.state.collectFlowAsState()
        val busy = dictateState is DictateController.UiState.Recording ||
            dictateState is DictateController.UiState.Transcribing ||
            dictateState is DictateController.UiState.Rewording
        RecentDictationsCard(
            entries = remember(entries) { recentEntries(entries, System.currentTimeMillis()) },
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

        // "Transcribe a file" (issue #301) stays one tap from the top, now as a labelled tile.
        val transcribePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) context.startActivity(TranscribeShareActivity.intentFor(context, uri))
        }
        val providerId by prefs.dictate.transcriptionProviderId.collectAsState()
        val accounts by prefs.dictate.providerAccounts.collectAsState()
        // The model in use, or the provider's name when it has no model of its own to show.
        val modelName = remember(providerId, accounts) {
            val account = accounts.getOrEmpty(providerId)
            account.transcriptionModel
                .ifBlank { ImportTranscriber.presetFor(account).defaultTranscriptionModel ?: "" }
                .ifBlank { providerDisplayName(providerId, accounts) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickTile(
                icon = Icons.Default.MenuBook,
                label = stringRes(R.string.dictionary_hub__title),
                detail = stringRes(R.string.home__words_detail),
                onClick = { navController.navigate(Routes.Settings.Words) },
            )
            QuickTile(
                icon = Icons.Default.AudioFile,
                label = stringRes(R.string.home__transcribe_file),
                detail = stringRes(R.string.home__transcribe_file_detail),
                onClick = { transcribePicker.launch(TranscribeShareActivity.MIME_TYPES) },
            )
            QuickTile(
                icon = Icons.Default.Cloud,
                label = stringRes(R.string.home__ai_model),
                detail = modelName,
                onClick = { navController.navigate(Routes.Settings.DictateProviders) },
            )
        }

        // Passive dictation-stats summary (issue #142): appears once the user has dictated, taps through
        // to the full statistics screen. No interruption — just a glanceable "time saved".
        val statDictations by prefs.dictate.statsDictations.collectAsState()
        val statWords by prefs.dictate.statsWords.collectAsState()
        val statSpoken by prefs.dictate.statsSpokenSeconds.collectAsState()
        if (statDictations > 0L) {
            TimeSavedCard(
                savedSeconds = DictateStats.savedSeconds(statWords, statSpoken).toLong(),
                dictations = statDictations,
                onClick = { navController.navigate(Routes.Settings.DictateStats) },
            )
        }

        // Milestone celebrations are shown on the keyboard (Smartbar nudge), consistent with rate/donate
        // (issue #142) — see DictateController.showMilestoneNudge. Not surfaced here.
    }
}

@Composable
private fun SetupCard(onFloatingButton: () -> Unit, onKeyboard: () -> Unit) {
    Card(
        modifier = Modifier.padding(8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringRes(R.string.home__setup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(stringRes(R.string.home__setup_body), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFloatingButton) { Text(stringRes(R.string.home__setup_floating_button)) }
                OutlinedButton(onClick = onKeyboard) { Text(stringRes(R.string.home__setup_keyboard)) }
            }
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
private fun RowScope.QuickTile(
    icon: ImageVector,
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.weight(1f)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
