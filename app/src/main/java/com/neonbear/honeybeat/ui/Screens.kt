package com.neonbear.honeybeat.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neonbear.honeybeat.AppViewModel
import com.neonbear.honeybeat.Playlist
import com.neonbear.honeybeat.Track
import kotlin.random.Random

private val ListBottom = PaddingValues(bottom = 200.dp)

@Composable
fun LibraryScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = vm.playlists.firstOrNull()?.tracks.orEmpty()
    LazyColumn(contentPadding = ListBottom) {
        item { ScreenHeader("Library", "Every folder in Music/HoneyBeat is a playlist.") }
        item {
            Spacer(Modifier.height(24.dp))
            SearchField(q, { q = it }, "Search songs, artists, albums", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(22.dp))
        }
        if (q.isBlank()) {
            items(vm.playlists) { p -> PlaylistCard(p) { onOpen(p.name) } }
            if (vm.playlists.isEmpty()) {
                item {
                    Text(
                        "No songs yet. Use Search to find a song and tap the download button.",
                        style = tBody(16, HB.Muted),
                        modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp)
                    )
                }
            }
        } else {
            val needle = q.trim().lowercase()
            val hits = all.filter { it.title.lowercase().contains(needle) || it.artist.lowercase().contains(needle) }
            itemsIndexed(hits) { i, t -> TrackRow(t, onClick = { vm.play(hits, i) }) }
        }
    }
}

@Composable
fun PlaylistScreen(vm: AppViewModel, playlist: Playlist, onBack: () -> Unit) {
    LazyColumn(contentPadding = ListBottom) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp).padding(top = 24.dp)) {
                Text(
                    "<  Library",
                    style = tBody(17, HB.Muted),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack).padding(vertical = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(playlist.cover, Modifier.size(96.dp), 22.dp)
                    Column(Modifier.padding(start = 18.dp)) {
                        Text(playlist.name, style = tDisplay(30), maxLines = 2)
                        Text("${playlist.tracks.size} songs", style = tBody(16, HB.Muted))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Play", false, HB.Pink) { vm.play(playlist.tracks, 0, shuffle = false) }
                    PillButton("Shuffle", false, HB.Pink) {
                        vm.play(playlist.tracks, Random.nextInt(playlist.tracks.size.coerceAtLeast(1)), shuffle = true)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        itemsIndexed(playlist.tracks) { i, t -> TrackRow(t, onClick = { vm.play(playlist.tracks, i) }) }
    }
}

@Composable
private fun DownloadButton(vm: AppViewModel, t: Track, onPick: () -> Unit) {
    val p = vm.downloads[t.id]
    val busy = p != null && p >= 0f && p <= 1f
    Box(
        Modifier.size(46.dp).clip(CircleShape).clickable(enabled = !busy && p != 2f) { onPick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            busy -> CircularProgressIndicator(progress = p ?: 0f, modifier = Modifier.size(26.dp), strokeWidth = 3.dp, color = HB.Pink)
            p == 2f -> Icon(Icons.Filled.Check, null, tint = Color(0xFF6FD08C))
            p != null && p < 0f -> Icon(Icons.Filled.Download, "Retry", tint = Color(0xFFFF6B6B))
            else -> Icon(Icons.Filled.Download, "Download", tint = Color.White)
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel) {
    var q by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<Track?>(null) }
    val focus: FocusManager = LocalFocusManager.current
    LazyColumn(contentPadding = ListBottom) {
        item { ScreenHeader("Search", "Find songs on YouTube Music and keep them offline.") }
        item {
            Spacer(Modifier.height(24.dp))
            SearchField(q, { q = it }, "Search YouTube Music", Modifier.padding(horizontal = 16.dp)) {
                focus.clearFocus()
                vm.search(q)
            }
            Spacer(Modifier.height(14.dp))
        }
        if (vm.searching) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HB.Pink)
                }
            }
        }
        vm.searchError?.let { msg ->
            item { Text(msg, style = tBody(16, Color(0xFFFF8A8A)), modifier = Modifier.padding(horizontal = 26.dp, vertical = 8.dp)) }
        }
        itemsIndexed(vm.results) { i, t ->
            TrackRow(t, onClick = { vm.play(vm.results, i) }) { DownloadButton(vm, t) { pick = t } }
        }
    }
    pick?.let { track ->
        var name by remember { mutableStateOf(vm.lastPlaylist) }
        val existing = vm.playlists.map { it.name }.filter { it != "All music" }
        AlertDialog(
            onDismissRequest = { pick = null },
            containerColor = Color(0xFF1B1D24),
            title = { Text("Save to playlist", style = tBold(20)) },
            text = {
                Column {
                    Text(track.title, style = tBody(15, HB.Muted), maxLines = 1)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Playlist folder") })
                    if (existing.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(existing) { n -> AssistChip(onClick = { name = n }, label = { Text(n) }) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.download(track, name.ifBlank { "Downloads" })
                    pick = null
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun OptionCard(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(HB.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Text(title, style = tBold(19))
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = tBody(15, HB.Muted))
    }
}

@Composable
fun OptionsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    LazyColumn(contentPadding = ListBottom) {
        item { ScreenHeader("Options", "Tune HoneyBeat to your taste.") }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            OptionCard("Rescan library", "${vm.playlists.firstOrNull()?.tracks?.size ?: 0} songs found") { vm.refresh() }
        }
        item {
            OptionCard("Music & notification access", "Open app settings to allow audio and notifications") {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                )
            }
        }
        item { OptionCard("Downloads", "Saved to Music/HoneyBeat/<playlist> as the highest-quality m4a available.") }
        item { OptionCard("About", "HoneyBeat 1.0.0 - YouTube Music client.\nPowered by NewPipe Extractor (GPL-3.0).") }
        item {
            Text(
                "NeonBear",
                style = tDisplay(18, HB.Muted),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp)
            )
        }
    }
}
