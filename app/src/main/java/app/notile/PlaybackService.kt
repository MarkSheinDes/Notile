package app.notile

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Plays in the background; the MediaSession gives the system notification, lock screen and headset controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = MainScope()
    private var sound: SoundControl? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true, // pause for calls and other apps
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .build()
        player.setShuffleOrder(NextUpShuffleOrder(IntArray(0), { player.currentMediaItemIndex }))
        player.addListener(history)
        Companion.player = player
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = NotileWidget.refresh(this@PlaybackService)
        })
        sound = SoundControl(player, Store.get(this), scope)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // items arrive from the UI's controller; the playable uri travels in requestMetadata
                override fun onAddMediaItems(
                    session: MediaSession, controller: MediaSession.ControllerInfo, items: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(items.map { it.buildUpon().setUri(it.requestMetadata.mediaUri).build() }.toMutableList())
            })
            .build()
    }

    /** Counts a play once a song has actually been heard for 30 seconds. */
    private val history = object : Player.Listener {
        private val handler = Handler(Looper.getMainLooper())
        private var counted: String? = null
        private val check = object : Runnable {
            override fun run() {
                val p = session?.player ?: return
                val id = p.currentMediaItem?.mediaId
                if (id != null && id != counted && p.currentPosition >= 30_000) {
                    counted = id
                    id.toLongOrNull()?.let { s -> Store.get(this@PlaybackService).update { it.recordPlay(s, System.currentTimeMillis()) } }
                }
                if (p.isPlaying) handler.postDelayed(this, 5_000)
            }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { counted = null }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(check)
            if (isPlaying) handler.post(check)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        sound?.release()
        scope.cancel()
        session?.run { player.release(); release() }
        session = null
        Companion.player = null
        NotileWidget.refresh(this)
        super.onDestroy()
    }

    companion object {
        /** The live player, for the home-screen widget (same process, main thread only). */
        @Volatile var player: Player? = null
    }
}
