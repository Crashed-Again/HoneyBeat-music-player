package com.neonbear.honeybeat.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.neonbear.honeybeat.AppViewModel
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

@Composable
private fun rememberAccent(model: String?): Color {
    val ctx = LocalContext.current
    var c by remember { mutableStateOf(HB.Pink) }
    LaunchedEffect(model) {
        if (model == null) {
            c = HB.Pink
            return@LaunchedEffect
        }
        runCatching {
            val req = ImageRequest.Builder(ctx)
                .data(if (model.startsWith("/")) File(model) else model)
                .allowHardware(false)
                .size(128)
                .build()
            val bmp = (ctx.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()
            if (bmp != null) {
                val p = Palette.from(bmp).generate()
                val sw = p.vibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch
                if (sw != null) {
                    var col = Color(sw.rgb)
                    if (col.luminance() < 0.12f) col = lerp(col, Color.White, 0.35f)
                    c = col
                }
            }
        }
    }
    return animateColorAsState(c, tween(600), label = "accent").value
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Waveform(progress: Float, seed: Int, accent: Color, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val bars = remember(seed) {
        val r = Random(seed)
        List(34) { 0.1f + r.nextFloat().pow(1.7f) * 0.9f }
    }
    var drag by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val shown = drag ?: progress

    Canvas(
        modifier
            .height(120.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> seek((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { seek(it) }; drag = null },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
                )
            }
    ) {
        val w = size.width
        val n = bars.size
        val barW = 8.dp.toPx()
        val gap = (w - n * barW) / (n - 1)
        val zone = 84.dp.toPx()
        bars.forEachIndexed { i, v ->
            val x = i * (barW + gap)
            val h = max(barW, v * zone)
            val played = (x + barW / 2) / w <= shown
            drawRoundRect(
                color = if (played) accent else accent.copy(alpha = 0.45f),
                topLeft = Offset(x, zone - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2)
            )
        }
        val ty = zone + 18.dp.toPx()
        val th = 22.dp.toPx()
        drawRoundRect(Color(0xFF3A3A40), Offset(0f, ty), Size(w, th), CornerRadius(th / 2))
        val px = (w * shown).coerceIn(0f, w)
        if (px > 0f) drawRoundRect(accent.copy(alpha = 0.8f), Offset(0f, ty), Size(px, th), CornerRadius(th / 2))
        val lw = 4.dp.toPx()
        drawRoundRect(accent, Offset((px - lw / 2).coerceIn(0f, w - lw), zone - 6.dp.toPx()), Size(lw, th + 40.dp.toPx()), CornerRadius(lw / 2))
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, size: Int, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF1C1C20)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size((size * 0.46f).dp)) }
}

@Composable
fun PlayerScreen(vm: AppViewModel, onClose: () -> Unit) {
    val ps = vm.player
    val ctx = LocalContext.current
    val accent = rememberAccent(ps.artwork)
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = vm.position()
            delay(250)
        }
    }
    val progress = if (ps.durationMs > 0) (pos.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxSize()
            .background(HB.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundIcon(Icons.Filled.KeyboardArrowDown, 54, "Close", onClose)
            Text("NOW PLAYING", style = tDisplay(16, HB.Muted).copy(letterSpacing = 1.sp))
            RoundIcon(Icons.Filled.Share, 54, "Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, if (ps.artist.isBlank()) ps.title else "${ps.title} - ${ps.artist}")
                }
                ctx.startActivity(Intent.createChooser(send, null))
            }
        }
        Spacer(Modifier.height(36.dp))
        Cover(ps.artwork, Modifier.padding(horizontal = 24.dp).fillMaxWidth().aspectRatio(1f), 30.dp)
        Spacer(Modifier.height(36.dp))
        Text(
            ps.title,
            style = tDisplay(26),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            ps.artist,
            style = tBody(19, HB.Muted),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(20.dp))
        Waveform(progress, ps.key.hashCode(), accent, { vm.seekFraction(it) }, Modifier.padding(horizontal = 24.dp).fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), style = tBody(16, HB.Muted))
            Text(fmt(ps.durationMs), style = tBody(16, HB.Muted))
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PillButton("Shuffle", ps.shuffle, accent) { vm.toggleShuffle() }
            RoundIcon(Icons.Filled.SkipPrevious, 54, "Previous") { vm.prev() }
            Box(
                Modifier.size(78.dp).clip(CircleShape).background(accent).clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (ps.isPlaying) "Pause" else "Play",
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(40.dp)
                )
            }
            RoundIcon(Icons.Filled.SkipNext, 54, "Next") { vm.next() }
            PillButton("Repeat", ps.repeatAll, accent) { vm.toggleRepeat() }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            if (ps.nextTitle != null) "Up next  -  ${ps.nextTitle}" else "End of queue",
            style = tBody(17, HB.Muted),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun MiniPlayer(vm: AppViewModel, onOpen: () -> Unit) {
    val ps = vm.player
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
            .background(Color(0xFF1A1C22))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(ps.artwork, Modifier.size(48.dp), 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(ps.title, style = tBold(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ps.artist, style = tBody(13, HB.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { vm.prev() }) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White) }
        IconButton(onClick = { vm.togglePlay() }) {
            Icon(if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Color.White)
        }
        IconButton(onClick = { vm.next() }) { Icon(Icons.Filled.SkipNext, "Next", tint = Color.White) }
    }
}
