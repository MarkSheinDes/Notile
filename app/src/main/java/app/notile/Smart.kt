package app.notile

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

private const val DAY = 86_400_000L

/** What a smart playlist can ask about a song. All rules of a playlist must hold at once. Texts are string resources. */
enum class RuleKind(@StringRes val label: Int, @StringRes val unit: Int?, val range: IntRange, val default: Int) {
    FAVORITE(R.string.rule_favorite, null, 0..0, 0),
    PLAYS_MIN(R.string.rule_plays_min, R.string.unit_times, 1..50, 3),
    PLAYS_MAX(R.string.rule_plays_max, R.string.unit_times, 0..50, 0), // 0 = never played
    IDLE_DAYS(R.string.rule_idle, R.string.unit_days, 1..365, 30), // never-played songs count as idle
    ADDED_DAYS(R.string.rule_added, R.string.unit_days, 1..365, 30),
    RATING_MIN(R.string.rule_rating, R.string.unit_stars, 1..5, 4), // unrated songs never match
    GENRE(R.string.rule_genre, null, 0..0, 0), // the genre itself is Rule.text
    YEAR_MIN(R.string.rule_year_min, R.string.unit_year, 1950..2030, 1990),
    YEAR_MAX(R.string.rule_year_max, R.string.unit_year, 1950..2030, 1999),
}

data class Rule(val kind: RuleKind, val n: Int, val text: String = "") {
    fun matches(s: Song, d: StoreData, now: Long): Boolean {
        val p = d.plays[s.id]
        return when (kind) {
            RuleKind.FAVORITE -> d.isFavorite(s.id)
            RuleKind.PLAYS_MIN -> (p?.count ?: 0) >= n
            RuleKind.PLAYS_MAX -> (p?.count ?: 0) <= n
            RuleKind.IDLE_DAYS -> p == null || now - p.last > n * DAY
            RuleKind.ADDED_DAYS -> now - s.dateAdded * 1000 <= n * DAY // MediaStore stores seconds
            RuleKind.RATING_MIN -> (d.ratings[s.id] ?: 0) >= n
            RuleKind.GENRE -> s.genre.equals(text, ignoreCase = true)
            RuleKind.YEAR_MIN -> s.year != 0 && s.year >= n
            RuleKind.YEAR_MAX -> s.year != 0 && s.year <= n // songs without a year match neither bound
        }
    }

    fun describe() = when {
        kind == RuleKind.GENRE -> str(R.string.genre_is, text)
        kind.unit == null -> str(kind.label)
        kind == RuleKind.PLAYS_MAX && n == 0 -> str(R.string.never_played)
        else -> "${str(kind.label)} $n ${str(kind.unit)}".trim()
    }
}

data class SmartPlaylist(val id: Long, val name: String, val rules: List<Rule>) {
    fun songs(d: StoreData, lib: Library, now: Long): List<Song> =
        if (rules.isEmpty()) emptyList() else lib.sortedSongs.filter { s -> rules.all { it.matches(s, d, now) } }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("rules", JSONArray(rules.map { JSONObject().put("k", it.kind.name).put("n", it.n).put("t", it.text) }))

    companion object {
        fun fromJson(o: JSONObject): SmartPlaylist {
            val a = o.optJSONArray("rules")
            val rules = if (a == null) emptyList() else (0 until a.length()).mapNotNull {
                val r = a.getJSONObject(it)
                runCatching { Rule(RuleKind.valueOf(r.getString("k")), r.getInt("n"), r.optString("t")) }.getOrNull() // skip rules from a newer version
            }
            return SmartPlaylist(o.getLong("id"), o.getString("name"), rules)
        }
    }
}
