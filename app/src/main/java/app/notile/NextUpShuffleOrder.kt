package app.notile

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * Shuffle order where songs added with "play next" land right after the current song (the stock order drops them
 * at random places) and songs appended to the queue go to the end of the shuffled order.
 */
class NextUpShuffleOrder(
    private val shuffled: IntArray,
    private val current: () -> Int, // the player's current index in the unshuffled list
    private val random: Random = Random.Default,
) : ShuffleOrder {
    private val posOf = IntArray(shuffled.size).also { p -> shuffled.forEachIndexed { pos, idx -> p[idx] = pos } }

    override fun getLength() = shuffled.size
    override fun getFirstIndex() = shuffled.firstOrNull() ?: C.INDEX_UNSET
    override fun getLastIndex() = shuffled.lastOrNull() ?: C.INDEX_UNSET
    override fun getNextIndex(index: Int) = posOf[index].let { if (it + 1 < shuffled.size) shuffled[it + 1] else C.INDEX_UNSET }
    override fun getPreviousIndex(index: Int) = posOf[index].let { if (it > 0) shuffled[it - 1] else C.INDEX_UNSET }

    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder {
        val order = (0 until insertionCount).shuffled(random).toMutableList()
        if (startIndex in order) { order.remove(startIndex); order.add(0, startIndex) }
        return NextUpShuffleOrder(order.toIntArray(), current, random)
    }

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (shuffled.isEmpty()) return cloneAndSet(insertionCount, C.INDEX_UNSET)
        val added = (insertionIndex until insertionIndex + insertionCount).toList()
        val shifted = shuffled.map { if (it >= insertionIndex) it + insertionCount else it }.toMutableList()
        val at = if (insertionIndex >= shuffled.size) shifted.size // appended: end of the shuffled queue
        else current().let { c -> if (c in shuffled.indices) posOf[c] + 1 else 0 } // play next
        shifted.addAll(at, added)
        return NextUpShuffleOrder(shifted.toIntArray(), current, random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val n = indexToExclusive - indexFrom
        val kept = shuffled.filter { it < indexFrom || it >= indexToExclusive }.map { if (it >= indexToExclusive) it - n else it }
        return NextUpShuffleOrder(kept.toIntArray(), current, random)
    }

    override fun cloneAndClear(): ShuffleOrder = NextUpShuffleOrder(IntArray(0), current, random)
}
