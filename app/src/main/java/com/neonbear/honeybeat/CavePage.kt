package com.neonbear.honeybeat

import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun isMetered(ctx: Context): Boolean =
    ctx.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true

@Composable
fun CavePage(artOn: Boolean, askMetered: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }

    fun go(url: String) {
        val u = url.trim()
        if (u.isEmpty() || CaveState.running) return
        if (askMetered && isMetered(ctx)) confirm = u else CaveService.start(ctx, u, artOn)
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PageTitle("Cave", "Paste a YouTube Music, Spotify or Apple Music playlist link. MP3s go to Music/Cave.")

        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(Cub.Card).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Cub.Hover).padding(12.dp)) {
                if (link.isEmpty()) Text("Playlist link", color = Cub.Muted, fontSize = 14.sp)
                BasicTextField(
                    value = link, onValueChange = { link = it }, singleLine = true,
                    textStyle = TextStyle(color = Cub.Text, fontSize = 14.sp),
                    cursorBrush = SolidColor(Cub.Accent), modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CubButton("Clone", primary = true) { go(link) }
                CubButton("Paste") {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    val t = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    if (!t.isNullOrBlank()) link = t.trim()
                }
            }
        }

        // progress
        val frac by animateFloatAsState(
            if (CaveState.total > 0) CaveState.done.toFloat() / CaveState.total else 0f, tween(250), label = "prog"
        )
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(Cub.Card).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(CaveState.status, color = Cub.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (CaveState.current.isNotEmpty()) {
                Text(CaveState.current, color = Cub.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (CaveState.running || CaveState.total > 0) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(Cub.Button)) {
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).background(Cub.Accent))
                }
                if (CaveState.total > 0) {
                    Text("${CaveState.done} / ${CaveState.total}", color = Cub.Muted, fontSize = 12.sp)
                }
            }
            if (CaveState.lastError.isNotEmpty()) {
                Text("Last error: ${CaveState.lastError}", color = Color(0xFFE5645F), fontSize = 12.sp)
            }
            if (CaveState.running) CubButton("Stop") { CaveJob.cancel() }
        }

        // recent playlists
        if (CaveState.recents.isNotEmpty()) {
            Text("Recent playlists", color = Cub.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CaveState.recents.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Cub.Card).padding(14.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = Cub.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${r.keys.size} downloaded", color = Cub.Muted, fontSize = 12.sp)
                        }
                        CubButton("Sync") { go(r.url) }
                        CubButton("Forget") {
                            val list = CaveState.recents.filter { it.url != r.url }
                            CaveState.recents = list
                            CaveStore.save(ctx.getSharedPreferences("honeybeat", Context.MODE_PRIVATE), list)
                        }
                    }
                }
            }
        }

        // tools + log
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CubButton(if (updating) "Updating..." else "Update yt-dlp") {
                if (!updating && !CaveState.running) {
                    updating = true
                    CaveState.ui { CaveState.status = "Updating yt-dlp..." }
                    scope.launch(Dispatchers.IO) {
                        val msg = CaveEngine.update(ctx)
                        CaveState.addLog(msg)
                        CaveState.ui { CaveState.status = msg }
                        updating = false
                    }
                }
            }
        }
        if (CaveState.log.isNotEmpty()) {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .background(Cub.Card).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("Log", color = Cub.Muted, fontSize = 12.sp)
                CaveState.log.takeLast(14).forEach {
                    Text(it, color = Cub.Text, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(
            "Only download music you have the right to copy.", color = Cub.Muted, fontSize = 11.sp,
            modifier = Modifier.padding(16.dp),
        )
    }

    confirm?.let { u ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Cub.Card,
            title = { Text("Use mobile data?", color = Cub.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("You're on a metered connection. Downloading a playlist can use a lot of data.", color = Cub.Muted, fontSize = 14.sp) },
            confirmButton = { CubButton("Download", primary = true) { confirm = null; CaveService.start(ctx, u, artOn) } },
            dismissButton = { CubButton("Cancel") { confirm = null } },
        )
    }
}
