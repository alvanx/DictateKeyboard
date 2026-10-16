/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore

/**
 * What a saved "Cloud" choice becomes while Cloud is switched off ([CloudAvailability]).
 *
 * Settings saved by an earlier build may still select Cloud for transcription or rewording. Those
 * ids are replaced once at startup, so nothing keeps pointing at a service that is not there.
 */
object CloudFallback {

    /** Same default as the preference itself. */
    private const val DEFAULT_ID = "openai"

    /**
     * The provider to use instead of [selectedId]: itself when it is not Cloud (or Cloud is on),
     * otherwise the first other provider the user has set up, otherwise the app's default.
     */
    fun resolve(
        selectedId: String,
        accounts: ProviderAccounts,
        needsChat: Boolean,
        isModelInstalled: (String) -> Boolean,
        cloudEnabled: Boolean = CloudAvailability.ENABLED,
    ): String {
        if (cloudEnabled || selectedId != ProviderRegistry.CLOUD.id) return selectedId
        return ProviderRegistry.presetsFor(false)
            .filter { if (needsChat) it.capabilities.chat else it.capabilities.transcription }
            .firstOrNull { ProviderListing.isSetUp(it, accounts, isModelInstalled) }
            ?.id ?: DEFAULT_ID
    }

    suspend fun applyIfNeeded(context: Context) {
        if (CloudAvailability.ENABLED) return
        val prefs by FlorisPreferenceStore
        val accounts = prefs.dictate.providerAccounts.get()
        val installed: (String) -> Boolean = { LocalModelManager.isInstalled(context, it) }
        val transcription = prefs.dictate.transcriptionProviderId.get()
        val rewording = prefs.dictate.rewordingProviderId.get()
        resolve(transcription, accounts, needsChat = false, isModelInstalled = installed)
            .takeIf { it != transcription }?.let { prefs.dictate.transcriptionProviderId.set(it) }
        resolve(rewording, accounts, needsChat = true, isModelInstalled = installed)
            .takeIf { it != rewording }?.let { prefs.dictate.rewordingProviderId.set(it) }
    }
}
