package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.DubbingError
import com.example.model.DubbingSessionState
import com.example.model.DubbingUiSnapshot
import com.example.model.LaunchableAppInfo
import com.example.model.TargetLanguage
import com.example.ui.LiveSetupDraftState
import com.example.ui.components.GlassSurfaceCard
import com.example.ui.components.GradientPrimaryButton
import com.example.ui.components.LtrMonoText
import com.example.ui.components.TargetLanguageChipRow
import com.example.ui.components.resolveDubbingErrorText
import com.example.ui.theme.ComicSpacing
import com.example.ui.theme.comicString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSetupScreen(
  draft: LiveSetupDraftState,
  targetLanguage: TargetLanguage,
  sessionSnapshot: DubbingUiSnapshot,
  onBack: () -> Unit,
  onOpenSettings: () -> Unit,
  onSelectSourceSpecific: (Boolean) -> Unit,
  onSelectApp: (LaunchableAppInfo) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectTargetLanguage: (TargetLanguage) -> Unit,
  onPrepareProjectionConsent: ((Intent) -> Unit) -> Unit,
  onRecordAudioDenied: () -> Unit,
  onProjectionResult: (Int, Intent?) -> Unit,
  onDismissError: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current

  val projectionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      onProjectionResult(result.resultCode, result.data)
    }

  val notifPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
      onPrepareProjectionConsent { intent -> projectionLauncher.launch(intent) }
    }

  val recordAudioLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (!granted) {
        onRecordAudioDenied()
      } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
          notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          onPrepareProjectionConsent { intent -> projectionLauncher.launch(intent) }
        }
      }
    }

  fun initiateDubbingFlow() {
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    onDismissError()
    val hasRecordAudio =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    if (!hasRecordAudio) {
      recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
      return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      return
    }

    onPrepareProjectionConsent { intent -> projectionLauncher.launch(intent) }
  }

  LaunchedEffect(draft.useSpecificApp) {
    if (draft.useSpecificApp && draft.apps.isEmpty() && !draft.isLoadingApps) {
      onSelectSourceSpecific(true)
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text(comicString(R.string.setup_title)) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("setup_back_button"),
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
        val error = sessionSnapshot.error
        AnimatedVisibility(visible = sessionSnapshot.state == DubbingSessionState.ERROR && error != null) {
          if (error != null) {
            GlassSurfaceCard(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
                Text(
                  text = resolveDubbingErrorText(error),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.weight(1f),
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
              ) {
                if (error is DubbingError.InvalidApiKey) {
                  TextButton(onClick = onOpenSettings) {
                    Text(comicString(R.string.nav_settings))
                  }
                }
                TextButton(onClick = onDismissError) {
                  Text(comicString(R.string.action_dismiss))
                }
              }
            }
          }
        }

        // Source Mode Section
        Text(
          text = comicString(R.string.setup_source_mode_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(ComicSpacing.Md),
        ) {
          SourceModeCard(
            title = comicString(R.string.setup_mode_entire_screen),
            description = comicString(R.string.setup_mode_entire_screen_desc),
            icon = Icons.Filled.Cast,
            selected = !draft.useSpecificApp,
            onClick = { onSelectSourceSpecific(false) },
            modifier = Modifier.weight(1f).testTag("source_mode_all"),
          )

          SourceModeCard(
            title = comicString(R.string.setup_mode_specific_app),
            description = comicString(R.string.setup_mode_specific_app_desc),
            icon = Icons.Filled.Apps,
            selected = draft.useSpecificApp,
            onClick = { onSelectSourceSpecific(true) },
            modifier = Modifier.weight(1f).testTag("source_mode_specific"),
          )
        }

        // Specific App Searchable Picker
        AnimatedVisibility(visible = draft.useSpecificApp) {
          GlassSurfaceCard {
            Text(
              text =
                draft.selectedApp?.let {
                  "${it.label} (${comicString(R.string.setup_selected_app_uid, it.uid)})"
                } ?: comicString(R.string.setup_select_app_prompt),
              style = MaterialTheme.typography.labelLarge,
              color =
                if (draft.selectedApp != null) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = draft.searchQuery,
              onValueChange = onSearchQueryChange,
              modifier = Modifier.fillMaxWidth().testTag("app_search_input"),
              placeholder = { Text(comicString(R.string.setup_search_apps_hint)) },
              leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
              },
              singleLine = true,
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (draft.isLoadingApps) {
              Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
              }
            } else {
              val filtered = draft.filteredApps
              if (filtered.isEmpty()) {
                Text(
                  text = comicString(R.string.setup_no_apps_found),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(vertical = 16.dp),
                )
              } else {
                LazyColumn(
                  modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  items(filtered, key = { it.packageName }) { app ->
                    val isSelected = draft.selectedApp?.packageName == app.packageName
                    AppPickerRow(
                      app = app,
                      selected = isSelected,
                      onClick = { onSelectApp(app) },
                    )
                  }
                }
              }
            }
          }
        }

        // Android OS Capture Notes
        GlassSurfaceCard(
          containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Filled.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(18.dp).padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text =
                  comicString(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                      R.string.setup_android14_note
                    else R.string.setup_legacy_projection_note
                  ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = comicString(R.string.setup_capture_limitation_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Target Language Selector
        Text(
          text = comicString(R.string.setup_target_language_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )

        TargetLanguageChipRow(
          selectedLanguage = targetLanguage,
          onSelectLanguage = onSelectTargetLanguage,
        )

        Text(
          text = comicString(R.string.setup_permission_banner),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val isStarting = sessionSnapshot.state.isRunningOrStarting
        GradientPrimaryButton(
          text = comicString(R.string.setup_start_dubbing_cta),
          onClick = { initiateDubbingFlow() },
          enabled = !isStarting && (!draft.useSpecificApp || draft.selectedApp != null),
          modifier = Modifier.testTag("start_dubbing_cta"),
          leadingContent = {
            if (isStarting) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
              )
            } else {
              Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
              )
            }
          },
        )

        Spacer(modifier = Modifier.height(ComicSpacing.Lg))
      }
    }
  }
}

@Composable
private fun SourceModeCard(
  title: String,
  description: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.heightIn(min = 122.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    shape = MaterialTheme.shapes.medium,
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
      else MaterialTheme.colorScheme.surfaceContainer,
    border =
      BorderStroke(
        width = if (selected) 1.5.dp else 1.dp,
        color =
          if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
      ),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
        )
        if (selected) {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AppPickerRow(
  app: LaunchableAppInfo,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 52.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(
          if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
          else MaterialTheme.colorScheme.surfaceContainerLow
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .testTag("app_item_${app.packageName}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = app.label,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
      )
      LtrMonoText(
        text = app.packageName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    LtrMonoText(
      text = comicString(R.string.setup_selected_app_uid, app.uid),
      color = MaterialTheme.colorScheme.secondary,
    )
  }
}
