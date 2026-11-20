package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ComicApplication
import com.example.data.AppPreferences
import com.example.gemini.DubbingException
import com.example.gemini.OkHttpGeminiLiveTranslateClient
import com.example.model.AppThemeMode
import com.example.model.AudioSourceMode
import com.example.model.DubbingError
import com.example.model.DubbingUiSnapshot
import com.example.model.LaunchableAppInfo
import com.example.model.TargetLanguage
import com.example.model.UiLanguage
import com.example.service.LiveDubbingForegroundService
import com.example.service.ProjectionPermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface KeyTestResult {
  data object Idle : KeyTestResult
  data object Testing : KeyTestResult
  data object Success : KeyTestResult
  data class Failure(val error: DubbingError) : KeyTestResult
}

data class LiveSetupDraftState(
  val useSpecificApp: Boolean = false,
  val selectedApp: LaunchableAppInfo? = null,
  val apps: List<LaunchableAppInfo> = emptyList(),
  val searchQuery: String = "",
  val isLoadingApps: Boolean = false,
  val targetLanguage: TargetLanguage? = null,
) {
  val filteredApps: List<LaunchableAppInfo>
    get() =
      if (searchQuery.isBlank()) {
        apps
      } else {
        val q = searchQuery.trim().lowercase()
        apps.filter {
          it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
      }
}

class LiveDubbingViewModel(application: Application) : AndroidViewModel(application) {

  private val comicApp = application as ComicApplication
  private val settings = comicApp.appSettings
  private val keyStorage = comicApp.apiKeyStorage
  private val appsRepo = comicApp.installedAppsRepository
  private val dubbingManager = comicApp.liveDubbingManager
  private val projectionPermissionManager = ProjectionPermissionManager(application)

  val isPreferencesLoaded: StateFlow<Boolean> = settings.isLoaded
  val preferences: StateFlow<AppPreferences> = settings.currentPreferencesFlow

  val hasSavedApiKey: StateFlow<Boolean> =
    keyStorage.hasSavedKeyFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = false,
    )

  val sessionSnapshot: StateFlow<DubbingUiSnapshot> = dubbingManager.snapshot

  private val _keyTestResult = MutableStateFlow<KeyTestResult>(KeyTestResult.Idle)
  val keyTestResult: StateFlow<KeyTestResult> = _keyTestResult.asStateFlow()

  private val _setupDraft = MutableStateFlow(LiveSetupDraftState())
  val setupDraft: StateFlow<LiveSetupDraftState> = _setupDraft.asStateFlow()

  val effectiveSetupTargetLanguage: StateFlow<TargetLanguage> =
    combine(_setupDraft, preferences) { draft, prefs ->
      draft.targetLanguage ?: prefs.defaultTargetLanguage
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = preferences.value.defaultTargetLanguage,
    )

  fun loadLaunchableApps() {
    viewModelScope.launch {
      _setupDraft.update { it.copy(isLoadingApps = true) }
      val loaded = appsRepo.getLaunchableApps()
      _setupDraft.update { it.copy(apps = loaded, isLoadingApps = false) }
    }
  }

  fun setSourceModeSpecific(specific: Boolean) {
    _setupDraft.update { it.copy(useSpecificApp = specific) }
    if (specific && _setupDraft.value.apps.isEmpty()) {
      loadLaunchableApps()
    }
  }

  fun selectTargetApp(app: LaunchableAppInfo) {
    _setupDraft.update { it.copy(selectedApp = app, useSpecificApp = true) }
  }

  fun updateAppSearchQuery(query: String) {
    _setupDraft.update { it.copy(searchQuery = query) }
  }

  fun selectSessionTargetLanguage(language: TargetLanguage) {
    _setupDraft.update { it.copy(targetLanguage = language) }
  }

  fun setUiLanguage(language: UiLanguage) {
    viewModelScope.launch { settings.setUiLanguage(language) }
  }

  fun setThemeMode(themeMode: AppThemeMode) {
    viewModelScope.launch { settings.setThemeMode(themeMode) }
  }

  fun setDefaultTargetLanguage(language: TargetLanguage) {
    viewModelScope.launch {
      settings.setDefaultTargetLanguage(language)
      _setupDraft.update { it.copy(targetLanguage = language) }
    }
  }

  fun setDubbedVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1.5f)
    dubbingManager.setVolume(clamped)
    viewModelScope.launch { settings.setDubbedVolume(clamped) }
  }

  fun completeOnboarding() {
    viewModelScope.launch { settings.setOnboardingCompleted(true) }
  }

  fun saveApiKey(rawKey: String, onDone: (Boolean) -> Unit = {}) {
    val trimmed = rawKey.trim()
    if (trimmed.isEmpty()) {
      _keyTestResult.value = KeyTestResult.Failure(DubbingError.InvalidApiKey())
      onDone(false)
      return
    }
    viewModelScope.launch {
      keyStorage.saveKey(trimmed)
      _keyTestResult.value = KeyTestResult.Idle
      onDone(true)
    }
  }

  fun deleteApiKey() {
    viewModelScope.launch {
      keyStorage.deleteKey()
      _keyTestResult.value = KeyTestResult.Idle
    }
  }

  fun testApiKeyConnection(candidateKey: String?) {
    viewModelScope.launch {
      _keyTestResult.value = KeyTestResult.Testing
      val keyToTest =
        candidateKey?.trim().takeUnless { it.isNullOrEmpty() }
          ?: keyStorage.getCredential()?.trim()

      if (keyToTest.isNullOrEmpty()) {
        _keyTestResult.value = KeyTestResult.Failure(DubbingError.InvalidApiKey())
        return@launch
      }

      val targetCode = preferences.value.defaultTargetLanguage.code
      val testClient = OkHttpGeminiLiveTranslateClient(okHttpClient = comicApp.sharedOkHttpClient)
      val result = testClient.testConnection(keyToTest, targetCode)
      if (result.isSuccess) {
        _keyTestResult.value = KeyTestResult.Success
      } else {
        val err =
          (result.exceptionOrNull() as? DubbingException)?.error
            ?: DubbingError.GeminiConnectionFailure(result.exceptionOrNull()?.message)
        _keyTestResult.value = KeyTestResult.Failure(err)
      }
    }
  }

  fun clearKeyTestStatus() {
    _keyTestResult.value = KeyTestResult.Idle
  }

  fun prepareAndLaunchProjectionConsent(onLaunchConsent: (Intent) -> Unit) {
    viewModelScope.launch {
      val draft = _setupDraft.value
      val prefs = settings.preferencesFlow.first()
      val targetLang = draft.targetLanguage ?: prefs.defaultTargetLanguage
      val sourceMode =
        if (draft.useSpecificApp) {
          val app = draft.selectedApp
          if (app == null) {
            dubbingManager.beginValidation(
              AudioSourceMode.SpecificApp("", "", -1),
              targetLang,
              prefs.dubbedVolume,
            )
            return@launch
          }
          AudioSourceMode.SpecificApp(app.packageName, app.label, app.uid)
        } else {
          AudioSourceMode.AllEligible
        }

      val validation = dubbingManager.beginValidation(sourceMode, targetLang, prefs.dubbedVolume)
      if (validation.isFailure) return@launch

      val consentIntent = projectionPermissionManager.createCaptureConsentIntent()
      if (consentIntent == null) {
        dubbingManager.onPermissionDenied(DubbingError.UnsupportedAndroidConfig)
        return@launch
      }
      onLaunchConsent(consentIntent)
    }
  }

  fun onRecordAudioPermissionDenied() {
    viewModelScope.launch {
      dubbingManager.onPermissionDenied(DubbingError.AudioCaptureDenied)
    }
  }

  fun onProjectionConsentResult(resultCode: Int, data: Intent?) {
    viewModelScope.launch {
      if (data == null || resultCode != android.app.Activity.RESULT_OK) {
        dubbingManager.onPermissionCancelled()
        return@launch
      }
      dubbingManager.onStartingCaptureService()
      val serviceIntent =
        LiveDubbingForegroundService.createStartIntent(comicApp, resultCode, data)
      try {
        ContextCompat.startForegroundService(comicApp, serviceIntent)
      } catch (t: Throwable) {
        dubbingManager.onForegroundServiceFailed(t.message)
      }
    }
  }

  fun pauseDubbing() {
    viewModelScope.launch { dubbingManager.pauseSession() }
  }

  fun resumeDubbing() {
    viewModelScope.launch { dubbingManager.resumeSession() }
  }

  fun stopDubbing() {
    viewModelScope.launch {
      dubbingManager.stopSession()
      comicApp.stopService(LiveDubbingForegroundService.createStopIntent(comicApp))
    }
  }

  fun dismissError() {
    viewModelScope.launch { dubbingManager.dismissError() }
  }
}
