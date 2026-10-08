package com.neonbear.cubplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { CubTheme { CubApp() } }
    }
}

enum class Page(val label: String) { Songs("Songs"), Playing("Playing"), Options("Options") }

/** Thin wrapper around a MediaController that talks to PlaybackService. */
class Remote(private val ctx: Context) {
    var player by mutableStateOf<Player?>(null)
    var item by mutableStateOf<MediaItem?>(null)
    var playing by mutableStateOf(false)
    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                player = c
                item = c.currentMediaItem
                playing = c.isPlaying
                c.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { item = mediaItem }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                })
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        player = null
    }

    fun play(list: List<Song>, index: Int) {
        val p = player ?: return
        p.setMediaItems(list.map { it.toItem() }, index, 0L)
        p.prepare()
        p.play()
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }
}

@Composable
fun CubApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("cub", Context.MODE_PRIVATE) }

    var page by remember { mutableStateOf(Page.Songs) }
    var shuffle by remember { mutableStateOf(prefs.getBoolean("shuffle", false)) }
    var repeatAll by remember { mutableStateOf(prefs.getBoolean("repeat", true)) }
    var onlyCave by remember { mutableStateOf(prefs.getBoolean("onlyCave", false)) }
    var showArt by remember { mutableStateOf(prefs.getBoolean("art", true)) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    fun granted() = ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED
    var hasAccess by remember { mutableStateOf(granted()) }

    fun rescan() {
        scope.launch {
            if (granted()) songs = withContext(Dispatchers.IO) { loadSongs(ctx) }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAccess = granted()
        rescan()
    }
    LaunchedEffect(Unit) { if (hasAccess) rescan() }

    val remote = remember { Remote(ctx) }
    DisposableEffect(Unit) {
        remote.connect()
        onDispose { remote.release() }
    }

    val player = remote.player
    LaunchedEffect(player, shuffle, repeatAll) {
        player?.let {
            it.shuffleModeEnabled = shuffle
            it.repeatMode = if (repeatAll) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        }
    }

    fun setPref(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    val shown = remember(songs, query, onlyCave) {
        songs.filter {
            (!onlyCave || it.path.contains("/Music/Cave/", ignoreCase = true)) &&
                (query.isBlank() ||
                    it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true))
        }
    }

    Column(Modifier.fillMaxSize().background(Cub.Panel).statusBarsPadding()) {
        Header()
        Box(Modifier.weight(1f)) {
            when (page) {
                Page.Songs -> SongsPage(
                    shown = shown, hasAccess = hasAccess, onlyCave = onlyCave,
                    query = query, onQuery = { query = it },
                    currentId = remote.item?.mediaId,
                    onAllow = {
                        val wanted = if (Build.VERSION.SDK_INT >= 33)
                            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        launcher.launch(wanted)
                    },
                    onPlay = { i -> remote.play(shown, i); page = Page.Playing },
                )
                Page.Playing -> PlayingPage(
                    remote = remote, showArt = showArt, shuffle = shuffle, repeatAll = repeatAll,
                    onShuffle = { shuffle = !shuffle; setPref("shuffle", shuffle) },
                    onRepeat = { repeatAll = !repeatAll; setPref("repeat", repeatAll) },
                )
                Page.Options -> OptionsPage(
                    shuffle = shuffle, repeatAll = repeatAll, onlyCave = onlyCave, showArt = showArt,
                    onShuffle = { shuffle = it; setPref("shuffle", it) },
                    onRepeat = { repeatAll = it; setPref("repeat", it) },
                    onOnlyCave = { onlyCave = it; setPref("onlyCave", it) },
                    onArt = { showArt = it; setPref("art", it) },
                    onRescan = { rescan() },
                    count = songs.size,
                )
            }
        }
        if (remote.item != null && page != Page.Playing) MiniPlayer(remote) { page = Page.Playing }
        NavBar(page) { page = it }
    }
}

@Composable
fun Header() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BearLogo()
        Column {
            Text("Cub Player", color = Cub.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("by NeonBear   v1.0.0", color = Cub.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun NavBar(current: Page, onPick: (Page) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Cub.Black).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Page.values().forEach { p ->
            val sel = p == current
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                    .background(if (sel) Color(0xFF222222) else Color.Transparent)
                    .clickable { onPick(p) }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (sel) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(18.dp).background(Cub.Accent))
                Text(p.label, color = if (sel) Cub.Text else Cub.Muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun SongsPage(
    shown: List<Song>, hasAccess: Boolean, onlyCave: Boolean, query: String, onQuery: (String) -> Unit,
    currentId: String?, onAllow: () -> Unit, onPlay: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageTitle("Songs", if (onlyCave) "Only what Cave downloaded to Music/Cave." else "Everything on this phone.")
        if (!hasAccess) {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .background(Cub.Card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Allow access to your music", color = Cub.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Cub Player needs permission to read audio files so it can list and play them.", color = Cub.Muted, fontSize = 12.sp)
                CubButton("Allow access", primary = true, onClick = onAllow)
            }
            return@Column
        }
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(Cub.Card).padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (query.isEmpty()) Text("Search songs, artists, albums", color = Cub.Muted, fontSize = 14.sp)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = TextStyle(color = Cub.Text, fontSize = 14.sp),
                cursorBrush = SolidColor(Cub.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (shown.isEmpty()) {
            Text(
                if (query.isNotEmpty()) "No songs match that search."
                else "No songs found. Download some with Cave, or turn off the Music/Cave filter in Options.",
                color = Cub.Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                SongRow(s, s.id.toString() == currentId) { onPlay(i) }
            }
        }
    }
}

@Composable
fun SongRow(s: Song, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(6.dp))
            .background(Cub.Card).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (current) Cub.Accent else Color.Transparent))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                s.title, color = if (current) Cub.Accent else Cub.Text, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (s.album.isNotEmpty()) "${s.artist} - ${s.album}" else s.artist,
                color = Cub.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(fmt(s.durationMs), color = Cub.Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 14.dp))
    }
}

@Composable
fun MiniPlayer(remote: Remote, onOpen: () -> Unit) {
    val md = remote.item?.mediaMetadata
    Column(Modifier.fillMaxWidth().background(Cub.Black)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Cub.Accent))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(md?.title?.toString() ?: "", color = Cub.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(md?.artist?.toString() ?: "", color = Cub.Muted, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(40.dp).background(Cub.Accent).clickable { remote.toggle() },
                contentAlignment = Alignment.Center,
            ) { Glyph(if (remote.playing) G.Pause else G.Play, Color.White, 22.dp) }
        }
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, if (on) Cub.Accent else Cub.Button, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, color = if (on) Cub.Accent else Cub.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun PlayingPage(
    remote: Remote, showArt: Boolean, shuffle: Boolean, repeatAll: Boolean,
    onShuffle: () -> Unit, onRepeat: () -> Unit,
) {
    val ctx = LocalContext.current
    val item = remote.item
    val player = remote.player
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        PageTitle("Playing", if (item == null) "Nothing yet." else "Now playing from your library.")
        if (item == null) {
            Text(
                "Pick a song on the Songs page to start.", color = Cub.Muted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            return@Column
        }

        val art by produceState<Bitmap?>(null, item.mediaId, showArt) {
            value = if (showArt && Build.VERSION.SDK_INT >= 29) {
                withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.loadThumbnail(songUri(item.mediaId.toLong()), android.util.Size(600, 600), null)
                    } catch (_: Exception) {
                        null
                    }
                }
            } else null
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.padding(horizontal = 16.dp).widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp)).background(Cub.Black),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = art
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    BearLogo(120.dp)
                }
            }
        }

        val md = item.mediaMetadata
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(md.title?.toString() ?: "", color = Cub.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(md.artist?.toString() ?: "", color = Cub.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }

        var pos by remember { mutableLongStateOf(0L) }
        var drag by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(player) {
            while (true) {
                player?.let { pos = it.currentPosition }
                delay(400)
            }
        }
        val dur = (player?.duration ?: 0L).coerceAtLeast(0L)
        val frac = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        Column(Modifier.padding(horizontal = 8.dp)) {
            Slider(
                value = frac,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { player?.seekTo((it * dur).toLong()) }
                    drag = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Cub.Accent, activeTrackColor = Cub.Accent, inactiveTrackColor = Cub.Button,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt((frac * dur).toLong()), color = Cub.Muted, fontSize = 12.sp)
                Text(fmt(dur), color = Cub.Muted, fontSize = 12.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Chip("Shuffle", shuffle, onShuffle)
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Cub.Card)
                .clickable { player?.seekToPrevious() }, contentAlignment = Alignment.Center) {
                Glyph(G.Prev, Cub.Text, 26.dp)
            }
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)).background(Cub.Accent)
                .clickable { remote.toggle() }, contentAlignment = Alignment.Center) {
                Glyph(if (remote.playing) G.Pause else G.Play, Color.White, 32.dp)
            }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Cub.Card)
                .clickable { player?.seekToNext() }, contentAlignment = Alignment.Center) {
                Glyph(G.Next, Cub.Text, 26.dp)
            }
            Chip("Repeat", repeatAll, onRepeat)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun OptionsPage(
    shuffle: Boolean, repeatAll: Boolean, onlyCave: Boolean, showArt: Boolean,
    onShuffle: (Boolean) -> Unit, onRepeat: (Boolean) -> Unit, onOnlyCave: (Boolean) -> Unit,
    onArt: (Boolean) -> Unit, onRescan: () -> Unit, count: Int,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageTitle("Options", "How playback and the library behave. Changes apply right away.")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleCard("Shuffle", "Play the queue in random order.", shuffle, onShuffle)
            ToggleCard("Repeat all", "Start over when the last song ends.", repeatAll, onRepeat)
            ToggleCard("Only Music/Cave", "Hide everything except songs Cave downloaded.", onlyCave, onOnlyCave)
            ToggleCard("Show cover art", "Uses the picture embedded in the file when there is one.", showArt, onArt)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Cub.Card)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Library", color = Cub.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("$count songs found.", color = Cub.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
                CubButton("Rescan", onClick = onRescan)
            }
        }
        Column(Modifier.padding(start = 20.dp, top = 40.dp, bottom = 24.dp)) {
            DotText("NEONBEAR")
            Text("Copyright (c) 2026 - NeonBear", color = Cub.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
