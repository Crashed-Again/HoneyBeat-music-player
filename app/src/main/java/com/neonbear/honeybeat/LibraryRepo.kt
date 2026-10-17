package com.neonbear.honeybeat

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import java.io.File
import java.io.IOException

object CoverStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "covers").apply { mkdirs() }
    private fun file(ctx: Context, id: Long) = File(dir(ctx), "$id.jpg")

    fun existing(ctx: Context, id: Long): String? =
        file(ctx, id).takeIf { it.exists() && it.length() > 0 }?.absolutePath

    fun saveFromUrl(ctx: Context, id: Long, url: String) {
        runCatching {
            Net.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val bytes = r.body?.bytes() ?: return
                if (r.isSuccessful && bytes.isNotEmpty()) file(ctx, id).writeBytes(bytes)
            }
        }
    }

    fun extractEmbedded(ctx: Context, id: Long, uri: Uri): String? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(ctx, uri)
            val pic = mmr.embeddedPicture
            if (pic != null) {
                val f = file(ctx, id)
                f.writeBytes(pic)
                f.absolutePath
            } else null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }
}

object LibraryRepo {
    const val ROOT = "HoneyBeat"
    private const val ALL = "All music"

    fun scan(ctx: Context): List<Playlist> {
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )
        val prefix = "Music/$ROOT/"
        val grouped = linkedMapOf<String, MutableList<Track>>()
        val everything = mutableListOf<Track>()

        ctx.contentResolver.query(
            base, proj, "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("$prefix%"), null
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iPath = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val rel = c.getString(iPath).orEmpty()
                val folder = rel.removePrefix(prefix).trimEnd('/').substringBefore('/').ifBlank { "Loose songs" }
                val uri = ContentUris.withAppendedId(base, id)
                val cover = CoverStore.existing(ctx, id) ?: CoverStore.extractEmbedded(ctx, id, uri)
                val artist = c.getString(iArtist).orEmpty().let { if (it == "<unknown>") "" else it }
                val track = Track(
                    id = uri.toString(),
                    title = c.getString(iTitle).orEmpty().ifBlank { "Unknown" },
                    artist = artist,
                    uri = uri.toString(),
                    cover = cover,
                    durationMs = c.getLong(iDur),
                )
                grouped.getOrPut(folder) { mutableListOf() }.add(track)
                everything.add(track)
            }
        }

        val sortedAll = everything.sortedBy { it.title.lowercase() }
        val list = mutableListOf<Playlist>()
        if (sortedAll.isNotEmpty()) {
            list.add(Playlist(ALL, sortedAll, sortedAll.firstOrNull { it.cover != null }?.cover))
        }
        grouped.entries.sortedBy { it.key.lowercase() }.forEach { (name, tracks) ->
            val sorted = tracks.sortedBy { it.title.lowercase() }
            list.add(Playlist(name, sorted, sorted.firstOrNull { it.cover != null }?.cover))
        }
        return list
    }
}

object Downloads {
    private const val CHUNK = 4L * 1024 * 1024

    private fun clean(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Untitled" }

    /** Downloads the best m4a stream into Music/HoneyBeat/<playlist>. Blocking - call from IO. */
    fun fetch(ctx: Context, track: Track, playlist: String, onProgress: (Float) -> Unit) {
        val stream = YouTubeRepo.bestAudio(track.uri, preferM4a = true)
        val isM4a = stream.format == MediaFormat.M4A
        val ext = if (isM4a) "m4a" else (stream.format?.suffix ?: "webm")
        val mime = if (isM4a) "audio/mp4" else "audio/webm"
        val name = clean(if (track.artist.isBlank()) track.title else "${track.artist} - ${track.title}")

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.$ext")
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/${LibraryRepo.ROOT}/${clean(playlist)}")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create file")
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                var start = 0L
                var total = -1L
                while (total < 0 || start < total) {
                    val req = Request.Builder()
                        .url(stream.content)
                        .header("User-Agent", Net.UA)
                        .header("Range", "bytes=$start-${start + CHUNK - 1}")
                        .build()
                    Net.client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                        val body = resp.body ?: throw IOException("Empty response")
                        if (total < 0 && resp.code == 206) {
                            total = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                        }
                        val copied = body.byteStream().copyTo(out)
                        start += copied
                        if (resp.code != 206 || total < 0) total = start
                    }
                    if (total > 0) onProgress(start.toFloat() / total)
                }
            }
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            track.cover?.let { CoverStore.saveFromUrl(ctx, ContentUris.parseId(uri), it) }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
