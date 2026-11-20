package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DubbingError
import com.example.model.SupportedTargetLanguages
import com.example.model.TargetLanguage
import com.example.ui.KeyTestResult
import com.example.ui.theme.AuroraIndigo
import com.example.ui.theme.AuroraPurple
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalLocalizedContext
import com.example.ui.theme.LocalUiLanguage
import com.example.ui.theme.comicString

@Composable
fun GlassSurfaceCard(
  modifier: Modifier = Modifier,
  borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
    tonalElevation = 1.dp,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      content = content,
    )
  }
}

/**
 * Renders technical/numeric tokens (ISO codes, timers, UIDs, model IDs) cleanly left-to-right
 * even inside a Persian RTL interface.
 */
@Composable
fun LtrMonoText(
  text: String,
  modifier: Modifier = Modifier,
  style: TextStyle = MaterialTheme.typography.labelSmall,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Text(
      text = text,
      modifier = modifier,
      style = style.copy(fontFamily = JetBrainsMonoFontFamily, textDirection = TextDirection.Ltr),
      color = color,
    )
  }
}

@Composable
fun ComicWordmarkBadge(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(CircleShape)
          .background(Brush.linearGradient(listOf(AuroraPurple, ElectricBlue))),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Filled.GraphicEq,
        contentDescription = comicString(R.string.app_name),
        tint = Color.White,
        modifier = Modifier.size(22.dp),
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = comicString(R.string.app_name),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.primaryContainer)
              .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                RoundedCornerShape(8.dp),
              )
              .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Text(
            text = comicString(R.string.release_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
      Text(
        text = comicString(R.string.app_tagline),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun ApiKeyConfigurationCard(
  hasSavedKey: Boolean,
  keyTestResult: KeyTestResult,
  onSaveKey: (String) -> Unit,
  onTestKey: (String?) -> Unit,
  onDeleteKey: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var keyInput by rememberSaveable { mutableStateOf("") }
  var showPlainKey by rememberSaveable { mutableStateOf(false) }
  var emptyValidationError by remember { mutableStateOf(false) }
  var localSavedFeedback by remember { mutableStateOf(false) }

  GlassSurfaceCard(modifier = modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Key,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = comicString(R.string.api_key_label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

    if (hasSavedKey) {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LiveEmerald.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = LiveEmerald,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = comicString(R.string.api_key_saved_masked),
          style = MaterialTheme.typography.labelMedium,
          color = LiveEmerald,
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
      value = keyInput,
      onValueChange = {
        keyInput = it
        emptyValidationError = false
        localSavedFeedback = false
      },
      modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
      placeholder = {
        Text(
          text =
            if (hasSavedKey) "••••••••••••••••••••••••"
            else comicString(R.string.api_key_placeholder),
          style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
        )
      },
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = JetBrainsMonoFontFamily,
        textDirection = TextDirection.Ltr,
      ),
      visualTransformation =
        if (showPlainKey) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions =
        KeyboardOptions(
          keyboardType = KeyboardType.Password,
          imeAction = ImeAction.Done,
        ),
      trailingIcon = {
        IconButton(
          onClick = { showPlainKey = !showPlainKey },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_api_key_visibility"),
        ) {
          Icon(
            imageVector =
              if (showPlainKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription =
              comicString(if (showPlainKey) R.string.api_key_hide else R.string.api_key_show),
          )
        }
      },
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )

    if (emptyValidationError) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = comicString(R.string.api_key_empty_error),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }

    if (localSavedFeedback) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = comicString(R.string.api_key_saved),
        style = MaterialTheme.typography.bodySmall,
        color = LiveEmerald,
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Button(
        onClick = {
          if (keyInput.trim().isEmpty()) {
            emptyValidationError = true
          } else {
            onSaveKey(keyInput.trim())
            keyInput = ""
            localSavedFeedback = true
          }
        },
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_api_key_button"),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
          ),
      ) {
        Text(
          text =
            comicString(if (hasSavedKey) R.string.action_replace_key else R.string.action_save)
        )
      }

      OutlinedButton(
        onClick = {
          val candidate = keyInput.trim().ifEmpty { null }
          if (candidate == null && !hasSavedKey) {
            emptyValidationError = true
          } else {
            onTestKey(candidate)
          }
        },
        enabled = keyTestResult !is KeyTestResult.Testing,
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("test_connection_button"),
      ) {
        if (keyTestResult is KeyTestResult.Testing) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(comicString(R.string.status_testing))
        } else {
          Text(comicString(R.string.action_test_connection))
        }
      }
    }

    if (hasSavedKey) {
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(
        onClick = {
          keyInput = ""
          localSavedFeedback = false
          onDeleteKey()
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_api_key_button"),
        colors =
          ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
      ) {
        Icon(
          imageVector = Icons.Filled.DeleteOutline,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(comicString(R.string.action_delete_key))
      }
    }

    AnimatedVisibility(visible = keyTestResult is KeyTestResult.Success || keyTestResult is KeyTestResult.Failure) {
      val isSuccess = keyTestResult is KeyTestResult.Success
      val message =
        when (keyTestResult) {
          KeyTestResult.Success -> comicString(R.string.api_key_test_success)
          is KeyTestResult.Failure -> resolveDubbingErrorText(keyTestResult.error)
          else -> ""
        }
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
              if (isSuccess) LiveEmerald.copy(alpha = 0.14f)
              else MaterialTheme.colorScheme.errorContainer
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
          contentDescription = null,
          tint = if (isSuccess) LiveEmerald else MaterialTheme.colorScheme.error,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = if (isSuccess) LiveEmerald else MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(
      text = comicString(R.string.api_key_security_note),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun TargetLanguageChipRow(
  selectedLanguage: TargetLanguage,
  onSelectLanguage: (TargetLanguage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiLanguage = LocalUiLanguage.current
  val scrollState = rememberScrollState()
  Row(
    modifier = modifier.fillMaxWidth().horizontalScroll(scrollState),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    SupportedTargetLanguages.ALL.forEach { lang ->
      val selected = lang.code == selectedLanguage.code
      FilterChip(
        selected = selected,
        onClick = { onSelectLanguage(lang) },
        modifier = Modifier.heightIn(min = 48.dp).testTag("lang_chip_${lang.code}"),
        label = {
          Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
              text = lang.displayName(uiLanguage),
              style = MaterialTheme.typography.labelLarge,
            )
            if (lang.nativeName != lang.displayName(uiLanguage)) {
              Text(
                text = lang.nativeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        colors =
          FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetLanguageSelectorSheet(
  selectedLanguage: TargetLanguage,
  onSelectLanguage: (TargetLanguage) -> Unit,
  onDismiss: () -> Unit,
) {
  val uiLanguage = LocalUiLanguage.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = comicString(R.string.home_target_sheet_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = comicString(R.string.home_target_sheet_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      LazyColumn(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 380.dp)
            .testTag("target_language_sheet_list"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(SupportedTargetLanguages.ALL, key = { it.code }) { lang ->
          val isSelected = lang.code == selectedLanguage.code
          Surface(
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onSelectLanguage(lang) }
                .testTag("sheet_lang_${lang.code}"),
            shape = RoundedCornerShape(14.dp),
            color =
              if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
              else MaterialTheme.colorScheme.surfaceContainerLow,
            border =
              BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color =
                  if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
              ),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = lang.displayName(uiLanguage),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                if (lang.nativeName != lang.displayName(uiLanguage)) {
                  Text(
                    text = lang.nativeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                LtrMonoText(
                  text = lang.code.uppercase(),
                  color =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                )
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun GradientPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leadingContent: (@Composable () -> Unit)? = null,
) {
  val gradient =
    if (enabled) {
      Brush.horizontalGradient(listOf(AuroraPurple, AuroraIndigo, ElectricBlue))
    } else {
      Brush.horizontalGradient(
        listOf(
          MaterialTheme.colorScheme.surfaceVariant,
          MaterialTheme.colorScheme.surfaceVariant,
        )
      )
    }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(gradient)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 24.dp, vertical = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (leadingContent != null) {
        leadingContent()
        Spacer(modifier = Modifier.width(10.dp))
      }
      Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun resolveDubbingErrorText(error: DubbingError): String {
  val context = LocalLocalizedContext.current
  return when (error) {
    is DubbingError.WebSocketClosedUnexpectedly ->
      context.getString(error.messageRes, error.reason)
    else -> {
      val base = context.getString(error.messageRes)
      val detail = error.detail?.takeIf { it.isNotBlank() }
      if (detail != null && error !is DubbingError.InvalidApiKey) {
        "$base ($detail)"
      } else {
        base
      }
    }
  }
}
