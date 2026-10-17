package com.neonbear.honeybeat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object YouTubeRepo {
    private class Cached(val url: String, val at: Long)
    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toTrack() }
    }

    private fun StreamInfoItem.toTrack(): Track {
        val thumb = thumbnails.lastOrNull()?.url
        return Track(
            id = url,
            title = name,
            artist = uploaderName ?: "",
            uri = url,
            cover = thumb?.let(::sharpen),
            durationMs = if (duration > 0) duration * 1000 else 0L,
            remote = true,
        )
    }

    /** YouTube Music covers come as tiny thumbnails; ask Google for a bigger one. */
    private fun sharpen(url: String): String =
        if (url.contains("googleusercontent.com") || url.contains("ggpht.com"))
            url.replace(Regex("=w\\d+-h\\d+.*$"), "=w720-h720-l90-rj")
        else url

    fun bestAudio(pageUrl: String, preferM4a: Boolean): AudioStream {
        val info = StreamInfo.getInfo(pageUrl)
        val streams = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl
        }
        val pool = if (preferM4a) streams.filter { it.format == MediaFormat.M4A }.ifEmpty { streams } else streams
        return pool.maxByOrNull { it.averageBitrate } ?: throw IOException("No audio stream found")
    }

    /** Direct stream URL for playback (cached for a few hours). */
    fun streamUrl(pageUrl: String): String {
        val hit = cache[pageUrl]
        val now = System.currentTimeMillis()
        if (hit != null && now - hit.at < 4 * 3600_000L) return hit.url
        val u = bestAudio(pageUrl, false).content
        cache[pageUrl] = Cached(u, now)
        return u
    }
}
