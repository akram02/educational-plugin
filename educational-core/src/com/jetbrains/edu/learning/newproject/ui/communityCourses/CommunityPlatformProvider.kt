package com.jetbrains.edu.learning.newproject.ui.communityCourses

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.ZipUtil
import com.jetbrains.edu.learning.EduUtils
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.newproject.ui.CoursesPanel
import com.jetbrains.edu.learning.newproject.ui.CoursesPlatformProvider
import com.jetbrains.edu.learning.newproject.ui.coursePanel.groups.CoursesGroup
import com.jetbrains.edu.learning.stepik.api.StepikCoursesProvider
import icons.EducationalCoreIcons
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException
import javax.swing.Icon

class CommunityPlatformProvider(private val coursesProvider: StepikCoursesProvider) : CoursesPlatformProvider() {
  private val bundledCoursesNames = listOf("Kotlin Koans.zip", "Introduction to Python.zip")

  override val name: String
    get() = EduCoreBundle.message("course.dialog.community.courses")

  override val icon: Icon get() = EducationalCoreIcons.CommunityCourses

  override fun createPanel(scope: CoroutineScope): CoursesPanel = CommunityCoursesPanel(this, coursesProvider, scope)

  override suspend fun loadCourses(): List<CoursesGroup> {
    val groups = mutableListOf<CoursesGroup>()
    val privateCourses = coursesProvider.getPrivateCourses()
    if (privateCourses.isNotEmpty()) {
      groups.add(CoursesGroup(EduCoreBundle.message("course.dialog.private.courses.group"), privateCourses))
    }

    val featuredCourses = coursesProvider.getFeaturedCourses()
    val bundledCourses = loadBundledCourses().filter { bundled ->
      featuredCourses.none { featured ->
        featured.name != bundled.name
      }
    }
    val bundledFeaturedGroup = bundledCourses + featuredCourses
    if (bundledFeaturedGroup.isNotEmpty()) {
      groups.add(CoursesGroup(EduCoreBundle.message("course.dialog.featured.courses.group"), bundledFeaturedGroup))
    }

    val otherCourses = coursesProvider.getAllOtherCourses()
    if (otherCourses.isNotEmpty()) {
      groups.add(CoursesGroup(EduCoreBundle.message("course.dialog.other.courses"), otherCourses))
    }

    return groups
  }

  private fun loadBundledCourses(): List<Course> {
    val courses = mutableListOf<Course>()
    for (path in getBundledCoursesPaths()) {
      val localCourse = EduUtils.getLocalCourse(path)
      if (localCourse == null) {
        LOG.error("Failed to import local course form $path")
        continue
      }
      courses.add(localCourse)
    }
    return courses
  }

  private fun getBundledCoursesPaths(): List<String> {
    return bundledCoursesNames.map { FileUtil.join(getBundledCourseRoot(it, javaClass).absolutePath, it) }
  }

  private fun getBundledCourseRoot(courseName: String, clazz: Class<*>): File {
    @NonNls val jarPath = PathUtil.getJarPathForClass(clazz)
    if (jarPath.endsWith(".jar")) {
      val jarFile = File(jarPath)
      val pluginBaseDir = jarFile.parentFile
      val coursesDir = File(pluginBaseDir, "courses")

      if (!coursesDir.exists()) {
        if (!coursesDir.mkdir()) {
          LOG.info("Failed to create courses dir")
          return coursesDir
        }
      }
      try {
        ZipUtil.extract(jarFile, pluginBaseDir) { _, name -> name == courseName }
      }
      catch (e: IOException) {
        LOG.info("Failed to extract default course", e)
      }
      return coursesDir
    }
    return File(jarPath, "courses")
  }


  companion object {
    private val LOG = Logger.getInstance(CommunityPlatformProvider::class.java)
  }
}