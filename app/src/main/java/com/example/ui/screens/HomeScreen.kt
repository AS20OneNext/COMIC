package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DubbingSessionState
import com.example.model.TargetLanguage
import com.example.ui.components.AuroraWaveform
import com.example.ui.components.ComicWordmarkBadge
import com.example.ui.components.GlassSurfaceCard
import com.example.ui.components.GradientPrimaryButton
import com.example.ui.components.LtrMonoText
import com.example.ui.components.TargetLanguageSelectorSheet
import com.example.ui.theme.AuroraPurple
import com.example.ui.theme.ComicSpacing
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalUiLanguage
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.comicString

@Composable
fun HomeScreen(
  targetLanguage: TargetLanguage,
  hasSavedApiKey: Boolean,
  sessionState: DubbingSessionState,
  onSelectTargetLanguage: (TargetLanguage) -> Unit = {},
  onStartLiveDubbing: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val uiLanguage = LocalUiLanguage.current
  val haptics = LocalHapticFeedback.current
  var showTargetSheet by rememberSaveable { mutableStateOf(false) }

  if (showTargetSheet) {
    TargetLanguageSelectorSheet(
      selectedLanguage = targetLanguage,
      onSelectLanguage = { selected ->
        onSelectTargetLanguage(selected)
        showTargetSheet = false
      },
      onDismiss = { showTargetSheet = false },
    )
  }

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
                ElectricBlue.copy(alpha = 0.04f),
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
            .padding(horizontal = ComicSpacing.Xl, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        // Top Brand Bar + Settings Shortcut (Focused single-screen Home flow)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ComicWordmarkBadge()
          IconButton(
            onClick = onOpenSettings,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .testTag("settings_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Settings,
              contentDescription = comicString(R.string.nav_settings),
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        // Subtle Animated Aurora Ribbon Preview Card
        GlassSurfaceCard(
          borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
          containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        ) {
          Box(
            modifier = Modifier.fillMaxWidth().height(152.dp),
            contentAlignment = Alignment.Center,
          ) {
            AuroraWaveform(
              state = sessionState,
              inputRms = 0f,
              outputRms = 0f,
              modifier = Modifier.fillMaxSize(),
            )
          }
          Spacer(modifier = Modifier.height(ComicSpacing.Sm))
          Text(
            text = comicString(R.string.home_live_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // Interactive Target Language Pill opening the curated 12-language quick selector
        Surface(
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 58.dp)
              .clip(RoundedCornerShape(18.dp))
              .clickable { showTargetSheet = true }
              .testTag("home_target_language_pill"),
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f),
            ) {
              Box(
                modifier =
                  Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Filled.Language,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(20.dp),
                )
              }
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = comicString(R.string.home_target_language_label),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    if (targetLanguage.nativeName != targetLanguage.displayName(uiLanguage)) {
                      "${targetLanguage.displayName(uiLanguage)} · ${targetLanguage.nativeName}"
                    } else {
                      targetLanguage.displayName(uiLanguage)
                    },
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                LtrMonoText(
                  text = targetLanguage.code.uppercase(),
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
              Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = comicString(R.string.home_change_target_hint),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Primary Hero Action: START LIVE DUBBING (or Open Live Session if active)
        val isActive = sessionState.isActive
        GradientPrimaryButton(
          text =
            comicString(
              if (isActive) R.string.home_resume_active_cta
              else R.string.home_start_live_dubbing
            ),
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onStartLiveDubbing()
          },
          modifier = Modifier.heightIn(min = 64.dp).testTag("start_live_dubbing_button"),
          leadingContent = {
            Icon(
              imageVector = if (isActive) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(26.dp),
            )
          },
        )

        // Gemini Connection Readiness Card
        GlassSurfaceCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector =
                  if (hasSavedApiKey) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = if (hasSavedApiKey) LiveEmerald else WarningAmber,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = comicString(R.string.home_gemini_status_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text =
                comicString(
                  if (hasSavedApiKey) R.string.home_status_configured
                  else R.string.home_status_unconfigured
                ),
              style = MaterialTheme.typography.labelLarge,
              color = if (hasSavedApiKey) LiveEmerald else WarningAmber,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          LtrMonoText(
            text = "gemini-3.5-live-translate-preview",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (!hasSavedApiKey) {
          OutlinedButton(
            onClick = onOpenSettings,
            modifier =
              Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home_configure_key_button"),
          ) {
            Text(comicString(R.string.home_configure_key_cta))
          }
        }

        // Natural Voice Mode Info Card
        GlassSurfaceCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.GraphicEq,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text =
                "${comicString(R.string.home_voice_mode_label)}: ${comicString(R.string.home_voice_mode_natural)}",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = comicString(R.string.home_voice_mode_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
