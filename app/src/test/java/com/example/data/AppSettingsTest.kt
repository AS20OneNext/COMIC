package com.example.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.example.model.AppThemeMode
import com.example.model.SupportedTargetLanguages
import com.example.model.UiLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppSettingsTest {

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun `fresh install defaults to English LTR UI with Persian default dubbing target`() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val dataStore =
        PreferenceDataStoreFactory.create(
          scope = scope,
          produceFile = { tempFolder.newFile("fresh_settings.preferences_pb") },
        )
      val settings = AppSettings(dataStore = dataStore, scope = scope)

      val prefs = settings.preferencesFlow.first()
      assertEquals(UiLanguage.ENGLISH, prefs.uiLanguage)
      assertFalse(prefs.uiLanguage.isRtl)
      assertEquals("fa", prefs.defaultTargetLanguage.code)
      assertEquals(AppThemeMode.DARK, prefs.themeMode)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `existing V0_1 saved Persian language preference is preserved on upgrade`() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val dataStore =
        PreferenceDataStoreFactory.create(
          scope = scope,
          produceFile = { tempFolder.newFile("upgrade_settings.preferences_pb") },
        )
      // Simulate pre-existing V0.1 stored preference
      dataStore.edit { prefs ->
        prefs[AppSettings.KEY_UI_LANGUAGE] = "fa"
        prefs[AppSettings.KEY_ONBOARDING_COMPLETED] = true
      }

      val settings = AppSettings(dataStore = dataStore, scope = scope)
      val loaded = settings.preferencesFlow.first()

      assertEquals(UiLanguage.PERSIAN, loaded.uiLanguage)
      assertTrue(loaded.uiLanguage.isRtl)
      assertTrue(loaded.onboardingCompleted)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `switching UI language and target language persists cleanly across reads`() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val dataStore =
        PreferenceDataStoreFactory.create(
          scope = scope,
          produceFile = { tempFolder.newFile("switch_settings.preferences_pb") },
        )
      val settings = AppSettings(dataStore = dataStore, scope = scope)

      settings.setUiLanguage(UiLanguage.PERSIAN)
      settings.setDefaultTargetLanguage(SupportedTargetLanguages.findByCode("ja"))
      settings.setDubbedVolume(1.25f)

      val updated = settings.preferencesFlow.first()
      assertEquals(UiLanguage.PERSIAN, updated.uiLanguage)
      assertEquals("ja", updated.defaultTargetLanguage.code)
      assertEquals(1.25f, updated.dubbedVolume, 0.001f)
    } finally {
      scope.cancel()
    }
  }
}
