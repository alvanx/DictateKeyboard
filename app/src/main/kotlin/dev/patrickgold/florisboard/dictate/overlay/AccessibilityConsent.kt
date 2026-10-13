/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.overlay

/**
 * The user's answer to the accessibility-service disclosure (Google Play "Prominent Disclosure and
 * Consent" requirement). Pure logic so it can be unit tested; the two stored values live in
 * [dev.patrickgold.florisboard.app.AppPrefs.Dictate].
 *
 * Bump [CURRENT_VERSION] whenever the disclosure text (or what the service does) changes in a way the
 * user should re-approve. Everyone who agreed to an older version then sees the disclosure again before
 * being sent to the system accessibility settings, and the bubble stays off until they agree.
 */
object AccessibilityConsent {
    const val CURRENT_VERSION = 1

    /** Stored when nothing has been agreed to (or consent was withdrawn). */
    const val NONE_VERSION = 0

    /** Whether a stored consent still covers the disclosure text that is shipping now. */
    fun isValid(storedVersion: Int, storedAtMillis: Long, currentVersion: Int = CURRENT_VERSION): Boolean =
        storedVersion != NONE_VERSION && storedAtMillis > 0L && storedVersion >= currentVersion
}
