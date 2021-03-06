package com.jetbrains.edu.learning.marketplace.api

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.templates.github.DownloadUtil
import com.intellij.util.messages.Topic
import com.jetbrains.edu.coursecreator.CCNotificationUtils.FAILED_TITLE
import com.jetbrains.edu.coursecreator.CCNotificationUtils.getErrorMessage
import com.jetbrains.edu.coursecreator.CCNotificationUtils.showErrorNotification
import com.jetbrains.edu.coursecreator.CCNotificationUtils.showNoRightsToUpdateNotification
import com.jetbrains.edu.coursecreator.CCNotificationUtils.showNotification
import com.jetbrains.edu.learning.*
import com.jetbrains.edu.learning.authUtils.OAuthUtils.GrantType.AUTHORIZATION_CODE
import com.jetbrains.edu.learning.authUtils.OAuthUtils.GrantType.REFRESH_TOKEN
import com.jetbrains.edu.learning.authUtils.OAuthUtils.checkBuiltinPortValid
import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.marketplace.*
import com.jetbrains.edu.learning.marketplace.api.GraphqlQuery.LOADING_STEP
import com.jetbrains.edu.learning.marketplace.settings.MarketplaceSettings
import com.jetbrains.edu.learning.messages.EduCoreBundle.message
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer
import okhttp3.ConnectionPool
import org.apache.http.HttpStatus
import retrofit2.converter.jackson.JacksonConverterFactory
import java.io.File

abstract class MarketplaceConnector {
  private var authorizationBusConnection = ApplicationManager.getApplication().messageBus.connect()

  private val connectionPool: ConnectionPool = ConnectionPool()
  private val converterFactory: JacksonConverterFactory
  val objectMapper: ObjectMapper

  protected abstract val authUrl: String

  protected abstract val repositoryUrl: String

  init {
    val module = SimpleModule()
    objectMapper = createMapper(module)
    converterFactory = JacksonConverterFactory.create(objectMapper)
  }

  private val authorizationService: MarketplaceAuthService
    get() {
      val retrofit = createRetrofitBuilder(authUrl, connectionPool)
        .addConverterFactory(converterFactory)
        .build()

      return retrofit.create(MarketplaceAuthService::class.java)
    }

  private val repositoryService: MarketplaceRepositoryService
    get() = repositoryService(MarketplaceSettings.INSTANCE.account)

  private fun repositoryService(account: MarketplaceAccount?): MarketplaceRepositoryService {
    if (!isUnitTestMode && account != null && !account.tokenInfo.isUpToDate()) {
      account.refreshTokens()
    }

    val retrofit = createRetrofitBuilder(repositoryUrl, connectionPool, accessToken = account?.tokenInfo?.accessToken)
      .addConverterFactory(converterFactory)
      .build()

    return retrofit.create(MarketplaceRepositoryService::class.java)
  }

  // Authorization requests:

  fun doAuthorize(vararg postLoginActions: Runnable) {
    if (!checkBuiltinPortValid()) return

    createAuthorizationListener(*postLoginActions)
    BrowserUtil.browse(HUB_AUTHORISATION_CODE_URL)
  }

  fun login(code: String): Boolean {
    val response = authorizationService.getTokens(EDU_CLIENT_ID,
                                                  EDU_CLIENT_SECRET,
                                                  REDIRECT_URI, code,
                                                  AUTHORIZATION_CODE).executeHandlingExceptions()
    val tokenInfo = response?.body() ?: return false
    val userId = decodeHubToken(tokenInfo.accessToken) ?: return false
    val account = MarketplaceAccount()
    account.tokenInfo = tokenInfo
    val currentUser = getCurrentUser(userId) ?: return false
    account.userInfo = currentUser
    MarketplaceSettings.INSTANCE.account = account
    ApplicationManager.getApplication().messageBus.syncPublisher(AUTHORIZATION_TOPIC).userLoggedIn()
    return true
  }

  private fun MarketplaceAccount.refreshTokens() {
    val refreshToken = tokenInfo.refreshToken
    val response = authorizationService.refreshTokens(REFRESH_TOKEN,
                                                      EDU_CLIENT_ID,
                                                      EDU_CLIENT_SECRET,
                                                      refreshToken).executeHandlingExceptions()
    val tokens = response?.body() ?: return
    // hub documentation https://www.jetbrains.com/help/hub/Refresh-Token.html#AccessTokenRequestError
    // says that new refresh token may be issued by hub, but that's not obligatory,
    // old refresh token should be used in this case
    if (tokens.refreshToken.isEmpty()) {
      tokens.refreshToken = refreshToken
    }
    updateTokens(tokens)
  }

  // Get requests:

  private fun getCurrentUser(userId: String): MarketplaceUserInfo? {
    val response = authorizationService.getCurrentUserInfo(userId).executeHandlingExceptions()
    val userInfo = response?.body() ?: return null
    if (userInfo.guest) {
      // it means that session is broken and we should force user to relogin
      LOG.warn("User ${userInfo.name} is anonymous")
      MarketplaceSettings.INSTANCE.account = null
      return null
    }
    return userInfo
  }

  fun searchCourses(): List<EduCourse> {
    var offset = 0
    val courses = mutableListOf<EduCourse>()

    do {
      val coursesList = getCourses(offset) ?: return courses
      val loadedCourses = coursesList.courses
      if (loadedCourses.isEmpty()) return courses
      courses.addAll(loadedCourses)
      offset += LOADING_STEP
    }
    while (courses.size < coursesList.total)

    return courses.toList()
  }

  private fun getCourses(offset: Int): CoursesList? {
    val query = QueryData(GraphqlQuery.search(offset))
    val response = repositoryService.search(query).executeHandlingExceptions()
    return response?.body()?.data?.coursesList
  }

  fun searchCourse(marketplaceId: Int): EduCourse? {
    val query = QueryData(GraphqlQuery.searchById(marketplaceId))
    val response = repositoryService.search(query).executeHandlingExceptions()
    val course = response?.body()?.data?.coursesList?.courses?.firstOrNull()
    course?.marketplaceId = marketplaceId
    return course
  }

  fun getLatestCourseUpdateInfo(marketplaceId: Int): UpdateInfo? {
    val response = repositoryService.getUpdateId(QueryData(GraphqlQuery.lastUpdateId(marketplaceId))).executeHandlingExceptions()
    val updateInfoList = response?.body()?.data?.updates?.updateInfoList
    if (updateInfoList == null) {
      error("Update info list for course $marketplaceId is null")
    }
    else {
      return updateInfoList.firstOrNull()
    }
  }

  fun loadCourseStructure(course: EduCourse) {
    val marketplaceId = course.marketplaceId
    val updateInfo = getLatestCourseUpdateInfo(marketplaceId)
    if (updateInfo == null) {
      error("Update info for course $marketplaceId is null")
    }
    course.marketplaceCourseVersion = updateInfo.version
    val link = "$repositoryUrl/plugin/$marketplaceId/update/${updateInfo.updateId}/download"
    val tempFile = FileUtil.createTempFile("marketplace-${course.name}", ".zip", true)
    DownloadUtil.downloadAtomically(null, link, tempFile)

    val unpackedCourse = EduUtils.getLocalEncryptedCourse(tempFile.path) as? EduCourse ?: error(
      message("dialog.title.failed.to.unpack.course"))

    course.items = unpackedCourse.items
    course.additionalFiles = unpackedCourse.additionalFiles
  }

  private fun uploadUnderProgress(message: String, uploadAction: () -> Unit) =
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        ProgressManager.getInstance().progressIndicator.isIndeterminate = true
        EduUtils.execCancelable {
          uploadAction()
        }
      }, message, true, null)

  fun uploadNewCourseUnderProgress(project: Project, course: EduCourse, file: File) {
    uploadUnderProgress(message("action.push.course")) {
      uploadNewCourse(project, course, file)
    }
  }

  private fun uploadNewCourse(project: Project, course: EduCourse, file: File) {
    if (!isUserAuthorized()) return
    LOG.info("Uploading new course from ${file.absolutePath}")
    val response = repositoryService.uploadNewCourse(file.toMultipartBody(), LICENSE_URL.toRequestBody()).executeHandlingExceptions()
    val courseBean = response?.body()
    if (courseBean == null) {
      showErrorNotification(project, FAILED_TITLE, getErrorMessage(course, true))
      return
    }
    course.marketplaceId = courseBean.marketplaceId
    YamlFormatSynchronizer.saveRemoteInfo(course)
    YamlFormatSynchronizer.saveItem(course)

    val message = message("marketplace.push.course.successfully.uploaded", courseBean.name)
    showNotification(project,
                     openOnMarketplaceAction(course.marketplaceId),
                     message,
                     message("marketplace.push.course.successfully.uploaded.message"))
    LOG.info("$message with id ${courseBean.marketplaceId}")
  }

  private fun openOnMarketplaceAction(courseId: Int): AnAction {
    return object : AnAction(message("action.open.on.text", MARKETPLACE)) {
      override fun actionPerformed(e: AnActionEvent) {
        EduBrowser.getInstance().browse(COURSE_URL + courseId)
      }
    }
  }

  fun uploadCourseUpdateUnderProgress(project: Project, course: EduCourse, file: File) {
    uploadUnderProgress(message("action.push.course.updating")) { uploadCourseUpdate(project, course, file) }
  }

  private fun uploadCourseUpdate(project: Project, course: EduCourse, file: File) {
    if (!isUserAuthorized()) return
    LOG.info("Uploading course update from ${file.absolutePath}")
    val response = repositoryService.uploadCourseUpdate(file.toMultipartBody(), course.marketplaceId).executeHandlingExceptions()
    val responseCode = response?.code()
    if (responseCode == null || responseCode == HttpStatus.SC_FORBIDDEN) {
      showNoRightsToUpdateNotification(project, course, MARKETPLACE) { uploadNewCourseUnderProgress(project, course, file) }
      return
    }
    if (responseCode != HttpStatus.SC_CREATED) {
      showErrorNotification(project, FAILED_TITLE, getErrorMessage(course, false))
      return
    }
    val message = message("marketplace.push.course.successfully.updated", course.name, course.marketplaceCourseVersion)
    showNotification(project, message, openOnMarketplaceAction(course.marketplaceId))
    LOG.info(message)
    YamlFormatSynchronizer.saveItem(course)
  }

  private fun isUserAuthorized(): Boolean {
    val user = MarketplaceSettings.INSTANCE.account
    if (user == null) {
      // we check that user isn't null before `postCourse` call
      LOG.warn("User is null when posting the course")
      return false
    }
    return true
  }

  private fun createAuthorizationListener(vararg postLoginActions: Runnable) {
    authorizationBusConnection.disconnect()
    authorizationBusConnection = ApplicationManager.getApplication().messageBus.connect()
    authorizationBusConnection.subscribe(AUTHORIZATION_TOPIC, object : EduLogInListener {
      override fun userLoggedOut() {}

      override fun userLoggedIn() {
        for (action in postLoginActions) {
          action.run()
        }
      }
    })
  }

  private fun createMapper(module: SimpleModule): ObjectMapper {
    val objectMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
    objectMapper.addMixIn(EduCourse::class.java, MarketplaceEduCourseMixin::class.java)
    objectMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
    objectMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
    objectMapper.disable(MapperFeature.AUTO_DETECT_FIELDS)
    objectMapper.disable(MapperFeature.AUTO_DETECT_GETTERS)
    objectMapper.disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
    objectMapper.disable(MapperFeature.AUTO_DETECT_SETTERS)
    objectMapper.registerModule(module)
    return objectMapper
  }

  companion object {
    private val LOG = logger<MarketplaceConnector>()

    @JvmStatic
    val AUTHORIZATION_TOPIC = Topic.create("Edu.marketplaceLoggedIn", EduLogInListener::class.java)

    @JvmStatic
    fun getInstance(): MarketplaceConnector = service()
  }
}