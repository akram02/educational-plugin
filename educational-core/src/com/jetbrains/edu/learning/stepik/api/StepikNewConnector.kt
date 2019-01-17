package com.jetbrains.edu.learning.stepik.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.edu.learning.EduSettings
import com.jetbrains.edu.learning.courseFormat.*
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.stepik.*
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.apache.http.HttpStatus
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.*
import java.util.concurrent.TimeUnit

object StepikNewConnector {
  private const val MAX_REQUEST_PARAMS = 100 // restriction of Stepik API for multiple requests
  private val LOG = Logger.getInstance(StepikNewConnector::class.java)
  private val converterFactory: JacksonConverterFactory

  init {
    converterFactory = JacksonConverterFactory.create(objectMapper)
  }

  val objectMapper: ObjectMapper
    get() {
      val module = SimpleModule()
      val objectMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
      objectMapper.addMixIn(EduCourse::class.java, StepikEduCourseMixin::class.java)
      objectMapper.addMixIn(Section::class.java, StepikSectionMixin::class.java)
      objectMapper.addMixIn(Lesson::class.java, StepikLessonMixin::class.java)
      objectMapper.addMixIn(TaskFile::class.java, StepikTaskFileMixin::class.java)
      objectMapper.addMixIn(AnswerPlaceholder::class.java, StepikAnswerPlaceholderMixin::class.java)
      objectMapper.addMixIn(AnswerPlaceholderDependency::class.java, StepikAnswerPlaceholderDependencyMixin::class.java)
      objectMapper.addMixIn(FeedbackLink::class.java, StepikFeedbackLinkMixin::class.java)
      objectMapper.addMixIn(StepikSteps.StepOptions::class.java, StepOptionsMixin::class.java)
      objectMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
      objectMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
      objectMapper.registerModule(module)
      return objectMapper
    }

  private val authorizationService: StepikOAuthService
    get() {
      val retrofit = Retrofit.Builder()
        .baseUrl(StepikNames.STEPIK_URL)
        .addConverterFactory(converterFactory)
        .build()

      return retrofit.create(StepikOAuthService::class.java)
    }

  private val service: StepikService
    get() = service(EduSettings.getInstance().user)

  private fun service(account: StepikUser?): StepikService {
    if (account != null && !account.tokenInfo.isUpToDate()) {
      account.refreshTokens()
    }

    val dispatcher = Dispatcher()
    dispatcher.maxRequests = 10

    val okHttpClient = OkHttpClient.Builder()
      .readTimeout(60, TimeUnit.SECONDS)
      .connectTimeout(60, TimeUnit.SECONDS)
      .addInterceptor { chain ->
        val tokenInfo = account?.tokenInfo
        if (tokenInfo == null) return@addInterceptor chain.proceed(chain.request())

        val newRequest = chain.request().newBuilder()
          .addHeader("Authorization", "Bearer ${tokenInfo.accessToken}")
          .build()
        chain.proceed(newRequest)
      }
      .dispatcher(dispatcher)
      .build()

    val retrofit = Retrofit.Builder()
      .baseUrl(StepikNames.STEPIK_API_URL_SLASH)
      .addConverterFactory(converterFactory)
      .client(okHttpClient)
      .build()

    return retrofit.create(StepikService::class.java)
  }

  private fun StepikUser.refreshTokens() {
    val refreshToken = tokenInfo.refreshToken
    val tokens = authorizationService.refreshTokens("refresh_token", StepikNames.CLIENT_ID, refreshToken).execute().body()
    if (tokens != null) {
      updateTokens(tokens)
    }
  }

  private fun getCurrentUserInfo(stepikUser: StepikUser): StepikUserInfo? {
    return service(stepikUser).getCurrentUser().execute().body()?.users?.firstOrNull()
  }

  fun login(code: String, redirectUri: String): Boolean {
    val tokenInfo = authorizationService.getTokens(StepikNames.CLIENT_ID, redirectUri,
                                                   code, "authorization_code").execute().body() ?: return false
    val stepikUser = StepikUser(tokenInfo)
    val stepikUserInfo = getCurrentUserInfo(stepikUser) ?: return false
    stepikUser.userInfo = stepikUserInfo
    EduSettings.getInstance().user = stepikUser
    return true
  }

  fun isEnrolledToCourse(courseId: Int, stepikUser: StepikUser): Boolean {
    val response = service(stepikUser).enrollments(courseId).execute()
    return response.code() == HttpStatus.SC_OK
  }

  fun enrollToCourse(courseId: Int, stepikUser: StepikUser) {
    val response = service(stepikUser).enrollments(EnrollmentData(courseId)).execute()
    if (response.code() != HttpStatus.SC_CREATED) {
      LOG.error("Failed to enroll user ${stepikUser.id} to course $courseId")
    }
  }

  fun getCourses(isPublic: Boolean, currentPage: Int, enrolled: Boolean?) =
    service.courses(true, isPublic, currentPage, enrolled).execute().body()

  fun getCourseInfo(courseId: Int, isIdeaCompatible: Boolean?): EduCourse? {
    val course = service.courses(courseId, isIdeaCompatible).execute().body()?.courses?.firstOrNull()
    if (course != null) {
      setCourseLanguage(course)
    }
    return course
  }

  fun getUsers(result: List<EduCourse>): MutableList<StepikUserInfo> {
    val instructorIds = result.flatMap { it -> it.instructors }.distinct().chunked(MAX_REQUEST_PARAMS)
    val allUsers = mutableListOf<StepikUserInfo>()
    instructorIds
      .mapNotNull { service.users(*it.toIntArray()).execute().body()?.users }
      .forEach { allUsers.addAll(it) }
    return allUsers
  }

  fun getSections(sectionIds: List<Int>): List<Section> {
    val sectionIdsChunks = sectionIds.distinct().chunked(MAX_REQUEST_PARAMS)
    val allSections = mutableListOf<Section>()
    sectionIdsChunks
      .mapNotNull { service.sections(*it.toIntArray()).execute().body()?.sections }
      .forEach { allSections.addAll(it) }
    return allSections
  }

  fun getSection(sectionId: Int): Section? {
    return service.sections(sectionId).execute().body()?.sections?.firstOrNull()
  }

  fun getLesson(lessonId: Int): Lesson? {
    return service.lessons(lessonId).execute().body()?.lessons?.firstOrNull()
  }

  fun getLessons(lessonIds: List<Int>): List<Lesson> {
    val lessonsIdsChunks = lessonIds.distinct().chunked(MAX_REQUEST_PARAMS)
    val allLessons = mutableListOf<Lesson>()
    lessonsIdsChunks
      .mapNotNull { service.lessons(*it.toIntArray()).execute().body()?.lessons }
      .forEach { allLessons.addAll(it) }
    return allLessons
  }

  fun getUnits(unitIds: List<Int>): List<StepikWrappers.Unit> {
    val unitsIdsChunks = unitIds.distinct().chunked(MAX_REQUEST_PARAMS)
    val allUnits = mutableListOf<StepikWrappers.Unit>()
    unitsIdsChunks
      .mapNotNull { service.units(*it.toIntArray()).execute().body()?.units }
      .forEach { allUnits.addAll(it) }
    return allUnits
  }

  fun getUnit(unitId: Int): StepikWrappers.Unit? {
    return service.units(unitId).execute().body()?.units?.firstOrNull()
  }

  fun getStepSources(stepIds: List<Int>): List<StepikSteps.StepSource> {
    // TODO: use language parameter
    val stepsIdsChunks = stepIds.distinct().chunked(MAX_REQUEST_PARAMS)
    val steps = mutableListOf<StepikSteps.StepSource>()
    stepsIdsChunks
      .mapNotNull { service.steps(*it.toIntArray()).execute().body()?.steps }
      .forEach { steps.addAll(it) }
    return steps
  }

  fun getStep(stepId: Int): StepikSteps.StepSource? {
    return service.steps(stepId).execute().body()?.steps?.firstOrNull()
  }

  private fun getSubmissions(isSolved: Boolean, stepId: Int) =
    service.submissions(status = if (isSolved) "correct" else "wrong", step = stepId).execute().body()?.submissions

  fun getLastSubmission(stepId: Int, isSolved: Boolean, language: String): StepikWrappers.Reply? {
    // TODO: make use of language
    val submissions = getSubmissions(isSolved, stepId)
    return submissions?.firstOrNull()?.reply
  }

  fun getSolutionForStepikAssignment(task: Task, isSolved: Boolean): HashMap<String, String> {
    val taskFileToText = HashMap<String, String>()
    val submissions = getSubmissions(isSolved, task.stepId)
    if (submissions == null) {
      return taskFileToText
    }
    val courseLanguage = task.lesson.course.languageById
    val stepikLanguage = StepikLanguages.langOfId(courseLanguage!!.id).langName
    for (submission in submissions) {
      val reply = submission.reply
      if (stepikLanguage != null && stepikLanguage == reply.language) {
        val taskFiles = task.taskFiles.values
        assert(taskFiles.size == 1)
        for (taskFile in taskFiles) {
          taskFileToText[taskFile.name] = reply.code
        }
      }
    }

    return taskFileToText
  }

  fun taskStatuses(ids: List<String>): List<Boolean>? {
    val idsChunks = ids.distinct().chunked(MAX_REQUEST_PARAMS)
    val progresses = mutableListOf<Progress>()
    idsChunks
      .mapNotNull { service.progresses(*it.toTypedArray()).execute().body()?.progresses }
      .forEach { progresses.addAll(it) }

    val progressesMap = progresses.associate { it.id to it.isPassed }
    return ids.mapNotNull { progressesMap[it] }
  }
}
