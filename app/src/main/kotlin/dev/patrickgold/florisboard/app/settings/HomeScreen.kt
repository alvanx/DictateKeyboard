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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import dev.patrickgold.florisboard.dictate.data.stats.DictateStats
import dev.patrickgold.florisboard.dictate.importer.TranscribeShareActivity
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import java.text.NumberFormat
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.stringRes

/** How many transcripts the home screen previews before "See all". */
private const val RECENT_COUNT = 3

/**
 * The app's landing page, built around what people come back for: their recent dictations and their word
 * lists. Shortcuts to the other things worth one tap sit right under them; every settings category
 * follows as a plain list below.
 */
@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    actions {
        FlorisIconButton(
            onClick = { navController.navigate(Routes.Settings.Search) },
            icon = Icons.Default.Search,
        )
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
        RecentDictationsCard(
            // The store floats pinned entries to the top; here it is about what was said last.
            entries = remember(entries) { entries.sortedByDescending { it.createdAt }.take(RECENT_COUNT) },
            onCopy = { entry ->
                copyToClipboard(context, entry.text)
                Toast.makeText(context, R.string.dictate__history_copied, Toast.LENGTH_SHORT).show()
            },
            onSeeAll = { navController.navigate(Routes.Settings.DictateHistory) },
        )

        // "Transcribe a file" (issue #301) stays one tap from the top, now as a labelled tile.
        val transcribePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) context.startActivity(TranscribeShareActivity.intentFor(context, uri))
        }
        val providerId by prefs.dictate.transcriptionProviderId.collectAsState()
        val accounts by prefs.dictate.providerAccounts.collectAsState()
        val providerName = remember(providerId, accounts) { providerDisplayName(providerId, accounts) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickTile(
                icon = Icons.Default.MenuBook,
                label = stringRes(R.string.words__title),
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
                detail = providerName,
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

        PreferenceGroup(title = stringRes(R.string.home__settings_group)) {
            Preference(
                icon = Icons.Default.Mic,
                title = stringRes(R.string.dictate__title),
                onClick = { navController.navigate(Routes.Settings.Dictate) },
            )
            Preference(
                icon = Icons.Default.Language,
                title = stringRes(R.string.settings__localization__title),
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
            Preference(
                icon = Icons.Outlined.Palette,
                title = stringRes(R.string.settings__theme__title),
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
            Preference(
                icon = Icons.Outlined.Keyboard,
                title = stringRes(R.string.settings__keyboard__title),
                onClick = { navController.navigate(Routes.Settings.Keyboard) },
            )
            Preference(
                icon = Icons.Default.SmartButton,
                title = stringRes(R.string.settings__smartbar__title),
                onClick = { navController.navigate(Routes.Settings.Smartbar) },
            )
            Preference(
                icon = Icons.Default.Spellcheck,
                title = stringRes(R.string.settings__typing__title),
                onClick = { navController.navigate(Routes.Settings.Typing) },
            )
            Preference(
                icon = Icons.Default.Gesture,
                title = stringRes(R.string.settings__gestures__title),
                onClick = { navController.navigate(Routes.Settings.Gestures) },
            )
            Preference(
                icon = Icons.AutoMirrored.Outlined.Assignment,
                title = stringRes(R.string.settings__clipboard__title),
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            Preference(
                icon = Icons.Default.SentimentSatisfiedAlt,
                title = stringRes(R.string.settings__media__title),
                onClick = { navController.navigate(Routes.Settings.Media) },
            )
            // With the other tools the Smartbar opens (issue #424): it is one, and not a dictation feature.
            Preference(
                icon = Icons.Outlined.Translate,
                title = stringRes(R.string.settings__translation__title),
                onClick = { navController.navigate(Routes.Settings.Translation) },
            )
            Preference(
                icon = Icons.Default.Extension,
                title = stringRes(R.string.ext__home__title),
                onClick = { navController.navigate(Routes.Ext.Home) },
            )
            Preference(
                icon = Icons.Outlined.Build,
                title = stringRes(R.string.settings__other__title),
                onClick = { navController.navigate(Routes.Settings.Other) },
            )
            Preference(
                icon = Icons.Outlined.Info,
                title = stringRes(R.string.about__title),
                onClick = { navController.navigate(Routes.Settings.About) },
            )
        }
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

@Composable
private fun RecentDictationsCard(
    entries: List<DictateHistoryEntry>,
    onCopy: (DictateHistoryEntry) -> Unit,
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
                            entry.text,
                            style = MaterialTheme.typography.bodyMedium,
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
