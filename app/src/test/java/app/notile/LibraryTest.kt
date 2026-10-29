package app.notile

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTest {
    private fun song(id: Long, title: String, album: String, albumId: Long, track: Int, artist: String = "A", added: Long = 0) =
        Song(id, title, artist, album, albumId, 1000, track, "Music/$album", "FLAC", added)

    @Test
    fun albumsAreGroupedAndTracksOrderedByDiscThenTrack() {
        val lib = Library(listOf(
            song(1, "B", "X", 10, 2002),
            song(2, "A", "X", 10, 1001),
            song(3, "C", "X", 10, 1002),
            song(4, "Z", "Y", 20, 1),
        ))
        assertEquals(listOf("X", "Y"), lib.albums.map { it.title })
        assertEquals(listOf(2L, 3L, 1L), lib.albums[0].songs.map { it.id })
        assertEquals(1, lib.albums[0].songs[0].trackNo)
    }

    @Test
    fun recentPutsNewestAlbumFirstAndSearchIgnoresCase() {
        val lib = Library(listOf(song(1, "Night", "Old", 1, 1, added = 5), song(2, "Day", "New", 2, 1, artist = "Neon", added = 9)))
        assertEquals("New", lib.recent.first().title)
        assertEquals(listOf(2L), lib.search("neon").first.map { it.id })
        assertEquals(listOf("Neon"), lib.search("NEO").third)
    }

    @Test
    fun formatsAndTime() {
        assertEquals("FLAC", Library.formatOf("audio/flac"))
        assertEquals("MP3", Library.formatOf("audio/mpeg"))
        assertEquals("3:05", formatTime(185_000))
        assertEquals("1:00:00", formatTime(3_600_000))
    }

    @Test
    fun pluralForms() {
        assertEquals(listOf(0, 1, 2, 2, 0, 2), listOf(1, 3, 5, 11, 21, 112).map { pluralForm(it, "ru") })
        assertEquals(listOf(0, 2, 2, 2, 2), listOf(1, 3, 11, 21, 112).map { pluralForm(it, "en") })
    }
}
