package com.example.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.text.BidiFormatter
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeMode
import com.example.model.UiLanguage
import java.util.Locale

object ComicSpacing {
  val Xs = 4.dp
  val Sm = 8.dp
  val Md = 12.dp
  val Lg = 16.dp
  val Xl = 20.dp
  val Xxl = 24.dp
}

private val ComicDarkColorScheme =
  darkColorScheme(
    primary = AuroraPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B1C54),
    onPrimaryContainer = AuroraPurpleBright,
    secondary = ElectricBlue,
    onSecondary = Color(0xFF041E2B),
    secondaryContainer = Color(0xFF10283B),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AuroraIndigo,
    onTertiary = Color.White,
    background = ObsidianBlack,
    onBackground = TextPrimaryDark,
    surface = CharcoalBase,
    onSurface = TextPrimaryDark,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = CharcoalBase,
    surfaceContainer = CharcoalElevated,
    surfaceContainerHigh = CharcoalCard,
    outline = CharcoalBorder,
    outlineVariant = CharcoalHairline,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralErrorContainer,
    onErrorContainer = Color(0xFFFCA5A5),
  )

private val ComicLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = ElectricBlueDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = AuroraIndigo,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
  )

private val ComicShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
  )

val LocalLocalizedContext =
  staticCompositionLocalOf<Context> { error("LocalizedContext not provided") }

val LocalUiLanguage = staticCompositionLocalOf { UiLanguage.ENGLISH }

@Composable
fun comicString(@StringRes id: Int): String =
  LocalLocalizedContext.current.getString(id)

@Composable
fun comicString(@StringRes id: Int, vararg formatArgs: Any): String =
  LocalLocalizedContext.current.getString(id, *formatArgs)

/**
 * Safely isolates LTR tokens (e.g. model IDs, UIDs, version numbers) inside RTL sentences.
 */
fun isolateLtrInBidi(text: String, isRtlContext: Boolean): String =
  if (isRtlContext) {
    BidiFormatter.getInstance(true).unicodeWrap(text, android.text.TextDirectionHeuristics.LTR)
  } else {
    text
  }

@Composable
fun ComicTheme(
  themeMode: AppThemeMode = AppThemeMode.DARK,
  uiLanguage: UiLanguage = UiLanguage.ENGLISH,
  content: @Composable () -> Unit,
) {
  val isDark =
    when (themeMode) {
      AppThemeMode.DARK -> true
      AppThemeMode.LIGHT -> false
      AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

  val baseContext = LocalContext.current
  val localizedContext =
    remember(baseContext, uiLanguage) {
      val config = Configuration(baseContext.resources.configuration)
      config.setLocale(Locale.forLanguageTag(uiLanguage.code))
      baseContext.createConfigurationContext(config)
    }

  val layoutDirection =
    if (uiLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

  val colorScheme = if (isDark) ComicDarkColorScheme else ComicLightColorScheme
  val typography = remember(uiLanguage) { comicTypography(uiLanguage) }

  CompositionLocalProvider(
    LocalLocalizedContext provides localizedContext,
    LocalUiLanguage provides uiLanguage,
    LocalLayoutDirection provides layoutDirection,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = typography,
      shapes = ComicShapes,
      content = content,
    )
  }
}
