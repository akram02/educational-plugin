package com.jetbrains.edu.learning.actions.navigate.hyperskill

import com.jetbrains.edu.learning.actions.NextTaskAction
import com.jetbrains.edu.learning.actions.PreviousTaskAction
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.stepik.hyperskill.HYPERSKILL_PROBLEMS
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse

class HyperskillMixedProblemsNavigationTest : HyperskillNavigateInCourseTestBase() {
  override val course: HyperskillCourse
    get() = createHyperskillCourse(withLegacyProblems = true, withTopicProblems = true)

  override fun getFirstProblem(): Task = course.findTask(HYPERSKILL_PROBLEMS, problem1Name)

  fun `test navigate to next unavailable on last legacy problem`() {
    val problem = course.findTask(HYPERSKILL_PROBLEMS, problem2Name)
    checkNavigationAction(problem, ::NextTaskAction, false)
  }

  fun `test navigate to previous unavailable on first topic problem`() {
    val problem = findTopicProblem(topic1LessonName, problem1Name)
    checkNavigationAction(problem, ::PreviousTaskAction, false)
  }
}