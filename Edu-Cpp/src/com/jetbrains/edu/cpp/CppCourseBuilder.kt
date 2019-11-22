package com.jetbrains.edu.cpp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.IncorrectOperationException
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cmake.CMakeListsFileType
import com.jetbrains.edu.coursecreator.actions.NewStudyItemInfo
import com.jetbrains.edu.coursecreator.actions.StudyItemType
import com.jetbrains.edu.learning.*
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.Lesson
import com.jetbrains.edu.learning.courseFormat.TaskFile
import com.jetbrains.edu.learning.courseFormat.ext.getVirtualFile
import com.jetbrains.edu.learning.courseFormat.ext.project
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.courseGeneration.GeneratorUtils
import com.jetbrains.edu.learning.newproject.CourseProjectGenerator

class CppCourseBuilder(
  override val taskTemplateName: String,
  override val testTemplateName: String
) : EduCourseBuilder<CppProjectSettings> {
  override fun getCourseProjectGenerator(course: Course): CourseProjectGenerator<CppProjectSettings>? =
    CppCourseProjectGenerator(this, course)

  override fun createDefaultTestFile(task: Task): TaskFile? =
    super.createDefaultTestFile(task)?.apply { name = GeneratorUtils.joinPaths(EduNames.TEST, CppBaseConfigurator.TEST_CPP) }

  override fun getLanguageSettings(): LanguageSettings<CppProjectSettings> = CppLanguageSettings()

  override fun initNewTask(project: Project, lesson: Lesson, task: Task, info: NewStudyItemInfo) {
    super.initNewTask(project, lesson, task, info)

    val cMakeProjectName = getCMakeProjectUniqueName(task) { FileUtil.sanitizeFileName(it.name, true) }
    task.addCMakeList(cMakeProjectName, getLanguageSettings().settings.languageStandard)
  }

  override fun getTextForNewTask(taskFile: TaskFile, taskDir: VirtualFile, newTask: Task): String? {
    if (taskFile.name != CMakeListsFileType.FILE_NAME) {
      return super.getTextForNewTask(taskFile, taskDir, newTask)
    }

    val project = newTask.project
    if (project == null) {
      LOG.warn("Cannot get project by the task `${newTask.name}`")
      return null
    }

    val virtualFile = taskFile.getVirtualFile(project)
    if (virtualFile == null) {
      LOG.warn("Cannot get a virtual file from the '${CMakeListsFileType.FILE_NAME}' task file")
      return null
    }

    val psiFile = PsiUtilBase.getPsiFile(project, virtualFile).copy() as PsiFile
    val projectCommand = psiFile.findCMakeCommand("project")

    if (projectCommand != null) {
      val newProjectName = getCMakeProjectUniqueName(newTask) { FileUtil.sanitizeFileName(it.name, true) }
      run {
        val cMakeCommandArguments = projectCommand.cMakeCommandArguments ?: return@run
        val firstArgument = cMakeCommandArguments.cMakeArgumentList.firstOrNull() ?: return@run
        try {
          firstArgument.setName(newProjectName)
        }
        catch (e: IncorrectOperationException) {
          LOG.warn("Cannot set project name for a new template task CMakeLists.txt file.", e)
        }
      }
    }

    return psiFile.text
  }

  override fun refreshProject(project: Project, cause: RefreshCause) {
    // if it is a new project it will be initialized, else it will be reloaded only.
    CMakeWorkspace.getInstance(project).selectProjectDir(VfsUtil.virtualToIoFile(project.courseDir))
  }

  override fun validateItemName(name: String, itemType: StudyItemType): String? =
    if (name.matches(STUDY_ITEM_NAME_PATTERN)) null else "Name should contain only latin letters, digits, spaces or '_' symbols."

  companion object {
    private val LOG: Logger = Logger.getInstance(CppCourseBuilder::class.java)

    private val STUDY_ITEM_NAME_PATTERN = "[a-zA-Z0-9_ ]+".toRegex()
  }
}