package app.notile

import android.media.audiofx.Equalizer
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow

/** What the device equalizer offers; filled in by the service once audio starts. */
data class EqCaps(val freqsHz: List<Int>, val minMb: Int, val maxMb: Int)

/** Sound state shared between the playback service and the UI (same process). */
object Sound {
    var caps by mutableStateOf<EqCaps?>(null)
    /** Wall-clock time to stop playback at, or null. */
    var sleepAt by mutableStateOf<Long?>(null)
    var sleepAtTrackEnd by mutableStateOf(false)
    internal var player: ExoPlayer? = null

    fun sleepIn(minutes: Int) { sleepAtTrackEnd = false; sleepAt = System.currentTimeMillis() + minutes * 60_000L }
    fun sleepAfterThisTrack() { sleepAt = null; sleepAtTrackEnd = true; player?.pauseAtEndOfMediaItems = true }
    fun cancelSleep() { sleepAt = null; sleepAtTrackEnd = false; player?.pauseAtEndOfMediaItems = false }

    /** Built-in presets as gain (dB) by frequency, so they fit any number of device bands. */
    val presets: List<Pair<String, (Int) -> Double>> get() = listOf(
        str(R.string.eq_flat) to { _ -> 0.0 },
        str(R.string.eq_bass) to { hz -> if (hz < 250) 6.0 - log10(hz / 60.0).coerceAtLeast(0.0) * 3 else 0.0 },
        str(R.string.eq_rock) to { hz -> if (hz < 200) 4.0 else if (hz > 4000) 4.0 else if (hz in 800..2000) -2.0 else 1.0 },
        str(R.string.eq_pop) to { hz -> if (hz in 400..3000) 3.0 else if (hz < 100) -1.0 else 0.0 },
        str(R.string.eq_voice) to { hz -> if (hz in 1000..4000) 4.0 else if (hz < 200) -3.0 else 0.0 },
        str(R.string.eq_electro) to { hz -> if (hz < 150) 5.0 else if (hz > 6000) 3.0 else if (hz in 500..1500) -1.0 else 0.0 },
    )

    fun presetBands(caps: EqCaps, f: (Int) -> Double) = caps.freqsHz.map { (f(it) * 100).toInt().coerceIn(caps.minMb, caps.maxMb) }
}

/**
 * Lives in the playback service: drives the equalizer from [Store] settings, fades volume in/out at track edges
 * ("smooth transitions"), and runs the sleep timer with a gentle fade before pausing.
 */
@OptIn(UnstableApi::class)
class SoundControl(private val player: ExoPlayer, private val store: Store, private val scope: CoroutineScope) : Player.Listener {
    private var eq: Equalizer? = null

    init {
        Sound.player = player
        player.addListener(this)
        attach(player.audioSessionId)
        scope.launch { snapshotFlow { store.data }.collect { apply(it) } }
        scope.launch { while (true) { delay(100); tick() } }
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) = attach(audioSessionId)

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // "after this track": pauseAtEndOfMediaItems already stopped us; switch the mode off
        if (Sound.sleepAtTrackEnd && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) Sound.cancelSleep()
    }

    private fun attach(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        eq?.release()
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()?.also { e ->
            val range = e.bandLevelRange
            Sound.caps = EqCaps((0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 }, range[0].toInt(), range[1].toInt())
        }
        apply(store.data)
    }

    private fun apply(d: StoreData) {
        val e = eq ?: return
        runCatching {
            e.enabled = d.eqEnabled
            if (d.eqEnabled) for (i in 0 until e.numberOfBands) {
                e.setBandLevel(i.toShort(), d.eqBands.getOrElse(i) { 0 }.toShort())
            }
        }
    }

    private fun tick() {
        var vol = 1f
        val fadeMs = store.data.crossfadeSec * 1000L
        if (fadeMs > 0 && player.isPlaying) {
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > fadeMs * 2 }
            val pos = player.currentPosition
            if (dur != null) vol = minOf(1f, pos.toFloat() / fadeMs, (dur - pos).toFloat() / fadeMs).coerceAtLeast(0f).pow(0.7f)
        }
        Sound.sleepAt?.let { at ->
            val left = at - System.currentTimeMillis()
            if (left <= 0) { player.pause(); Sound.cancelSleep(); vol = 1f }
            else if (left < 10_000) vol *= left / 10_000f // fade out over the last 10 seconds
        }
        if (player.volume != vol) player.volume = vol
    }

    fun release() {
        eq?.release()
        Sound.caps = null
        Sound.player = null
    }
}
