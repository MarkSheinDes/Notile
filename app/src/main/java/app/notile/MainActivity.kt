package app.notile

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var hub: PlayerHub

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // always dark UI, so light status and navigation bar icons
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        hub = PlayerHub(this)
        setContent { NotileApp(hub) }
    }

    override fun onStart() {
        super.onStart()
        hub.connect()
    }

    override fun onStop() {
        hub.release()
        super.onStop()
    }
}

private val PERMS = arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NotileApp(hub: PlayerHub) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it[Manifest.permission.READ_MEDIA_AUDIO] == true
    }
    var lib by remember { mutableStateOf<Library?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // re-read on every return to the app, so newly added music shows up
    LaunchedEffect(granted) {
        if (granted) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { lib = withContext(Dispatchers.IO) { Library.load(ctx) } }
    }

    val backdrop = rememberLayerBackdrop()
    var root by remember { mutableStateOf<Screen>(Screen.Listen) }
    val stack = remember { mutableStateListOf<Screen>() }
    var nowPlaying by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<Pair<ItemMenu, Rect>?>(null) } // playlist / queue long-press menu
    val store = remember { Store.get(ctx) }
    fun nav(l: Library, pad: PaddingValues) = Nav(
        l, hub, store, { stack.add(it); nowPlaying = false }, { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }, pad,
        { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() },
    )
    val current = stack.lastOrNull() ?: root
    val song = lib?.song(hub.currentId)
    val coverArt = rememberArt(song)
    val accent by animateColorAsState(remember(coverArt) { accentFor(coverArt) }, tween(900), label = "accent")
    LaunchedEffect(Unit) { snapshotFlow { accent }.collect { setAccent(it) } } // every animation frame, without recomposing the whole app
    LaunchedEffect(hub.currentId == null) { if (hub.currentId == null) nowPlaying = false } // queue emptied: don't reopen the player on the next song

    BackHandler(enabled = itemMenu != null || nowPlaying || stack.isNotEmpty()) {
        when {
            itemMenu != null -> itemMenu = null
            nowPlaying -> nowPlaying = false
            else -> stack.removeAt(stack.lastIndex)
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize().background(Bg)) {
        CompositionLocalProvider(LocalShared provides this, LocalItemMenu provides { m, r -> itemMenu = m to r }) { Box(Modifier.fillMaxSize()) {
            // everything drawn here is what the glass bars refract
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                Ambient(averageColor(coverArt) ?: Color(0xFF5B3DF5))
                val l = lib
                when {
                    !granted -> PermissionScreen { ask.launch(PERMS) }
                    l == null -> Box(Modifier.fillMaxSize()) { Empty(str(R.string.loading_music)) }
                    l.songs.isEmpty() -> Box(Modifier.fillMaxSize()) { Empty(str(R.string.no_music)) }
                    else -> {
                        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + if (stack.isEmpty()) 12.dp else 64.dp
                        val nav = nav(l, PaddingValues(top = top, bottom = 190.dp))
                        AnimatedContent(NavState(current, stack.size, TAB_ORDER.indexOf(root)), transitionSpec = { navTransition() },
                            label = "screen") {
                            CompositionLocalProvider(LocalAnim provides this) { ScreenContent(it.screen, nav) }
                        }
                    }
                }
            }

            AnimatedVisibility(stack.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                GlassCircle(backdrop, Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.back),
                    Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp)) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
            }

            if (lib != null) Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
                AnimatedVisibility(song != null && !nowPlaying, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    CompositionLocalProvider(LocalAnim provides this) {
                        song?.let { MiniPlayer(backdrop, it, hub) { nowPlaying = true } }
                    }
                }
                BottomBar(backdrop, root) { root = it; stack.clear() }
            }

            AnimatedVisibility(nowPlaying && song != null, enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()) {
                CompositionLocalProvider(LocalAnim provides this) {
                    lib?.let { l -> NowPlaying(nav(l, PaddingValues()), onClose = { nowPlaying = false }) }
                }
            }

            itemMenu?.let { (m, r) -> ItemMenuOverlay(m, r, backdrop) { itemMenu = null } }
        } }
    }
}

@Composable
private fun MiniPlayer(backdrop: Backdrop, song: Song, hub: PlayerHub, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) } // swipe left/right on the mini player = next/previous
    val skipAt = with(LocalDensity.current) { 80.dp.toPx() }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).height(60.dp).bouncyClick(onOpen).glass(backdrop, shape)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                dx.value < -skipAt -> hub.next()
                                dx.value > skipAt -> hub.previous()
                            }
                            dx.animateTo(0f, spring(0.6f, 400f))
                        }
                    },
                ) { _, d -> scope.launch { dx.snapTo(dx.value + d) } }
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).offset { IntOffset(dx.value.roundToInt(), 0) }, verticalAlignment = Alignment.CenterVertically) {
            Cover(song, Modifier.size(44.dp), 22.dp, sharedKey = "np-cover")
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(44.dp).bouncyClick { hub.toggle() }, contentAlignment = Alignment.Center) { PlayPauseIcon(hub.isPlaying, 30.dp) }
        Box(Modifier.size(44.dp).bouncyClick { hub.next() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SkipNext, str(R.string.next), Modifier.size(28.dp), tint = Color.White)
        }
    }
}

/** Where we are: the screen, how deep in the back stack, and which tab it belongs to. */
private data class NavState(val screen: Screen, val depth: Int, val tab: Int)

private val TAB_ORDER = listOf(Screen.Listen, Screen.Library, Screen.Search)

/**
 * iOS-like motion: a pushed page slides in from the right over a slightly receding page (and back on pop);
 * switching tabs glides the content a little toward where the tab drop moved.
 */
private fun AnimatedContentTransitionScope<NavState>.navTransition(): ContentTransform {
    val soft = spring<IntOffset>(dampingRatio = 0.92f, stiffness = 420f)
    // pages have no opaque background, so the old one must be gone quickly or its text shows through the new one
    val appear = fadeIn(tween(220, delayMillis = 60))
    val vanish = fadeOut(tween(140))
    val from = initialState
    val to = targetState
    return when {
        to.depth > from.depth -> (slideInHorizontally(soft) { it } + appear) togetherWith (slideOutHorizontally(soft) { -it / 4 } + vanish)
        to.depth < from.depth ->
            ((slideInHorizontally(soft) { -it / 4 } + appear) togetherWith (slideOutHorizontally(soft) { it } + vanish)).apply { targetContentZIndex = -1f }
        else -> {
            val dir = if (to.tab >= from.tab) 1 else -1
            (slideInHorizontally(soft) { dir * it / 6 } + appear) togetherWith (slideOutHorizontally(soft) { -dir * it / 6 } + vanish)
        }
    }
}

private val TABS get() = listOf(
    Triple(Screen.Listen, Icons.Rounded.PlayCircle, str(R.string.tab_listen)),
    Triple(Screen.Library, Icons.Rounded.LibraryMusic, str(R.string.tab_library)),
)

/**
 * iOS 26 tab bar: a glass capsule; the selected tab sits on a lighter glass "drop" under the icons. The drop
 * springs between tabs, stretches while it travels, and can be dragged with a finger. In search the capsule
 * smoothly shrinks to one bubble and the search bubble widens.
 */
@Composable
private fun BottomBar(backdrop: Backdrop, root: Screen, onSelect: (Screen) -> Unit) {
    val searching = root == Screen.Search
    var lastTab by remember { mutableStateOf<Screen>(Screen.Listen) }
    if (!searching) lastTab = root
    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
        val gap = 10.dp
        val full = maxWidth
        val tabsW by animateDpAsState(if (searching) 64.dp else full - 64.dp - gap, spring(0.8f, 320f), label = "tabs")
        Row {
            TabCapsule(backdrop, root, lastTab, searching, Modifier.width(tabsW), onSelect)
            Spacer(Modifier.width(gap))
            val shape = RoundedCornerShape(32.dp)
            Row(
                Modifier.width(full - tabsW - gap).height(64.dp).bouncyClick { onSelect(Screen.Search) }.glass(backdrop, shape),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, str(R.string.search), tint = if (searching) Accent else Color.White)
                AnimatedVisibility(searching, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text(str(R.string.search), Modifier.padding(start = 8.dp), color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TabCapsule(
    backdrop: Backdrop, root: Screen, lastTab: Screen, searching: Boolean, modifier: Modifier, onSelect: (Screen) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.height(64.dp).glass(backdrop, RoundedCornerShape(32.dp)).padding(6.dp)) {
        if (searching) {
            // collapsed to a single bubble: the tab we came from; tap goes back to it
            val t = TABS.first { it.first == lastTab }
            Box(Modifier.fillMaxSize().bouncyClick { onSelect(lastTab) }, contentAlignment = Alignment.Center) {
                Icon(t.second, t.third, Modifier.size(26.dp), tint = Color.White)
            }
            return@BoxWithConstraints
        }
        val tabW = maxWidth / TABS.size
        val tabPx = with(density) { tabW.toPx() }
        val maxX = tabPx * (TABS.size - 1)
        val selected = TABS.indexOfFirst { it.first == root }.coerceAtLeast(0)
        var dragX by remember { mutableStateOf<Float?>(null) }
        val target = dragX ?: (selected * tabPx)
        val x by animateFloatAsState(target, spring(dampingRatio = 0.62f, stiffness = 380f), label = "drop")
        // liquid feel: the further the drop is from where it is heading, the more it stretches
        val stretch = (kotlin.math.abs(target - x) / tabPx).coerceIn(0f, 1f)
        val held by animateFloatAsState(if (dragX != null) 1f else 0f, spring(0.6f, 500f), label = "held")

        // the drop, under the icons; inset so it never touches the capsule rim, stretches only sideways
        val inset = with(density) { 4.dp.toPx() }
        val grow = with(density) { 14.dp.toPx() } * stretch
        Box(
            Modifier
                .offset { IntOffset((x + inset - grow / 2).roundToInt(), 0) }
                .width(with(density) { (tabPx - 2 * inset + grow).toDp() })
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.13f + 0.05f * held))
                .border(0.5.dp, Color.White.copy(alpha = 0.22f + 0.1f * held), RoundedCornerShape(24.dp))
        )
        Row(
            Modifier.fillMaxSize().pointerInput(tabPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = (it.x - tabPx / 2).coerceIn(0f, maxX) },
                    onDragEnd = {
                        val i = ((dragX ?: 0f) / tabPx).roundToInt().coerceIn(0, TABS.lastIndex)
                        dragX = null
                        onSelect(TABS[i].first)
                    },
                    onDragCancel = { dragX = null },
                ) { change, d -> change.consume(); dragX = ((dragX ?: 0f) + d).coerceIn(0f, maxX) }
            },
        ) {
            TABS.forEachIndexed { i, (screen, icon, label) ->
                // colour follows the drop: full accent when it sits under this tab
                val near = (1f - kotlin.math.abs(x / tabPx - i)).coerceIn(0f, 1f)
                // icon turns accent, label stays readable white (pink text on the light drop was too faint)
                val iconColor = androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.75f), Accent, near)
                val textColor = androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.75f), Color.White, near)
                Column(Modifier.weight(1f).fillMaxHeight().bouncyClick { onSelect(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icon, null, Modifier.size(24.dp), tint = iconColor)
                    Text(label, color = textColor, fontSize = 11.sp, fontWeight = if (near > 0.5f) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}
