package com.jetbrains.edu.coursecreator

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.jetbrains.edu.learning.EduDocumentListenerBase
import com.jetbrains.edu.learning.EduUtils
import com.jetbrains.edu.learning.getContainingTask
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionToolWindowFactory.Companion.STUDY_TOOL_WINDOW
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionView

class SynchronizeTaskDescription(project: Project, parentDisposable: Disposable) : EduDocumentListenerBase(project) {
  private val updateQueue = MergingUpdateQueue(TASK_DESCRIPTION_UPDATE, TASK_DESCRIPTION_TIME_SPAN, true, null, parentDisposable)

  override fun documentChanged(event: DocumentEvent) {
    if (!event.isInProjectContent()) return
    if (!EduUtils.isEduProject(project)) return
    val eventDocument = event.document
    val editedFile = fileDocumentManager.getFile(eventDocument) ?: return
    if (editedFile is LightVirtualFile || !EduUtils.isTaskDescriptionFile(editedFile.name)) {
      return
    }
    val task = editedFile.getContainingTask(project) ?: return
    task.descriptionText = eventDocument.text
    if (ToolWindowManager.getInstance(project).getToolWindow(STUDY_TOOL_WINDOW) == null) return

    updateQueue.queue(Update.create(TASK_DESCRIPTION_UPDATE) {
      TaskDescriptionView.getInstance(project).updateTaskDescription(task)
    })
  }

  companion object {
    private const val TASK_DESCRIPTION_UPDATE = "Task Description Update"
    private const val TASK_DESCRIPTION_TIME_SPAN = 1000
  }
}
