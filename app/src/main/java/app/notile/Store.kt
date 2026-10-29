package app.notile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

data class Playlist(val id: Long, val name: String, val songs: List<Long>)
data class PlayStat(val count: Int, val last: Long)
/** A queue set aside to come back to: the songs, which one was playing and where in it. */
data class SavedQueue(
    val id: Long, val name: String, val songs: List<Long>, val current: Long, val posMs: Long, val shuffle: Boolean, val repeat: Int,
)

/** Everything the user creates or the app learns: favorites, playlists, listening history, sound settings. */
data class StoreData(
    val favorites: List<Long> = emptyList(), // newest first
    val playlists: List<Playlist> = emptyList(),
    val plays: Map<Long, PlayStat> = emptyMap(),
    val smart: List<SmartPlaylist> = emptyList(),
    val queues: List<SavedQueue> = emptyList(), // newest first
    val pinned: List<String> = emptyList(), // playlists on the Listen screen: "p<id>" regular, "s<id>" smart
    val hideRecent: Boolean = false,
    val ratings: Map<Long, Int> = emptyMap(), // song id -> 1..5 stars; absent = unrated
    val eqEnabled: Boolean = false,
    val eqBands: List<Int> = emptyList(), // millibels per band; empty = flat
    val crossfadeSec: Int = 0,
) {
    fun isFavorite(id: Long) = id in favorites
    fun toggleFavorite(id: Long) = copy(favorites = if (id in favorites) favorites - id else listOf(id) + favorites)
    fun recordPlay(id: Long, now: Long) = copy(plays = plays + (id to PlayStat((plays[id]?.count ?: 0) + 1, now)))

    fun createPlaylist(name: String, songs: List<Long>): StoreData {
        val id = (playlists.maxOfOrNull { it.id } ?: 0) + 1
        return copy(playlists = playlists + Playlist(id, name.trim().ifEmpty { str(R.string.playlist_n, id) }, songs))
    }
    fun addToPlaylist(pid: Long, ids: List<Long>) = editPlaylist(pid) { it.copy(songs = it.songs + ids.filter { s -> s !in it.songs }) }
    fun removeFromPlaylist(pid: Long, id: Long) = editPlaylist(pid) { it.copy(songs = it.songs - id) }
    fun renamePlaylist(pid: Long, name: String) = editPlaylist(pid) { it.copy(name = name.trim().ifEmpty { it.name }) }
    fun deletePlaylist(pid: Long) = copy(playlists = playlists.filter { it.id != pid }, pinned = pinned - "p$pid")
    fun saveSmart(id: Long?, name: String, rules: List<Rule>): StoreData {
        val newId = id ?: ((smart.maxOfOrNull { it.id } ?: 0) + 1)
        val p = SmartPlaylist(newId, name.trim().ifEmpty { str(R.string.smart_n, newId) }, rules)
        return copy(smart = if (id == null) smart + p else smart.map { if (it.id == id) p else it })
    }
    fun addQueue(q: SavedQueue) = copy(queues = listOf(q.copy(id = (queues.maxOfOrNull { it.id } ?: 0) + 1)) + queues)
    /** Tapping the star the song already has clears the rating. */
    fun rate(id: Long, stars: Int) = copy(ratings = if (stars == 0 || ratings[id] == stars) ratings - id else ratings + (id to stars))
    fun togglePin(key: String) = copy(pinned = if (key in pinned) pinned - key else pinned + key)
    fun removeQueue(id: Long) = copy(queues = queues.filter { it.id != id })
    fun deleteSmart(id: Long) = copy(smart = smart.filter { it.id != id }, pinned = pinned - "s$id")
    private fun editPlaylist(pid: Long, f: (Playlist) -> Playlist) = copy(playlists = playlists.map { if (it.id == pid) f(it) else it })

    fun toJson(): String = JSONObject()
        .put("favorites", JSONArray(favorites))
        .put("playlists", JSONArray(playlists.map { JSONObject().put("id", it.id).put("name", it.name).put("songs", JSONArray(it.songs)) }))
        .put("plays", JSONObject().apply { plays.forEach { (id, p) -> put(id.toString(), JSONArray(listOf(p.count, p.last))) } })
        .put("smart", JSONArray(smart.map { it.toJson() }))
        .put("queues", JSONArray(queues.map {
            JSONObject().put("id", it.id).put("name", it.name).put("songs", JSONArray(it.songs)).put("current", it.current)
                .put("posMs", it.posMs).put("shuffle", it.shuffle).put("repeat", it.repeat)
        }))
        .put("pinned", JSONArray(pinned))
        .put("hideRecent", hideRecent)
        .put("ratings", JSONObject().apply { ratings.forEach { (id, n) -> put(id.toString(), n) } })
        .put("eqEnabled", eqEnabled)
        .put("eqBands", JSONArray(eqBands))
        .put("crossfadeSec", crossfadeSec)
        .toString()

    companion object {
        fun fromJson(s: String): StoreData {
            val o = JSONObject(s)
            fun longs(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getLong(it) }
            val pl = o.optJSONArray("playlists")
            val plays = o.optJSONObject("plays")
            return StoreData(
                favorites = longs(o.optJSONArray("favorites")),
                playlists = if (pl == null) emptyList() else (0 until pl.length()).map {
                    val p = pl.getJSONObject(it)
                    Playlist(p.getLong("id"), p.getString("name"), longs(p.optJSONArray("songs")))
                },
                plays = plays?.keys()?.asSequence()?.associate { k ->
                    val a = plays.getJSONArray(k)
                    k.toLong() to PlayStat(a.getInt(0), a.getLong(1))
                } ?: emptyMap(),
                smart = o.optJSONArray("smart")?.let { a -> (0 until a.length()).map { SmartPlaylist.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
                queues = o.optJSONArray("queues")?.let { a ->
                    (0 until a.length()).map {
                        val q = a.getJSONObject(it)
                        SavedQueue(q.getLong("id"), q.getString("name"), longs(q.optJSONArray("songs")), q.getLong("current"),
                            q.getLong("posMs"), q.getBoolean("shuffle"), q.getInt("repeat"))
                    }
                } ?: emptyList(),
                pinned = o.optJSONArray("pinned")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                hideRecent = o.optBoolean("hideRecent"),
                ratings = o.optJSONObject("ratings")?.let { r -> r.keys().asSequence().associate { it.toLong() to r.getInt(it) } } ?: emptyMap(),
                eqEnabled = o.optBoolean("eqEnabled"),
                eqBands = o.optJSONArray("eqBands")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList(),
                crossfadeSec = o.optInt("crossfadeSec"),
            )
        }
    }
}

/** Smart picks for the Listen screen, from listening history. */
object Picks {
    private const val DAY = 86_400_000L

    fun recent(d: StoreData, lib: Library, n: Int = 20) =
        d.plays.entries.sortedByDescending { it.value.last }.mapNotNull { lib.song(it.key) }.take(n)

    fun top(d: StoreData, lib: Library, n: Int = 20) =
        d.plays.entries.sortedByDescending { it.value.count }.mapNotNull { lib.song(it.key) }.take(n)

    /** Played at least twice, but not for a month. */
    fun forgotten(d: StoreData, lib: Library, now: Long, n: Int = 20) =
        d.plays.entries.filter { it.value.count >= 2 && now - it.value.last > 30 * DAY }
            .sortedByDescending { it.value.count }.mapNotNull { lib.song(it.key) }.take(n)

    /** Same set all day (up to 300 songs): favorites and most played, topped up from the library; new set each day. */
    fun mixOfTheDay(d: StoreData, lib: Library, now: Long, n: Int = 300): List<Song> {
        val rnd = Random(now / DAY)
        val liked = (d.favorites + top(d, lib, 100).map { it.id }).distinct().mapNotNull(lib::song)
        val fill = lib.songs.shuffled(rnd).filter { it !in liked }
        return (liked.shuffled(rnd).take(n * 2 / 3) + fill).take(n).shuffled(rnd)
    }
}

/** App-wide store, saved to a JSON file after every change. One instance per process (UI and service share it). */
class Store private constructor(private val file: File) {
    var data by mutableStateOf(runCatching { StoreData.fromJson(file.readText()) }.getOrDefault(StoreData()))
        private set
    private val io = Executors.newSingleThreadExecutor()

    fun update(f: (StoreData) -> StoreData) {
        data = f(data)
        val json = data.toJson()
        io.execute {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(file) // atomic swap: a crash mid-write never leaves a half file
        }
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(ctx: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(File(ctx.applicationContext.filesDir, "notile.json")).also { instance = it }
        }
    }
}
