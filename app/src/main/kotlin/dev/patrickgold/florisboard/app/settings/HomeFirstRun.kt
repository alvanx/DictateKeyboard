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
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.ui.MovingMicCharm
import dev.patrickgold.florisboard.app.settings.style.charmLabel
import dev.patrickgold.florisboard.dictate.DictateMicCharm
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
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
 * up), and a line saying where dictations will appear. The steps reuse the normal Home's ways of turning
 * things on, and each option shows a check mark once it is on.
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

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringRes(R.string.firstrun__history_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
