package app.notile

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int, // MediaStore packs disc*1000 + track
    val folder: String,
    val format: String,
    val dateAdded: Long,
    val genre: String = "",
    val year: Int = 0, // 0 = no tag
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artUri: Uri get() = Uri.parse("content://media/external/audio/media/$id/albumart")
    val trackNo: Int get() = track % 1000
}

data class Album(val id: Long, val title: String, val artist: String, val songs: List<Song>) {
    val cover: Song get() = songs.first()
    val durationMs: Long get() = songs.sumOf { it.durationMs }
}

/** The user's music, grouped the ways the library screens show it. Pure, so it is testable without a device. */
class Library(val songs: List<Song>) {
    val albums: List<Album> = songs.groupBy { it.albumId }
        .map { (id, s) -> Album(id, s[0].album, s[0].artist, s.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))) }
        .sortedBy { it.title.lowercase() }
    val artists: Map<String, List<Album>> = albums.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val folders: Map<String, List<Song>> = songs.groupBy { it.folder }
        .mapValues { (_, s) -> s.sortedBy { it.title.lowercase() } }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val recent: List<Album> = albums.sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }.take(12)
    val sortedSongs: List<Song> = songs.sortedBy { it.title.lowercase() }
    /** Genres found in the tags (spelling variants merged), most songs first. */
    val genres: List<Pair<String, Int>> = songs.map { it.genre }.filter { it.isNotBlank() }.groupBy { it.lowercase() }
        .values.sortedByDescending { it.size }.map { it.first() to it.size }
    private val byId = songs.associateBy { it.id }

    fun song(id: Long?): Song? = id?.let(byId::get)
    fun album(id: Long): Album? = albums.firstOrNull { it.id == id }

    fun search(q: String): Triple<List<Song>, List<Album>, List<String>> {
        val s = q.trim()
        if (s.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        fun has(t: String) = t.contains(s, ignoreCase = true)
        return Triple(
            sortedSongs.filter { has(it.title) || has(it.artist) }.take(50),
            albums.filter { has(it.title) || has(it.artist) }.take(20),
            artists.keys.filter(::has).take(20),
        )
    }

    companion object {
        val unknownArtist: String get() = str(R.string.unknown_artist)

        fun load(ctx: Context): Library {
            val cols = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.GENRE, MediaStore.Audio.Media.YEAR,
            )
            val songs = ArrayList<Song>()
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val artist = c.getString(2)
                    songs += Song(
                        id = c.getLong(0),
                        title = c.getString(1) ?: str(R.string.untitled),
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") unknownArtist else artist,
                        album = c.getString(3) ?: str(R.string.no_album),
                        albumId = c.getLong(4),
                        durationMs = c.getLong(5),
                        track = c.getInt(6),
                        folder = (c.getString(7) ?: "").trimEnd('/'),
                        format = formatOf(c.getString(8)),
                        dateAdded = c.getLong(9),
                        genre = c.getString(10)?.trim() ?: "",
                        year = c.getInt(11),
                    )
                }
            }
            return Library(songs)
        }

        fun formatOf(mime: String?): String = when (mime?.lowercase()) {
            null -> ""
            "audio/mpeg" -> "MP3"
            "audio/mp4", "audio/aac", "audio/x-m4a" -> "AAC"
            "audio/x-wav", "audio/wav" -> "WAV"
            else -> mime.substringAfter('/').removePrefix("x-").uppercase()
        }
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
