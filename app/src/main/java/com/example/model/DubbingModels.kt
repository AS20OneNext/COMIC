package com.example.model

import androidx.annotation.StringRes
import com.example.R

enum class UiLanguage(val code: String, val isRtl: Boolean) {
  ENGLISH("en", isRtl = false),
  PERSIAN("fa", isRtl = true);

  companion object {
    /**
     * Returns the saved language if present; defaults to ENGLISH for fresh V0.2 installations.
     */
    fun fromCode(code: String?): UiLanguage =
      entries.firstOrNull { it.code == code } ?: ENGLISH
  }
}

enum class AppThemeMode(val key: String) {
  DARK("dark"),
  SYSTEM("system"),
  LIGHT("light");

  companion object {
    fun fromKey(key: String?): AppThemeMode =
      entries.firstOrNull { it.key == key } ?: DARK
  }
}

data class TargetLanguage(
  val code: String,
  val englishName: String,
  val persianName: String,
  val nativeName: String,
) {
  fun displayName(uiLanguage: UiLanguage): String =
    when (uiLanguage) {
      UiLanguage.PERSIAN -> persianName
      UiLanguage.ENGLISH -> englishName
    }
}

object SupportedTargetLanguages {
  val DEFAULT = TargetLanguage("fa", "Persian", "فارسی", "فارسی")

  // Curated top 12 global and regional languages
  val ALL: List<TargetLanguage> =
    listOf(
      DEFAULT,
      TargetLanguage("en", "English", "انگلیسی", "English"),
      TargetLanguage("ar", "Arabic", "عربی", "العربية"),
      TargetLanguage("tr", "Turkish", "ترکی استانبولی", "Türkçe"),
      TargetLanguage("es", "Spanish", "اسپانیایی", "Español"),
      TargetLanguage("fr", "French", "فرانسوی", "Français"),
      TargetLanguage("de", "German", "آلمانی", "Deutsch"),
      TargetLanguage("ru", "Russian", "روسی", "Русский"),
      TargetLanguage("zh", "Chinese", "چینی", "中文"),
      TargetLanguage("ja", "Japanese", "ژاپنی", "日本語"),
      TargetLanguage("ko", "Korean", "کره‌ای", "한국어"),
      TargetLanguage("hi", "Hindi", "هندی", "हिन्दी"),
    )

  fun findByCode(code: String?): TargetLanguage =
    ALL.firstOrNull { it.code == code } ?: DEFAULT
}

sealed interface AudioSourceMode {
  data object AllEligible : AudioSourceMode

  data class SpecificApp(
    val packageName: String,
    val label: String,
    val uid: Int,
  ) : AudioSourceMode
}

data class LaunchableAppInfo(
  val packageName: String,
  val label: String,
  val uid: Int,
)

enum class DubbingSessionState {
  IDLE,
  VALIDATING,
  REQUESTING_PERMISSION,
  INITIALIZING_CAPTURE,
  CONNECTING,
  LISTENING,
  TRANSLATING,
  PLAYING,
  RECONNECTING,
  PAUSED,
  STOPPING,
  ERROR;

  val isActive: Boolean
    get() =
      this == LISTENING ||
        this == TRANSLATING ||
        this == PLAYING ||
        this == RECONNECTING ||
        this == PAUSED

  val isRunningOrStarting: Boolean
    get() = this != IDLE && this != ERROR && this != STOPPING
}

sealed interface DubbingError {
  @get:StringRes val messageRes: Int
  val detail: String? get() = null

  data class InvalidApiKey(override val detail: String? = null) : DubbingError {
    override val messageRes: Int = R.string.err_invalid_api_key
  }

  data class GeminiConnectionFailure(override val detail: String? = null) : DubbingError {
    override val messageRes: Int = R.string.err_gemini_connection
  }

  data class QuotaExceeded(override val detail: String? = null) : DubbingError {
    override val messageRes: Int = R.string.err_quota_exceeded
  }

  data class ModelUnavailable(override val detail: String? = null) : DubbingError {
    override val messageRes: Int = R.string.err_model_unavailable
  }

  data class UnsupportedAudioSource(override val detail: String? = null) : DubbingError {
    override val messageRes: Int = R.string.err_unsupported_source
  }

  data object NoCapturedAudio : DubbingError {
    override val messageRes: Int = R.string.err_no_captured_audio
  }

  data object AudioCaptureDenied : DubbingError {
    override val messageRes: Int = R.string.err_audio_capture_denied
  }

  data object ProjectionStopped : DubbingError {
    override val messageRes: Int = R.string.err_projection_stopped
  }

  data class WebSocketClosedUnexpectedly(val reason: String) : DubbingError {
    override val messageRes: Int = R.string.err_websocket_closed
    override val detail: String = reason
  }

  data object NetworkInterrupted : DubbingError {
    override val messageRes: Int = R.string.err_network_interrupted
  }

  data class InvalidPcmInput(override val detail: String? = null) : DubbingError {
    override val messageRes: Int = R.string.err_invalid_pcm
  }

  data class AudioOutputFailure(override val detail: String? = null) : DubbingError {
    override val messageRes: Int = R.string.err_audio_output
  }

  data class ForegroundServiceFailure(override val detail: String? = null) : DubbingError {
    override val messageRes: Int = R.string.err_foreground_service
  }

  data object UnsupportedAndroidConfig : DubbingError {
    override val messageRes: Int = R.string.err_unsupported_android
  }

  data object NoAppSelected : DubbingError {
    override val messageRes: Int = R.string.err_no_app_selected
  }
}

data class SessionPerformanceMetrics(
  val captureInitDelayMs: Long = 0L,
  val geminiConnectDelayMs: Long = 0L,
  val firstTranslationDelayMs: Long = 0L,
  val droppedInputChunks: Long = 0L,
  val droppedPlaybackChunks: Long = 0L,
  val playbackUnderruns: Int = 0,
  val reconnectAttempts: Int = 0,
  val sentAudioBytes: Long = 0L,
  val receivedAudioBytes: Long = 0L,
)

data class DubbingUiSnapshot(
  val state: DubbingSessionState = DubbingSessionState.IDLE,
  val sourceMode: AudioSourceMode = AudioSourceMode.AllEligible,
  val targetLanguage: TargetLanguage = SupportedTargetLanguages.DEFAULT,
  val elapsedSeconds: Long = 0L,
  val inputRmsLevel: Float = 0f,
  val outputRmsLevel: Float = 0f,
  val isPlaybackActive: Boolean = false,
  val noAudioWarning: Boolean = false,
  val reconnectAttempt: Int = 0,
  val error: DubbingError? = null,
  val metrics: SessionPerformanceMetrics = SessionPerformanceMetrics(),
)
