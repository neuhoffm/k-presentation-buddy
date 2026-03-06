// Copyright (c) 2025 Advntrs LLC
// SPDX-License-Identifier: MIT

package com.advntrs.kpresentationbuddy

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent plugin settings.
 *
 * Values are stored in `k-presentation-buddy.xml` inside the IDE configuration
 * directory and are exposed via the Settings UI ([PresentationBuddyConfigurable]).
 *
 * All defaults are taken from [PluginConstants] so that there are no magic
 * numbers scattered through the code.
 */
@Service(Service.Level.APP)
@State(
    name = "PresentationBuddySettings",
    storages = [Storage("k-presentation-buddy.xml")]
)
class PresentationBuddySettings : PersistentStateComponent<PresentationBuddySettings> {

    /** Inter-keystroke delay in milliseconds. */
    var typingDelayMs: Long = PluginConstants.DEFAULT_TYPING_DELAY_MS

    override fun getState(): PresentationBuddySettings = this

    override fun loadState(state: PresentationBuddySettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
