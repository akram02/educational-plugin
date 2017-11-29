package com.jetbrains.edu.kotlin.check

import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl
import com.intellij.testFramework.*
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockManager
import com.intellij.util.io.ZipUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.edu.learning.EduUtils
import com.jetbrains.edu.learning.actions.CheckAction
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.intellij.JdkProjectSettings
import com.jetbrains.edu.learning.newproject.CourseProjectGenerator
import com.jetbrains.edu.test.CheckActionListener
import org.junit.Assert
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.InvocationTargetException
import java.util.zip.ZipOutputStream
import javax.swing.SwingUtilities

abstract class CheckersTestBase : UsefulTestCase() {
    private lateinit var myManager: FileEditorManagerImpl
    private lateinit var myOldManager: FileEditorManager
    private lateinit var myOldDockContainers: Set<DockContainer>

    private lateinit var course: Course
    protected lateinit var myProject: Project
    private lateinit var myApplication: IdeaTestApplication

    private lateinit var myTestDir: File

    private val MY_TEST_JDK_NAME = "Test JDK"

    fun doTest() {
        UIUtil.dispatchAllInvocationEvents()

        for (lesson in course.lessons) {
            for (task in lesson.getTaskList()) {
                val taskDir = task.getTaskDir(myProject) ?: return Assert.fail("Cannot find task directory for task ${task.name}")
                val firstTaskFileName = task.getTaskFiles().keys.first()
                val taskFile = taskDir.findFileByRelativePath(firstTaskFileName) ?: continue

                FileEditorManager.getInstance(myProject).openFile(taskFile, true)

                launchAction(taskFile, CheckAction())

                UIUtil.dispatchAllInvocationEvents()
            }
        }
    }

    protected abstract fun getGenerator(course: Course): CourseProjectGenerator<JdkProjectSettings>

    private fun projectName() = getTestName(true)

    private fun launchAction(virtualFile: VirtualFile, action: AnAction) {
        val e = getActionEvent(virtualFile, action)
        action.beforeActionPerformedUpdate(e)
        Assert.assertTrue(e.presentation.isEnabled && e.presentation.isVisible)
        action.actionPerformed(e)
    }

    private fun getActionEvent(virtualFile: VirtualFile, action: AnAction): TestActionEvent {
        val context = MapDataContext()
        context.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(virtualFile))
        context.put<Project>(CommonDataKeys.PROJECT, myProject)
        return TestActionEvent(context, action)
    }

    private fun createEduProject() {
        val zipPath = packCourseToZip()

        course = EduUtils.getLocalCourse(zipPath)
                ?: return Assert.fail("Cannot create course from testData/check/${projectName()} project")

        val jdk = ProjectJdkTable.getInstance().findJdk(MY_TEST_JDK_NAME)
                ?: return Assert.fail("Gradle JDK should be configured in setUp()")

        val sdksModel = ProjectSdksModel()
        sdksModel.addSdk(jdk)

        val settings = JdkProjectSettings(sdksModel, object : JdkComboBox.JdkComboBoxItem() {
            override fun getJdk() = jdk
            override fun getSdkName() = jdk.name
        })

        runInEdtAndWait {
            getGenerator(course).createCourseProject(myTestDir.absolutePath, settings)
            myProject = ProjectManager.getInstance().openProjects.firstOrNull { it.name == UsefulTestCase.TEMP_DIR_MARKER + projectName() }
                    ?: return@runInEdtAndWait Assert.fail("Cannot find project with name ${projectName()}")
        }
    }

    private fun packCourseToZip(): String {
        val testName = projectName()
        val courseFile = File("testData/check/$testName/course.json")
        val zipFile = File(myTestDir, testName + ".zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            ZipUtil.addFileOrDirRecursively(zos, null, File(courseFile.path), courseFile.name, null, null)
            zos.close()
        }
        return zipFile.absolutePath
    }

    override fun setUp() {
        super.setUp()

        CheckActionListener.reset()

        val myJdkHome = IdeaTestUtil.requireRealJdkHome()
        VfsRootAccess.allowRootAccess(testRootDisposable, myJdkHome)

        myApplication = IdeaTestApplication.getInstance()

        object : WriteAction<Any>() {
            override fun run(result: Result<Any>) {
                val oldJdk = ProjectJdkTable.getInstance().findJdk(MY_TEST_JDK_NAME)
                if (oldJdk != null) {
                    ProjectJdkTable.getInstance().removeJdk(oldJdk)
                }
                val jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(myJdkHome))!!
                val jdk = SdkConfigurationUtil.setupSdk(arrayOfNulls(0), jdkHomeDir, JavaSdk.getInstance(), true, null, MY_TEST_JDK_NAME)
                Assert.assertNotNull("Cannot create JDK for " + myJdkHome, jdk)
                ProjectJdkTable.getInstance().addJdk(jdk!!)
            }
        }.execute()

        myTestDir = File(FileUtil.getTempDirectory())
        myTestDir.mkdirs()

        VfsUtil.markDirtyAndRefresh(false, true, true, VfsUtil.findFileByIoFile(myTestDir, true))

        createEduProject()

        InjectedLanguageManagerImpl.pushInjectors(myProject)

        val dockManager = DockManager.getInstance(myProject)
        myOldDockContainers = dockManager.containers
        myManager = FileEditorManagerImpl(myProject, dockManager)
        myOldManager = (myProject as ComponentManagerImpl).registerComponentInstance<FileEditorManager>(FileEditorManager::class.java, myManager)
        (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()
    }

    override fun tearDown() {
        try {
            FileUtilRt.delete(myTestDir)

            LightPlatformTestCase.doTearDown(myProject, myApplication)
            InjectedLanguageManagerImpl.checkInjectorsAreDisposed(myProject)

            object : WriteAction<Any>() {
                override fun run(result: Result<Any>) {
                    val old = ProjectJdkTable.getInstance().findJdk(MY_TEST_JDK_NAME)
                    if (old != null) {
                        SdkConfigurationUtil.removeSdk(old)
                    }
                }
            }.execute()

            DockManager.getInstance(myProject).containers
                    .filterNot { myOldDockContainers.contains(it) }
                    .forEach { Disposer.dispose(it) }

            (myProject as ComponentManagerImpl).registerComponentInstance(FileEditorManager::class.java, myOldManager)
            myManager.closeAllFiles()

            EditorHistoryManager.getInstance(myProject).files.forEach {
                EditorHistoryManager.getInstance(myProject).removeFile(it)
            }

            (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()
        } finally {
            super.tearDown()
        }
    }

    private fun runInEdtAndWait(runnable: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable()
        }
        else {
            try {
                SwingUtilities.invokeAndWait(runnable)
            } catch (e: InvocationTargetException) {
                throw e.cause ?: e
            }
        }
    }
}
