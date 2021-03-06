package com.jetbrains.edu.gradle

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.jetbrains.edu.jvm.gradle.GradleCourseRefresher
import com.jetbrains.edu.learning.EduCourseBuilder
import com.jetbrains.edu.learning.EduUtils
import com.jetbrains.edu.learning.RefreshCause
import com.jetbrains.edu.learning.isUnitTestMode
import com.jetbrains.edu.learning.projectView.CourseViewPane

class StudioGradleCourseRefresher : GradleCourseRefresher {
  override fun isAvailable(): Boolean = EduUtils.isAndroidStudio()

  override fun refresh(project: Project, cause: RefreshCause) {
    if (cause == RefreshCause.PROJECT_CREATED && !isUnitTestMode) return

    val syncListener = object : GradleSyncListener {
      override fun syncSucceeded(project: Project) {
        ExternalSystemUtil.invokeLater(project, ModalityState.NON_MODAL) {
          ProjectView.getInstance(project).changeViewCB(CourseViewPane.ID, null)
        }
      }
    }
    val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED)
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, syncListener)
  }
}
