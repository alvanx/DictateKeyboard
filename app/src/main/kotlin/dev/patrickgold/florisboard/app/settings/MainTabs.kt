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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.dictate.isOverlayServiceEnabled
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes

/**
 * The four places the app opens onto, one tab each. Everything else is a screen pushed on top of one of
 * them, with a back arrow and no tab bar.
 */
enum class MainTab(
    val route: Any,
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(Routes.Settings.Home, R.string.nav__home, Icons.Outlined.Home, Icons.Filled.Home),
    STYLE(Routes.Settings.Style, R.string.nav__style, Icons.Outlined.Palette, Icons.Filled.Palette),
    DICTIONARY(
        Routes.Settings.Words,
        R.string.dictionary_hub__title,
        Icons.AutoMirrored.Outlined.MenuBook,
        Icons.AutoMirrored.Filled.MenuBook,
    ),
    SETTINGS(Routes.Settings.All, R.string.settings__all__title, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/**
 * Switches to [tab] the way bottom tabs do: each tab keeps its own place, tapping one never stacks
 * another copy of it, and back from any tab goes to Home and then out of the app.
 */
fun NavController.navigateToTab(tab: MainTab) {
    navigate(tab.route) {
        popUpTo<Routes.Settings.Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The bottom tab bar. Draws nothing unless one of the four tab screens is showing. */
@Composable
fun MainTabBar(navController: NavController) {
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination ?: return
    val current = MainTab.entries.firstOrNull { tab ->
        destination.hierarchy.any { it.hasRoute(tab.route::class) }
    } ?: return
    NavigationBar {
        MainTab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) navController.navigateToTab(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(stringRes(tab.label)) },
            )
        }
    }
}

/**
 * Whether the floating mic button can actually show: switched on here *and* allowed in the system's
 * accessibility settings. The second is changed outside the app, so it is checked again on every return.
 */
@Composable
fun rememberMicButtonOn(): Boolean {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val enabled by prefs.dictate.floatingButtonEnabled.collectAsState()
    var serviceEnabled by remember { mutableStateOf(isOverlayServiceEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceEnabled = isOverlayServiceEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled && serviceEnabled
}

/** Whether the app's keyboard is turned on in the system's keyboard list. */
@Composable
fun rememberKeyboardOn(): Boolean {
    val enabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
    return enabled
}
