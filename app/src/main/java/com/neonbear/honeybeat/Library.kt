package com.neonbear.honeybeat

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String,
    /** First folder under Music/Cave, or "" for songs directly in Music/Cave. */
    val folder: String,
) {
    val uri: Uri get() = songUri(id)
}

/** A Cave playlist: one folder inside Music/Cave. The key "" is the built-in All music list. */
data class Folder(val key: String, val name: String, val songs: List<Song>)

fun songUri(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

fun Song.toItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .build()
    )
    .build()

private const val CAVE = "/Music/Cave/"

@Suppress("DEPRECATION")
fun loadSongs(ctx: Context): List<Song> {
    val out = ArrayList<Song>()
    val a = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
    )
    val sel = "${MediaStore.Audio.Media.DATA} LIKE '%$CAVE%' AND ${MediaStore.Audio.Media.DURATION} > 0"
    ctx.contentResolver.query(a, proj, sel, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (c.moveToNext()) {
            val path = c.getString(iData) ?: ""
            val at = path.indexOf(CAVE, ignoreCase = true)
            val rel = if (at >= 0) path.substring(at + CAVE.length) else ""
            val folder = if (rel.contains('/')) rel.substringBefore('/') else ""
            out += Song(
                id = c.getLong(iId),
                title = c.getString(iTitle) ?: "Untitled",
                artist = c.getString(iArtist)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                album = c.getString(iAlbum)?.takeIf { it != "<unknown>" } ?: "",
                durationMs = c.getLong(iDur),
                path = path,
                folder = folder,
            )
        }
    }
    return out
}

/** All music first, then one playlist per folder in Music/Cave. */
fun buildFolders(songs: List<Song>): List<Folder> {
    val byFolder = songs.filter { it.folder.isNotEmpty() }.groupBy { it.folder }
    val sorted = byFolder.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
    return listOf(Folder("", "All music", songs)) + sorted.map { Folder(it, it, byFolder.getValue(it)) }
}

/** Cached cover art thumbnails (embedded pictures, Android 10+). */
object Covers {
    private val cache = LruCache<String, Bitmap>(150)
    private val missing = HashSet<String>()

    fun peek(id: Long, px: Int): Bitmap? = cache.get("$id:$px")

    suspend fun load(ctx: Context, id: Long, px: Int): Bitmap? {
        if (id < 0 || Build.VERSION.SDK_INT < 29) return null
        val key = "$id:$px"
        cache.get(key)?.let { return it }
        if (key in missing) return null
        val b = withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.loadThumbnail(songUri(id), android.util.Size(px, px), null)
            } catch (_: Exception) {
                null
            }
        }
        if (b != null) cache.put(key, b) else missing.add(key)
        return b
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
