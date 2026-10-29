package app.notile

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

val Bg = Color(0xFF0D0E1A)
val DefaultAccent = Color(0xFFFF4F7B)
private val accentState = mutableStateOf(DefaultAccent)
/** Buttons and highlights; follows the cover of the playing song (see [accentFor]). Reading it in a composable re-draws on change. */
val Accent: Color get() = accentState.value
fun setAccent(c: Color) { accentState.value = c }

/** The cover's dominant colorful hue, made vivid; covers that are mostly gray or dark keep the default pink. */
fun accentFor(art: ImageBitmap?): Color = accentOf(art?.asAndroidBitmap())

fun accentOf(art: Bitmap?): Color {
    if (art == null) return DefaultAccent
    val n = 24
    val px = IntArray(n * n)
    Bitmap.createScaledBitmap(art, n, n, true).getPixels(px, 0, n, 0, 0, n, n)
    val hsv = FloatArray(3)
    var x = 0.0; var y = 0.0; var weight = 0.0
    for (p in px) {
        android.graphics.Color.colorToHSV(p, hsv)
        if (hsv[1] < 0.35f || hsv[2] < 0.35f) continue // gray or near-black pixels carry no hue
        val w = (hsv[1] * hsv[2]).toDouble()
        val r = Math.toRadians(hsv[0].toDouble()) // hues wrap around, so average them as vectors
        x += w * Math.cos(r); y += w * Math.sin(r); weight += w
    }
    if (weight < n * n * 0.04) return DefaultAccent
    val hue = ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
    val yellowish = hue in 40f..70f // bright yellow gives white icons no contrast
    return Color.hsv(hue, 0.7f, if (yellowish) 0.8f else 0.95f)
}
val Muted = Color.White.copy(alpha = 0.6f)

/** Liquid glass: blurs and bends whatever is drawn into [backdrop] behind this element. */
fun Modifier.glass(backdrop: Backdrop, shape: Shape, tint: Color = Color.White.copy(alpha = 0.10f)): Modifier =
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(8.dp.toPx())
            lens(14.dp.toPx(), 28.dp.toPx())
        },
        onDrawSurface = { drawRect(tint) },
    )

/** Frosted panel for content inside the scrolling layer itself (it can't sample the backdrop it belongs to). */
fun Modifier.frost(shape: Shape): Modifier =
    clip(shape).background(Color.White.copy(alpha = 0.08f)).border(0.5.dp, Color.White.copy(alpha = 0.18f), shape)

/** Soft colored light behind everything; tinted by the playing cover. */
@Composable
fun Ambient(tint: Color, modifier: Modifier = Modifier) {
    val c by animateColorAsState(tint, tween(900), label = "ambient")
    Box(modifier.fillMaxSize().background(Bg)) {
        Blob(c.copy(alpha = 0.9f), 440.dp, Modifier.offset((-140).dp, (-160).dp))
        Blob(Accent.copy(alpha = 0.45f), 380.dp, Modifier.align(Alignment.CenterEnd).offset(170.dp, 40.dp))
        Blob(Color(0xFF19B6C9).copy(alpha = 0.35f), 320.dp, Modifier.align(Alignment.BottomStart).offset((-100).dp, 90.dp))
    }
}

@Composable
private fun Blob(color: Color, size: Dp, modifier: Modifier) =
    Box(modifier.size(size).blur(100.dp, BlurredEdgeTreatment.Unbounded).background(color, CircleShape))

private object ArtCache {
    val bitmaps = object : LruCache<Long, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    val missing: MutableSet<Long> = ConcurrentHashMap.newKeySet()
}

/** Album cover from MediaStore, cached per album; null while loading or if the files have none. */
@Composable
fun rememberArt(song: Song?): ImageBitmap? {
    val ctx = LocalContext.current
    val key = song?.albumId
    var bmp by remember(key) { mutableStateOf(key?.let { ArtCache.bitmaps.get(it) }) }
    LaunchedEffect(key) {
        if (song == null || bmp != null || key in ArtCache.missing) return@LaunchedEffect
        repeat(3) { attempt ->
            bmp = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.loadThumbnail(song.uri, Size(600, 600), null).copy(Bitmap.Config.ARGB_8888, false) // software copy: readable for the ambient color
                } catch (e: java.io.FileNotFoundException) {
                    ArtCache.missing.add(key!!) // the files really have no cover
                    null
                } catch (e: Exception) {
                    null // media provider busy (e.g. right after start): try again below
                }
            }
            bmp?.let { ArtCache.bitmaps.put(key, it) }
            if (bmp != null || key in ArtCache.missing) return@LaunchedEffect
            if (attempt < 2) kotlinx.coroutines.delay(700)
        }
    }
    return remember(bmp) { bmp?.asImageBitmap() }
}

/** Average color of a cover, for tinting the background. */
fun averageColor(art: ImageBitmap?): Color? =
    art?.let { Color(Bitmap.createScaledBitmap(it.asAndroidBitmap(), 1, 1, true).getPixel(0, 0)) }

fun placeholderColor(song: Song?): Color =
    if (song == null) Color(0xFF2A2B3D) else Color.hsv(Math.floorMod(song.albumId * 47, 360L).toFloat(), 0.5f, 0.42f) // album ids can be negative

/** Shared-element plumbing: set by the root so a cover can fly between screens with the same key. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalShared = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalAnim = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.shared(key: Any?): Modifier {
    val s = LocalShared.current
    val a = LocalAnim.current
    if (key == null || s == null || a == null) return this
    return with(s) { this@shared.sharedElement(rememberSharedContentState(key), a) }
}

/** Apple-style press: the element shrinks a little on a spring while held. */
fun Modifier.pressScale(src: InteractionSource): Modifier = composed {
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Click with the press spring and no ripple. */
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    pressScale(src).clickable(src, indication = null, onClick = onClick)
}

@Composable
fun PlayPauseIcon(playing: Boolean, size: Dp) =
    AnimatedContent(playing, transitionSpec = {
        (scaleIn(initialScale = 0.5f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut())
    }, label = "playPause") { p ->
        Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p) str(R.string.pause) else str(R.string.play), Modifier.size(size),
            tint = Color.White)
    }

@Composable
fun Cover(song: Song?, modifier: Modifier = Modifier, corner: Dp = 10.dp, sharedKey: Any? = null) {
    val art = rememberArt(song)
    Box(
        modifier.shared(sharedKey).clip(RoundedCornerShape(corner)).background(placeholderColor(song)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, Modifier.fillMaxSize(0.4f), tint = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(horizontal = 20.dp), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis)

@Composable
fun SectionTitle(text: String) =
    Text(text, Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp), color = Color.White, fontSize = 20.sp,
        fontWeight = FontWeight.Bold)

/** A swipe on a row: [direction] 1 = to the right, -1 = to the left. */
class Swipe(val icon: ImageVector, val color: Color, val direction: Int, val action: () -> Unit)

/** Row that can be dragged sideways; past the threshold it fires [swipe] and springs back. */
@Composable
fun Swipeable(swipe: Swipe?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (swipe == null) { Box(modifier) { content() }; return }
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val trigger = with(LocalDensity.current) { 90.dp.toPx() }
    val progress = (abs(dx.value) / trigger).coerceAtMost(1f)
    Box(modifier.fillMaxWidth().background(swipe.color.copy(alpha = 0.85f * progress))) {
        Icon(swipe.icon, null, Modifier.align(if (swipe.direction > 0) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = 24.dp).graphicsLayer { scaleX = 0.6f + 0.4f * progress; scaleY = scaleX; alpha = progress },
            tint = Color.White)
        Box(
            Modifier.offset { IntOffset(dx.value.roundToInt(), 0) }.pointerInput(swipe) {
                detectHorizontalDragGestures(
                    onDragEnd = { scope.launch { if (abs(dx.value) > trigger) swipe.action(); dx.animateTo(0f, spring(0.6f, 400f)) } },
                    onDragCancel = { scope.launch { dx.animateTo(0f) } },
                ) { _, d ->
                    val next = dx.value + d
                    scope.launch { dx.snapTo(if (swipe.direction > 0) next.coerceIn(0f, trigger * 1.4f) else next.coerceIn(-trigger * 1.4f, 0f)) }
                }
            }
        ) { content() }
    }
}

@Composable
fun SongRow(
    song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, menu: (() -> ItemMenu)? = null,
    swipe: Swipe? = null, trailing: (@Composable () -> Unit)? = null, leading: (@Composable () -> Unit)? = null,
) = Swipeable(swipe, modifier) {
    Row(
        Modifier.fillMaxWidth().let { if (menu != null) it.menuClick(onClick, menu) else it.clickable(onClick = onClick) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else Cover(song, Modifier.size(46.dp), 8.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = if (playing) Accent else Color.White, fontSize = 16.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(song.durationMs), color = Muted, fontSize = 13.sp)
        trailing?.invoke()
    }
}

@Composable
fun AlbumTile(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.bouncyClick(onClick)) {
        Cover(album.cover, Modifier.fillMaxWidth().aspectRatio(1f), 14.dp, sharedKey = "album-${album.id}")
        Text(album.title, Modifier.padding(top = 6.dp), color = Color.White, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text(album.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Two buttons under album/artist headers. */
@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        PillButton(str(R.string.play), Icons.Rounded.PlayArrow, Modifier.weight(1f), onPlay)
        Spacer(Modifier.width(12.dp))
        PillButton(str(R.string.shuffle), Icons.Rounded.Shuffle, Modifier.weight(1f), onShuffle)
    }
}

@Composable
fun PillButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.bouncyClick(onClick).frost(shape).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Accent)
        Text(text, Modifier.padding(start = 6.dp), color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GlassCircle(backdrop: Backdrop, icon: ImageVector, desc: String, modifier: Modifier = Modifier, size: Dp = 44.dp,
                tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        modifier.bouncyClick(onClick).size(size).glass(backdrop, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint) }
}

@Composable
fun BoxScope.Empty(text: String) =
    Text(text, Modifier.align(Alignment.Center).padding(32.dp), color = Muted, fontSize = 16.sp)

/**
 * Glass slider: frosted capsule track, accent fill from [fillFrom] (e.g. 0 dB for the equalizer) to the value,
 * white thumb that grows while dragged. [step] snaps values (0 = continuous).
 */
@Composable
fun GlassSlider(
    value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier,
    fillFrom: Float = range.start, step: Float = 0f,
) {
    var dragging by remember { mutableStateOf(false) }
    val thumb by animateFloatAsState(if (dragging) 1.25f else 1f, spring(0.5f, 500f), label = "thumb")
    val span = range.endInclusive - range.start
    fun frac(v: Float) = ((v - range.start) / span).coerceIn(0f, 1f)
    fun valueAt(x: Float, w: Float): Float {
        val v = range.start + (x / w).coerceIn(0f, 1f) * span
        return if (step > 0f) (Math.round((v - range.start) / step) * step + range.start) else v
    }
    BoxWithConstraints(
        modifier.height(32.dp)
            .pointerInput(range, step) { detectTapGestures { onChange(valueAt(it.x, size.width.toFloat())) } }
            .pointerInput(range, step) {
                detectHorizontalDragGestures(onDragStart = { dragging = true }, onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }) { change, _ ->
                    change.consume(); onChange(valueAt(change.position.x, size.width.toFloat()))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        val a = frac(fillFrom)
        val b = frac(value)
        Box(Modifier.fillMaxWidth().height(10.dp).frost(RoundedCornerShape(50)))
        Box(Modifier.offset(x = w * minOf(a, b)).width(w * kotlin.math.abs(b - a)).height(10.dp)
            .background(Accent.copy(alpha = 0.9f), RoundedCornerShape(50)))
        Box(Modifier.offset(x = (w - 24.dp) * b).size(24.dp).graphicsLayer { scaleX = thumb; scaleY = thumb }
            .background(Color.White, CircleShape).border(0.5.dp, Color.Black.copy(alpha = 0.1f), CircleShape))
    }
}

/** Glass toggle: frosted capsule that fills with accent; the knob slides on a spring and stretches mid-way. */
@Composable
fun GlassToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(0.62f, 380f), label = "toggle")
    val stretch = 1f + 0.35f * (1f - kotlin.math.abs(t * 2f - 1f)) // widest halfway through
    Box(
        modifier.size(56.dp, 32.dp).bouncyClick { onChange(!checked) }.frost(RoundedCornerShape(50))
            .background(Accent.copy(alpha = 0.85f * t), RoundedCornerShape(50)).padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = 24.dp * t).size(26.dp)
            .graphicsLayer { scaleX = stretch; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(t, 0.5f) }
            .background(Color.White, CircleShape))
    }
}
