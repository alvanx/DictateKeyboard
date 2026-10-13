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

/**
 * THE switch for the managed "Voice Mode Cloud" service.
 *
 * While [ENABLED] is false the app hides every way to reach or choose Cloud (provider lists and pickers,
 * setup, settings search, the credit screen, the "What's new" tour page) and sends no request to the Cloud
 * server. The code behind it stays in place, untouched.
 *
 * Flip it to true when the server is deployed and ProviderRegistry.CLOUD.baseUrl points at it
 * (docs/rebrand.md, "Not done yet").
 */
object CloudAvailability {
    const val ENABLED = false
}
