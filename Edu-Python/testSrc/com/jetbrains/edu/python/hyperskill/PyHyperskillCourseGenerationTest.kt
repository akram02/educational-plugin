package com.jetbrains.edu.python.hyperskill

import com.intellij.testFramework.LightPlatformTestCase
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.learning.*
import com.jetbrains.edu.learning.stepik.hyperskill.HyperskillConfigurator.Companion.HYPERSKILL_TEST_DIR
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.python.learning.PyConfigurator.Companion.TASK_PY
import com.jetbrains.edu.python.learning.PyConfigurator.Companion.TESTS_PY
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.newProject.PyNewProjectSettings


class PyHyperskillCourseGenerationTest : EduTestCase() {

  override fun runTestInternal(context: TestContext) {
    // Hyperskill python support is not available in Android Studio
    if (!EduUtils.isAndroidStudio()) {
      super.runTestInternal(context)
    }
  }

  fun `test course structure creation`() {
    courseWithFiles(courseProducer = ::HyperskillCourse, language = PythonLanguage.INSTANCE, courseMode = CCUtils.COURSE_MODE,
                    settings = PyNewProjectSettings()) {}

    checkFileTree {
      dir("lesson1/task1") {
        file(TASK_PY)
        dir(HYPERSKILL_TEST_DIR) {
          file(TESTS_PY)
        }
        file("task.html")
      }
    }
  }

  private fun checkFileTree(block: FileTreeBuilder.() -> Unit) {
    fileTree(block).assertEquals(LightPlatformTestCase.getSourceRoot(), myFixture)
  }
}
