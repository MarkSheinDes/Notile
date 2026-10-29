package app.notile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/** One row. With [sub] the menu swaps to that list instead of closing. */
class MenuAction(
    val label: String, val icon: ImageVector, val destructive: Boolean = false, val sub: (() -> ItemMenu)? = null, val onClick: () -> Unit = {},
)

/** What a long-press menu shows: a title line, optional star rating, and the actions under it. */
class ItemMenu(
    val title: String, val subtitle: String?, val actions: List<MenuAction>,
    val rating: (() -> Int)? = null, val onRate: (Int) -> Unit = {},
)

/** Set by the root: shows a menu next to the given on-screen bounds. */
val LocalItemMenu = staticCompositionLocalOf<(ItemMenu, Rect) -> Unit> { { _, _ -> } }

private val Danger = Color(0xFFE5484D)

/** Tap runs [onClick]; press and hold buzzes and opens the glass menu next to the item (built only when it opens). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.menuClick(onClick: () -> Unit, menu: () -> ItemMenu): Modifier {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    val show = LocalItemMenu.current
    return onGloballyPositioned { bounds = it.boundsInWindow() }.combinedClickable(
        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); show(menu(), bounds) },
        onClick = onClick,
    )
}

/** iOS-style context menu: a small glass card that grows out of the pressed item, over a dimmed screen. */
@Composable
fun ItemMenuOverlay(first: ItemMenu, anchor: Rect, backdrop: Backdrop, onDismiss: () -> Unit) {
    var menu by remember(first) { mutableStateOf(first) }
    val noRipple = remember { MutableInteractionSource() }
    val d = LocalDensity.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val t by animateFloatAsState(if (shown) 1f else 0f, spring(0.75f, 500f), label = "menu")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f * t)).clickable(noRipple, null, onClick = onDismiss)) {
        val w = 270.dp
        val screenW = with(d) { maxWidth.toPx() }
        val screenH = with(d) { maxHeight.toPx() }
        val edge = with(d) { 16.dp.toPx() }
        val gap = with(d) { 8.dp.toPx() }
        val menuH = with(d) { (62.dp + (if (menu.rating != null) 44.dp else 0.dp) + 50.dp * minOf(menu.actions.size, 7)).toPx() }
        val below = anchor.bottom + gap + menuH < screenH - with(d) { 110.dp.toPx() } // keep clear of the tab bar
        val x = anchor.left.coerceIn(edge, (screenW - with(d) { w.toPx() } - edge).coerceAtLeast(edge))
        val y = if (below) anchor.bottom + gap else (anchor.top - gap - menuH).coerceAtLeast(with(d) { 48.dp.toPx() })
        Column(
            Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(w)
                .graphicsLayer { scaleX = 0.8f + 0.2f * t; scaleY = scaleX; alpha = t.coerceIn(0f, 1f); transformOrigin = TransformOrigin(0.15f, if (below) 0f else 1f) }
                .glass(backdrop, RoundedCornerShape(22.dp), Color(0xFF15162A).copy(alpha = 0.55f))
                .clickable(noRipple, null) {}, // taps inside don't close it
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(menu.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                menu.subtitle?.let { Text(it, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            menu.rating?.let { rating ->
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.14f))
                val stars = rating()
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (1..5).forEach { n ->
                        Icon(if (n <= stars) Icons.Rounded.Star else Icons.Rounded.StarBorder, "$n", Modifier.size(40.dp).bouncyClick { menu.onRate(n) }.padding(7.dp),
                            tint = if (n <= stars) Accent else Muted)
                    }
                }
            }
            Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                menu.actions.forEach { a ->
                    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.14f))
                    val tint = if (a.destructive) Danger else Color.White
                    Row(Modifier.fillMaxWidth().clickable { if (a.sub != null) menu = a.sub.invoke() else { onDismiss(); a.onClick() } }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(a.label, Modifier.weight(1f), color = tint, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(a.icon, null, Modifier.padding(start = 12.dp), tint = tint)
                    }
                }
            }
        }
    }
}
