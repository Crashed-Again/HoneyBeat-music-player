package com.neonbear.honeybeat

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

enum class Page(val label: String) { Library("Library"), Playing("Playing"), Cave("Cave"), Options("Options") }

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
        if (list.isEmpty()) return
        p.setMediaItems(list.map { it.toItem() }, index, 0L)
        p.prepare()
        p.play()
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun prev() { player?.seekToPrevious() }
    fun next() { player?.seekToNext() }
}

@Composable
fun CubApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("honeybeat", Context.MODE_PRIVATE) }

    var page by remember { mutableStateOf(Page.Library) }
    var shuffle by remember { mutableStateOf(prefs.getBoolean("shuffle", false)) }
    var repeatAll by remember { mutableStateOf(prefs.getBoolean("repeat", true)) }
    var showArt by remember { mutableStateOf(prefs.getBoolean("art", true)) }
    var caveArt by remember { mutableStateOf(prefs.getBoolean("cave_art", true)) }
    var caveAsk by remember { mutableStateOf(prefs.getBoolean("cave_ask", true)) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var openKey by remember { mutableStateOf<String?>(null) }

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
    fun askPermissions() {
        val wanted = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        launcher.launch(wanted)
    }
    LaunchedEffect(Unit) {
        CaveState.recents = CaveStore.load(prefs)
        if (hasAccess) rescan() else askPermissions()
    }
    LaunchedEffect(CaveState.finished) { if (CaveState.finished > 0) rescan() }

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

    BackHandler(enabled = page == Page.Library && openKey != null) { openKey = null; query = "" }
    fun setPref(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    val folders = remember(songs) { buildFolders(songs) }

    CompositionLocalProvider(LocalShowArt provides showArt) {
        Column(Modifier.fillMaxSize().background(Cub.Panel).statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 24 }) togetherWith fadeOut(tween(120))
                    },
                    label = "page",
                ) { p ->
                    when (p) {
                        Page.Library -> LibraryPage(
                            folders = folders, hasAccess = hasAccess, onAllow = { askPermissions() },
                            openKey = openKey,
                            onOpen = { openKey = it; query = "" },
                            query = query, onQuery = { query = it },
                            currentId = remote.item?.mediaId, playing = remote.playing,
                            onPlay = { list, i -> remote.play(list, i) },
                        )
                        Page.Playing -> PlayingPage(
                            remote = remote, shuffle = shuffle, repeatAll = repeatAll,
                            onShuffle = { shuffle = !shuffle; setPref("shuffle", shuffle) },
                            onRepeat = { repeatAll = !repeatAll; setPref("repeat", repeatAll) },
                        )
                        Page.Cave -> CavePage(artOn = caveArt, askMetered = caveAsk)
                        Page.Options -> OptionsPage(
                            shuffle = shuffle, repeatAll = repeatAll, showArt = showArt, caveArt = caveArt, caveAsk = caveAsk,
                            onShuffle = { shuffle = it; setPref("shuffle", it) },
                            onRepeat = { repeatAll = it; setPref("repeat", it) },
                            onArt = { showArt = it; setPref("art", it) },
                            onCaveArt = { caveArt = it; setPref("cave_art", it) },
                            onCaveAsk = { caveAsk = it; setPref("cave_ask", it) },
                            onRescan = { rescan() },
                            songCount = songs.size, listCount = folders.size - 1,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = remote.item != null && page != Page.Playing,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
            ) { MiniPlayer(remote) { page = Page.Playing } }
            NavBar(page) { page = it }
        }
    }
}

@Composable
fun NavBar(current: Page, onPick: (Page) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Cub.Black).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Page.values().forEach { p ->
            val sel = p == current
            val bg by animateColorAsState(if (sel) Color(0xFF222222) else Color.Transparent, tween(150), label = "navBg")
            val fg by animateColorAsState(if (sel) Cub.Text else Cub.Muted, tween(150), label = "navFg")
            val bar by animateDpAsState(if (sel) 18.dp else 0.dp, tween(180), label = "navBar")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(bg)
                    .clickable { onPick(p) }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(bar).background(Cub.Accent))
                Text(p.label, color = fg, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SearchBox(query: String, onQuery: (String) -> Unit) {
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
}

@Composable
fun LibraryPage(
    folders: List<Folder>, hasAccess: Boolean, onAllow: () -> Unit,
    openKey: String?, onOpen: (String?) -> Unit,
    query: String, onQuery: (String) -> Unit,
    currentId: String?, playing: Boolean, onPlay: (List<Song>, Int) -> Unit,
) {
    if (!hasAccess) {
        Column(Modifier.fillMaxSize()) {
            PageTitle("Library", "Your Cave music.")
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .background(Cub.Card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Allow access to your music", color = Cub.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("HoneyBeat needs permission to read audio files so it can list and play them.", color = Cub.Muted, fontSize = 12.sp)
                CubButton("Allow access", primary = true, onClick = onAllow)
            }
        }
        return
    }
    AnimatedContent(
        targetState = openKey,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 5 } + fadeOut(tween(150)))
            } else {
                (slideInHorizontally(tween(220)) { -it / 5 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(150)))
            }
        },
        label = "lib",
    ) { key ->
        if (key == null) {
            Column(Modifier.fillMaxSize()) {
                PageTitle("Library", "Every folder in Music/Cave is a playlist.")
                if (folders.first().songs.isEmpty()) {
                    Text(
                        "No songs in Music/Cave yet. Clone a playlist in the Cave tab.",
                        color = Cub.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(folders, key = { it.key }) { f ->
                        FolderCard(f, Modifier.animateItem()) { onOpen(f.key) }
                    }
                }
            }
        } else {
            val folder = folders.find { it.key == key } ?: folders.first()
            val shown = remember(folder, query) {
                folder.songs.filter {
                    query.isBlank() || it.title.contains(query, true) ||
                        it.artist.contains(query, true) || it.album.contains(query, true)
                }
            }
            Column(Modifier.fillMaxSize()) {
                Text(
                    "< Library", color = Cub.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onOpen(null) }.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
                )
                PageTitle(folder.name, if (folder.songs.size == 1) "1 song" else "${folder.songs.size} songs")
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    CubButton("Play", primary = true) { onPlay(shown, 0) }
                }
                Spacer(Modifier.height(8.dp))
                SearchBox(query, onQuery)
                if (shown.isEmpty()) {
                    Text(
                        if (query.isNotEmpty()) "No songs match that search." else "This playlist is empty.",
                        color = Cub.Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                        SongRow(s, s.id.toString() == currentId, playing, Modifier.animateItem()) { onPlay(shown, i) }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderCard(f: Folder, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.bounceClick(0.98f, onClick).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Cub.Card).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(f.songs.firstOrNull()?.id ?: -1L, 160, Modifier.size(56.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, color = Cub.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (f.songs.size == 1) "1 song" else "${f.songs.size} songs", color = Cub.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp))
        }
        Text(">", color = Cub.Muted, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
    }
}

@Composable
fun SongRow(s: Song, current: Boolean, playing: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val titleColor by animateColorAsState(if (current) Cub.Accent else Cub.Text, tween(150), label = "title")
    val bar by animateColorAsState(if (current) Cub.Accent else Color.Transparent, tween(150), label = "bar")
    Row(
        modifier.bounceClick(0.98f, onClick).fillMaxWidth().height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp)).background(Cub.Card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(bar))
        Cover(s.id, 120, Modifier.padding(start = 10.dp).size(46.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                s.title, color = titleColor, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (s.album.isNotEmpty()) "${s.artist} - ${s.album}" else s.artist,
                color = Cub.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (current) {
            EqBars(playing)
            Spacer(Modifier.width(10.dp))
        }
        Text(fmt(s.durationMs), color = Cub.Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
    }
}

@Composable
fun MiniPlayer(remote: Remote, onOpen: () -> Unit) {
    val md = remote.item?.mediaMetadata
    val id = remote.item?.mediaId?.toLongOrNull() ?: -1L
    Column(Modifier.fillMaxWidth().background(Cub.Black)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Cub.Accent))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Cover(id, 120, Modifier.size(42.dp))
            Column(Modifier.weight(1f)) {
                Text(md?.title?.toString() ?: "", color = Cub.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(md?.artist?.toString() ?: "", color = Cub.Muted, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            TBtn(G.Prev, 38.dp, 20.dp, Cub.Card, Cub.Text) { remote.prev() }
            PlayPauseBtn(remote.playing, 42.dp, 22.dp) { remote.toggle() }
            TBtn(G.Next, 38.dp, 20.dp, Cub.Card, Cub.Text) { remote.next() }
        }
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val c by animateColorAsState(if (on) Cub.Accent else Cub.Muted, tween(150), label = "chip")
    Box(
        Modifier.bounceClick(onClick = onClick)
            .border(1.dp, if (on) Cub.Accent else Cub.Button, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, color = c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun PlayingPage(
    remote: Remote, shuffle: Boolean, repeatAll: Boolean,
    onShuffle: () -> Unit, onRepeat: () -> Unit,
) {
    val item = remote.item
    val player = remote.player
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minH = maxHeight
        Column(
            Modifier.fillMaxWidth().heightIn(min = minH).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (item == null) {
                Text("Nothing playing", color = Cub.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Pick a song in the Library.", color = Cub.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                return@Column
            }

            val scale by animateFloatAsState(if (remote.playing) 1f else 0.9f, tween(300), label = "coverScale")
            Cover(
                item.mediaId.toLongOrNull() ?: -1L, 700,
                Modifier.padding(horizontal = 24.dp).widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )

            val md = item.mediaMetadata
            Crossfade(
                targetState = (md.title?.toString() ?: "") to (md.artist?.toString() ?: ""),
                animationSpec = tween(220), label = "titles",
            ) { (title, artist) ->
                Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = Cub.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = Cub.Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 3.dp))
                }
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
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
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
                Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Chip("Shuffle", shuffle, onShuffle)
                TBtn(G.Prev, 48.dp, 26.dp, Cub.Card, Cub.Text) { remote.prev() }
                PlayPauseBtn(remote.playing, 64.dp, 32.dp) { remote.toggle() }
                TBtn(G.Next, 48.dp, 26.dp, Cub.Card, Cub.Text) { remote.next() }
                Chip("Repeat", repeatAll, onRepeat)
            }
        }
    }
}

@Composable
fun OptionsPage(
    shuffle: Boolean, repeatAll: Boolean, showArt: Boolean, caveArt: Boolean, caveAsk: Boolean,
    onShuffle: (Boolean) -> Unit, onRepeat: (Boolean) -> Unit, onArt: (Boolean) -> Unit,
    onCaveArt: (Boolean) -> Unit, onCaveAsk: (Boolean) -> Unit,
    onRescan: () -> Unit, songCount: Int, listCount: Int,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageTitle("Options", "How HoneyBeat and Cave behave. Changes apply right away.")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleCard("Shuffle", "Play the queue in random order.", shuffle, onShuffle)
            ToggleCard("Repeat all", "Start over when the last song ends.", repeatAll, onRepeat)
            ToggleCard("Show cover art", "Covers in the lists and on the Playing page.", showArt, onArt)
            ToggleCard("Cover art in downloads", "Cave embeds the cover in each MP3. If it fails for a song, Cave retries without it.", caveArt, onCaveArt)
            ToggleCard("Ask on mobile data", "Cave asks before downloading on a metered connection.", caveAsk, onCaveAsk)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Cub.Card)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cave library", color = Cub.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("$songCount songs in $listCount playlists.", color = Cub.Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp))
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
