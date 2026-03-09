/*
 * Copyright (c) 2026 Advntrs LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.advntrs.kpresentationbuddy.actions

import com.advntrs.kpresentationbuddy.PresentationBuddyService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class LoadInstructionsAction : AnAction() {
    companion object {
        private const val MAX_INSTRUCTIONS_FILE_SIZE_BYTES = 1_048_576L // 1 MB
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("Select Instructions File")
            .withFileFilter { it.extension.equals("json", ignoreCase = true) }

        FileChooser.chooseFile(descriptor, project, null) { file ->
            if (!validateInstructionFile(project, file)) {
                return@chooseFile
            }

            val service = project.service<PresentationBuddyService>()
            when (val result = service.loadInstructions(file)) {
                is PresentationBuddyService.LoadInstructionsResult.Success -> {
                    // Keep success quiet to avoid noisy UX.
                }
                is PresentationBuddyService.LoadInstructionsResult.Failure -> {
                    notifyError(project, "Could Not Load Instructions", result.message)
                }
            }
        }
    }

    private fun validateInstructionFile(project: Project, file: VirtualFile): Boolean {
        if (!file.isValid || file.isDirectory) {
            notifyError(project, "Invalid File", "Please select a valid JSON file.")
            return false
        }

        if (!file.extension.equals("json", ignoreCase = true)) {
            notifyError(project, "Invalid File Type", "Instruction files must use the .json extension.")
            return false
        }

        if (file.length == 0L) {
            notifyError(project, "Empty File", "The selected instructions file is empty.")
            return false
        }

        if (file.length > MAX_INSTRUCTIONS_FILE_SIZE_BYTES) {
            notifyError(
                project,
                "File Too Large",
                "Instruction files must be 1 MB or smaller. Selected size: ${file.length} bytes.",
            )
            return false
        }

        return true
    }

    private fun notifyError(project: Project, title: String, content: String) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("KPresentationBuddyNotifications")
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }
}
