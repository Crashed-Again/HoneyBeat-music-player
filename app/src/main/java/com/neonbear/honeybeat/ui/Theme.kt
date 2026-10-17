package com.neonbear.honeybeat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.neonbear.honeybeat.Fonts

object HB {
    val Navy = Color(0xFF0A1C3E)
    val Ink = Color(0xFF0F0F12)
    val Card = Color.White.copy(alpha = 0.09f)
    val Muted = Color(0xFF8F95A3)
    val NavBar = Color(0xFF0C0E13)
    val NavActive = Color(0xFF17305E)
    val Pink = Color(0xFFB03F80)
    val Chip = Color(0xFF1B1B20)
    val Outline = Color(0xFF3A3A40)
    val bg = Brush.verticalGradient(0f to Navy, 0.55f to Ink, 1f to Ink)
}

fun tDisplay(size: Int, color: Color = Color.White) =
    TextStyle(fontFamily = Fonts.display, fontSize = size.sp, color = color)

fun tBody(size: Int, color: Color = Color.White) =
    TextStyle(fontFamily = Fonts.body, fontSize = size.sp, color = color)

fun tBold(size: Int, color: Color = Color.White) =
    TextStyle(fontFamily = Fonts.bold, fontSize = size.sp, color = color)

@Composable
fun HoneyBeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HB.Pink,
            background = HB.Ink,
            surface = HB.Ink,
            onSurface = Color.White,
        ),
        content = content
    )
}
