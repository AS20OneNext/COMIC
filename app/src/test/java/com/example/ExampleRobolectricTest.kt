package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppPreferences
import com.example.model.AppThemeMode
import com.example.model.DubbingSessionState
import com.example.model.DubbingUiSnapshot
import com.example.model.SupportedTargetLanguages
import com.example.model.UiLanguage
import com.example.ui.KeyTestResult
import com.example.ui.screens.ActiveDubbingScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.ComicTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `app_name, V0_2 badge, and localized English and Persian strings load correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("COMIC", context.getString(R.string.app_name))
    assertEquals("EXPERIMENTAL V0.2", context.getString(R.string.release_badge))

    val faConfig = Configuration(context.resources.configuration)
    faConfig.setLocale(Locale.forLanguageTag("fa"))
    val faContext = context.createConfigurationContext(faConfig)
    assertEquals("شروع دوبله زنده", faContext.getString(R.string.home_start_live_dubbing))
    assertEquals("نسخه آزمایشی V0.2", faContext.getString(R.string.release_badge))
  }

  @Test
  fun `welcome screen defaults cleanly in English and skip action triggers callback`() {
    var skipped = false
    composeTestRule.setContent {
      ComicTheme(themeMode = AppThemeMode.DARK, uiLanguage = UiLanguage.ENGLISH) {
        WelcomeScreen(
          hasSavedKey = false,
          keyTestResult = KeyTestResult.Idle,
          onSaveKey = {},
          onTestKey = {},
          onDeleteKey = {},
          onToggleUiLanguage = {},
          onContinue = {},
          onSkip = { skipped = true },
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("EXPERIMENTAL V0.2").assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("skip_onboarding_button")
      .performScrollTo()
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()
    assertTrue(skipped)
  }

  @Test
  fun `home screen interactive target language pill opens quick selector and updates language`() {
    var selectedLangCode = ""
    var started = false
    composeTestRule.setContent {
      ComicTheme(themeMode = AppThemeMode.DARK, uiLanguage = UiLanguage.ENGLISH) {
        HomeScreen(
          targetLanguage = SupportedTargetLanguages.DEFAULT,
          hasSavedApiKey = true,
          sessionState = DubbingSessionState.IDLE,
          onSelectTargetLanguage = { selectedLangCode = it.code },
          onStartLiveDubbing = { started = true },
          onOpenSettings = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("home_target_language_pill")
      .performScrollTo()
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()

    // Scroll modal sheet LazyColumn to Japanese and select it
    composeTestRule
      .onNodeWithTag("target_language_sheet_list")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("sheet_lang_ja"))
    composeTestRule
      .onNodeWithTag("sheet_lang_ja")
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()
    assertEquals("ja", selectedLangCode)

    composeTestRule
      .onNodeWithTag("start_live_dubbing_button")
      .performScrollTo()
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()
    assertTrue(started)
  }

  @Test
  fun `settings screen supports language switch to Persian RTL and Vazirmatn license dialog`() {
    var switchedLanguage: UiLanguage? = null
    composeTestRule.setContent {
      ComicTheme(themeMode = AppThemeMode.DARK, uiLanguage = UiLanguage.PERSIAN) {
        SettingsScreen(
          preferences = AppPreferences(uiLanguage = UiLanguage.PERSIAN),
          hasSavedKey = false,
          keyTestResult = KeyTestResult.Idle,
          onBack = {},
          onSelectUiLanguage = { switchedLanguage = it },
          onSelectThemeMode = {},
          onSelectDefaultTargetLanguage = {},
          onDubbedVolumeChange = {},
          onSaveKey = {},
          onTestKey = {},
          onDeleteKey = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("settings_lang_en")
      .performScrollTo()
      .assertIsDisplayed()
      .performClick()
    assertEquals(UiLanguage.ENGLISH, switchedLanguage)

    composeTestRule
      .onNodeWithTag("view_vazirmatn_license_button")
      .performScrollTo()
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("SIL OPEN FONT LICENSE Version 1.1", substring = true).assertIsDisplayed()
  }

  @Test
  fun `active dubbing screen pause and stop controls work cleanly`() {
    var paused = false
    var stopped = false
    composeTestRule.setContent {
      ComicTheme(themeMode = AppThemeMode.DARK, uiLanguage = UiLanguage.ENGLISH) {
        ActiveDubbingScreen(
          snapshot =
            DubbingUiSnapshot(
              state = DubbingSessionState.PLAYING,
              elapsedSeconds = 12L,
            ),
          dubbedVolume = 1.0f,
          onVolumeChange = {},
          onPause = { paused = true },
          onResume = {},
          onStop = { stopped = true },
          onDismissErrorAndReturn = {},
          onOpenSettings = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("session_state_pill").assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("pause_resume_button")
      .performScrollTo()
      .assertIsDisplayed()
      .performClick()
    composeTestRule
      .onNodeWithTag("stop_dubbing_button")
      .performScrollTo()
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()
    assertTrue(paused)
    assertTrue(stopped)
  }
}
