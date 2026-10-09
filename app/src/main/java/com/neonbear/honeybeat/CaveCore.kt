package com.neonbear.honeybeat

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One cloned playlist we remember, so Sync only fetches new songs. */
data class Recent(val url: String, val name: String, val keys: List<String>)
data class Item(val key: String, val target: String, val label: String)
data class Resolved(val name: String, val items: List<Item>)

/** Everything the Cave tab shows. Written from the download service, read by Compose. */
object CaveState {
    var running by mutableStateOf(false)
    var status by mutableStateOf("Ready.")
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var current by mutableStateOf("")
    var lastError by mutableStateOf("")
    var finished by mutableIntStateOf(0)
    var recents by mutableStateOf<List<Recent>>(emptyList())
    val log = mutableStateListOf<String>()

    private val main = Handler(Looper.getMainLooper())
    fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun addLog(line: String) = ui {
        log.add(line)
        while (log.size > 60) log.removeAt(0)
    }
}

object CaveStore {
    fun load(p: SharedPreferences): List<Recent> = try {
        val arr = JSONArray(p.getString("cave_recents", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val k = o.getJSONArray("keys")
            Recent(o.getString("url"), o.getString("name"), (0 until k.length()).map { k.getString(it) })
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(p: SharedPreferences, list: List<Recent>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().put("url", r.url).put("name", r.name).put("keys", JSONArray(r.keys)))
        }
        p.edit().putString("cave_recents", arr.toString()).apply()
    }
}

object CaveEngine {
    private var ready = false

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        YoutubeDL.getInstance().init(ctx)
        FFmpeg.getInstance().init(ctx)
        ready = true
    }

    fun update(ctx: Context): String = try {
        init(ctx)
        val st = YoutubeDL.getInstance().updateYoutubeDL(ctx)
        "yt-dlp update: ${st ?: "already up to date"}"
    } catch (e: Exception) {
        "Update failed: ${e.message}"
    }
}

private const val PROC = "cave-dl"

private fun httpGet(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15000
    c.readTimeout = 20000
    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
    c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
    return c.inputStream.bufferedReader().use { it.readText() }
}

private fun findKey(n: Any?, key: String): Any? {
    when (n) {
        is JSONObject -> {
            if (n.has(key)) return n.get(key)
            for (k in n.keys()) findKey(n.get(k), key)?.let { return it }
        }
        is JSONArray -> for (i in 0 until n.length()) findKey(n.get(i), key)?.let { return it }
    }
    return null
}

private fun searchItem(prefix: String, artist: String, title: String): Item {
    val label = if (artist.isBlank()) title else "$artist - $title"
    return Item("$prefix:${label.lowercase()}", "ytsearch1:$label audio", label)
}

private fun youtube(url: String): Resolved {
    val req = YoutubeDLRequest(url)
    req.addOption("--flat-playlist")
    req.addOption("--yes-playlist")
    req.addOption("--print", "%(playlist_title|)s|||%(id)s|||%(title)s")
    val out = YoutubeDL.getInstance().execute(req, PROC).out
    val rows = out.lines().filter { it.contains("|||") }.map { it.split("|||") }.filter { it.size >= 3 }
    if (rows.isEmpty()) error("No songs found. Is the playlist public?")
    val name = rows.map { it[0].trim() }.firstOrNull { it.isNotEmpty() && it != "NA" } ?: "Singles"
    return Resolved(name, rows.map { Item("yt:${it[1]}", "https://www.youtube.com/watch?v=${it[1]}", it[2]) })
}

private fun spotify(url: String): Resolved {
    val m = Regex("open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?(playlist|album|track)/([A-Za-z0-9]+)").find(url)
        ?: error("That doesn't look like a Spotify playlist link.")
    val html = httpGet("https://open.spotify.com/embed/${m.groupValues[1]}/${m.groupValues[2]}")
    val data = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
        .find(html)?.groupValues?.get(1) ?: error("Couldn't read the Spotify page.")
    val root = JSONObject(data)
    val entity = findKey(root, "entity") as? JSONObject
    val name = entity?.optString("name")?.ifEmpty { entity.optString("title") }?.ifEmpty { "Spotify" } ?: "Spotify"
    val list = findKey(root, "trackList") as? JSONArray
    val items = ArrayList<Item>()
    if (list != null) {
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val title = o.optString("title")
            if (title.isNotBlank()) items += searchItem("sp", o.optString("subtitle").replace('\u00a0', ' '), title)
        }
    } else if (entity != null) {
        val artist = entity.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: ""
        items += searchItem("sp", artist, name)
    }
    if (items.isEmpty()) error("No songs found on that Spotify page. Is it public?")
    return Resolved(name, items)
}

private fun artistOf(o: JSONObject): String = when (val b = o.opt("byArtist") ?: o.opt("artist")) {
    is JSONObject -> b.optString("name")
    is JSONArray -> (0 until b.length()).joinToString(", ") { b.optJSONObject(it)?.optString("name") ?: "" }
    is String -> b
    else -> ""
}

private fun apple(url: String): Resolved {
    val html = httpGet(url)
    val blocks = Regex("<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
        .findAll(html).map { it.groupValues[1] }
    for (b in blocks) {
        val o = try { JSONObject(b) } catch (_: Exception) { continue }
        val tracks = o.optJSONArray("track") ?: o.optJSONArray("tracks") ?: continue
        val albumArtist = artistOf(o)
        val items = ArrayList<Item>()
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            val title = t.optString("name")
            if (title.isBlank()) continue
            items += searchItem("am", artistOf(t).ifBlank { albumArtist }, title)
        }
        if (items.isNotEmpty()) return Resolved(o.optString("name").ifBlank { "Apple Music" }, items)
    }
    error("Couldn't read that Apple Music page. Is the playlist public?")
}

private fun resolve(url: String): Resolved = when {
    "spotify.com" in url -> spotify(url)
    "music.apple.com" in url || "itunes.apple.com" in url -> apple(url)
    else -> youtube(url)
}

private fun safeName(n: String): String =
    n.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.').take(80).ifEmpty { "Playlist" }

private fun downloadTrack(ctx: Context, target: String, art: Boolean): File {
    val dir = File(ctx.cacheDir, "cave-dl/${System.nanoTime()}").apply { mkdirs() }
    fun attempt(withArt: Boolean) {
        val r = YoutubeDLRequest(target)
        r.addOption("--no-playlist")
        r.addOption("-x")
        r.addOption("--audio-format", "mp3")
        r.addOption("--audio-quality", "0")
        r.addOption("--embed-metadata")
        r.addOption("--no-mtime")
        if (withArt) {
            r.addOption("--embed-thumbnail")
            r.addOption("--convert-thumbnails", "jpg")
        }
        r.addOption("-o", dir.absolutePath + "/%(title).120B.%(ext)s")
        YoutubeDL.getInstance().execute(r, PROC)
    }
    try {
        attempt(art)
    } catch (e: Exception) {
        if (CaveJob.cancelled || !art) throw e
        dir.listFiles()?.forEach { it.delete() }
        attempt(false) // Cave retries without cover art when it fails
    }
    return dir.listFiles()?.firstOrNull { it.extension == "mp3" } ?: error("yt-dlp produced no MP3")
}

private fun saveToMusic(ctx: Context, f: File, folder: String) {
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, f.name)
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Cave/$folder")
        put(MediaStore.Audio.Media.IS_PENDING, 1)
    }
    val resolver = ctx.contentResolver
    val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        ?: error("Couldn't create the file in Music/Cave")
    resolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
    values.clear()
    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
}

object CaveJob {
    @Volatile
    var cancelled = false

    fun cancel() {
        cancelled = true
        try { YoutubeDL.getInstance().destroyProcessById(PROC) } catch (_: Exception) {}
    }

    /** Blocking. Clones a playlist (or syncs a known one) into Music/Cave/<playlist name>. */
    fun run(ctx: Context, url: String, art: Boolean, onProgress: (String) -> Unit) {
        cancelled = false
        val prefs = ctx.getSharedPreferences("honeybeat", Context.MODE_PRIVATE)
        CaveState.ui { CaveState.running = true; CaveState.lastError = ""; CaveState.done = 0; CaveState.total = 0; CaveState.current = ""; CaveState.status = "Starting yt-dlp..." }
        var ok = 0
        var failed = 0
        try {
            CaveEngine.init(ctx)
            CaveState.ui { CaveState.status = "Reading playlist..." }
            onProgress("Reading playlist...")
            val res = resolve(url)
            val folder = safeName(res.name)
            val keys = (CaveStore.load(prefs).firstOrNull { it.url == url }?.keys ?: emptyList()).toMutableList()
            val known = keys.toSet()
            val todo = res.items.filter { it.key !in known }
            CaveState.addLog("${res.name}: ${res.items.size} songs, ${todo.size} new")
            if (todo.isEmpty()) {
                CaveState.ui { CaveState.status = "${res.name} is already up to date." }
                return
            }
            CaveState.ui { CaveState.total = todo.size }
            for ((i, item) in todo.withIndex()) {
                if (cancelled) break
                CaveState.ui { CaveState.done = i; CaveState.current = item.label; CaveState.status = "Downloading ${i + 1} of ${todo.size}" }
                onProgress("${i + 1}/${todo.size}  ${item.label}")
                try {
                    val file = downloadTrack(ctx, item.target, art)
                    saveToMusic(ctx, file, folder)
                    file.parentFile?.deleteRecursively()
                    keys += item.key
                    ok++
                    val list = CaveStore.load(prefs).filter { it.url != url }
                    val updated = listOf(Recent(url, res.name, keys.toList())) + list
                    CaveStore.save(prefs, updated)
                    CaveState.ui { CaveState.recents = updated }
                    CaveState.addLog("ok: ${item.label}")
                } catch (e: Exception) {
                    if (cancelled) break
                    failed++
                    val msg = (e.message ?: e.toString()).lines().lastOrNull { it.isNotBlank() } ?: "failed"
                    CaveState.addLog("failed: ${item.label} - $msg")
                    CaveState.ui { CaveState.lastError = "${item.label}: $msg" }
                }
            }
            val end = if (cancelled) "Stopped. $ok new." else "Done. $ok new" + if (failed > 0) ", $failed failed." else "."
            CaveState.ui { CaveState.done = todo.size; CaveState.current = ""; CaveState.status = end }
        } catch (e: Exception) {
            val msg = (e.message ?: e.toString()).lines().lastOrNull { it.isNotBlank() } ?: "failed"
            CaveState.addLog("error: $msg")
            CaveState.ui { CaveState.lastError = msg; CaveState.status = "Stopped." }
        } finally {
            File(ctx.cacheDir, "cave-dl").deleteRecursively()
            CaveState.ui { CaveState.running = false; CaveState.finished += 1 }
        }
    }
}
