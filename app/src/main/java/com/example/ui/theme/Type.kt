package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.UiLanguage

val VazirmatnFontFamily =
  FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
  )

// Clean Android Roboto system sans-serif paired with JetBrains Mono readouts for English UI
val RobotoSansFontFamily = FontFamily.SansSerif

val JetBrainsMonoFontFamily =
  FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
  )

fun comicTypography(uiLanguage: UiLanguage): Typography {
  val isPersian = uiLanguage == UiLanguage.PERSIAN
  val primaryFamily = if (isPersian) VazirmatnFontFamily else RobotoSansFontFamily

  return Typography(
    displaySmall =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = if (isPersian) 44.sp else 40.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = if (isPersian) 36.sp else 32.sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = if (isPersian) 30.sp else 28.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = if (isPersian) 28.sp else 26.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = if (isPersian) 26.sp else 24.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = if (isPersian) 22.sp else 20.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = if (isPersian) 26.sp else 23.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = if (isPersian) 24.sp else 21.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = if (isPersian) 20.sp else 18.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = if (isPersian) 22.sp else 20.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = primaryFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = if (isPersian) 18.sp else 16.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
      ),
  )
}
