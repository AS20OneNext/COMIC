package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppThemeMode
import com.example.ui.LiveDubbingViewModel
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.ComicTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    setContent {
      val dubbingViewModel: LiveDubbingViewModel = viewModel()
      val prefs by dubbingViewModel.preferences.collectAsStateWithLifecycle()
      val systemDark = isSystemInDarkTheme()
      val isDark =
        when (prefs.themeMode) {
          AppThemeMode.DARK -> true
          AppThemeMode.LIGHT -> false
          AppThemeMode.SYSTEM -> systemDark
        }

      LaunchedEffect(isDark) {
        val style =
          if (isDark) SystemBarStyle.dark(Color.TRANSPARENT)
          else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
      }

      ComicTheme(
        themeMode = prefs.themeMode,
        uiLanguage = prefs.uiLanguage,
      ) {
        AppNavigation(viewModel = dubbingViewModel)
      }
    }
  }
}
