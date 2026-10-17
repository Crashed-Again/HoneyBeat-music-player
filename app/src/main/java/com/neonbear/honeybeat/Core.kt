package com.neonbear.honeybeat

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String,
    val cover: String?,
    val durationMs: Long,
    val remote: Boolean = false,
)

data class Playlist(val name: String, val tracks: List<Track>, val cover: String?)

object Net {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }
}

/** Fonts are downloaded by the GitHub workflow into assets/fonts. Falls back to the system font. */
object Fonts {
    var display: FontFamily = FontFamily.Default; private set
    var body: FontFamily = FontFamily.Default; private set
    var bold: FontFamily = FontFamily.Default; private set

    fun init(ctx: Context) {
        display = load(ctx, "fonts/oxanium.ttf", 700) ?: FontFamily.Default
        body = load(ctx, "fonts/outfit.ttf", 400) ?: FontFamily.Default
        bold = load(ctx, "fonts/outfit.ttf", 700) ?: FontFamily.Default
    }

    private fun load(ctx: Context, path: String, weight: Int): FontFamily? = runCatching {
        val tf = Typeface.Builder(ctx.assets, path)
            .setFontVariationSettings("'wght' $weight")
            .build()
        if (tf == null) null else FontFamily(tf)
    }.getOrNull()
}
