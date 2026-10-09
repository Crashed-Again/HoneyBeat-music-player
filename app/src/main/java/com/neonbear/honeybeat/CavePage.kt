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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.withContext

private fun isMetered(ctx: Context): Boolean =
    ctx.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true

@Composable
private fun InputBox(value: String, hint: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Cub.Hover).padding(12.dp)) {
        if (value.isEmpty()) Text(hint, color = Cub.Muted, fontSize = 14.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Cub.Text, fontSize = 14.sp, fontFamily = Body),
            cursorBrush = SolidColor(Cub.Accent), modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun FetchPage(askMetered: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }

    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchMsg by remember { mutableStateOf("") }
    val queued = remember { mutableStateListOf<String>() }

    fun guarded(action: () -> Unit) {
        if (askMetered && isMetered(ctx)) confirm = action else action()
    }
    fun clone(url: String) {
        val u = url.trim()
        if (u.isEmpty() || CaveState.running) return
        guarded { CaveService.start(ctx, u) }
    }
    fun search() {
        val query = q.trim()
        if (query.isEmpty() || searching) return
        searching = true
        searchMsg = "Searching..."
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { CaveEngine.search(ctx, query) }
                hits = r
                searchMsg = if (r.isEmpty()) "No results." else ""
            } catch (e: Exception) {
                searchMsg = (e.message ?: "Search failed").lines().lastOrNull { it.isNotBlank() } ?: "Search failed"
            }
            searching = false
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PageTitle("Fetch", "Clone a playlist link or search for a song. Files go to Music/Cave.")

        // playlist link
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(Cub.Card).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Playlist link", color = Cub.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            InputBox(link, "YouTube Music, Spotify or Apple Music link") { link = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CubButton("Clone", primary = true) { clone(link) }
                CubButton("Paste") {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    val t = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    if (!t.isNullOrBlank()) link = t.trim()
                }
            }
        }

        // song search
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(Cub.Card).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Search music", color = Cub.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            InputBox(q, "Song or artist") { q = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CubButton(if (searching) "Searching..." else "Search", primary = true) { search() }
            }
            if (searchMsg.isNotEmpty()) Text(searchMsg, color = Cub.Muted, fontSize = 12.sp)
            hits.forEach { h ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NetCover("https://i.ytimg.com/vi/${h.id}/mqdefault.jpg", Modifier.size(52.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.title, color = Cub.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(h.by, if (h.seconds > 0) fmt(h.seconds * 1000L) else "").filter { it.isNotEmpty() }.joinToString("  -  "),
                            color = Cub.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val isQueued = h.id in queued
                    CubButton(if (isQueued) "Queued" else "Get", primary = !isQueued) {
                        if (!isQueued) guarded {
                            queued += h.id
                            CaveService.queueTrack(ctx, h.id, h.title)
                        }
                    }
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = Cub.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${r.keys.size} downloaded", color = Cub.Muted, fontSize = 12.sp)
                        }
                        CubButton("Sync") { clone(r.url) }
                        CubButton("Forget") {
                            val list = CaveState.recents.filter { it.url != r.url }
                            CaveState.recents = list
                            CaveStore.save(ctx.getSharedPreferences("honeybeat", Context.MODE_PRIVATE), list)
                        }
                    }
                }
            }
        }

        if (CaveState.log.isNotEmpty()) {
            Column(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
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

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Cub.Card,
            title = { Text("Use mobile data?", color = Cub.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("You're on a metered connection. Downloads can use a lot of data.", color = Cub.Muted, fontSize = 14.sp) },
            confirmButton = { CubButton("Download", primary = true) { confirm = null; action() } },
            dismissButton = { CubButton("Cancel") { confirm = null } },
        )
    }
}
