package com.jetbrains.edu.python.learning

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkUpdater

fun updateOrShowError(sdk: Sdk, project: Project) = PythonSdkUpdater.updateOrShowError(sdk, null, project, null)
