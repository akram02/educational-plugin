package com.jetbrains.edu.coursecreator.actions.stepik

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.edu.coursecreator.CCNotificationUtils.showErrorNotification
import com.jetbrains.edu.coursecreator.CCNotificationUtils.showNotification
import com.jetbrains.edu.coursecreator.stepik.CCStepikConnector
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.messages.EduCoreBundle

@Suppress("ComponentNotRegistered") // educational-core.xml
class UpdateAdditionalMaterials : DumbAwareAction(EduCoreBundle.lazyMessage("action.update.additional.materials.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val course = StudyTaskManager.getInstance(project).course as? EduCourse ?: return
    if (!course.isRemote) {
      return
    }
    ProgressManager.getInstance().run(object : Task.Modal(
      project,
      EduCoreBundle.message("action.update.additional.materials.action"),
      false
    ) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false
        if (!CCStepikConnector.updateCourseAdditionalInfo(project, course)) {
          showErrorNotification(
            project,
            EduCoreBundle.message("error.failed.to.update"),
            EduCoreBundle.message("error.failed.to.update.additional.materials")
          )
        }
        else {
          showNotification(project, EduCoreBundle.message("action.update.additional.materials.updated"), null)
        }
      }
    })
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabledAndVisible = false
    val project = e.project ?: return
    val course = StudyTaskManager.getInstance(project).course as? EduCourse ?: return
    if (!course.isRemote || course.isStudy) {
      return
    }
    presentation.isEnabledAndVisible = true
  }
}