package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.ComicApplication
import com.example.MainActivity
import com.example.R
import com.example.model.DubbingSessionState
import com.example.model.UiLanguage
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LiveDubbingForegroundService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var observerJob: Job? = null
  private lateinit var projectionPermissionManager: ProjectionPermissionManager

  override fun onCreate() {
    super.onCreate()
    projectionPermissionManager = ProjectionPermissionManager(this)
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val app = application as ComicApplication
    val manager = app.liveDubbingManager

    when (intent?.action) {
      ACTION_STOP_DUBBING -> {
        serviceScope.launch {
          manager.stopSession()
          stopForegroundAndSelf()
        }
        return START_NOT_STICKY
      }

      ACTION_START_DUBBING -> {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
          } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
          }

        // Promote synchronously inside onStartCommand using currentPreferencesSnapshot to meet
        // Android 14+ foreground service promotion timing before calling getMediaProjection().
        val currentPrefs = app.appSettings.currentPreferencesSnapshot
        val snapshot = manager.snapshot.value
        val targetName = snapshot.targetLanguage.displayName(currentPrefs.uiLanguage)
        val notification = buildNotification(currentPrefs.uiLanguage, targetName, isPaused = false)

        try {
          startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
          )
        } catch (t: Throwable) {
          serviceScope.launch { manager.onForegroundServiceFailed(t.message) }
          stopSelf()
          return START_NOT_STICKY
        }

        serviceScope.launch {
          if (resultCode != Activity.RESULT_OK || resultData == null) {
            manager.onPermissionDenied()
            stopForegroundAndSelf()
            return@launch
          }

          val projection = projectionPermissionManager.obtainMediaProjection(resultCode, resultData)
          observeSessionState(app)
          manager.startPipelineWithProjection(
            mediaProjection = projection,
            onTeardownService = { stopForegroundAndSelf() },
          )
        }
        return START_NOT_STICKY
      }

      else -> {
        stopSelf()
        return START_NOT_STICKY
      }
    }
  }

  private fun observeSessionState(app: ComicApplication) {
    observerJob?.cancel()
    observerJob =
      serviceScope.launch {
        app.liveDubbingManager.snapshot.collect { snap ->
          when (snap.state) {
            DubbingSessionState.IDLE, DubbingSessionState.ERROR -> {
              stopForegroundAndSelf()
            }
            DubbingSessionState.PAUSED,
            DubbingSessionState.LISTENING,
            DubbingSessionState.TRANSLATING,
            DubbingSessionState.PLAYING,
            DubbingSessionState.RECONNECTING -> {
              val prefs = app.appSettings.currentPreferencesSnapshot
              val notif =
                buildNotification(
                  uiLanguage = prefs.uiLanguage,
                  targetLanguageName = snap.targetLanguage.displayName(prefs.uiLanguage),
                  isPaused = snap.state == DubbingSessionState.PAUSED,
                )
              val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
              nm.notify(NOTIFICATION_ID, notif)
            }
            else -> Unit
          }
        }
      }
  }

  private fun stopForegroundAndSelf() {
    observerJob?.cancel()
    observerJob = null
    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    stopSelf()
  }

  private fun createNotificationChannel() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.notif_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = getString(R.string.notif_channel_desc)
        setShowBadge(false)
      }
    nm.createNotificationChannel(channel)
  }

  private fun buildNotification(
    uiLanguage: UiLanguage,
    targetLanguageName: String,
    isPaused: Boolean,
  ): Notification {
    val localizedContext = localizedContextFor(uiLanguage)
    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      Intent(this, LiveDubbingForegroundService::class.java).apply {
        action = ACTION_STOP_DUBBING
      }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        1,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val bodyText =
      if (isPaused) {
        localizedContext.getString(R.string.notif_body_paused, targetLanguageName)
      } else {
        localizedContext.getString(R.string.notif_body_target, targetLanguageName)
      }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher_round)
      .setContentTitle(localizedContext.getString(R.string.notif_title))
      .setContentText(bodyText)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setContentIntent(contentPendingIntent)
      .addAction(
        android.R.drawable.ic_media_pause,
        localizedContext.getString(R.string.notif_action_stop),
        stopPendingIntent,
      )
      .build()
  }

  private fun localizedContextFor(language: UiLanguage): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(language.code))
    return createConfigurationContext(config)
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    observerJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    private const val CHANNEL_ID = "comic_live_dubbing_channel"
    private const val NOTIFICATION_ID = 4201

    const val ACTION_START_DUBBING = "com.example.comic.action.START_DUBBING"
    const val ACTION_STOP_DUBBING = "com.example.comic.action.STOP_DUBBING"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"

    fun createStartIntent(context: Context, resultCode: Int, data: Intent): Intent =
      Intent(context, LiveDubbingForegroundService::class.java).apply {
        action = ACTION_START_DUBBING
        putExtra(EXTRA_RESULT_CODE, resultCode)
        putExtra(EXTRA_RESULT_DATA, data)
      }

    fun createStopIntent(context: Context): Intent =
      Intent(context, LiveDubbingForegroundService::class.java).apply {
        action = ACTION_STOP_DUBBING
      }
  }
}
