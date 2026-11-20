package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.AppPreferences
import com.example.model.AppThemeMode
import com.example.model.TargetLanguage
import com.example.model.UiLanguage
import com.example.ui.KeyTestResult
import com.example.ui.components.ApiKeyConfigurationCard
import com.example.ui.components.GlassSurfaceCard
import com.example.ui.components.LtrMonoText
import com.example.ui.components.TargetLanguageChipRow
import com.example.ui.theme.AuroraPurple
import com.example.ui.theme.ComicSpacing
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.comicString
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  preferences: AppPreferences,
  hasSavedKey: Boolean,
  keyTestResult: KeyTestResult,
  onBack: () -> Unit,
  onSelectUiLanguage: (UiLanguage) -> Unit,
  onSelectThemeMode: (AppThemeMode) -> Unit,
  onSelectDefaultTargetLanguage: (TargetLanguage) -> Unit,
  onDubbedVolumeChange: (Float) -> Unit,
  onSaveKey: (String) -> Unit,
  onTestKey: (String?) -> Unit,
  onDeleteKey: () -> Unit,
) {
  val context = LocalContext.current
  var showLicenseDialog by rememberSaveable { mutableStateOf(false) }

  if (showLicenseDialog) {
    val licenseText =
      remember(context) {
        runCatching {
          context.assets.open("licenses/VAZIRMATN_OFL.txt").bufferedReader().use { it.readText() }
        }.getOrElse {
          "Copyright 2015 The Vazirmatn Project Authors (https://github.com/rastikerdar/vazirmatn)\n" +
            "Licensed under the SIL Open Font License, Version 1.1."
        }
      }
    AlertDialog(
      onDismissRequest = { showLicenseDialog = false },
      title = {
        Text(
          text = comicString(R.string.license_dialog_title),
          style = MaterialTheme.typography.titleMedium,
        )
      },
      text = {
        Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
          LtrMonoText(
            text = licenseText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showLicenseDialog = false }) {
          Text(comicString(R.string.action_close))
        }
      },
    )
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text(comicString(R.string.settings_title)) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = comicString(R.string.nav_back),
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ComicSpacing.Xl, vertical = ComicSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ComicSpacing.Lg),
      ) {
        // App Language (English LTR default on fresh install / Persian RTL)
        GlassSurfaceCard {
          Text(
            text = comicString(R.string.settings_ui_language),
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            FilterChip(
              selected = preferences.uiLanguage == UiLanguage.ENGLISH,
              onClick = { onSelectUiLanguage(UiLanguage.ENGLISH) },
              label = { Text(comicString(R.string.settings_lang_english)) },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("settings_lang_en"),
            )
            FilterChip(
              selected = preferences.uiLanguage == UiLanguage.PERSIAN,
              onClick = { onSelectUiLanguage(UiLanguage.PERSIAN) },
              label = { Text(comicString(R.string.settings_lang_persian)) },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("settings_lang_fa"),
            )
          }
        }

        // Theme Mode (Dark / System / Light)
        GlassSurfaceCard {
          Text(
            text = comicString(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = preferences.themeMode == AppThemeMode.DARK,
              onClick = { onSelectThemeMode(AppThemeMode.DARK) },
              label = { Text(comicString(R.string.settings_theme_dark)) },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("theme_dark"),
            )
            FilterChip(
              selected = preferences.themeMode == AppThemeMode.SYSTEM,
              onClick = { onSelectThemeMode(AppThemeMode.SYSTEM) },
              label = { Text(comicString(R.string.settings_theme_system)) },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("theme_system"),
            )
            FilterChip(
              selected = preferences.themeMode == AppThemeMode.LIGHT,
              onClick = { onSelectThemeMode(AppThemeMode.LIGHT) },
              label = { Text(comicString(R.string.settings_theme_light)) },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("theme_light"),
            )
          }
        }

        // Default Target Language
        GlassSurfaceCard {
          Text(
            text = comicString(R.string.settings_default_target),
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(10.dp))
          TargetLanguageChipRow(
            selectedLanguage = preferences.defaultTargetLanguage,
            onSelectLanguage = onSelectDefaultTargetLanguage,
          )
        }

        // Default Dubbed Audio Volume
        GlassSurfaceCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = comicString(R.string.settings_dubbed_volume),
              style = MaterialTheme.typography.titleMedium,
            )
            LtrMonoText(
              text = "${(preferences.dubbedVolume * 100f).roundToInt()}%",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
          Slider(
            value = preferences.dubbedVolume,
            onValueChange = onDubbedVolumeChange,
            valueRange = 0f..1.5f,
            modifier = Modifier.fillMaxWidth().testTag("settings_volume_slider"),
            colors =
              SliderDefaults.colors(
                thumbColor = ElectricBlue,
                activeTrackColor = AuroraPurple,
              ),
          )
        }

        // Gemini API Key & Connection Test
        Text(
          text = comicString(R.string.settings_api_section),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
        )

        ApiKeyConfigurationCard(
          hasSavedKey = hasSavedKey,
          keyTestResult = keyTestResult,
          onSaveKey = onSaveKey,
          onTestKey = onTestKey,
          onDeleteKey = onDeleteKey,
        )

        // Privacy Information
        GlassSurfaceCard {
          Text(
            text = comicString(R.string.settings_privacy_title),
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = comicString(R.string.settings_privacy_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // Typography & Open Source Font License Attribution (Vazirmatn SIL OFL 1.1)
        GlassSurfaceCard {
          Text(
            text = comicString(R.string.settings_licenses_title),
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = comicString(R.string.settings_vazirmatn_credit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = { showLicenseDialog = true },
            modifier = Modifier.heightIn(min = 48.dp).testTag("view_vazirmatn_license_button"),
          ) {
            Text(comicString(R.string.settings_view_license))
          }
        }

        // App Version & Pipeline Identity
        GlassSurfaceCard {
          Text(
            text = comicString(R.string.settings_version_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(4.dp))
          LtrMonoText(
            text =
              comicString(
                R.string.settings_version_value,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE.toString(),
              ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = comicString(R.string.settings_about_pipeline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(ComicSpacing.Lg))
      }
    }
  }
}
