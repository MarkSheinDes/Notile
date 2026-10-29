package app.notile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.Player
import java.util.concurrent.Executors

/** Home-screen player: three layouts (small, medium, large) chosen by the size the user stretches it to. */
class NotileWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val p = PlaybackService.player
        when (intent.action) {
            FAV -> p?.currentMediaItem?.mediaId?.toLongOrNull()?.let { id -> Store.get(ctx).update { it.toggleFavorite(id) } }
            else -> if (intent.action in ACTIONS) {
                if (p == null || p.mediaItemCount == 0) { // nothing loaded yet: open the app to pick music
                    ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else when (intent.action) {
                    PLAY -> if (p.isPlaying) p.pause() else p.play()
                    NEXT -> p.seekToNext()
                    PREV -> p.seekToPrevious()
                    SHUFFLE -> p.shuffleModeEnabled = !p.shuffleModeEnabled
                }
            }
        }
        if (intent.action in ACTIONS) refresh(ctx)
    }

    companion object {
        private const val PLAY = "app.notile.widget.PLAY"
        private const val NEXT = "app.notile.widget.NEXT"
        private const val PREV = "app.notile.widget.PREV"
        private const val SHUFFLE = "app.notile.widget.SHUFFLE"
        private const val FAV = "app.notile.widget.FAV"
        private val ACTIONS = setOf(PLAY, NEXT, PREV, SHUFFLE, FAV)

        private val main = Handler(Looper.getMainLooper())
        private val io = Executors.newSingleThreadExecutor()
        private var artId: Long? = null // cover currently cached in [art]
        private var art: Bitmap? = null
        private var artAccent = DefaultAccent // buttons follow the cover, like in the app

        /** Redraw every placed widget from the current player state. Call on the main thread. */
        fun refresh(ctx: Context) {
            val app = ctx.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, NotileWidget::class.java))
            if (ids.isEmpty()) return
            val p: Player? = PlaybackService.player
            val item = p?.currentMediaItem
            val id = item?.mediaId?.toLongOrNull()
            if (id != artId) { // new song: load its cover off the main thread, then redraw
                artId = id; art = null; artAccent = DefaultAccent
                if (id != null) io.execute {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val bmp = runCatching { app.contentResolver.loadThumbnail(uri, Size(360, 360), null) }.getOrNull()
                    main.post { if (artId == id) { art = bmp; artAccent = accentOf(bmp); refresh(app) } }
                }
            }
            val s = State(
                title = item?.mediaMetadata?.title?.toString() ?: "Notile",
                artist = item?.mediaMetadata?.artist?.toString() ?: str(R.string.widget_idle),
                playing = p?.isPlaying == true,
                fav = id != null && Store.get(app).data.isFavorite(id),
            )
            val views = mapOf(
                SizeF(250f, 70f) to build(app, R.layout.widget_small, s) { it.button(app, R.id.next, NEXT) },
                SizeF(250f, 120f) to build(app, R.layout.widget_medium, s) {
                    it.button(app, R.id.next, NEXT); it.button(app, R.id.prev, PREV); it.button(app, R.id.shuffle, SHUFFLE)
                    it.button(app, R.id.heart, FAV)
                    it.setImageViewResource(R.id.heart, if (s.fav) R.drawable.ic_w_heart_on else R.drawable.ic_w_heart)
                    it.setColorStateList(R.id.heart, "setImageTintList", ColorStateList.valueOf(if (s.fav) artAccent.toArgb() else android.graphics.Color.WHITE))
                },
                SizeF(250f, 230f) to build(app, R.layout.widget_large, s) {},
            )
            ids.forEach { mgr.updateAppWidget(it, RemoteViews(views)) }
        }

        private class State(val title: String, val artist: String, val playing: Boolean, val fav: Boolean)

        private fun build(ctx: Context, layout: Int, s: State, extra: (RemoteViews) -> Unit) = RemoteViews(ctx.packageName, layout).also {
            it.setTextViewText(R.id.title, s.title)
            it.setTextViewText(R.id.artist, s.artist)
            art?.let { b -> it.setImageViewBitmap(R.id.art, b) }
            it.setImageViewResource(R.id.play, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            it.button(ctx, R.id.play, PLAY)
            it.setColorStateList(R.id.play, "setBackgroundTintList", ColorStateList.valueOf(artAccent.toArgb()))
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            it.setOnClickPendingIntent(R.id.art, open)
            extra(it)
        }

        private fun RemoteViews.button(ctx: Context, viewId: Int, action: String) {
            val i = Intent(ctx, NotileWidget::class.java).setAction(action)
            setOnClickPendingIntent(viewId, PendingIntent.getBroadcast(ctx, action.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
    }
}
