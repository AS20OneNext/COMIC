package com.example

import android.app.Application
import com.example.audio.AndroidAudioCaptureManager
import com.example.audio.AndroidAudioPlaybackManager
import com.example.data.AppSettings
import com.example.data.InstalledAppsRepository
import com.example.data.SecureApiKeyStorage
import com.example.dubbing.LiveDubbingManager
import com.example.gemini.OkHttpGeminiLiveTranslateClient
import okhttp3.OkHttpClient

class ComicApplication : Application() {

  val sharedOkHttpClient: OkHttpClient by lazy {
    OkHttpGeminiLiveTranslateClient.defaultOkHttpClient()
  }

  val appSettings: AppSettings by lazy { AppSettings(this) }
  val apiKeyStorage: SecureApiKeyStorage by lazy { SecureApiKeyStorage(this) }
  val installedAppsRepository: InstalledAppsRepository by lazy { InstalledAppsRepository(this) }

  val liveDubbingManager: LiveDubbingManager by lazy {
    LiveDubbingManager(
      apiKeyProvider = apiKeyStorage,
      captureSourceFactory = { AndroidAudioCaptureManager() },
      playbackSinkFactory = { AndroidAudioPlaybackManager() },
      geminiClientFactory = { OkHttpGeminiLiveTranslateClient(okHttpClient = sharedOkHttpClient) },
    )
  }
}
