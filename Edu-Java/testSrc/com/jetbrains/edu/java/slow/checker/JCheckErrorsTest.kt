package com.jetbrains.edu.java.slow.checker

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.ui.UIUtil
import com.jetbrains.edu.jvm.slow.checker.JdkCheckerTestBase
import com.jetbrains.edu.learning.checker.CheckActionListener
import com.jetbrains.edu.learning.checker.CheckUtils
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.xmlEscaped
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat

class JCheckErrorsTest : JdkCheckerTestBase() {

  override fun createCourse(): Course = course(language = JavaLanguage.INSTANCE) {
    lesson {
      eduTask("javaCompilationError") {
        javaTaskFile("src/Task.java", """
          public class Task {
            public static final String STRING;
          }
        """)
        javaTaskFile("test/Test.java", """
            class Test {}
        """)
      }
      eduTask("testFail") {
        javaTaskFile("src/Task.java", """
          public class Task {
            public static int foo() {
              return 0;
            }
          }
        """)
        javaTaskFile("test/Test.java", """
          import org.junit.Assert;

          public class Test {
            @org.junit.Test
            public void test() {
              Assert.assertTrue("Task.foo() should return 42", Task.foo() == 42);
            }
          }
        """)
      }
      eduTask("comparisonTestFail") {
        javaTaskFile("src/Task.java", """
          public class Task {
            public static int foo() {
              return 0;
            }
          }
        """)
        javaTaskFile("test/Test.java", """
          import org.junit.Assert;

          public class Test {
            @org.junit.Test
            public void test() {
              Assert.assertEquals(42, Task.foo());
            }
          }
        """)
      }
      eduTask("escapeMessageInFailedTest") {
        javaTaskFile("src/Task.java")
        javaTaskFile("test/Test.java", """
          import org.junit.Assert;

          public class Test {
            @org.junit.Test
            public void test() {
              Assert.assertTrue("<br>", false);
            }
          }
        """)
      }
    }
  }

  fun `test errors`() {
    CheckActionListener.shouldFail()
    CheckActionListener.expectedMessage { task ->
      when (task.name) {
        "javaCompilationError" -> CheckUtils.COMPILATION_FAILED_MESSAGE
        "testFail" -> "Task.foo() should return 42"
        "comparisonTestFail" -> EduCoreBundle.message("check.incorrect")
        "escapeMessageInFailedTest" -> "<br>".xmlEscaped
        else -> error("Unexpected task name: ${task.name}")
      }
    }
    doTest()
  }

  fun `test broken jdk`() {
    UIUtil.dispatchAllInvocationEvents()

    @Suppress("DEPRECATION")
    val jdk = SdkConfigurationUtil.setupSdk(arrayOfNulls(0), myProject.baseDir, JavaSdk.getInstance(), true, null, "Broken JDK")!!
    runWriteAction {
      ProjectRootManager.getInstance(myProject).projectSdk = jdk
      ProjectJdkTable.getInstance().addJdk(jdk)
    }

    CheckActionListener.shouldSkip()
    CheckActionListener.setCheckResultVerifier { _, checkResult ->
      assertThat(checkResult.message, containsString(EduCoreBundle.message("error.failed.to.launch.checking")))
    }

    try {
      doTest()
    }
    finally {
      SdkConfigurationUtil.removeSdk(jdk)
    }
  }
}
