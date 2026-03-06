// Copyright (c) 2025 Advntrs LLC
// SPDX-License-Identifier: MIT

package com.advntrs.kpresentationbuddy

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.vfs.VfsUtil

/**
 * "Type presentation script" action.
 *
 * The action is available from the Tools menu (see plugin.xml).  It opens a
 * file-chooser dialog for the user to select a plain-text script, then
 * delegates character-by-character typing to [PresentationBuddyService].
 */
class PresentationBuddyAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: run {
            service(project).notify(
                project,
                "k-presentation-buddy: no editor is open.",
                NotificationType.WARNING
            )
            return
        }

        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(
            PluginConstants.SCRIPT_FILE_EXTENSION
        )
        descriptor.title = "Select Presentation Script"

        FileChooser.chooseFile(descriptor, project, null) { virtualFile ->
            val script = VfsUtil.loadText(virtualFile)
            service(project).typeScript(project, editor, script)
        }
    }

    override fun update(event: AnActionEvent) {
        // Disable the action when there is no open editor.
        event.presentation.isEnabled = event.getData(CommonDataKeys.EDITOR) != null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun service(project: Project): PresentationBuddyService =
        ApplicationManager.getApplication().getService(PresentationBuddyService::class.java)
}
