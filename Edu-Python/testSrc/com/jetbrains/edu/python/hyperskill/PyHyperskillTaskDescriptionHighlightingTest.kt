package com.jetbrains.edu.python.hyperskill

import com.intellij.lang.Language
import com.jetbrains.edu.learning.EduUtils
import com.jetbrains.edu.learning.TestContext
import com.jetbrains.edu.learning.stepik.hyperskill.HyperskillTaskDescriptionHighlightingTest
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.newProject.PyNewProjectSettings

class PyHyperskillTaskDescriptionHighlightingTest : HyperskillTaskDescriptionHighlightingTest() {

  override fun runTestInternal(context: TestContext) {
    // Hyperskill python support is not available in Android Studio
    if (!EduUtils.isAndroidStudio()) {
      super.runTestInternal(context)
    }
  }

  override val language: Language
    get() = PythonLanguage.INSTANCE

  override val settings: Any
    get() = PyNewProjectSettings()

  override val codeSample: String
    get() = """print(1)"""
  override val codeSampleWithHighlighting: String
    get() = """<span style="...">print(</span><span style="...">1</span><span style="...">)</span>"""
}
