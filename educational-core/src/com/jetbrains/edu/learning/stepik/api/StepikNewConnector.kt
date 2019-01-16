package com.jetbrains.edu.learning.stepik.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.module.SimpleModule
import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.edu.learning.EduSettings
import com.jetbrains.edu.learning.EduSettings.SETTINGS_CHANGED
import com.jetbrains.edu.learning.stepik.StepikNames
import com.jetbrains.edu.learning.stepik.StepikUser
import com.jetbrains.edu.learning.stepik.StepikUserInfo
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.concurrent.TimeUnit

object StepikNewConnector {
  private val converterFactory: JacksonConverterFactory

  init {
    val module = SimpleModule()
    val objectMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
    objectMapper.registerModule(module)
    converterFactory = JacksonConverterFactory.create(objectMapper)
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
    get() {
      val account = EduSettings.getInstance().user
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

  fun login(code: String, redirectUri: String): Boolean {
    val tokenInfo = authorizationService.getTokens(StepikNames.CLIENT_ID, redirectUri,
                                                   code, "authorization_code").execute().body() ?: return false
    val stepikUser = StepikUser(tokenInfo)
    EduSettings.getInstance().user = stepikUser
    val stepikUserInfo = getCurrentUserInfo() ?: return false
    stepikUser.userInfo = stepikUserInfo
    ApplicationManager.getApplication().messageBus.syncPublisher<EduSettings.StudySettingsListener>(SETTINGS_CHANGED).settingsChanged()
    return true
  }

  private fun getCurrentUserInfo(): StepikUserInfo? {
    return service.getCurrentUser().execute().body()?.users?.firstOrNull() ?: return null
  }
}
