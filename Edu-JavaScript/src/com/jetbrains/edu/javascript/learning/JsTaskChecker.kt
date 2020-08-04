package com.jetbrains.edu.javascript.learning

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.lang.javascript.ui.NodeModuleNamesUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkAdapter
import com.jetbrains.edu.javascript.learning.messages.EduJavaScriptBundle
import com.jetbrains.edu.learning.checker.CheckResult
import com.jetbrains.edu.learning.checker.EduTaskCheckerBase
import com.jetbrains.edu.learning.checker.EnvironmentChecker
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseDir
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.ext.getAllTestFiles
import com.jetbrains.edu.learning.courseFormat.tasks.EduTask
import com.jetbrains.edu.learning.messages.EduCoreBundle
import javax.swing.event.HyperlinkEvent

open class JsTaskChecker(task: EduTask, envChecker: EnvironmentChecker, project: Project) : EduTaskCheckerBase(task, envChecker, project) {

  override fun createTestConfigurations(): List<RunnerAndConfigurationSettings> {
    return task.getAllTestFiles(project).mapNotNull { ConfigurationContext(it).configuration }
  }

  // It is tested only with Jest so may not work with other JS test frameworks
  override fun getComparisonErrorMessage(node: SMTestProxy): String = extractComparisonErrorMessage(node)

  override fun getErrorMessage(node: SMTestProxy): String {
    val failedMessageStart = "Failed: \""
    return if (node.errorMessage.startsWith(failedMessageStart))
      node.errorMessage.substringAfter(failedMessageStart).substringBeforeLast('"').replace("\\\"", "\"")
    else node.errorMessage
  }

  override fun validateConfiguration(configuration: RunnerAndConfigurationSettings): CheckResult? {
    return try {
      configuration.checkSettings()
      null
    }
    catch (e: RuntimeConfigurationError) {
      val baseDir = project.course?.getDir(project.courseDir)
      val packageJson = baseDir?.findChild(NodeModuleNamesUtil.PACKAGE_JSON) ?: return null
      val message = """${EduCoreBundle.message("check.no.tests")}. ${EduJavaScriptBundle.message("install.dependencies")}."""
      CheckResult(CheckStatus.Unchecked, message, hyperlinkListener = object : HyperlinkAdapter() {
        override fun hyperlinkActivated(e: HyperlinkEvent?) {
          installNodeDependencies(project, packageJson)
        }
      })
    }
  }
}
