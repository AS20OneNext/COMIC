package com.example.service

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build

class ProjectionPermissionManager(private val context: Context) {

  private val mediaProjectionManager: MediaProjectionManager?
    get() = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager

  /**
   * Builds the system MediaProjection consent Intent.
   * On Android 14+ (API 34+), uses user choice config so users can select a single app window
   * or the entire display. On API 29-33, uses the standard display capture prompt.
   */
  fun createCaptureConsentIntent(): Intent? {
    val manager = mediaProjectionManager ?: return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
    } else {
      manager.createScreenCaptureIntent()
    }
  }

  /**
   * Must only be called AFTER a Foreground Service with foregroundServiceType="mediaProjection"
   * has called startForeground().
   */
  fun obtainMediaProjection(resultCode: Int, data: Intent): MediaProjection? {
    val manager = mediaProjectionManager ?: return null
    return runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
  }
}
