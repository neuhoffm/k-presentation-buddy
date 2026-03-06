// Copyright (c) 2025 Advntrs LLC
// SPDX-License-Identifier: MIT

package com.advntrs.kpresentationbuddy

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

/**
 * Application-level service responsible for the typing simulation.
 *
 * The service runs the simulation on a background thread and dispatches
 * individual character insertions onto the EDT via [ApplicationManager.getApplication]
 * to comply with the IntelliJ threading model.
 *
 * The inter-keystroke delay is read from [PresentationBuddySettings] on every
 * invocation so that changes made in Settings take effect without restarting.
 */
@Service(Service.Level.APP)
class PresentationBuddyService {

    /**
     * Types [script] into [editor] one character at a time.
     *
     * The call returns immediately; the actual work runs on a daemon thread.
     * The thread stops automatically if the editor's document becomes invalid
     * (e.g. the user closes the file) so that no writes are attempted on a
     * disposed component.
     */
    fun typeScript(project: Project, editor: Editor, script: String) {
        val settings = ApplicationManager.getApplication()
            .getService(PresentationBuddySettings::class.java)
        val document = editor.document

        Thread {
            for (char in script) {
                // Stop if the project or document has been disposed since
                // the task was started.
                if (project.isDisposed || !document.isWritable) break

                val delayMs = settings.typingDelayMs.coerceIn(
                    PluginConstants.MIN_TYPING_DELAY_MS,
                    PluginConstants.MAX_TYPING_DELAY_MS
                )
                if (delayMs > 0) {
                    Thread.sleep(delayMs)
                }

                // Re-check after sleeping — user may have closed the editor.
                if (project.isDisposed || !document.isWritable) break

                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed && document.isWritable) {
                        ApplicationManager.getApplication().runWriteAction {
                            val caretOffset = editor.caretModel.offset
                            document.insertString(caretOffset, char.toString())
                            editor.caretModel.moveToOffset(caretOffset + 1)
                        }
                    }
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    /**
     * Shows a balloon notification in the IDE.
     *
     * NOTE: [NotificationGroupManager] is the current API (replacing the
     * deprecated `Notifications.Bus.notify` + constructor-based
     * `NotificationGroup` that was removed in 2022.3).
     */
    fun notify(project: Project, message: String, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(PluginConstants.NOTIFICATION_GROUP_ID)
            ?.createNotification(message, type)
            ?.notify(project)
    }
}
