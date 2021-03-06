package com.jetbrains.edu.learning.taskDescription.ui.check

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.Alarm
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.actions.*
import com.jetbrains.edu.learning.checker.CheckResult
import com.jetbrains.edu.learning.checkio.courseFormat.CheckiOMission
import com.jetbrains.edu.learning.codeforces.CodeforcesCopyAndSubmitAction
import com.jetbrains.edu.learning.codeforces.CodeforcesNames.CODEFORCES_TITLE
import com.jetbrains.edu.learning.codeforces.courseFormat.CodeforcesCourse
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.navigation.NavigationUtils
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.taskDescription.createActionLink
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionView
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class CheckPanel(val project: Project, parentDisposable: Disposable) : JPanel(BorderLayout()) {
  private val checkFinishedPanel: JPanel = JPanel(BorderLayout())
  private val checkActionsPanel: JPanel = JPanel(BorderLayout())
  private val linkPanel = JPanel(BorderLayout())
  private val checkDetailsPlaceholder: JPanel = JPanel(BorderLayout())
  private val checkButtonWrapper = JPanel(BorderLayout())
  private val rightActionsToolbar = JPanel(HorizontalLayout(10))
  private val course = project.course
  private val checkTimeAlarm: Alarm = Alarm(parentDisposable)

  init {
    checkActionsPanel.add(checkButtonWrapper, BorderLayout.WEST)
    checkActionsPanel.add(checkFinishedPanel, BorderLayout.CENTER)
    checkActionsPanel.add(createRightActionsToolbar(), BorderLayout.EAST)
    checkActionsPanel.add(linkPanel, BorderLayout.SOUTH)
    add(checkActionsPanel, BorderLayout.CENTER)
    add(checkDetailsPlaceholder, BorderLayout.SOUTH)
  }

  private fun createRightActionsToolbar(): JPanel {
    if (course is CodeforcesCourse) {
      rightActionsToolbar.add(
        createActionLink(EduCoreBundle.message("action.open.on.text", CODEFORCES_TITLE), OpenTaskOnSiteAction.ACTION_ID))
      rightActionsToolbar.add(
        createActionLink(EduCoreBundle.message("codeforces.copy.and.submit"), CodeforcesCopyAndSubmitAction.ACTION_ID))
      return rightActionsToolbar
    }
    rightActionsToolbar.add(createSingleActionToolbar(RevertTaskAction.ACTION_ID))
    rightActionsToolbar.add(createSingleActionToolbar(LeaveCommentAction.ACTION_ID))
    return rightActionsToolbar
  }

  private fun createSingleActionToolbar(actionId: String): JComponent {
    val action = ActionManager.getInstance().getAction(actionId)
    return createSingleActionToolbar(action)
  }

  private fun createSingleActionToolbar(action: AnAction): JComponent {
    val toolbar = ActionManager.getInstance().createActionToolbar(ACTION_PLACE, DefaultActionGroup(action), true)
    //these options affect paddings
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    toolbar.adjustTheSameSize(true)

    val component = toolbar.component
    component.border = JBUI.Borders.empty(5, 0, 0, 0)
    return component
  }

  fun readyToCheck() {
    if (course is HyperskillCourse) {
      linkPanel.add(createActionLink(EduCoreBundle.message("action.open.on.text", EduNames.JBA), OpenTaskOnSiteAction.ACTION_ID, 10, 3))
    }
    checkFinishedPanel.removeAll()
    checkDetailsPlaceholder.removeAll()
    checkTimeAlarm.cancelAllRequests()
  }

  fun checkStarted() {
    readyToCheck()
    val asyncProcessIcon = AsyncProcessIcon("Check in progress")
    val iconPanel = JPanel(BorderLayout())
    iconPanel.add(asyncProcessIcon, BorderLayout.CENTER)
    iconPanel.border = JBUI.Borders.empty(8, 16, 0, 0)
    checkFinishedPanel.add(iconPanel, BorderLayout.WEST)
    updateBackground()
  }

  fun updateCheckDetails(task: Task, result: CheckResult? = null) {
    checkFinishedPanel.removeAll()
    checkFinishedPanel.addNextTaskButton(task)

    val checkResult = result ?: restoreSavedResult(task)
    if (checkResult != null) {
      linkPanel.removeAll()
      checkDetailsPlaceholder.add(CheckDetailsPanel(project, task, checkResult, checkTimeAlarm), BorderLayout.SOUTH)
    }
    updateBackground()
  }

  private fun restoreSavedResult(task: Task): CheckResult? {
    /**
     * We are not showing old result for CheckiO because we store last successful attempt
     * @see com.jetbrains.edu.learning.checkio.courseFormat.CheckiOMission.setStatus
     */
    if (task is CheckiOMission) return null
    if (task.feedback == null && task.status == CheckStatus.Unchecked) return null

    val feedback = task.feedback ?: return CheckResult(task.status, "")
    return feedback.toCheckResult(task.status)
  }

  private fun updateBackground() {
    UIUtil.setBackgroundRecursively(checkFinishedPanel, TaskDescriptionView.getTaskDescriptionBackgroundColor())
    UIUtil.setBackgroundRecursively(checkDetailsPlaceholder, TaskDescriptionView.getTaskDescriptionBackgroundColor())
  }

  fun updateCheckPanel(task: Task) {
    updateCheckButtonWrapper(task)
    updateRightActionsToolbar()
    updateCheckDetails(task)
  }

  private fun updateCheckButtonWrapper(task: Task) {
    checkButtonWrapper.removeAll()
    checkButtonWrapper.add(CheckPanelButtonComponent(CheckAction.createCheckAction(task), true), BorderLayout.WEST)
    checkFinishedPanel.addNextTaskButton(task)
  }

  private fun updateRightActionsToolbar() {
    rightActionsToolbar.removeAll()
    createRightActionsToolbar()
  }

  private fun JPanel.addNextTaskButton(task: Task) {
    val mayAddNext = task.status == CheckStatus.Solved || task is TheoryTask || task.course is HyperskillCourse
    if (mayAddNext && NavigationUtils.nextTask(task) != null) {
      val nextButton = CheckPanelButtonComponent(ActionManager.getInstance().getAction(NextTaskAction.ACTION_ID))
      nextButton.border = JBUI.Borders.empty(0, 12, 0, 0)
      add(nextButton, BorderLayout.WEST)
    }
  }

  fun checkTooltipPosition(): RelativePoint {
    return JBPopupFactory.getInstance().guessBestPopupLocation(checkButtonWrapper)
  }

  companion object {
    const val ACTION_PLACE = "CheckPanel"
  }
}
