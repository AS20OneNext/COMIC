package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeMode
import com.example.model.SupportedTargetLanguages
import com.example.model.TargetLanguage
import com.example.model.UiLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "comic_app_settings")

data class AppPreferences(
  val uiLanguage: UiLanguage = UiLanguage.ENGLISH,
  val themeMode: AppThemeMode = AppThemeMode.DARK,
  val defaultTargetLanguage: TargetLanguage = SupportedTargetLanguages.DEFAULT,
  val dubbedVolume: Float = 1.0f,
  val onboardingCompleted: Boolean = false,
)

class AppSettings(
  private val dataStore: DataStore<Preferences>,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  constructor(context: Context) : this(context.settingsDataStore)

  val preferencesFlow: Flow<AppPreferences> =
    dataStore.data.map { prefs -> mapPreferences(prefs) }

  private val _currentPreferences = MutableStateFlow(AppPreferences())
  val currentPreferencesFlow: StateFlow<AppPreferences> = _currentPreferences.asStateFlow()

  private val _isLoaded = MutableStateFlow(false)
  val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

  val currentPreferencesSnapshot: AppPreferences
    get() = _currentPreferences.value

  init {
    scope.launch {
      preferencesFlow.collect { prefs ->
        _currentPreferences.value = prefs
        _isLoaded.value = true
      }
    }
  }

  private fun mapPreferences(prefs: Preferences): AppPreferences {
    // Only default to English when no language preference has previously been saved.
    // Any previously saved V0.1/V0.2 preference ("fa" or "en") is preserved intact.
    val savedLangCode = prefs[KEY_UI_LANGUAGE]
    return AppPreferences(
      uiLanguage = UiLanguage.fromCode(savedLangCode),
      themeMode = AppThemeMode.fromKey(prefs[KEY_THEME_MODE]),
      defaultTargetLanguage = SupportedTargetLanguages.findByCode(prefs[KEY_TARGET_LANGUAGE]),
      dubbedVolume = (prefs[KEY_DUBBED_VOLUME] ?: 1.0f).coerceIn(0f, 1.5f),
      onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
    )
  }

  suspend fun setUiLanguage(language: UiLanguage) {
    dataStore.edit { prefs -> prefs[KEY_UI_LANGUAGE] = language.code }
  }

  suspend fun setThemeMode(themeMode: AppThemeMode) {
    dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = themeMode.key }
  }

  suspend fun setDefaultTargetLanguage(targetLanguage: TargetLanguage) {
    dataStore.edit { prefs -> prefs[KEY_TARGET_LANGUAGE] = targetLanguage.code }
  }

  suspend fun setDubbedVolume(volume: Float) {
    dataStore.edit { prefs -> prefs[KEY_DUBBED_VOLUME] = volume.coerceIn(0f, 1.5f) }
  }

  suspend fun setOnboardingCompleted(completed: Boolean) {
    dataStore.edit { prefs -> prefs[KEY_ONBOARDING_COMPLETED] = completed }
  }

  companion object {
    val KEY_UI_LANGUAGE = stringPreferencesKey("ui_language")
    val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    val KEY_TARGET_LANGUAGE = stringPreferencesKey("default_target_language")
    val KEY_DUBBED_VOLUME = floatPreferencesKey("dubbed_volume")
    val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
  }
}
