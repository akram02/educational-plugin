package com.jetbrains.edu.coursecreator.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.jetbrains.edu.coursecreator.CCUtils.askToWrapTopLevelLessons
import com.jetbrains.edu.coursecreator.CCUtils.isCourseCreator
import com.jetbrains.edu.coursecreator.ui.CCCreateCourseArchiveDialog
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.courseFormat.ext.hasSections
import com.jetbrains.edu.learning.courseFormat.ext.hasTopLevelLessons
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.statistics.EduCounterUsageCollector.createCourseArchive
import com.jetbrains.edu.learning.stepik.StepikUserInfo
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

abstract class CreateCourseArchiveAction(text: Supplier<String>) : DumbAwareAction(text) {

  constructor(@Nls(capitalization = Nls.Capitalization.Title) text: String) : this(Supplier { text })

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project
    presentation.isEnabledAndVisible = project != null && isCourseCreator(project)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val course = StudyTaskManager.getInstance(project).course ?: return

    if (course.hasSections && course.hasTopLevelLessons) {
      if (!askToWrapTopLevelLessons(project, (course as EduCourse))) {
        return
      }
    }

    val dlg = CCCreateCourseArchiveDialog(project, course.name, showAuthorField())
    if (!dlg.showAndGet()) {
      return
    }

    val locationPath = dlg.locationPath

    if (showAuthorField()) {
      val authorName = dlg.authorName
      course.authors = listOf(StepikUserInfo(authorName))
      PropertiesComponent.getInstance(project).setValue(AUTHOR_NAME, authorName)
    }

    val errorMessage = createCourseArchive(project, locationPath)
    if (errorMessage == null) {
      invokeLater {
        Messages.showInfoMessage(EduCoreBundle.message("dialog.message.course.archive.saved.to", locationPath),
                                 EduCoreBundle.message("action.create.course.archive.success.message"))
      }
      PropertiesComponent.getInstance(project).setValue(LAST_ARCHIVE_LOCATION, locationPath)
      createCourseArchive()
    }
    else {
      Messages.showErrorDialog(project, errorMessage, EduCoreBundle.message("error.failed.to.create.course.archive"))
    }
  }

  /**
   * @return null if course archive was created successfully, non-empty error message otherwise
   */
  fun createCourseArchive(project: Project, location: String): String? {
    FileDocumentManager.getInstance().saveAllDocuments()
    return ApplicationManager.getApplication().runWriteAction<String>(getArchiveCreator(project, location))
  }

  abstract fun showAuthorField(): Boolean
  abstract fun getArchiveCreator(project: Project, location: String): CourseArchiveCreator

  companion object {
    @NonNls
    const val LAST_ARCHIVE_LOCATION = "Edu.CourseCreator.LastArchiveLocation"
    @NonNls
    const val AUTHOR_NAME = "Edu.Author.Name"
  }
}
