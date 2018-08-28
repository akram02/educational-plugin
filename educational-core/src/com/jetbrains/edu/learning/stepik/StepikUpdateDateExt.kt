@file:JvmName("StepikUpdateDateExt")

package com.jetbrains.edu.learning.stepik

import com.intellij.util.Time
import com.jetbrains.edu.learning.EduSettings
import com.jetbrains.edu.learning.EduSettings.isLoggedIn
import com.jetbrains.edu.learning.courseFormat.Lesson
import com.jetbrains.edu.learning.courseFormat.Section
import com.jetbrains.edu.learning.courseFormat.ext.hasTopLevelLessons
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.isUnitTestMode
import com.jetbrains.edu.learning.stepik.StepikConnector.fillItems
import com.jetbrains.edu.learning.stepik.StepikConnector.getCourseInfo
import com.jetbrains.edu.learning.stepik.courseFormat.StepikCourse
import com.jetbrains.edu.learning.stepik.courseFormat.StepikCourseRemoteInfo
import com.jetbrains.edu.learning.stepik.courseFormat.ext.id
import com.jetbrains.edu.learning.stepik.courseFormat.ext.isCompatible
import com.jetbrains.edu.learning.stepik.courseFormat.ext.stepId
import com.jetbrains.edu.learning.stepik.courseFormat.ext.updateDate
import org.jetbrains.annotations.TestOnly
import java.util.*


fun StepikCourse.isUpToDate(): Boolean {
  if (!isLoggedIn()) {
    return true
  }

  val courseInfo = getCourseInfo(EduSettings.getInstance().user, id, isCompatible) ?: return true
  return isUpToDate(courseInfo)
}

@TestOnly
fun StepikCourse.isUpToDate(courseFromStepik: StepikCourse): Boolean {
  val dateFromServer = courseFromStepik.updateDate

  if (dateFromServer.isSignificantlyAfter(updateDate)) {
    return false
  }

  if (!isUnitTestMode) {
    fillItems(courseFromStepik)
  }

  if (hasNewOrRemovedSections(courseFromStepik) || hasNewOrRemovedTopLevelLessons(courseFromStepik)) {
    return false
  }

  val sectionsFromServer = courseFromStepik.sections.associateBy { it.id }
  val lessonsFromServer = courseFromStepik.lessons.associateBy { it.id }

  return isAdditionalMaterialsUpToDate(courseFromStepik)
         && sections.all { it.isUpToDate(sectionsFromServer[it.id]) }
         && lessons.all {it.isUpToDate(lessonsFromServer[it.id])}
}

fun Section.isUpToDate(sectionFromStepik: Section?): Boolean {
  if (sectionFromStepik == null) {
    return false
  }
  if (id == 0 || !isLoggedIn()) {
    return true
  }

  val lessonsFromStepikById = sectionFromStepik.lessons.associateBy { it.id }
  return !sectionFromStepik.updateDate.isSignificantlyAfter(updateDate)
         && sectionFromStepik.lessons.size == lessons.size
         && lessons.all { it.isUpToDate(lessonsFromStepikById[it.id]) }
}


fun Lesson.isUpToDate(lessonFromStepik: Lesson?): Boolean {
  if (lessonFromStepik == null) {
    return false
  }

  if (id == 0 || !isLoggedIn()) {
    return true
  }

  val lessonsFromServer = lessonFromStepik.taskList.associateBy { it.id }
  return !lessonFromStepik.updateDate.isSignificantlyAfter(updateDate)
         && taskList.size == lessonFromStepik.taskList.size
         && taskList.all { it.isUpToDate(lessonsFromServer[it.id]) }

}

fun Task.isUpToDate(tasksFromServer: Task?): Boolean {
  if (tasksFromServer == null) {
    return false
  }
  if (id == 0 || !isLoggedIn()) {
    return true
  }

  return !tasksFromServer.updateDate.isSignificantlyAfter(updateDate)
}

fun StepikCourse.setUpdated() {
  val courseInfo = getCourseInfo(EduSettings.getInstance().user, id, isCompatible) ?: return
  fillItems(courseInfo)

  updateDate = courseInfo.updateDate

  val lessonsById = courseInfo.lessons.associateBy { it.id }
  lessons.forEach {
    val lessonFromServer = lessonsById[it.id] ?: error("Lesson with id ${it.id} not found")
    it.setUpdated(lessonFromServer)
  }

  val sectionsById = courseInfo.sections.associateBy { it.id }
  sections.forEach {
    val sectionFromServer = sectionsById[it.id] ?: error("Section with id ${it.id} not found")
    it.setUpdated(sectionFromServer)
  }
}

fun Date.isSignificantlyAfter(otherDate: Date): Boolean {
  val diff = time - otherDate.time
  return diff > Time.MINUTE
}

private fun isAdditionalMaterialsUpToDate(courseFromStepik: StepikCourse): Boolean {
  val additionalLesson = courseFromStepik.getLessons(true).singleOrNull { it.isAdditional } ?: return true
  return !additionalLesson.updateDate.isSignificantlyAfter((courseFromStepik.remoteInfo as StepikCourseRemoteInfo).additionalMaterialsUpdateDate)
}

private fun StepikCourse.hasNewOrRemovedSections(courseFromStepik: StepikCourse): Boolean {
  return courseFromStepik.sections.size != sections.size
}

private fun StepikCourse.hasNewOrRemovedTopLevelLessons(courseFromStepik: StepikCourse): Boolean {
  if (!hasTopLevelLessons) {
    return false
  }

  return courseFromStepik.lessons.size != lessons.size
}

private fun Section.setUpdated(sectionFromStepik: Section) {
  updateDate = sectionFromStepik.updateDate
  val lessonsById = sectionFromStepik.lessons.associateBy { it.id }
  lessons.forEach {
    val lessonFromServer = lessonsById[it.id] ?: error("Lesson with id ${it.id} not found")
    it.setUpdated(lessonFromServer)
  }
}

private fun Lesson.setUpdated(lessonFromServer: Lesson) {
  updateDate = lessonFromServer.updateDate
  val tasksById = lessonFromServer.taskList.associateBy { it.id }
  taskList.forEach {
    val taskFromServer = tasksById[it.stepId] ?: error("Task with id ${it.stepId} not found")
    it.updateDate = taskFromServer.updateDate
  }
}
