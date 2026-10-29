package app.notile

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.mutableStateListOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

/** Live playback position, refreshed while playing. */
@Composable
fun rememberPosition(hub: PlayerHub): Pair<Long, Long> {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    val c = hub.controller
    LaunchedEffect(c, hub.isPlaying, hub.currentId) {
        while (c != null) {
            pos = c.currentPosition
            dur = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            if (!hub.isPlaying) break
            delay(250)
        }
    }
    return pos to dur
}

@Composable
fun NowPlaying(nav: Nav, onClose: () -> Unit) {
    val hub = nav.hub
    val song = nav.lib.song(hub.currentId) ?: return
    val backdrop = rememberLayerBackdrop()
    val art = rememberArt(song)
    var panel by remember { mutableStateOf<String?>(null) } // null = cover, "queue", "sound"
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) } // sheet follows the finger down, springs back
    val closeAt = with(LocalDensity.current) { 140.dp.toPx() }
    // the spring back overshoots slightly below 0; a negative corner radius crashes, so clamp
    val pulled = drag.value.coerceAtLeast(0f)
    val corner = with(LocalDensity.current) { (pulled / closeAt * 40f).coerceAtMost(40f).toDp() }

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, drag.value.coerceAtLeast(0f).roundToInt()) }
            .clip(RoundedCornerShape(corner))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { scope.launch { if (drag.value > closeAt) onClose() else drag.animateTo(0f, spring(0.7f, 300f)) } },
                    onDragCancel = { scope.launch { drag.animateTo(0f, spring(0.7f, 300f)) } },
                ) { _, dy -> scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } }
            }
    ) {
        Ambient(averageColor(art) ?: placeholderColor(song), Modifier.layerBackdrop(backdrop))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(vertical = 10.dp).size(40.dp, 5.dp).background(Color.White.copy(alpha = 0.4f), CircleShape)
                .clickable(onClick = onClose))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (panel) {
                    "queue" -> Queue(nav)
                    "sound" -> SoundPanel(nav)
                    else -> CoverPager(nav)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Muted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (song.format.isNotEmpty()) Text(song.format, Modifier.frost(RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                HeartButton(nav.store, song.id, 28)
            }
            val (pos, dur) = rememberPosition(hub)
            SeekBar(pos, dur.takeIf { it > 0 } ?: song.durationMs) { hub.seek(it) }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).height(84.dp).glass(backdrop, RoundedCornerShape(42.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlIcon(Icons.Rounded.SkipPrevious, str(R.string.previous), 40) { hub.previous() }
                Box(Modifier.size(74.dp).bouncyClick { hub.toggle() }, contentAlignment = Alignment.Center) {
                    PlayPauseIcon(hub.isPlaying, 54.dp)
                }
                ControlIcon(Icons.Rounded.SkipNext, str(R.string.next), 40) { hub.next() }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                GlassCircle(backdrop, Icons.Rounded.Shuffle, str(R.string.shuffle), size = 50.dp,
                    tint = if (hub.shuffle) Accent else Color.White) { hub.toggleShuffle() }
                GlassCircle(backdrop, if (hub.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    str(R.string.repeat), size = 50.dp, tint = if (hub.repeat != Player.REPEAT_MODE_OFF) Accent else Color.White) { hub.cycleRepeat() }
                GlassCircle(backdrop, Icons.Rounded.GraphicEq, str(R.string.sound), size = 50.dp,
                    tint = if (panel == "sound" || Sound.sleepAt != null || Sound.sleepAtTrackEnd) Accent else Color.White) {
                    panel = if (panel == "sound") null else "sound"
                }
                GlassCircle(backdrop, Icons.AutoMirrored.Rounded.QueueMusic, str(R.string.queue), size = 50.dp,
                    tint = if (panel == "queue") Accent else Color.White) { panel = if (panel == "queue") null else "queue" }
            }
        }
    }
}

@Composable
private fun ControlIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, sizeDp: Int, onClick: () -> Unit) =
    Box(Modifier.size((sizeDp + 20).dp).bouncyClick(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(sizeDp.dp), tint = Color.White)
    }

/** Covers of the whole queue side by side: swipe to change track; follows skips made elsewhere. */
@Composable
private fun CoverPager(nav: Nav) {
    val hub = nav.hub
    val order = hub.order
    val pos = hub.orderPos
    if (pos < 0) { Cover(nav.lib.song(hub.currentId), Modifier.fillMaxWidth().aspectRatio(1f), 22.dp, "np-cover"); return }
    val pager = rememberPagerState(initialPage = pos) { order.size }
    val latestOrder by rememberUpdatedState(order)
    LaunchedEffect(pos) { if (pager.currentPage != pos) pager.animateScrollToPage(pos) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { p ->
            if (p != hub.orderPos) latestOrder.getOrNull(p)?.let(hub::playQueued)
        }
    }
    // Apple-style: the cover shrinks a little while paused
    val scale by animateFloatAsState(if (hub.isPlaying) 1f else 0.86f, spring(0.6f, 300f), label = "cover")
    HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 24.dp, beyondViewportPageCount = 1) { p ->
        Cover(nav.lib.song(order.getOrNull(p)), Modifier.fillMaxWidth().aspectRatio(1f).scale(if (p == pos) scale else 0.86f),
            22.dp, sharedKey = if (p == pos) "np-cover" else null)
    }
}

@Composable
private fun SeekBar(pos: Long, dur: Long, onSeek: (Long) -> Unit) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val frac = dragFrac ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(24.dp)
                .pointerInput(dur) { detectTapGestures { onSeek((it.x / size.width * dur).toLong()) } }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragFrac = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { dragFrac?.let { f -> onSeek((f * dur).toLong()) }; dragFrac = null },
                        onDragCancel = { dragFrac = null },
                    ) { change, _ -> dragFrac = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val h = if (dragFrac != null) 10.dp else 6.dp
            Box(Modifier.fillMaxWidth().height(h).background(Color.White.copy(alpha = 0.22f), CircleShape))
            Box(Modifier.width(maxWidth * frac).height(h).background(Color.White, CircleShape))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime((frac * dur).toLong()), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("-" + formatTime(dur - (frac * dur).toLong()), color = Muted, fontSize = 12.sp)
        }
    }
}

/** Equalizer, smooth transitions and the sleep timer. */
@Composable
private fun SoundPanel(nav: Nav) {
    val store = nav.store
    val d = store.data
    val caps = Sound.caps
    Column(Modifier.fillMaxSize().padding(top = 8.dp).frost(RoundedCornerShape(20.dp)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(str(R.string.equalizer), Modifier.weight(1f), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            GlassToggle(d.eqEnabled, { on -> store.update { it.copy(eqEnabled = on) } })
        }
        if (caps == null) Text(str(R.string.eq_hint), color = Muted, fontSize = 14.sp)
        else {
            FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = List(caps.freqsHz.size) { d.eqBands.getOrElse(it) { 0 } }
                Sound.presets.forEach { (name, f) ->
                    val bands = Sound.presetBands(caps, f)
                    Chip(name, selected = d.eqEnabled && current == bands) { store.update { it.copy(eqEnabled = true, eqBands = bands) } }
                }
            }
            caps.freqsHz.forEachIndexed { i, hz ->
                val mb = d.eqBands.getOrElse(i) { 0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hz >= 1000) str(R.string.freq_khz, hz / 1000) else str(R.string.freq_hz, hz), Modifier.width(64.dp), color = Muted, fontSize = 13.sp)
                    GlassSlider(mb.toFloat(), { v ->
                        store.update { s -> s.copy(eqEnabled = true, eqBands = List(caps.freqsHz.size) { j -> if (j == i) v.toInt() else s.eqBands.getOrElse(j) { 0 } }) }
                    }, caps.minMb.toFloat()..caps.maxMb.toFloat(), Modifier.weight(1f), fillFrom = 0f, step = 100f)
                    Text(str(R.string.db).format(mb / 100), Modifier.padding(start = 10.dp).width(52.dp), color = Color.White, fontSize = 13.sp)
                }
            }
        }
        Text(str(R.string.smooth_transitions), Modifier.padding(top = 18.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(if (d.crossfadeSec == 0) str(R.string.fade_off) else str(R.string.crossfade_on, d.crossfadeSec), color = Muted, fontSize = 14.sp)
        GlassSlider(d.crossfadeSec.toFloat(), { v -> store.update { it.copy(crossfadeSec = v.toInt()) } }, 0f..6f,
            Modifier.fillMaxWidth().padding(top = 6.dp), step = 1f)
        Text(str(R.string.sleep_timer), Modifier.padding(top = 18.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        val at = Sound.sleepAt
        Text(when {
            Sound.sleepAtTrackEnd -> str(R.string.sleep_after_track)
            at != null -> str(R.string.stops_at) + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))
            else -> str(R.string.timer_off)
        }, color = Muted, fontSize = 14.sp)
        FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { m -> Chip(str(R.string.minutes_short, m)) { Sound.sleepIn(m) } }
            Chip(str(R.string.after_track)) { Sound.sleepAfterThisTrack() }
            if (at != null || Sound.sleepAtTrackEnd) Chip(str(R.string.turn_off)) { Sound.cancelSleep() }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) =
    Text(text, Modifier.bouncyClick(onClick).frost(RoundedCornerShape(50))
        .background(if (selected) Accent.copy(alpha = 0.85f) else Color.Transparent, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
        color = Color.White, fontSize = 14.sp)


/** Up next: drag the handle to reorder (when not shuffled), swipe left to remove, tap to jump. */
@Composable
private fun Queue(nav: Nav) {
    val hub = nav.hub
    val base = hub.orderPos + 1
    val items = remember { mutableStateListOf<Long>() }
    LaunchedEffect(hub.order, hub.orderPos) { items.clear(); items.addAll(hub.upNext) }
    val lazy = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(lazy) { from, to ->
        items.add(to.index, items.removeAt(from.index))
        hub.move(base + from.index, base + to.index)
    }
    // stable keys even if a song is queued twice: "id#nth occurrence"
    val keys = remember(items.toList()) { val seen = HashMap<Long, Int>(); items.map { id -> "$id#${seen.merge(id, 1, Int::plus)}" } }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(str(R.string.up_next), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (hub.shuffle) Text(str(R.string.shuffled_tag), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Chip(str(R.string.park_queue)) { hub.park() }
        }
        if (items.isEmpty()) Text(str(R.string.queue_empty), color = Muted, fontSize = 15.sp)
        LazyColumn(Modifier.fillMaxHeight().frost(RoundedCornerShape(20.dp)), lazy) {
            itemsIndexed(items, key = { i, _ -> keys.getOrElse(i) { "x$i" } }) { i, id ->
                val s = nav.lib.song(id) ?: return@itemsIndexed
                ReorderableItem(reorder, keys.getOrElse(i) { "x$i" }, enabled = !hub.shuffle) { dragging ->
                    SongRow(s, false, { hub.playQueued(id) },
                        Modifier.background(if (dragging) Color.White.copy(alpha = 0.12f) else Color.Transparent),
                        swipe = Swipe(Icons.Rounded.Delete, Color(0xFFE5484D), -1) { hub.removeAt(base + i) },
                        trailing = {
                            if (!hub.shuffle) Icon(Icons.Rounded.DragHandle, str(R.string.drag), Modifier.padding(start = 10.dp).draggableHandle(),
                                tint = Muted)
                        })
                }
            }
        }
    }
}
