package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.UiLanguage
import com.example.ui.KeyTestResult
import com.example.ui.components.ApiKeyConfigurationCard
import com.example.ui.components.ComicWordmarkBadge
import com.example.ui.components.GlassSurfaceCard
import com.example.ui.components.GradientPrimaryButton
import com.example.ui.theme.AuroraPurple
import com.example.ui.theme.ComicSpacing
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LocalUiLanguage
import com.example.ui.theme.comicString

@Composable
fun WelcomeScreen(
  hasSavedKey: Boolean,
  keyTestResult: KeyTestResult,
  onSaveKey: (String) -> Unit,
  onTestKey: (String?) -> Unit,
  onDeleteKey: () -> Unit,
  onToggleUiLanguage: (UiLanguage) -> Unit,
  onContinue: () -> Unit,
  onSkip: () -> Unit,
) {
  val uiLanguage = LocalUiLanguage.current

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              listOf(
                MaterialTheme.colorScheme.background,
                AuroraPurple.copy(alpha = 0.08f),
                MaterialTheme.colorScheme.background,
              )
            )
          )
          .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ComicSpacing.Xl, vertical = ComicSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ComicWordmarkBadge()
          TextButton(
            onClick = {
              val next =
                if (uiLanguage == UiLanguage.PERSIAN) UiLanguage.ENGLISH else UiLanguage.PERSIAN
              onToggleUiLanguage(next)
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("welcome_lang_toggle"),
          ) {
            Text(
              text = if (uiLanguage == UiLanguage.PERSIAN) "EN" else "فارسی",
              style = MaterialTheme.typography.labelLarge,
              color = ElectricBlue,
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(ComicSpacing.Sm)) {
          Text(
            text = comicString(R.string.welcome_headline),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = comicString(R.string.welcome_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Text(
          text = comicString(R.string.welcome_api_section_title),
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

        GlassSurfaceCard {
          Text(
            text = comicString(R.string.welcome_permissions_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(ComicSpacing.Md))
          PermissionOverviewItem(
            icon = Icons.Filled.MicOff,
            title = comicString(R.string.welcome_perm_audio_title),
            description = comicString(R.string.welcome_perm_audio_desc),
          )
          Spacer(modifier = Modifier.height(10.dp))
          PermissionOverviewItem(
            icon = Icons.Filled.Cast,
            title = comicString(R.string.welcome_perm_projection_title),
            description = comicString(R.string.welcome_perm_projection_desc),
          )
          Spacer(modifier = Modifier.height(10.dp))
          PermissionOverviewItem(
            icon = Icons.Filled.NotificationsActive,
            title = comicString(R.string.welcome_perm_notif_title),
            description = comicString(R.string.welcome_perm_notif_desc),
          )
        }

        GradientPrimaryButton(
          text = comicString(R.string.welcome_continue_home),
          onClick = onContinue,
          modifier = Modifier.testTag("continue_onboarding_button"),
        )

        TextButton(
          onClick = onSkip,
          modifier =
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("skip_onboarding_button"),
        ) {
          Text(
            text = comicString(R.string.action_skip_for_now),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(ComicSpacing.Md))
      }
    }
  }
}

@Composable
private fun PermissionOverviewItem(
  icon: ImageVector,
  title: String,
  description: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(ComicSpacing.Md),
    verticalAlignment = Alignment.Top,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.secondary,
      modifier = Modifier.size(20.dp).padding(top = 2.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
