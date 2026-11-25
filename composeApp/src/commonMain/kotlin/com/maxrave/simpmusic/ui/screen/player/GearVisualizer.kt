package com.maxrave.simpmusic.ui.screen.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.maxrave.domain.data.player.AudioBands
import com.maxrave.domain.data.player.AudioBandsBus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

private const val BASS_TEETH = 20
private const val MID_TEETH = 14
private const val TREBLE_TEETH = 10

/** Smoothed loudness of each band plus the rotation of the big gear; the other two gears follow it. */
private class GearModel {
    var bass = 0f
    var mid = 0f
    var treble = 0f
    private var speed = 0f
    var turn = 0f

    fun update(
        bands: AudioBands?,
        dt: Float,
    ) {
        bass = follow(bass, bands?.bass ?: 0f, dt)
        mid = follow(mid, bands?.mid ?: 0f, dt)
        treble = follow(treble, bands?.treble ?: 0f, dt)
        val loudness = 0.5f * bass + 0.3f * mid + 0.2f * treble
        val targetSpeed = 0.35f + 4.5f * loudness
        speed += (targetSpeed - speed) * min(1f, dt * 4f)
        turn += speed * dt
    }

    private fun follow(
        current: Float,
        target: Float,
        dt: Float,
    ): Float {
        val rate = if (target > current) 18f else 5f
        return current + (target - current) * min(1f, dt * rate)
    }
}

/**
 * Three meshing gears over the Now Playing screen. The big gear moves with the bass, the middle one
 * with the mids and the small one with the treble: each pulses and brightens with its own band, and
 * together they turn faster the louder the music is. Touches pass straight through to the player.
 * The small gear at the screen edge switches the visualizer on and off.
 */
@Composable
fun BoxScope.GearVisualizerOverlay() {
    var showGears by rememberSaveable { mutableStateOf(true) }
    val model = remember { GearModel() }
    var tick by remember { mutableIntStateOf(0) }

    // Analysis only runs while the gears are on screen.
    DisposableEffect(showGears) {
        AudioBandsBus.enabled.value = showGears
        onDispose { AudioBandsBus.enabled.value = false }
    }

    LaunchedEffect(showGears) {
        if (!showGears) return@LaunchedEffect
        var lastFrame = 0L
        var lastBands: AudioBands? = null
        var lastChange = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrame == 0L) lastFrame = now
                val dt = ((now - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                lastFrame = now
                val bands = AudioBandsBus.bands.value
                if (bands !== lastBands) {
                    lastBands = bands
                    lastChange = now
                }
                // No new reading for a moment means playback stopped: let the gears wind down.
                val stale = now - lastChange > 150_000_000L
                model.update(if (stale) null else bands, dt)
                tick++
            }
        }
    }

    val bassColor = MaterialTheme.colorScheme.primary
    val midColor = MaterialTheme.colorScheme.secondary
    val trebleColor = MaterialTheme.colorScheme.tertiary
    val toggleColor = MaterialTheme.colorScheme.onSurface

    if (showGears) {
        Canvas(Modifier.matchParentSize()) {
            if (tick >= 0) drawGearTrain(model, bassColor, midColor, trebleColor)
        }
    }

    Canvas(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 4.dp)
            .size(32.dp)
            .clickable { showGears = !showGears },
    ) {
        val unit = size.minDimension
        val path =
            gearPath(
                center = Offset(size.width / 2f, size.height / 2f),
                pitchRadius = unit * 0.30f,
                teeth = 8,
                rotation = 0f,
                addendum = unit * 0.07f,
                dedendum = unit * 0.08f,
                holeRadius = unit * 0.10f,
            )
        drawPath(path, toggleColor.copy(alpha = if (showGears) 0.65f else 0.35f))
    }
}

private fun DrawScope.drawGearTrain(
    model: GearModel,
    bassColor: Color,
    midColor: Color,
    trebleColor: Color,
) {
    // Layout in units of one gear module, so the pitch radius of a gear is half its tooth count.
    val r1 = BASS_TEETH / 2f
    val r2 = MID_TEETH / 2f
    val r3 = TREBLE_TEETH / 2f
    val reach = 1.0f
    val a = (-20.0 * PI / 180.0).toFloat()
    val halfTurn = PI.toFloat()
    val step2 = (2.0 * PI / MID_TEETH).toFloat()
    // The third gear must sit where the middle gear has a tooth when the first has one pointing at it.
    val beta0 = a + halfTurn + halfTurn / MID_TEETH
    val target = (35.0 * PI / 180.0).toFloat()
    val beta = beta0 + step2 * round((target - beta0) / step2)

    val p1 = Offset(0f, 0f)
    val p2 = p1 + Offset(cos(a), sin(a)) * (r1 + r2)
    val p3 = p2 + Offset(cos(beta), sin(beta)) * (r2 + r3)

    val minX = min(p1.x - r1 - reach, min(p2.x - r2 - reach, p3.x - r3 - reach))
    val maxX = max(p1.x + r1 + reach, max(p2.x + r2 + reach, p3.x + r3 + reach))
    val minY = min(p1.y - r1 - reach, min(p2.y - r2 - reach, p3.y - r3 - reach))
    val maxY = max(p1.y + r1 + reach, max(p2.y + r2 + reach, p3.y + r3 + reach))

    // The gears fill the lower part of the screen, behind the controls.
    val regionLeft = size.width * 0.04f
    val regionTop = size.height * 0.46f
    val regionWidth = size.width * 0.92f
    val regionHeight = size.height * 0.52f
    val scale = min(regionWidth / (maxX - minX), regionHeight / (maxY - minY))
    val originX = regionLeft + (regionWidth - (maxX - minX) * scale) / 2f - minX * scale
    val originY = regionTop + (regionHeight - (maxY - minY) * scale) / 2f - minY * scale
    fun place(p: Offset) = Offset(originX + p.x * scale, originY + p.y * scale)

    val s = model.turn
    val angle1 = a + s
    val angle2 = a + halfTurn + halfTurn / MID_TEETH - s * BASS_TEETH / MID_TEETH
    val angle3 = beta + halfTurn + halfTurn / TREBLE_TEETH + s * BASS_TEETH / TREBLE_TEETH

    fun gear(
        centre: Offset,
        pitch: Float,
        teeth: Int,
        angle: Float,
        level: Float,
        color: Color,
    ) {
        val pulse = 1f + 0.06f * level
        val path =
            gearPath(
                center = place(centre),
                pitchRadius = pitch * scale * pulse,
                teeth = teeth,
                rotation = angle,
                addendum = 1.0f * scale,
                dedendum = 1.25f * scale,
                holeRadius = pitch * scale * 0.28f,
            )
        drawPath(path, color.copy(alpha = (0.18f + 0.5f * level).coerceAtMost(0.75f)))
    }

    gear(p1, r1, BASS_TEETH, angle1, model.bass, bassColor)
    gear(p2, r2, MID_TEETH, angle2, model.mid, midColor)
    gear(p3, r3, TREBLE_TEETH, angle3, model.treble, trebleColor)
}

/** A gear outline with a round hole, built as one even-odd path. Angles are in radians. */
private fun gearPath(
    center: Offset,
    pitchRadius: Float,
    teeth: Int,
    rotation: Float,
    addendum: Float,
    dedendum: Float,
    holeRadius: Float,
): Path {
    val path = Path()
    path.fillType = PathFillType.EvenOdd
    val rootRadius = pitchRadius - dedendum
    val tipRadius = pitchRadius + addendum
    val pitchAngle = (2.0 * PI / teeth).toFloat()
    var first = true
    for (i in 0 until teeth) {
        val toothAngle = rotation + i * pitchAngle
        // Root, tip, tip, root: a trapezoidal tooth centred on toothAngle.
        val offsets = floatArrayOf(-0.30f, -0.14f, 0.14f, 0.30f)
        val radii = floatArrayOf(rootRadius, tipRadius, tipRadius, rootRadius)
        for (j in offsets.indices) {
            val angle = toothAngle + offsets[j] * pitchAngle
            val x = center.x + cos(angle) * radii[j]
            val y = center.y + sin(angle) * radii[j]
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
    }
    path.close()
    path.addOval(Rect(center.x - holeRadius, center.y - holeRadius, center.x + holeRadius, center.y + holeRadius))
    return path
}
