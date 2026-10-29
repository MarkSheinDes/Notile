package app.notile

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StoreTest {
    private fun song(id: Long) = Song(id, "S$id", "A", "Al$id", id, 1000, 1, "Music", "MP3", 0)
    private val lib = Library((1L..200L).map(::song))
    private val day = 86_400_000L

    @Test
    fun jsonRoundTripKeepsEverything() {
        val d = StoreData()
            .toggleFavorite(3).toggleFavorite(7)
            .createPlaylist("Дорога", listOf(1, 2)).addToPlaylist(1, listOf(2, 5))
            .recordPlay(9, 100).recordPlay(9, 200)
            .copy(eqEnabled = true, eqBands = listOf(300, -150), crossfadeSec = 4)
        assertEquals(d, StoreData.fromJson(d.toJson()))
        assertEquals(listOf(7L, 3L), d.favorites)
        assertEquals(listOf(1L, 2L, 5L), d.playlists.single().songs)
        assertEquals(PlayStat(2, 200), d.plays[9])
    }

    @Test
    fun favoritesToggleAndPlaylistEdits() {
        val d = StoreData().toggleFavorite(1).toggleFavorite(1)
        assertFalse(d.isFavorite(1))
        val p = StoreData().createPlaylist("  ", emptyList()).renamePlaylist(1, "Новое").addToPlaylist(1, listOf(4)).removeFromPlaylist(1, 4)
        assertEquals(Playlist(1, "Новое", emptyList()), p.playlists.single())
        assertTrue(p.deletePlaylist(1).playlists.isEmpty())
    }

    @Test
    fun picksUseHistoryAndMixIsStableForADay() {
        val d = StoreData().recordPlay(1, 0).recordPlay(1, 1).recordPlay(2, 40 * day).toggleFavorite(5)
        assertEquals(listOf(2L, 1L), Picks.recent(d, lib).map { it.id })
        assertEquals(1L, Picks.top(d, lib).first().id)
        assertEquals(listOf(1L), Picks.forgotten(d, lib, 40 * day).map { it.id })
        val morning = Picks.mixOfTheDay(d, lib, 50 * day + 1000)
        assertEquals(morning, Picks.mixOfTheDay(d, lib, 50 * day + 80_000_000))
        assertEquals(200, morning.size) // whole 200-song library, under the 300 cap
        assertEquals(50, Picks.mixOfTheDay(d, lib, 0, n = 50).size)
        assertTrue(morning.any { it.id == 5L })
    }

    @Test
    fun smartPlaylistRulesAndJson() {
        val now = 100 * day
        val d = StoreData().recordPlay(1, 0).recordPlay(1, 1).recordPlay(1, 2).recordPlay(2, 99 * day).toggleFavorite(1)
        fun ids(vararg r: Rule) = SmartPlaylist(1, "t", r.toList()).songs(d, lib, now).map { it.id }
        assertEquals(listOf(1L), ids(Rule(RuleKind.FAVORITE, 0)))
        assertEquals(listOf(1L), ids(Rule(RuleKind.PLAYS_MIN, 3)))
        assertEquals(listOf(1L), ids(Rule(RuleKind.IDLE_DAYS, 30), Rule(RuleKind.PLAYS_MIN, 1))) // song 2 was heard yesterday
        assertEquals(198, ids(Rule(RuleKind.PLAYS_MAX, 0)).size) // never played: all but 1 and 2
        assertTrue(ids().isEmpty()) // no rules = nothing, not everything
        val rich = Library(listOf(
            song(1).copy(genre = "Rock", year = 1994), song(2).copy(genre = "rock", year = 2005), song(3).copy(genre = "Pop", year = 1995), song(4)))
        val rd = StoreData().rate(1, 5).rate(3, 3).rate(3, 3).rate(2, 4)
        fun rids(vararg r: Rule) = SmartPlaylist(1, "t", r.toList()).songs(rd, rich, now).map { it.id }
        assertEquals(listOf(1L, 2L), rids(Rule(RuleKind.RATING_MIN, 4)))
        assertEquals(listOf(1L), rids(Rule(RuleKind.GENRE, 0, "ROCK"), Rule(RuleKind.YEAR_MIN, 1990), Rule(RuleKind.YEAR_MAX, 1999)))
        assertEquals(listOf(1L, 3L), rids(Rule(RuleKind.YEAR_MIN, 1990), Rule(RuleKind.YEAR_MAX, 1999))) // 4 has no year: excluded
        assertEquals(null, rd.ratings[3]) // same star twice clears
        assertEquals(listOf("Rock" to 2, "Pop" to 1), rich.genres)
        val rs = rd.saveSmart(null, "Рок 90-х", listOf(Rule(RuleKind.GENRE, 0, "Rock"), Rule(RuleKind.YEAR_MIN, 1990)))
        assertEquals(rs, StoreData.fromJson(rs.toJson()))
        assertTrue(ids(Rule(RuleKind.ADDED_DAYS, 1)).isEmpty()) // test songs were added at time 0
        assertEquals(200, ids(Rule(RuleKind.ADDED_DAYS, 200)).size)
        val s = StoreData().saveSmart(null, "Забытое", listOf(Rule(RuleKind.IDLE_DAYS, 30), Rule(RuleKind.FAVORITE, 0)))
        assertEquals(s, StoreData.fromJson(s.toJson()))
        assertEquals("Новое", s.saveSmart(1, "Новое", emptyList()).smart.single().name)
        assertTrue(s.deleteSmart(1).smart.isEmpty())
    }

    @Test
    fun parkedQueuesKeepPlaceAndRoundTrip() {
        val q = SavedQueue(0, "Трек", listOf(4, 5, 6), 5, 61_000, true, 2)
        val d = StoreData().addQueue(q).addQueue(q.copy(name = "Другая"))
        assertEquals(listOf("Другая", "Трек"), d.queues.map { it.name }) // newest first
        assertEquals(listOf(2L, 1L), d.queues.map { it.id })
        assertEquals(d, StoreData.fromJson(d.toJson()))
        val p = d.togglePin("p1").togglePin("s2").togglePin("p1").copy(hideRecent = true).createPlaylist("x", emptyList()).togglePin("p1")
        assertEquals(p, StoreData.fromJson(p.toJson()))
        assertEquals(listOf("s2"), p.deletePlaylist(1).pinned) // deleting a playlist unpins it
        assertEquals(listOf(2L), d.removeQueue(1).queues.map { it.id })
    }

    @Test
    fun playNextLandsRightAfterCurrentEvenWhenShuffled() {
        var current = 2
        val o = NextUpShuffleOrder(IntArray(0), { current }, Random(1)).cloneAndSet(5, 2) // 5 songs, starting at #2
        assertEquals(2, o.firstIndex)
        val next = o.cloneAndInsert(3, 1) // "play next" inserts at current + 1
        assertEquals(3, next.getNextIndex(2))
        val appended = next.cloneAndInsert(6, 1) // "add to queue" appends
        assertEquals(6, appended.lastIndex)
        val removed = appended.cloneAndRemove(3, 4)
        assertEquals(6, removed.length)
        assertEquals(C.INDEX_UNSET, removed.getNextIndex(removed.lastIndex))
    }
}
