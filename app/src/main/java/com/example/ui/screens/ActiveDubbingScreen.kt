package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioSourceMode
import com.example.model.DubbingError
import com.example.model.DubbingSessionState
import com.example.model.DubbingUiSnapshot
import com.example.ui.components.AuroraWaveform
import com.example.ui.components.GlassSurfaceCard
import com.example.ui.components.LtrMonoText
import com.example.ui.components.resolveDubbingErrorText
import com.example.ui.theme.AuroraPurple
import com.example.ui.theme.ComicSpacing
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalUiLanguage
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.comicString
import kotlin.math.roundToInt

@Composable
fun ActiveDubbingScreen(
  snapshot: DubbingUiSnapshot,
  dubbedVolume: Float,
  onVolumeChange: (Float) -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onStop: () -> Unit,
  onDismissErrorAndReturn: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val uiLanguage = LocalUiLanguage.current
  val haptics = LocalHapticFeedback.current

  BackHandler {
    if (snapshot.state == DubbingSessionState.ERROR || snapshot.state == DubbingSessionState.IDLE) {
      onDismissErrorAndReturn()
    } else {
      onStop()
    }
  }

  val statusColor =
    when (snapshot.state) {
      DubbingSessionState.PLAYING -> LiveEmerald
      DubbingSessionState.TRANSLATING, DubbingSessionState.LISTENING -> ElectricBlue
      DubbingSessionState.RECONNECTING, DubbingSessionState.PAUSED -> WarningAmber
      DubbingSessionState.ERROR -> CoralError
      else -> AuroraPurple
    }

  val stateLabel =
    when (snapshot.state) {
      DubbingSessionState.IDLE -> comicString(R.string.state_idle)
      DubbingSessionState.VALIDATING -> comicString(R.string.state_validating)
      DubbingSessionState.REQUESTING_PERMISSION -> comicString(R.string.state_requesting_permission)
      DubbingSessionState.INITIALIZING_CAPTURE -> comicString(R.string.state_initializing_capture)
      DubbingSessionState.CONNECTING -> comicString(R.string.state_connecting_gemini)
      DubbingSessionState.LISTENING -> comicString(R.string.state_listening)
      DubbingSessionState.TRANSLATING -> comicString(R.string.state_translating)
      DubbingSessionState.PLAYING -> comicString(R.string.state_playing)
      DubbingSessionState.RECONNECTING ->
        comicString(R.string.state_reconnecting, snapshot.reconnectAttempt)
      DubbingSessionState.PAUSED -> comicString(R.string.state_paused)
      DubbingSessionState.STOPPING -> comicString(R.string.state_stopping)
      DubbingSessionState.ERROR -> comicString(R.string.state_error)
    }

  val playbackStatusText =
    when {
      snapshot.state == DubbingSessionState.PAUSED -> comicString(R.string.playback_status_paused)
      snapshot.isPlaybackActive -> comicString(R.string.playback_status_ready)
      snapshot.state.isActive -> comicString(R.string.playback_status_waiting)
      else -> comicString(R.string.playback_status_stopped)
    }

  val sourceText =
    when (val src = snapshot.sourceMode) {
      AudioSourceMode.AllEligible -> comicString(R.string.setup_mode_entire_screen)
      is AudioSourceMode.SpecificApp -> src.label
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
                statusColor.copy(alpha = 0.08f),
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
        verticalArrangement = Arrangement.spacedBy(ComicSpacing.Lg),
      ) {
        // Header: Status Pill + LTR Monospace Elapsed Timer
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(statusColor.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("session_state_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.GraphicEq,
              contentDescription = null,
              tint = statusColor,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = stateLabel,
              style = MaterialTheme.typography.labelLarge,
              color = statusColor,
            )
          }

          LtrMonoText(
            text = formatElapsed(snapshot.elapsedSeconds),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag("session_elapsed_timer"),
          )
        }

        // Large Fluid Aurora Waveform Stage
        GlassSurfaceCard(
          borderColor = statusColor.copy(alpha = 0.42f),
          containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
        ) {
          Box(
            modifier = Modifier.fillMaxWidth().height(206.dp),
            contentAlignment = Alignment.Center,
          ) {
            AuroraWaveform(
              state = snapshot.state,
              inputRms = snapshot.inputRmsLevel,
              outputRms = snapshot.outputRmsLevel,
              modifier = Modifier.fillMaxSize(),
            )
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = comicString(R.string.session_natural_mode_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // No-Audio Warning Banner (non-blocking)
        AnimatedVisibility(visible = snapshot.noAudioWarning && snapshot.state.isActive) {
          GlassSurfaceCard(
            containerColor = WarningAmber.copy(alpha = 0.14f),
            borderColor = WarningAmber.copy(alpha = 0.5f),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = WarningAmber,
              )
              Text(
                text = comicString(R.string.err_no_captured_audio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        // Error Card if session terminated with error
        val error = snapshot.error
        AnimatedVisibility(visible = snapshot.state == DubbingSessionState.ERROR && error != null) {
          if (error != null) {
            GlassSurfaceCard(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                )
              }
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                if (error is DubbingError.InvalidApiKey) {
                  OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                  ) {
                    Text(comicString(R.string.nav_settings))
                  }
                }
                Button(
                  onClick = onDismissErrorAndReturn,
                  modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("error_return_button"),
                ) {
                  Text(comicString(R.string.nav_back))
                }
              }
            }
          }
        }

        // Telemetry Grid: Target Language, Source, Playback, Connection
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(ComicSpacing.Md),
        ) {
          StatusMetricTile(
            label = comicString(R.string.session_target_label),
            value = snapshot.targetLanguage.displayName(uiLanguage),
            modifier = Modifier.weight(1f),
          )
          StatusMetricTile(
            label = comicString(R.string.session_source_label),
            value = sourceText,
            modifier = Modifier.weight(1f),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(ComicSpacing.Md),
        ) {
          StatusMetricTile(
            label = comicString(R.string.session_connection_label),
            value = stateLabel,
            modifier = Modifier.weight(1f),
          )
          StatusMetricTile(
            label = comicString(R.string.session_playback_label),
            value = playbackStatusText,
            modifier = Modifier.weight(1f),
          )
        }

        // Dubbed Output Volume, Diagnostics & Original Audio Note
        GlassSurfaceCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
              Text(
                text = comicString(R.string.session_dubbed_volume_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            LtrMonoText(
              text = "${(dubbedVolume * 100f).roundToInt()}%",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.secondary,
            )
          }

          Slider(
            value = dubbedVolume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1.5f,
            modifier = Modifier.fillMaxWidth().testTag("dubbed_volume_slider"),
            colors =
              SliderDefaults.colors(
                thumbColor = ElectricBlue,
                activeTrackColor = AuroraPurple,
              ),
          )

          Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Headphones,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp).padding(top = 2.dp),
            )
            Text(
              text = comicString(R.string.session_original_audio_note),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = comicString(R.string.session_stats_dropped, snapshot.metrics.droppedInputChunks),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text =
                comicString(
                  R.string.session_stats_dropped_playback,
                  snapshot.metrics.droppedPlaybackChunks,
                ),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = comicString(R.string.session_stats_underruns, snapshot.metrics.playbackUnderruns),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text =
                comicString(R.string.session_stats_reconnects, snapshot.metrics.reconnectAttempts),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Controls: Pause/Resume & Large Stop Dubbing Action
        if (snapshot.state != DubbingSessionState.ERROR) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ComicSpacing.Md),
          ) {
            val isPaused = snapshot.state == DubbingSessionState.PAUSED
            OutlinedButton(
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (isPaused) onResume() else onPause()
              },
              enabled = snapshot.state.isActive,
              modifier =
                Modifier.weight(1f).heightIn(min = 56.dp).testTag("pause_resume_button"),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Icon(
                imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = comicString(if (isPaused) R.string.session_resume else R.string.session_pause)
              )
            }

            Button(
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onStop()
              },
              modifier =
                Modifier.weight(1.4f).heightIn(min = 56.dp).testTag("stop_dubbing_button"),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = CoralError,
                  contentColor = Color.White,
                ),
              shape = RoundedCornerShape(18.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = null,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = comicString(R.string.session_stop_dubbing),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(ComicSpacing.Lg))
      }
    }
  }
}

@Composable
private fun StatusMetricTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  GlassSurfaceCard(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
    )
  }
}

private fun formatElapsed(seconds: Long): String {
  val mins = seconds / 60L
  val secs = seconds % 60L
  return "%02d:%02d".format(mins, secs)
}
