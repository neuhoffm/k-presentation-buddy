// Copyright (c) 2025 Advntrs LLC
// SPDX-License-Identifier: MIT

package com.advntrs.kpresentationbuddy

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.application.ApplicationManager
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

/**
 * Settings page shown under **Settings ▸ Tools ▸ Presentation Buddy**.
 *
 * The only user-facing knob at this point is the typing delay.  The
 * minimum/maximum/default values come from [PluginConstants] so there are no
 * magic numbers in the UI layer.
 */
class PresentationBuddyConfigurable : Configurable {

    private var spinner: JSpinner? = null

    override fun getDisplayName(): String = "Presentation Buddy"

    override fun createComponent(): JComponent {
        val settings = settings()
        val model = SpinnerNumberModel(
            settings.typingDelayMs,          // initial value
            PluginConstants.MIN_TYPING_DELAY_MS,
            PluginConstants.MAX_TYPING_DELAY_MS,
            10L                              // step
        )
        spinner = JSpinner(model)

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("Typing delay (ms):"), gbc)
        gbc.gridx = 1
        panel.add(spinner!!, gbc)

        return panel
    }

    override fun isModified(): Boolean =
        spinner?.value as? Long != settings().typingDelayMs

    override fun apply() {
        settings().typingDelayMs = spinner?.value as? Long
            ?: PluginConstants.DEFAULT_TYPING_DELAY_MS
    }

    override fun reset() {
        spinner?.value = settings().typingDelayMs
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun settings(): PresentationBuddySettings =
        ApplicationManager.getApplication().getService(PresentationBuddySettings::class.java)
}
