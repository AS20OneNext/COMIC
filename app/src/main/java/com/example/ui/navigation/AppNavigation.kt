package com.example.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.DubbingSessionState
import com.example.ui.LiveDubbingViewModel
import com.example.ui.screens.ActiveDubbingScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen
import kotlinx.serialization.Serializable

@Serializable data object WelcomeRoute

@Serializable data object HomeRoute

@Serializable data object LiveSetupRoute

@Serializable data object ActiveDubbingRoute

@Serializable data object SettingsRoute

@Composable
fun AppNavigation(viewModel: LiveDubbingViewModel) {
  val isPreferencesLoaded by viewModel.isPreferencesLoaded.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()

  // Wait for DataStore's initial snapshot so fresh installs render English immediately
  // and upgraded V0.1 installs preserve their saved language/onboarding state without flicker.
  if (!isPreferencesLoaded) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    return
  }

  val navController = rememberNavController()
  val hasSavedKey by viewModel.hasSavedApiKey.collectAsStateWithLifecycle()
  val keyTestResult by viewModel.keyTestResult.collectAsStateWithLifecycle()
  val setupDraft by viewModel.setupDraft.collectAsStateWithLifecycle()
  val targetLanguage by viewModel.effectiveSetupTargetLanguage.collectAsStateWithLifecycle()
  val sessionSnapshot by viewModel.sessionSnapshot.collectAsStateWithLifecycle()

  val startDestination: Any =
    if (preferences.onboardingCompleted) HomeRoute else WelcomeRoute

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  // Navigate automatically to ActiveDubbingRoute once capture / Gemini pipeline starts
  LaunchedEffect(sessionSnapshot.state) {
    val activePipelineState =
      sessionSnapshot.state == DubbingSessionState.INITIALIZING_CAPTURE ||
        sessionSnapshot.state == DubbingSessionState.CONNECTING ||
        sessionSnapshot.state.isActive

    val activeRouteName = ActiveDubbingRoute::class.qualifiedName
    if (activePipelineState && currentRoute != activeRouteName) {
      navController.navigate(ActiveDubbingRoute) {
        launchSingleTop = true
      }
    } else if (sessionSnapshot.state == DubbingSessionState.IDLE &&
      currentRoute == activeRouteName
    ) {
      navController.popBackStack(HomeRoute, inclusive = false)
    }
  }

  NavHost(
    navController = navController,
    startDestination = startDestination,
    enterTransition = { fadeIn(tween(200)) },
    exitTransition = { fadeOut(tween(160)) },
  ) {
    composable<WelcomeRoute> {
      WelcomeScreen(
        hasSavedKey = hasSavedKey,
        keyTestResult = keyTestResult,
        onSaveKey = { viewModel.saveApiKey(it) },
        onTestKey = { viewModel.testApiKeyConnection(it) },
        onDeleteKey = { viewModel.deleteApiKey() },
        onToggleUiLanguage = { viewModel.setUiLanguage(it) },
        onContinue = {
          viewModel.completeOnboarding()
          navController.navigate(HomeRoute) {
            popUpTo(WelcomeRoute) { inclusive = true }
          }
        },
        onSkip = {
          viewModel.completeOnboarding()
          navController.navigate(HomeRoute) {
            popUpTo(WelcomeRoute) { inclusive = true }
          }
        },
      )
    }

    composable<HomeRoute> {
      HomeScreen(
        targetLanguage = preferences.defaultTargetLanguage,
        hasSavedApiKey = hasSavedKey,
        sessionState = sessionSnapshot.state,
        onSelectTargetLanguage = { viewModel.setDefaultTargetLanguage(it) },
        onStartLiveDubbing = {
          if (sessionSnapshot.state.isActive) {
            navController.navigate(ActiveDubbingRoute) { launchSingleTop = true }
          } else {
            navController.navigate(LiveSetupRoute) { launchSingleTop = true }
          }
        },
        onOpenSettings = {
          viewModel.clearKeyTestStatus()
          navController.navigate(SettingsRoute) { launchSingleTop = true }
        },
      )
    }

    composable<LiveSetupRoute> {
      LiveSetupScreen(
        draft = setupDraft,
        targetLanguage = targetLanguage,
        sessionSnapshot = sessionSnapshot,
        onBack = { navController.popBackStack() },
        onOpenSettings = {
          viewModel.clearKeyTestStatus()
          navController.navigate(SettingsRoute) { launchSingleTop = true }
        },
        onSelectSourceSpecific = { viewModel.setSourceModeSpecific(it) },
        onSelectApp = { viewModel.selectTargetApp(it) },
        onSearchQueryChange = { viewModel.updateAppSearchQuery(it) },
        onSelectTargetLanguage = { viewModel.selectSessionTargetLanguage(it) },
        onPrepareProjectionConsent = { launchIntent ->
          viewModel.prepareAndLaunchProjectionConsent(launchIntent)
        },
        onRecordAudioDenied = { viewModel.onRecordAudioPermissionDenied() },
        onProjectionResult = { code, data -> viewModel.onProjectionConsentResult(code, data) },
        onDismissError = { viewModel.dismissError() },
      )
    }

    composable<ActiveDubbingRoute> {
      ActiveDubbingScreen(
        snapshot = sessionSnapshot,
        dubbedVolume = preferences.dubbedVolume,
        onVolumeChange = { viewModel.setDubbedVolume(it) },
        onPause = { viewModel.pauseDubbing() },
        onResume = { viewModel.resumeDubbing() },
        onStop = { viewModel.stopDubbing() },
        onDismissErrorAndReturn = {
          viewModel.dismissError()
          navController.popBackStack()
        },
        onOpenSettings = {
          viewModel.dismissError()
          navController.navigate(SettingsRoute) { launchSingleTop = true }
        },
      )
    }

    composable<SettingsRoute> {
      SettingsScreen(
        preferences = preferences,
        hasSavedKey = hasSavedKey,
        keyTestResult = keyTestResult,
        onBack = { navController.popBackStack() },
        onSelectUiLanguage = { viewModel.setUiLanguage(it) },
        onSelectThemeMode = { viewModel.setThemeMode(it) },
        onSelectDefaultTargetLanguage = { viewModel.setDefaultTargetLanguage(it) },
        onDubbedVolumeChange = { viewModel.setDubbedVolume(it) },
        onSaveKey = { viewModel.saveApiKey(it) },
        onTestKey = { viewModel.testApiKeyConnection(it) },
        onDeleteKey = { viewModel.deleteApiKey() },
      )
    }
  }
}
