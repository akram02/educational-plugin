package com.jetbrains.edu.learning.codeforces.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.codeforces.run.CodeforcesRunConfigurationType.Companion.CONFIGURATION_ID
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.ext.configurator
import org.jetbrains.annotations.NonNls

class CodeforcesRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
  override fun createTemplateConfiguration(project: Project): RunConfiguration {
    return project.course?.configurator?.taskCheckerProvider?.getCodeExecutor()?.createRedirectInputConfiguration(project, this)
           ?: error("Unable to create configuration for ${project.name}")
  }

  @NonNls
  override fun getId(): String {
    return CONFIGURATION_ID
  }
}