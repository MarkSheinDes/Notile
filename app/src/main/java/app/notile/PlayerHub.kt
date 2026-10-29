package app.notile

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** UI-side handle on the playback service, exposed as Compose state. */
class PlayerHub(private val ctx: Context) : Player.Listener {
    private val store = Store.get(ctx)
    var controller: MediaController? by mutableStateOf(null)
        private set
    var currentId: Long? by mutableStateOf(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    /** Whole queue in play order (respects shuffle) and where the current song sits in it. */
    var order by mutableStateOf<List<Long>>(emptyList())
        private set
    var orderPos by mutableIntStateOf(-1)
        private set
    private var orderWindows: List<Int> = emptyList() // player index of each entry in [order]
    /** Upcoming song ids in play order. */
    val upNext: List<Long> get() = order.drop(orderPos + 1).take(100)

    fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({ controller = future.get().also { it.addListener(this); sync(it) } }, ctx.mainExecutor)
    }

    fun release() {
        controller?.release()
        controller = null
    }

    override fun onEvents(player: Player, events: Player.Events) = sync(player)

    private fun sync(p: Player) {
        currentId = p.currentMediaItem?.mediaId?.toLongOrNull()
        isPlaying = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
        val tl = p.currentTimeline
        val ids = ArrayList<Long>(p.mediaItemCount)
        val windows = ArrayList<Int>(p.mediaItemCount)
        var pos = -1
        var i = if (tl.isEmpty) C.INDEX_UNSET else tl.getFirstWindowIndex(p.shuffleModeEnabled)
        while (i != C.INDEX_UNSET) {
            if (i == p.currentMediaItemIndex) pos = ids.size
            ids += p.getMediaItemAt(i).mediaId.toLongOrNull() ?: -1L
            windows += i
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, p.shuffleModeEnabled)
        }
        if (ids != order) order = ids
        orderWindows = windows
        orderPos = pos
    }

    fun play(songs: List<Song>, start: Int = 0, shuffled: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.shuffleModeEnabled = shuffled
        c.setMediaItems(songs.map { it.toItem() }, if (shuffled) songs.indices.random() else start, 0)
        c.prepare()
        c.play()
    }

    fun toggle() = controller?.run { if (isPlaying) pause() else play() }
    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seek(ms: Long) = controller?.seekTo(ms)
    fun toggleShuffle() = controller?.run { shuffleModeEnabled = !shuffleModeEnabled }
    fun cycleRepeat() = controller?.run {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Set the current queue aside (with its place in it) and leave the player empty. */
    fun park() {
        val c = controller ?: return
        snapshot()?.let { q -> store.update { it.addQueue(q) } }
        c.clearMediaItems()
        c.pause()
    }

    /** Switch to a parked queue; whatever was playing is parked in its place. Continues from the saved song and position. */
    fun resume(q: SavedQueue, lib: Library) {
        val c = controller ?: return
        val songs = q.songs.mapNotNull(lib::song)
        val parked = if (songs.isEmpty()) null else snapshot()
        store.update { s -> s.removeQueue(q.id).let { if (parked != null) it.addQueue(parked) else it } }
        if (songs.isEmpty()) return // every song of it was deleted from the phone
        c.shuffleModeEnabled = q.shuffle
        c.repeatMode = q.repeat
        c.setMediaItems(songs.map { it.toItem() }, songs.indexOfFirst { it.id == q.current }.coerceAtLeast(0), q.posMs)
        c.prepare()
        c.play()
    }

    private fun snapshot(): SavedQueue? {
        val c = controller ?: return null
        if (c.mediaItemCount == 0) return null
        val ids = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        val title = c.currentMediaItem?.mediaMetadata?.title?.toString() ?: str(R.string.queue)
        return SavedQueue(0, title, ids, currentId ?: ids.first(), c.currentPosition, c.shuffleModeEnabled, c.repeatMode)
    }

    /** Put a song right after the current one (also when shuffled — see [NextUpShuffleOrder]). */
    fun playNext(song: Song) = controller?.run {
        if (mediaItemCount == 0) play(listOf(song)) else addMediaItem(currentMediaItemIndex + 1, song.toItem())
    }

    fun addToQueue(song: Song) = controller?.run { if (mediaItemCount == 0) play(listOf(song)) else addMediaItem(song.toItem()) }

    /** Remove the entry at [orderIndex] of [order]. */
    fun removeAt(orderIndex: Int) = orderWindows.getOrNull(orderIndex)?.let { controller?.removeMediaItem(it) }

    /** Reorder the queue; only meaningful when not shuffled, where play order equals list order. */
    fun move(fromOrderIndex: Int, toOrderIndex: Int) {
        if (shuffle) return
        controller?.moveMediaItem(fromOrderIndex, toOrderIndex)
    }

    /** Jump to a song that is already in the queue. */
    fun playQueued(id: Long) = controller?.run {
        (0 until mediaItemCount).firstOrNull { getMediaItemAt(it).mediaId == id.toString() }?.let { seekTo(it, 0) }
    }
}

private fun Song.toItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(artUri).build()
    )
    .build()
