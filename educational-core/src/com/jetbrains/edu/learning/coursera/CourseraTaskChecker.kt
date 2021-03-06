package com.jetbrains.edu.learning.coursera

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.jetbrains.edu.learning.EduUtils
import com.jetbrains.edu.learning.addProxy
import com.jetbrains.edu.learning.checker.CheckResult
import com.jetbrains.edu.learning.checker.remote.RemoteTaskChecker
import com.jetbrains.edu.learning.courseDir
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.ext.getVirtualFile
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.loadEncodedContent
import okhttp3.*
import org.apache.http.HttpStatus
import org.apache.http.entity.ContentType
import java.time.Duration

class CourseraTaskChecker : RemoteTaskChecker {
  private val checkWithoutCredentials = CheckResult(CheckStatus.Unchecked, "Can't check on remote without credentials")
  override fun canCheck(project: Project, task: Task) =
    EduUtils.isStudentProject(project) && task.course is CourseraCourse


  override fun check(project: Project, task: Task, indicator: ProgressIndicator): CheckResult {
    val course = task.course as CourseraCourse
    if (course.submitManually) {
      val link = getLinkToSubmission(task)
      return CheckResult(CheckStatus.Unchecked, SUBMIT_MANUALLY.format(link))
    }
    val courseraSettings = CourseraSettings.getInstance()
    var askedForCredentials = false
    if (!courseraSettings.haveFullCredentials()) {
      askToEnterCredentials(task)
      askedForCredentials = true

      if (!courseraSettings.haveFullCredentials()) {
        return checkWithoutCredentials
      }
    }

    return try {
      val response = postSubmission(createSubmissionJson(project, task, courseraSettings))
      var responseCode = response.code()
      if (responseCode != HttpStatus.SC_CREATED && !askedForCredentials) {
        askToEnterCredentials(task, createCheckResult(responseCode, task).message)
        responseCode = postSubmission(createSubmissionJson(project, task, courseraSettings)).code()
      }
      createCheckResult(responseCode, task)
    }
    catch (e: Exception) {
      Logger.getInstance(CourseraTaskChecker::class.java).warn(e)
      CheckResult.CONNECTION_FAILED
    }
  }

  private fun createCheckResult(statusCode: Int, task: Task): CheckResult {
    return when (statusCode) {
      HttpStatus.SC_CREATED -> {
        val link = getLinkToSubmission(task)
        CheckResult(CheckStatus.Unchecked, SUCCESS.format(link))
      }
      HttpStatus.SC_UNAUTHORIZED -> CheckResult(CheckStatus.Unchecked, "Invalid token or email")
      HttpStatus.SC_BAD_REQUEST -> CheckResult(CheckStatus.Unchecked, "Token is for a different assignment")
      else -> CheckResult(CheckStatus.Unchecked, "Failed to create new submission: $statusCode error received")
    }
  }

  private fun getLinkToSubmission(task: Task): String {
    return task.feedbackLink.link?.replace("/discussions", "/submission") ?: "https://www.coursera.org/"
  }

  private fun getLinkToToken(task: Task): String {
    return task.feedbackLink.link?.removeSuffix("/discussions") ?: "https://www.coursera.org/"
  }

  @VisibleForTesting
  fun createSubmissionJson(project: Project, task: Task, courseraSettings: CourseraSettings): String {
    val taskDir = task.getDir(project.courseDir) ?: error("No directory for task ${task.name}")

    val assignmentKey = taskDir.getValueFromChildFile(ASSIGNMENT_KEY)
    val partId = taskDir.getValueFromChildFile(PART_ID)

    val output = task.taskFiles.filterValues { it.name != PART_ID && it.name != ASSIGNMENT_KEY }.mapValues {
      val file = it.value.getVirtualFile(project) ?: error("VirtualFile for ${it.key} not found")
      file.loadEncodedContent(isToEncodeContent = true)
    }
    val submission = Submission(assignmentKey, courseraSettings.email, courseraSettings.token,
                                mapOf(Pair(partId, Part(ObjectMapper().writeValueAsString(output)))))
    return ObjectMapper().writeValueAsString(submission)
  }

  private fun postSubmission(json: String): Response {
    val builder = OkHttpClient.Builder()
      .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS.toLong()))
      .callTimeout(Duration.ofSeconds(TIMEOUT_SECONDS.toLong()))
    addProxy(ON_DEMAND_SUBMIT, builder)

    val request = Request.Builder()
      .url(ON_DEMAND_SUBMIT)
      .method("POST", RequestBody.create(MediaType.get(ContentType.APPLICATION_JSON.mimeType), json)).build()

    return builder.build().newCall(request).execute()
  }

  private fun askToEnterCredentials(task: Task, message: String? = null) {
    val courseraSettings = CourseraSettings.getInstance()

    val emailField = JBTextField(courseraSettings.email)
    val tokenField = JBTextField(courseraSettings.token)
    val credentialsPanel = panel {
      if (message != null) {
        val messageLabel = JBLabel(message)
        messageLabel.foreground = JBColor.RED
        messageLabel.withFont(JBUI.Fonts.label().asBold())
        row { messageLabel() }
      }
      row("Email:") { emailField(growPolicy = GrowPolicy.MEDIUM_TEXT) }
      row("Token:") { tokenField(growPolicy = GrowPolicy.MEDIUM_TEXT) }
      noteRow("Token can be obtained <a href=\"${getLinkToToken(task)}\">here</a>")
    }
    var refusedToProvideCredentials = false

    ApplicationManager.getApplication().invokeAndWait {
      refusedToProvideCredentials = !DialogBuilder().centerPanel(credentialsPanel).title(NEED_CREDENTIALS).showAndGet()
    }

    if (!refusedToProvideCredentials) {
      CourseraSettings.getInstance().email = emailField.text
      CourseraSettings.getInstance().token = tokenField.text
    }
  }

  private fun VirtualFile.getValueFromChildFile(fileName: String): String {
    val file = this.findChild(fileName) ?: error("$fileName not found in ${this.path}")
    return VfsUtil.loadText(file)
  }

  private fun CourseraSettings.haveFullCredentials() = email.isNotEmpty() && token.isNotEmpty()

  companion object {
    private const val ON_DEMAND_SUBMIT = "https://www.coursera.org/api/onDemandProgrammingScriptSubmissions.v1"
    private const val NEED_CREDENTIALS = "${CourseraNames.COURSERA} Credentials"
    private const val SUCCESS = "<html>Submission successful, please <a href=\"%s\">check the status on Coursera</a></html>"
    private const val SUBMIT_MANUALLY = "<html>Local tests passed, please <a href=\"%s\">submit to Coursera</a></html>"
    private const val TIMEOUT_SECONDS = 10

    @VisibleForTesting
    const val ASSIGNMENT_KEY = "assignmentKey"

    @VisibleForTesting
    const val PART_ID = "partId"
  }
}