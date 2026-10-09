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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** One cloned playlist we remember, so Sync only fetches new songs. */
data class Recent(val url: String, val name: String, val keys: List<String>)
data class Item(val key: String, val target: String, val label: String)
data class Resolved(val name: String, val items: List<Item>)
data class Hit(val id: String, val title: String, val by: String, val seconds: Int)

/** Songs picked from the Fetch search, waiting for the downloader. */
object CaveQueue {
    private val q = ArrayDeque<Item>()

    @Synchronized fun add(i: Item) { q.addLast(i) }
    @Synchronized fun take(): List<Item> { val l = q.toList(); q.clear(); return l }
    @Synchronized fun clear() { q.clear() }
    @Synchronized fun isEmpty() = q.isEmpty()
}

/** Everything the Cave tab shows. Written from the download service, read by Compose. */
object CaveState {
    var running by mutableStateOf(false)
    var status by mutableStateOf("Ready.")
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var current by mutableStateOf("")
    var lastError by mutableStateOf("")
    var finished by mutableIntStateOf(0)
    var updating by mutableStateOf(false)
    var updateMsg by mutableStateOf("")
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

    /** Cave updates yt-dlp by itself every 12 hours (can be switched off in Options). */
    fun maybeUpdate(ctx: Context, prefs: SharedPreferences) {
        if (!prefs.getBoolean("cave_autoupdate", true)) return
        val last = prefs.getLong("cave_update_at", 0L)
        if (System.currentTimeMillis() - last < 12 * 3600 * 1000L) return
        CaveState.ui { CaveState.status = "Updating yt-dlp..." }
        val msg = update(ctx)
        CaveState.addLog(msg)
        if (!msg.startsWith("Update failed")) prefs.edit().putLong("cave_update_at", System.currentTimeMillis()).apply()
    }

    /** Search YouTube for songs (no download). */
    fun search(ctx: Context, q: String): List<Hit> {
        init(ctx)
        val req = YoutubeDLRequest("ytsearch15:$q")
        req.addOption("--flat-playlist")
        req.addOption("--print", "%(id)s|||%(title)s|||%(uploader,channel|)s|||%(duration|0)s")
        val out = YoutubeDL.getInstance().execute(req, "cave-search").out
        return out.lines().map { it.split("|||") }
            .filter { it.size >= 4 && it[0].length in 8..15 }
            .map { Hit(it[0], it[1], it[2], it[3].toDoubleOrNull()?.toInt() ?: 0) }
    }

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
    val out = YoutubeDL.getInstance().execute(req, "cave-list").out
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

private fun downloadTrack(ctx: Context, target: String, art: Boolean, m4a: Boolean, proc: String): File {
    val dir = File(ctx.cacheDir, "cave-dl/${System.nanoTime()}").apply { mkdirs() }
    fun attempt(withArt: Boolean) {
        val r = YoutubeDLRequest(target)
        r.addOption("--no-playlist")
        r.addOption("-x")
        if (m4a) {
            // best original audio, no re-encode to a lossy format
            r.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best")
            r.addOption("--audio-format", "m4a")
        } else {
            r.addOption("-f", "bestaudio/best")
            r.addOption("--audio-format", "mp3")
            r.addOption("--audio-quality", "320K")
        }
        r.addOption("--embed-metadata")
        r.addOption("--no-mtime")
        if (withArt) {
            r.addOption("--embed-thumbnail")
            r.addOption("--convert-thumbnails", "jpg")
        }
        r.addOption("-o", dir.absolutePath + "/%(title).120B.%(ext)s")
        YoutubeDL.getInstance().execute(r, proc)
    }
    try {
        attempt(art)
    } catch (e: Exception) {
        if (CaveJob.cancelled || !art) throw e
        dir.listFiles()?.forEach { it.delete() }
        attempt(false) // retry without cover art when it fails
    }
    return dir.listFiles()?.firstOrNull { it.extension == "mp3" || it.extension == "m4a" }
        ?: error("yt-dlp produced no audio file")
}

private fun saveToMusic(ctx: Context, f: File, folder: String) {
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, f.name)
        put(MediaStore.Audio.Media.MIME_TYPE, if (f.extension == "m4a") "audio/mp4" else "audio/mpeg")
        put(MediaStore.Audio.Media.RELATIVE_PATH, if (folder.isEmpty()) "Music/Cave" else "Music/Cave/$folder")
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
    private val procs = ConcurrentHashMap.newKeySet<String>()

    fun cancel() {
        cancelled = true
        CaveQueue.clear()
        (procs + "cave-list").forEach {
            try { YoutubeDL.getInstance().destroyProcessById(it) } catch (_: Exception) {}
        }
    }

    private fun reset() {
        cancelled = false
        CaveState.ui {
            CaveState.lastError = ""; CaveState.done = 0; CaveState.total = 0
            CaveState.current = ""; CaveState.status = "Starting yt-dlp..."
        }
    }

    private fun fail(e: Exception) {
        val msg = (e.message ?: e.toString()).lines().lastOrNull { it.isNotBlank() } ?: "failed"
        CaveState.addLog("error: $msg")
        CaveState.ui { CaveState.lastError = msg; CaveState.status = "Stopped." }
    }

    private fun finish(ctx: Context) {
        File(ctx.cacheDir, "cave-dl").deleteRecursively()
        CaveState.ui { CaveState.finished += 1 }
    }

    /** Blocking. Clones a playlist (or syncs a known one) into Music/Cave/<playlist name>. */
    fun run(ctx: Context, url: String, onProgress: (String) -> Unit) {
        reset()
        val prefs = ctx.getSharedPreferences("honeybeat", Context.MODE_PRIVATE)
        try {
            CaveEngine.init(ctx)
            CaveEngine.maybeUpdate(ctx, prefs)
            CaveState.ui { CaveState.status = "Reading playlist..." }
            onProgress("Reading playlist...")
            val res = resolve(url)
            download(ctx, res, url, safeName(res.name), onProgress)
        } catch (e: Exception) {
            if (!cancelled) fail(e)
        } finally {
            finish(ctx)
        }
    }

    /** Blocking. Downloads songs picked from the search into Music/Cave itself. */
    fun runItems(ctx: Context, items: List<Item>, onProgress: (String) -> Unit) {
        reset()
        try {
            CaveEngine.init(ctx)
            download(ctx, Resolved("Songs", items), null, "", onProgress)
        } catch (e: Exception) {
            if (!cancelled) fail(e)
        } finally {
            finish(ctx)
        }
    }

    private fun download(ctx: Context, res: Resolved, recentUrl: String?, folder: String, onProgress: (String) -> Unit) {
        val prefs = ctx.getSharedPreferences("honeybeat", Context.MODE_PRIVATE)
        val art = prefs.getBoolean("cave_art", true)
        val par = prefs.getInt("cave_par", 4) // 0 = every song at once
        val m4a = prefs.getString("cave_quality", "mp3") == "m4a"

        val keys = (recentUrl?.let { u -> CaveStore.load(prefs).firstOrNull { it.url == u }?.keys } ?: emptyList()).toMutableList()
        val known = keys.toSet()
        val todo = res.items.filter { it.key !in known }
        CaveState.addLog("${res.name}: ${res.items.size} songs, ${todo.size} new")
        if (todo.isEmpty()) {
            CaveState.ui { CaveState.status = "Already up to date." }
            return
        }
        CaveState.ui { CaveState.total = todo.size; CaveState.done = 0 }

        val workers = (if (par <= 0) todo.size else minOf(par, todo.size)).coerceAtLeast(1)
        val pool = Executors.newFixedThreadPool(workers)
        val doneC = AtomicInteger()
        val okC = AtomicInteger()
        val failC = AtomicInteger()
        val active = AtomicInteger()
        val lock = Any()

        fun refresh(label: String?) {
            val d = doneC.get()
            val a = active.get()
            val text = "Downloading $d of ${todo.size} ($a running)"
            CaveState.ui {
                CaveState.done = d
                CaveState.status = text
                if (label != null) CaveState.current = label
            }
            onProgress("$d/${todo.size}  ${label ?: ""}")
        }

        val futures = todo.mapIndexed { i, item ->
            pool.submit(Runnable {
                if (cancelled) return@Runnable
                val proc = "cave-$i"
                procs.add(proc)
                active.incrementAndGet()
                refresh(item.label)
                try {
                    val file = downloadTrack(ctx, item.target, art, m4a, proc)
                    saveToMusic(ctx, file, folder)
                    file.parentFile?.deleteRecursively()
                    synchronized(lock) {
                        keys += item.key
                        if (recentUrl != null) {
                            val list = CaveStore.load(prefs).filter { it.url != recentUrl }
                            val updated = listOf(Recent(recentUrl, res.name, keys.toList())) + list
                            CaveStore.save(prefs, updated)
                            CaveState.ui { CaveState.recents = updated }
                        }
                    }
                    okC.incrementAndGet()
                    CaveState.addLog("ok: ${item.label}")
                } catch (e: Exception) {
                    if (!cancelled) {
                        failC.incrementAndGet()
                        val msg = (e.message ?: e.toString()).lines().lastOrNull { it.isNotBlank() } ?: "failed"
                        CaveState.addLog("failed: ${item.label} - $msg")
                        CaveState.ui { CaveState.lastError = "${item.label}: $msg" }
                    }
                } finally {
                    procs.remove(proc)
                    active.decrementAndGet()
                    doneC.incrementAndGet()
                    refresh(null)
                }
            })
        }
        futures.forEach { try { it.get() } catch (_: Exception) {} }
        pool.shutdown()

        val ok = okC.get()
        val failed = failC.get()
        val end = if (cancelled) "Stopped. $ok new." else "Done. $ok new" + if (failed > 0) ", $failed failed." else "."
        CaveState.ui { CaveState.done = todo.size; CaveState.current = ""; CaveState.status = end }
    }
}
