package com.neonbear.honeybeat.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colors sampled by eye from the HoneyBeat Library screenshot.
object HB {
    val NavyTop = Color(0xFF0A2145)
    val Bg = Color(0xFF0E0E10)
    val Card = Color(0xFF262E3E)
    val Search = Color(0xFF232C42)
    val NavBar = Color(0xFF111118)
    val NavPill = Color(0xFF17284F)
    val TextDim = Color(0xFF9AA0AE)

    // Drop a techy font (e.g. Orbitron/Michroma) into res/font and set it here:
    // val Title = FontFamily(Font(R.font.honeybeat_title))
    val Title: FontFamily = FontFamily.Default
}

/** Navy at the top fading into near-black, like the screenshot. Wrap every screen in this. */
@Composable
fun HoneyBeatBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to HB.NavyTop, 0.45f to HB.Bg, 1f to HB.Bg)),
        content = content,
    )
}

/** Big screen title + dim subtitle ("Library" / "Every folder in Music/HoneyBeat is a playlist."). */
@Composable
fun HoneyBeatHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, color = Color.White, fontSize = 44.sp, fontFamily = HB.Title, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = HB.TextDim, fontSize = 16.sp)
    }
}

/** Rounded card row used for playlists/songs. */
@Composable
fun HoneyBeatCard(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(HB.Card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Floating pill bottom bar: Library / Search / Options. */
@Composable
fun HoneyBeatNavBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(40.dp))
            .background(HB.NavBar)
            .padding(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (i == selected) HB.NavPill else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (i == selected) Color.White else HB.TextDim, fontFamily = HB.Title, fontSize = 16.sp)
            }
        }
    }
}

/** Put this as the LAST item of the Settings list. */
@Composable
fun HoneyBeatSettingsFooter() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NeonBear", color = Color.White, fontSize = 20.sp, fontFamily = HB.Title, fontWeight = FontWeight.Bold)
        Text("Based on Metrolist", color = HB.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
