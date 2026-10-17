package com.neonbear.honeybeat

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerUi(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artwork: String? = null,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatAll: Boolean = false,
    val nextTitle: String? = null,
    val key: String = "",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var results by mutableStateOf<List<Track>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var player by mutableStateOf(PlayerUi()); private set
    var lastPlaylist by mutableStateOf("Downloads")

    /** track id -> 0..1 progress, 2 = done, -1 = failed */
    val downloads = mutableStateMapOf<String, Float>()

    private var controller: MediaController? = null

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            runCatching {
                val c = future.get()
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onEvents(p: Player, events: Player.Events) = sync()
                })
                sync()
            }
        }, ContextCompat.getMainExecutor(ctx))
        refresh()
    }

    private fun sync() {
        val c = controller ?: return
        val md = c.mediaMetadata
        val next = if (c.hasNextMediaItem())
            c.getMediaItemAt(c.nextMediaItemIndex).mediaMetadata.title?.toString() else null
        player = PlayerUi(
            hasMedia = c.mediaItemCount > 0,
            isPlaying = c.isPlaying,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            artwork = md.artworkUri?.toString(),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeatAll = c.repeatMode == Player.REPEAT_MODE_ALL,
            nextTitle = next,
            key = c.currentMediaItem?.mediaId.orEmpty(),
        )
    }

    fun refresh() {
        viewModelScope.launch {
            playlists = withContext(Dispatchers.IO) { runCatching { LibraryRepo.scan(ctx) }.getOrDefault(emptyList()) }
        }
    }

    private fun Track.toItem(): MediaItem {
        val art = cover?.let { if (it.startsWith("/")) Uri.fromFile(File(it)) else Uri.parse(it) }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(art).build()
            )
            .build()
    }

    fun play(tracks: List<Track>, index: Int, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        if (shuffle != null) c.shuffleModeEnabled = shuffle
        c.setMediaItems(tracks.map { it.toItem() }, index.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() {
        controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    }
    fun seekFraction(f: Float) {
        val c = controller ?: return
        val d = c.duration
        if (d != C.TIME_UNSET && d > 0) c.seekTo((d * f).toLong())
    }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
        }
    }
    fun position(): Long = controller?.currentPosition ?: 0L

    fun search(q: String) {
        if (q.isBlank()) return
        viewModelScope.launch {
            searching = true
            searchError = null
            try {
                results = YouTubeRepo.search(q.trim())
                if (results.isEmpty()) searchError = "No songs found."
            } catch (e: Exception) {
                searchError = e.message ?: "Search failed"
            } finally {
                searching = false
            }
        }
    }

    fun download(track: Track, playlist: String) {
        val cur = downloads[track.id]
        if (cur != null && cur in 0f..1f) return
        lastPlaylist = playlist
        downloads[track.id] = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Downloads.fetch(ctx, track, playlist) { p -> downloads[track.id] = p.coerceIn(0f, 0.99f) }
                downloads[track.id] = 2f
                withContext(Dispatchers.Main) { refresh() }
            } catch (e: Exception) {
                downloads[track.id] = -1f
            }
        }
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
