package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.model.DubbingSessionState
import com.example.ui.theme.AuroraIndigo
import com.example.ui.theme.AuroraPurple
import com.example.ui.theme.AuroraPurpleBright
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.WarningAmber
import kotlin.math.PI
import kotlin.math.sin

/**
 * Fluid glowing purple-blue aurora ribbons reacting dynamically to COMIC's 12-state live FSM
 * and real-time audio RMS energy.
 *
 * Performance & Accessibility:
 * - Only composes `rememberInfiniteTransition` when lifecycle is STARTED, reduced motion is off,
 *   and the session is not PAUSED / STOPPING / ERROR (keeping Robolectric/Compose idleness clean
 *   and preventing background GPU/CPU drain).
 * - Reuses Path instances across draw passes to avoid per-frame allocations.
 */
@Composable
fun AuroraWaveform(
  state: DubbingSessionState,
  inputRms: Float,
  outputRms: Float,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()

  val reducedMotion =
    remember(context) {
      runCatching {
        Settings.Global.getFloat(
          context.contentResolver,
          Settings.Global.ANIMATOR_DURATION_SCALE,
          1f,
        ) == 0f
      }.getOrDefault(false)
    }

  val targetEnergy =
    when (state) {
      DubbingSessionState.PLAYING -> maxOf(0.42f, outputRms)
      DubbingSessionState.TRANSLATING -> maxOf(0.32f, inputRms * 0.92f)
      DubbingSessionState.LISTENING -> (0.08f + inputRms * 0.85f).coerceAtMost(0.95f)
      DubbingSessionState.CONNECTING,
      DubbingSessionState.INITIALIZING_CAPTURE,
      DubbingSessionState.VALIDATING,
      DubbingSessionState.REQUESTING_PERMISSION -> 0.22f
      DubbingSessionState.RECONNECTING -> 0.28f
      DubbingSessionState.PAUSED -> 0.03f
      DubbingSessionState.ERROR,
      DubbingSessionState.STOPPING -> 0.05f
      DubbingSessionState.IDLE -> 0.14f
    }

  val smoothEnergy by
    animateFloatAsState(
      targetValue = targetEnergy,
      animationSpec = spring(dampingRatio = 0.74f, stiffness = 210f),
      label = "aurora_energy",
    )

  val shouldAnimate =
    !reducedMotion &&
      lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
      state != DubbingSessionState.PAUSED &&
      state != DubbingSessionState.ERROR &&
      state != DubbingSessionState.STOPPING

  val phasePrimary: Float
  val shimmerClock: Float
  if (shouldAnimate) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_wave")
    val animPhase by
      infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec =
          infiniteRepeatable(
            animation =
              tween(
                durationMillis =
                  when (state) {
                    DubbingSessionState.PLAYING -> 1800
                    DubbingSessionState.TRANSLATING -> 2100
                    DubbingSessionState.LISTENING -> 2800
                    DubbingSessionState.RECONNECTING -> 1600
                    else -> 4600
                  },
                easing = LinearEasing,
              ),
            repeatMode = RepeatMode.Restart,
          ),
        label = "phase_primary",
      )
    val animShimmer by
      infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "aurora_shimmer",
      )
    phasePrimary = animPhase
    shimmerClock = animShimmer
  } else {
    phasePrimary = 0.6f
    shimmerClock = 0.78f
  }

  val primaryTint = MaterialTheme.colorScheme.primary
  val secondaryTint = MaterialTheme.colorScheme.secondary
  val reusablePaths = remember { Array(RIBBON_SPECS.size) { Path() } }

  Canvas(modifier = modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@Canvas

    val centerY = h * 0.5f
    val maxAmp = h * 0.34f * smoothEnergy

    val (ribbonColorA, ribbonColorB, ribbonColorC) =
      when (state) {
        DubbingSessionState.PLAYING ->
          Triple(ElectricBlue, AuroraPurpleBright, LiveEmerald.copy(alpha = 0.75f))
        DubbingSessionState.TRANSLATING ->
          Triple(AuroraPurple, ElectricBlue, AuroraIndigo)
        DubbingSessionState.LISTENING ->
          Triple(AuroraIndigo, ElectricBlue.copy(alpha = 0.85f), AuroraPurpleBright)
        DubbingSessionState.RECONNECTING ->
          Triple(WarningAmber, AuroraPurple, ElectricBlue.copy(alpha = 0.6f))
        DubbingSessionState.PAUSED ->
          Triple(
            primaryTint.copy(alpha = 0.35f),
            secondaryTint.copy(alpha = 0.32f),
            AuroraIndigo.copy(alpha = 0.25f),
          )
        DubbingSessionState.ERROR ->
          Triple(
            CoralError.copy(alpha = 0.75f),
            AuroraPurple.copy(alpha = 0.30f),
            CoralError.copy(alpha = 0.40f),
          )
        else ->
          Triple(AuroraPurple.copy(alpha = 0.82f), ElectricBlue.copy(alpha = 0.78f), AuroraIndigo)
      }

    // Soft atmospheric aurora glow halo behind ribbons
    val haloRadius = maxOf(w, h) * 0.52f
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              ribbonColorA.copy(alpha = 0.20f * shimmerClock),
              ribbonColorB.copy(alpha = 0.09f),
              Color.Transparent,
            ),
          center = Offset(w * 0.5f, centerY),
          radius = haloRadius,
        ),
      center = Offset(w * 0.5f, centerY),
      radius = haloRadius,
    )

    val stepPx = (w / 72f).coerceAtLeast(4f)

    RIBBON_SPECS.forEachIndexed { idx, spec ->
      val path = reusablePaths[idx]
      path.reset()
      var x = 0f
      var started = false
      while (x <= w + stepPx) {
        val normX = (x / w).coerceIn(0f, 1f)
        val envelope = sin(normX * PI).toFloat()
        val angle = (normX * spec.freq * TWO_PI) + (phasePrimary * spec.speedMul) + spec.phaseShift
        val harmonic = sin(angle * 1.5f + phasePrimary) * 0.25f
        val y = centerY + (sin(angle) + harmonic) * maxAmp * spec.ampMul * envelope
        if (!started) {
          path.moveTo(x, y)
          started = true
        } else {
          path.lineTo(x, y)
        }
        x += stepPx
      }

      val ribbonBrush =
        Brush.horizontalGradient(
          colors =
            when (idx) {
              0 -> listOf(ribbonColorA.copy(alpha = 0.08f), ribbonColorA, ribbonColorB, ribbonColorB.copy(alpha = 0.08f))
              1 -> listOf(ribbonColorB.copy(alpha = 0.08f), ribbonColorB, ribbonColorC, ribbonColorA.copy(alpha = 0.08f))
              else -> listOf(ribbonColorC.copy(alpha = 0.08f), ribbonColorC, ribbonColorA, ribbonColorC.copy(alpha = 0.08f))
            }
        )

      drawPath(
        path = path,
        brush = ribbonBrush,
        alpha = spec.alpha * 0.26f * shimmerClock,
        style = Stroke(width = (spec.strokeDp * 3.0f).dp.toPx(), cap = StrokeCap.Round),
      )
      drawPath(
        path = path,
        brush = ribbonBrush,
        alpha = spec.alpha,
        style = Stroke(width = spec.strokeDp.dp.toPx(), cap = StrokeCap.Round),
      )
    }
  }
}

private const val TWO_PI = (2.0 * PI).toFloat()

private data class RibbonSpec(
  val freq: Float,
  val speedMul: Float,
  val ampMul: Float,
  val phaseShift: Float,
  val strokeDp: Float,
  val alpha: Float,
)

private val RIBBON_SPECS =
  listOf(
    RibbonSpec(freq = 1.6f, speedMul = 1.0f, ampMul = 1.0f, phaseShift = 0.0f, strokeDp = 4.2f, alpha = 0.92f),
    RibbonSpec(freq = 2.3f, speedMul = -1.35f, ampMul = 0.78f, phaseShift = 1.4f, strokeDp = 3.0f, alpha = 0.72f),
    RibbonSpec(freq = 3.1f, speedMul = 1.8f, ampMul = 0.55f, phaseShift = 2.7f, strokeDp = 2.0f, alpha = 0.55f),
  )
