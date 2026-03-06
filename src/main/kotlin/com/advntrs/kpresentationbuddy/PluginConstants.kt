// Copyright (c) 2025 Advntrs LLC
// SPDX-License-Identifier: MIT

package com.advntrs.kpresentationbuddy

/**
 * Constants that centralise every value that would otherwise be hardcoded across
 * the plugin sources.  All numeric/string literals that represent configurable
 * behaviour live here so they are easy to find, review, and change in one place.
 */
object PluginConstants {

    // ── Typing simulation ────────────────────────────────────────────────────

    /** Default inter-keystroke delay in milliseconds. */
    const val DEFAULT_TYPING_DELAY_MS: Long = 50L

    /** Minimum delay exposed in Settings (0 = no pause between keystrokes). */
    const val MIN_TYPING_DELAY_MS: Long = 0L

    /** Maximum delay exposed in Settings. */
    const val MAX_TYPING_DELAY_MS: Long = 2_000L

    // ── Script file ──────────────────────────────────────────────────────────

    /** Extension used when the plugin creates a new presentation script file. */
    const val SCRIPT_FILE_EXTENSION: String = "txt"

    /** Default name offered in the "Create script" dialog. */
    const val DEFAULT_SCRIPT_FILE_NAME: String = "presentation-script.$SCRIPT_FILE_EXTENSION"

    // ── Notification group ───────────────────────────────────────────────────

    /** ID of the notification group registered in plugin.xml. */
    const val NOTIFICATION_GROUP_ID: String = "k-presentation-buddy"
}
