/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether the phone has any route to the internet at all, so a dictation can say "you're offline" at
 * once instead of after a provider call has run through its retries and timed out.
 */
object DictateConnectivity {

    /**
     * True only when the phone *positively* has no usable network: airplane mode, no signal, or a
     * network that does not carry internet traffic.
     *
     * Deliberately not "is the connection validated". A Wi-Fi network that cannot reach the internet but
     * can reach the server someone configured (a LAN Ollama, #136) is exactly the setup that check would
     * wrongly call offline. Anything unclear — an error asking the system, no answer — counts as online,
     * so the worst case is the old behaviour: try, and fail the way it always did.
     */
    fun isOffline(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
        val network = manager.activeNetwork ?: return@runCatching true
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching true
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}
