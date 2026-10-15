/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.setup.isProviderConfigured
import dev.patrickgold.florisboard.dictate.ui.MicCharm
import dev.patrickgold.florisboard.dictate.ui.MovingMicCharm
import dev.patrickgold.florisboard.app.settings.style.charmLabel
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateMicCharm
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import dev.patrickgold.florisboard.dictate.data.history.DictateHistorySource
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.recognition.RecognitionActivity
import org.florisboard.lib.compose.stringRes

/** The looks offered on the first-run home, before "More" opens the whole Style tab. */
private val FIRST_RUN_LOOKS = listOf(DictateMicCharm.DAISY, DictateMicCharm.BUTTERFLY, DictateMicCharm.HEART)

/**
 * Whether Home shows its first-run layout: nothing has been dictated yet. [history] is null until the
 * history has loaded, so somebody with dictations never sees the first-run page flash by. The dictation
 * count covers people who keep no history (or cleared it) but have dictated before.
 *
 * Setup is not part of the rule on purpose: the setup wizard already turns the keyboard on before Home
 * ever shows, so "nothing is on yet" would almost never be true here. Step 2 shows what is on instead.
 */
internal fun isFirstRunHome(history: List<DictateHistoryEntry>?, dictations: Long): Boolean =
    history != null && history.isEmpty() && dictations == 0L

/**
 * The few looks in step 1. A look picked earlier in the Style tab takes the last place, so the current
 * choice is always among them; the classic button is the default rather than a choice, so it doesn't.
 */
internal fun firstRunLooks(current: DictateMicCharm): List<DictateMicCharm> =
    if (current in FIRST_RUN_LOOKS || current == DictateMicCharm.CLASSIC) FIRST_RUN_LOOKS
    else FIRST_RUN_LOOKS.dropLast(1) + current

/**
 * Home before the first dictation: a heading, two steps (a look for the mic button, and where it shows
 * up), and a "Try it here" card that takes a voice note right on this page. The steps reuse the normal
 * Home's ways of turning things on, and each option shows a check mark once it is on.
 */
@Composable
internal fun FirstRunHome(
    charm: DictateMicCharm,
    colorOf: (DictateMicCharm) -> Int,
    buttonOn: Boolean,
    keyboardOn: Boolean,
    onPickLook: (DictateMicCharm) -> Unit,
    onMoreLooks: () -> Unit,
    onTurnOnButton: () -> Unit,
    onTurnOnKeyboard: () -> Unit,
    onTryIt: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringRes(R.string.firstrun__title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            stringRes(R.string.firstrun__subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    StepCard(number = 1, title = stringRes(R.string.firstrun__step_look), highlighted = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            firstRunLooks(charm).forEach { look ->
                LookChoice(
                    charm = look,
                    color = colorOf(look),
                    selected = look == charm,
                    onClick = { onPickLook(look) },
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(role = Role.Button, onClick = onMoreLooks),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringRes(R.string.firstrun__more_looks),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    StepCard(number = 2, title = stringRes(R.string.firstrun__step_where), highlighted = false) {
        WhereOption(
            icon = Icons.Outlined.Mic,
            title = stringRes(R.string.firstrun__floating_title),
            summary = stringRes(R.string.firstrun__floating_summary),
            on = buttonOn,
            primary = true,
            onTurnOn = onTurnOnButton,
        )
        WhereOption(
            icon = Icons.Outlined.Keyboard,
            title = stringRes(R.string.home__keyboard_title),
            summary = stringRes(R.string.firstrun__keyboard_summary),
            on = keyboardOn,
            primary = false,
            onTurnOn = onTurnOnKeyboard,
        )
    }

    TryItCard(charm = charm, color = colorOf(charm), onClick = onTryIt)
}

/** One numbered step: a round number, its heading, then what the step asks for. */
@Composable
private fun StepCard(
    number: Int,
    title: String,
    highlighted: Boolean,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
        colors = if (highlighted) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

/** A look to tap. Only the chosen one moves, as on the Style tab. */
@Composable
private fun LookChoice(
    charm: DictateMicCharm,
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val label = charmLabel(charm)
    Box(
        modifier = modifier
            .height(84.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 2.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        MovingMicCharm(charm = charm, color = color, moving = selected, modifier = Modifier.size(56.dp))
    }
}

/** One place the mic can show up, with its "Turn on" button, or a check mark once it is on. */
@Composable
private fun WhereOption(
    icon: ImageVector,
    title: String,
    summary: String,
    on: Boolean,
    primary: Boolean,
    onTurnOn: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            on -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringRes(R.string.firstrun__done),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            // The floating mic is the main way in, so it gets the filled button; the keyboard the outlined one.
            primary -> Button(onClick = onTurnOn) { Text(stringRes(R.string.home__turn_on)) }
            else -> OutlinedButton(onClick = onTurnOn) { Text(stringRes(R.string.home__turn_on)) }
        }
    }
}

/**
 * "Try it here": a voice note right on this page, with the mic in the look picked in step 1 (the classic
 * look draws as a plain round mic). The whole card is the button, so it is an easy thing to hit.
 */
@Composable
private fun TryItCard(charm: DictateMicCharm, color: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val dashColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val width = 1.5.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(28.dp.toPx() - width / 2),
                    style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MicCharm(charm = charm, color = color, modifier = Modifier.size(60.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringRes(R.string.firstrun__try_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringRes(R.string.firstrun__try_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * What tapping "Try it here" does. Home has no recorder of its own: it opens the same voice-input popup
 * other apps open ([RecognitionActivity]), which records, transcribes with the user's provider and hands
 * the text back. The note is logged to the history like any dictation, so Home turns into its normal
 * layout with the note at the top of "Recent dictations", ready to copy. [onNote] also gets the text, for
 * the one case the history cannot show it: somebody who turned the history off.
 *
 * Checked first, so the popup never just sits there listening to nothing: a provider that can transcribe
 * (otherwise the provider screen opens, saying why) and the microphone permission (asked for here).
 * A cancelled popup, or one that heard nothing, leaves the page as it was; a failure says why in a toast.
 *
 * Registered by Home itself rather than by the first-run page: the history row appears while the popup is
 * still open, which takes the first-run page off screen, and its launcher would go with it.
 */
@Composable
internal fun rememberTryItHere(onNote: (String) -> Unit): () -> Unit {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val navController = LocalNavController.current
    val micNeeded = stringRes(R.string.setup__grant_mic_permission__description)

    val popup = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            onNote(text)
        } else {
            // The popup closes the same way for "cancelled" and "failed"; the controller still knows which.
            (DictateController.state.value as? DictateController.UiState.Error)?.let {
                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    val openPopup = { popup.launch(Intent(context, RecognitionActivity::class.java)) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openPopup() else Toast.makeText(context, micNeeded, Toast.LENGTH_LONG).show()
    }

    return {
        val providerId = prefs.dictate.transcriptionProviderId.get()
        val ready = isProviderConfigured(prefs.dictate.providerAccounts.get(), providerId) {
            LocalModelManager.isInstalled(context, it)
        }
        when {
            !ready -> {
                // The same words the keyboard uses for each case, then the screen that fixes it.
                val cloud = providerId == ProviderRegistry.CLOUD.id
                val message = when {
                    cloud -> R.string.dictate__error_cloud_no_account
                    providerId == ProviderRegistry.LOCAL.id -> R.string.dictate__local_model_not_installed_error
                    else -> R.string.dictate__error_no_api_key
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (cloud) navController.navigate(Routes.Settings.DictateCloud)
                else navController.navigate(Routes.Settings.DictateProviders)
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> openPopup()
            else -> mic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/**
 * The note from "Try it here" as a row for "Recent dictations", for somebody with the history turned off:
 * nothing is stored, but the note they just took still shows on Home, ready to copy.
 */
internal fun tryItEntry(text: String, nowMs: Long): DictateHistoryEntry = DictateHistoryEntry(
    text = text,
    createdAt = nowMs,
    providerId = "",
    providerName = "",
    model = "",
    language = "",
    durationSecs = 0L,
    audioPath = null,
    audioBytes = 0L,
    source = DictateHistorySource.KEYBOARD,
    reworded = false,
)
