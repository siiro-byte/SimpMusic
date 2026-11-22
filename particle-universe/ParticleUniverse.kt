package com.simpmusic.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.*

/** Particle Universe prototype. FFT bands must be normalized to 0..1. */
@Composable
fun ParticleUniverse(
    bands: FloatArray,
    albumColors: List<Color>,
    modifier: Modifier = Modifier,
    intensity: Float = 0.65f,
    speed: Float = 0.65f,
    enabled: Boolean = true
) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(enabled, speed) {
        while (enabled) { delay(33L); tick++ }
    }
    val palette = albumColors.ifEmpty {
        listOf(Color(0xFF47D6E5), Color(0xFFAA74F5), Color(0xFFED6CBA))
    }
    val bass = bands.take(6).average().toFloat().coerceIn(0f, 1f)
    val mids = bands.drop(6).take(18).average().toFloat().coerceIn(0f, 1f)
    val treble = bands.drop(24).average().toFloat().coerceIn(0f, 1f)
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) * 0.43f
        val t = tick * 0.025f * speed
        repeat(440) { i ->
            val norm = sqrt((i + 0.5f) / 440f)
            val angle = i * 2.399963f + t * (0.25f + norm * 0.18f)
            val expansion = 1f + bass * intensity * 0.5f
            val r = radius * (0.14f + norm * 0.86f) * expansion
            val drift = sin(t * 2f + i * 0.12f) * mids * intensity * radius * 0.08f
            val pos = Offset(center.x + cos(angle) * (r + drift), center.y + sin(angle) * (r + drift) * 0.68f)
            val pointRadius = (0.65f + (i % 7) * 0.17f) * (1f + treble * intensity * 1.4f) * density
            drawCircle(palette[i % palette.size].copy(alpha = 0.25f + (1f - norm) * 0.65f), pointRadius, pos)
        }
        repeat(3) { ring ->
            drawCircle(palette[ring % palette.size].copy(alpha = 0.12f), radius * (0.35f + ring * 0.25f) * (1f + bass * intensity * 0.2f), center, style = Stroke(0.7f * density))
        }
    }
}
