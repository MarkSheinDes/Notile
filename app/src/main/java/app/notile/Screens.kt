package app.notile

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.runtime.mutableStateMapOf
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface Screen {
    data object Listen : Screen
    data object Library : Screen
    data object Search : Screen
    data object Albums : Screen
    data object Artists : Screen
    data object Songs : Screen
    data object Folders : Screen
    data class AlbumPage(val id: Long) : Screen
    data class ArtistPage(val name: String) : Screen
    data class FolderPage(val path: String) : Screen
    data object Favorites : Screen
    data object Playlists : Screen
    data class PlaylistPage(val id: Long) : Screen
    data class SmartPage(val id: Long) : Screen
    data class SmartEdit(val id: Long?) : Screen // null = new
}

/** Everything a screen needs: data, playback and navigation. */
class Nav(
    val lib: Library, val hub: PlayerHub, val store: Store, val open: (Screen) -> Unit, val back: () -> Unit,
    val pad: PaddingValues, val toast: (String) -> Unit,
)

@Composable
fun ScreenContent(screen: Screen, nav: Nav) = when (screen) {
    Screen.Listen -> ListenScreen(nav)
    Screen.Library -> LibraryScreen(nav)
    Screen.Search -> SearchScreen(nav)
    Screen.Albums -> AlbumsScreen(nav)
    Screen.Artists -> ArtistsScreen(nav)
    Screen.Songs -> SongsScreen(nav)
    Screen.Folders -> FoldersScreen(nav)
    is Screen.AlbumPage -> AlbumScreen(screen.id, nav)
    is Screen.ArtistPage -> ArtistScreen(screen.name, nav)
    is Screen.FolderPage -> FolderScreen(screen.path, nav)
    Screen.Favorites -> FavoritesScreen(nav)
    Screen.Playlists -> PlaylistsScreen(nav)
    is Screen.PlaylistPage -> PlaylistScreen(screen.id, nav)
    is Screen.SmartPage -> SmartScreen(screen.id, nav)
    is Screen.SmartEdit -> SmartEditScreen(screen.id, nav)
}

/** Scrolling page; the large title scrolls away and a compact one fades in at the top, like iOS. */
@Composable
private fun Page(nav: Nav, title: String?, largeTitle: Boolean = true, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 140 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state, contentPadding = nav.pad) {
            if (title != null && largeTitle) item { LargeTitle(title) }
            content()
        }
        if (title != null) AnimatedVisibility(collapsed, enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }) {
            Box(Modifier.fillMaxWidth().background(Bg.copy(alpha = 0.78f)).statusBarsPadding().height(60.dp),
                contentAlignment = Alignment.Center) {
                Text(title, Modifier.padding(horizontal = 76.dp), color = Color.White, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Albums two per row. */
private fun LazyListScope.albumGrid(albums: List<Album>, nav: Nav) {
    items(albums.chunked(2)) { pair ->
        Row(Modifier.animateItem().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            pair.forEach { a -> AlbumTile(a, Modifier.weight(1f)) { nav.open(Screen.AlbumPage(a.id)) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Songs; tap plays from here, long-press opens the menu, swipe right = play next. */
private fun LazyListScope.songList(songs: List<Song>, nav: Nav, playlistId: Long? = null) {
    itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
        SongRow(s, s.id == nav.hub.currentId, onClick = { nav.hub.play(songs, i) }, modifier = Modifier.animateItem(),
            menu = { songMenu(nav, s, playlistId) }, swipe = playNextSwipe(s, nav))
    }
}

private fun playNextSwipe(s: Song, nav: Nav) =
    Swipe(Icons.Rounded.PlaylistPlay, Accent, 1) { nav.hub.playNext(s); nav.toast(str(R.string.plays_next)) }

@Composable
private fun ListenScreen(nav: Nav) {
    val d = nav.store.data
    val now = remember { System.currentTimeMillis() }
    val mix = remember(d.favorites, d.plays.size, nav.lib.songs.size) { Picks.mixOfTheDay(d, nav.lib, now) }
    val recent = remember(d.plays) { Picks.recent(d, nav.lib) }
    val top = remember(d.plays) { Picks.top(d, nav.lib) }
    val forgotten = remember(d.plays) { Picks.forgotten(d, nav.lib, now) }
    Page(nav, str(R.string.tab_listen)) {
        item { MixCard(mix, nav) }
        if (d.queues.isNotEmpty()) {
            item { SectionTitle(str(R.string.parked_queues)) }
            items(d.queues, key = { "queue${it.id}" }) { q -> QueueRow(q, nav) }
        }
        pinnedShelf(d, nav)
        songShelf(str(R.string.recently_played), recent, nav)
        songShelf(str(R.string.often_played), top, nav)
        songShelf(str(R.string.forgotten), forgotten, nav)
        if (nav.lib.recent.isNotEmpty()) {
            if (d.hideRecent) item {
                Text(str(R.string.show_recent), Modifier.fillMaxWidth().clickable { nav.store.update { it.copy(hideRecent = false) } }.padding(24.dp),
                    color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) { SectionTitle(str(R.string.recently_added)) }
                        Text(str(R.string.hide), Modifier.clickable { nav.store.update { it.copy(hideRecent = true) } }.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
                            color = Muted, fontSize = 15.sp)
                    }
                }
                albumGrid(nav.lib.recent, nav)
            }
        }
    }
}

/** Playlists the user pinned: a shelf of tiles on the Listen screen. */
private fun LazyListScope.pinnedShelf(d: StoreData, nav: Nav) {
    class Tile(val name: String, val count: Int, val cover: Song?, val smart: Boolean, val screen: Screen, val menu: () -> ItemMenu)
    val now = System.currentTimeMillis()
    val tiles = d.pinned.mapNotNull { k ->
        val id = k.drop(1).toLongOrNull() ?: return@mapNotNull null
        if (k[0] == 'p') d.playlists.firstOrNull { it.id == id }?.let { Tile(it.name, it.songs.size, it.songs.firstNotNullOfOrNull(nav.lib::song), false, Screen.PlaylistPage(id)) { playlistMenu(nav, it) } }
        else d.smart.firstOrNull { it.id == id }?.let { Tile(it.name, it.songs(d, nav.lib, now).size, null, true, Screen.SmartPage(id)) { smartMenu(nav, it) } }
    }
    if (tiles.isEmpty()) return
    item { SectionTitle(str(R.string.playlists)) }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(tiles) { t ->
                Column(Modifier.width(140.dp).menuClick({ nav.open(t.screen) }, t.menu)) {
                    if (t.smart) Box(Modifier.size(140.dp).frost(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = Accent)
                    } else Cover(t.cover, Modifier.size(140.dp), 14.dp)
                    Text(t.name, Modifier.padding(top = 6.dp), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(songsCount(t.count), color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun listMenu(nav: Nav, title: String, key: String, songs: List<Song>, edit: (() -> Unit)?, delete: () -> Unit) =
    ItemMenu(title, songsCount(songs.size), listOfNotNull(
        MenuAction(str(R.string.play), Icons.Rounded.PlayArrow) { nav.hub.play(songs) },
        MenuAction(str(R.string.shuffle), Icons.Rounded.Shuffle) { nav.hub.play(songs, shuffled = true) },
        MenuAction(if (key in nav.store.data.pinned) str(R.string.unpin) else str(R.string.pin), Icons.Rounded.PushPin) {
            nav.store.update { it.togglePin(key) }
        },
        edit?.let { MenuAction(str(R.string.edit), Icons.Rounded.Edit, onClick = it) },
        MenuAction(str(R.string.delete), Icons.Rounded.Delete, destructive = true, onClick = delete),
    ))

private fun playlistMenu(nav: Nav, p: Playlist) =
    listMenu(nav, p.name, "p${p.id}", p.songs.mapNotNull(nav.lib::song), null) { nav.store.update { it.deletePlaylist(p.id) } }

private fun smartMenu(nav: Nav, p: SmartPlaylist) =
    listMenu(nav, p.name, "s${p.id}", p.songs(nav.store.data, nav.lib, System.currentTimeMillis()), { nav.open(Screen.SmartEdit(p.id)) }) {
        nav.store.update { it.deleteSmart(p.id) }
    }

private fun queueMenu(nav: Nav, q: SavedQueue) = ItemMenu(q.name, songsCount(q.songs.size), listOf(
    MenuAction(str(R.string.resume), Icons.Rounded.PlayArrow) { nav.hub.resume(q, nav.lib) },
    MenuAction(str(R.string.delete), Icons.Rounded.Delete, destructive = true) { nav.store.update { it.removeQueue(q.id) } },
))

/** A parked queue: tap to continue from where it stopped (the current queue is parked instead), swipe left to forget it. */
@Composable
private fun QueueRow(q: SavedQueue, nav: Nav) {
    Swipeable(Swipe(Icons.Rounded.Delete, Color(0xFFE5484D), -1) { nav.store.update { it.removeQueue(q.id) } }) {
        Row(Modifier.fillMaxWidth().menuClick({ nav.hub.resume(q, nav.lib) }, { queueMenu(nav, q) }).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Cover(nav.lib.song(q.current), Modifier.size(56.dp), 12.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(q.name, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(str(R.string.queue_sub, songsCount(q.songs.size), formatTime(q.posMs)), color = Muted, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.PlayArrow, str(R.string.resume), tint = Accent)
        }
    }
}

/** "Mix of the day": four covers in a frosted card, plays the day's 50 songs. */
@Composable
private fun MixCard(mix: List<Song>, nav: Nav) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().bouncyClick { nav.hub.play(mix) }.frost(shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            mix.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { Cover(it, Modifier.size(52.dp), 8.dp) } }
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(str(R.string.mix_of_day), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(str(R.string.mix_sub, songsCount(mix.size)), color = Muted, fontSize = 13.sp)
        }
        Box(Modifier.size(48.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, str(R.string.play), tint = Color.White)
        }
    }
}

/** Horizontal shelf of song covers; hidden when empty. */
private fun LazyListScope.songShelf(title: String, songs: List<Song>, nav: Nav) {
    if (songs.isEmpty()) return
    item { SectionTitle(title) }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                Column(Modifier.width(140.dp).menuClick({ nav.hub.play(songs, i) }, { songMenu(nav, s, null) })) {
                    Cover(s, Modifier.size(140.dp), 14.dp)
                    Text(s.title, Modifier.padding(top = 6.dp), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(nav: Nav) = Page(nav, str(R.string.tab_library)) {
    item {
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).frost(shape)) {
            val rows = listOf(
                Triple(Icons.AutoMirrored.Rounded.QueueMusic, str(R.string.playlists), Screen.Playlists) to nav.store.data.playlists.size,
                Triple(Icons.Rounded.Favorite, str(R.string.favorites), Screen.Favorites) to nav.store.data.favorites.count { nav.lib.song(it) != null },
                Triple(Icons.Rounded.Person, str(R.string.artists), Screen.Artists) to nav.lib.artists.size,
                Triple(Icons.Rounded.Album, str(R.string.albums), Screen.Albums) to nav.lib.albums.size,
                Triple(Icons.Rounded.MusicNote, str(R.string.songs), Screen.Songs) to nav.lib.songs.size,
                Triple(Icons.Rounded.Folder, str(R.string.folders), Screen.Folders) to nav.lib.folders.size,
            )
            rows.forEachIndexed { i, (r, count) ->
                CategoryRow(r.first, r.second, count) { nav.open(r.third) }
                if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp), 0.5.dp, Color.White.copy(alpha = 0.12f))
            }
        }
    }
    if (nav.lib.recent.isNotEmpty() && !nav.store.data.hideRecent) {
        item { SectionTitle(str(R.string.recently_added)) }
        albumGrid(nav.lib.recent.take(6), nav)
    }
}

@Composable
private fun CategoryRow(icon: ImageVector, title: String, count: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent)
        Text(title, Modifier.weight(1f).padding(start = 16.dp), color = Color.White, fontSize = 17.sp)
        Text("$count", color = Muted, fontSize = 15.sp)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
    }
}

@Composable
private fun AlbumsScreen(nav: Nav) = Page(nav, str(R.string.albums)) {
    item { Spacer(Modifier.height(8.dp)) }
    albumGrid(nav.lib.albums, nav)
}

@Composable
private fun ArtistsScreen(nav: Nav) = Page(nav, str(R.string.artists)) {
    items(nav.lib.artists.entries.toList(), key = { it.key }) { (name, albums) ->
        Row(Modifier.fillMaxWidth().clickable { nav.open(Screen.ArtistPage(name)) }.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Cover(albums.first().cover, Modifier.size(52.dp), 26.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(name, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(albumsCount(albums.size), color = Muted, fontSize = 13.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
        }
    }
}

@Composable
private fun SongsScreen(nav: Nav) = Page(nav, str(R.string.songs)) {
    item { PlayShuffleRow({ nav.hub.play(nav.lib.sortedSongs) }, { nav.hub.play(nav.lib.sortedSongs, shuffled = true) }) }
    songList(nav.lib.sortedSongs, nav)
}

@Composable
private fun FoldersScreen(nav: Nav) = Page(nav, str(R.string.folders)) {
    items(nav.lib.folders.entries.toList(), key = { it.key }) { (path, songs) ->
        Row(Modifier.fillMaxWidth().clickable { nav.open(Screen.FolderPage(path)) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).frost(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Folder, null, tint = Accent)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(path.substringAfterLast('/').ifEmpty { str(R.string.phone_storage) }, color = Color.White, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$path · ${songsCount(songs.size)}", color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AlbumScreen(id: Long, nav: Nav) {
    val album = nav.lib.album(id) ?: return
    Page(nav, album.title, largeTitle = false) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(album.cover, Modifier.size(250.dp), 18.dp, sharedKey = "album-${album.id}")
                Text(album.title, Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp), color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(album.artist, Modifier.clickable { nav.open(Screen.ArtistPage(album.artist)) }.padding(4.dp),
                    color = Accent, fontSize = 18.sp)
                val fmt = album.songs.map { it.format }.filter { it.isNotEmpty() }.distinct().joinToString(" / ")
                Text(listOf(songsCount(album.songs.size), formatTime(album.durationMs), fmt).filter { it.isNotEmpty() }
                    .joinToString(" · "), color = Muted, fontSize = 13.sp)
            }
        }
        item { PlayShuffleRow({ nav.hub.play(album.songs) }, { nav.hub.play(album.songs, shuffled = true) }) }
        itemsIndexed(album.songs, key = { _, s -> s.id }) { i, s ->
            val playing = s.id == nav.hub.currentId
            SongRow(s, playing, { nav.hub.play(album.songs, i) }, menu = { songMenu(nav, s, null) },
                swipe = playNextSwipe(s, nav)) {
                Text(if (playing) "♪" else "${s.trackNo.takeIf { it > 0 } ?: (i + 1)}", Modifier.width(30.dp),
                    color = if (playing) Accent else Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ArtistScreen(name: String, nav: Nav) {
    val albums = nav.lib.artists[name] ?: return
    val songs = albums.flatMap { it.songs }
    Page(nav, name) {
        item { PlayShuffleRow({ nav.hub.play(songs) }, { nav.hub.play(songs, shuffled = true) }) }
        item { SectionTitle(str(R.string.albums)) }
        albumGrid(albums, nav)
    }
}

@Composable
private fun FolderScreen(path: String, nav: Nav) {
    val songs = nav.lib.folders[path] ?: return
    Page(nav, path.substringAfterLast('/').ifEmpty { str(R.string.phone_storage) }) {
        item { PlayShuffleRow({ nav.hub.play(songs) }, { nav.hub.play(songs, shuffled = true) }) }
        songList(songs, nav)
    }
}

@Composable
private fun FavoritesScreen(nav: Nav) {
    val songs = nav.store.data.favorites.mapNotNull(nav.lib::song)
    Page(nav, str(R.string.favorites)) {
        if (songs.isEmpty()) item { Hint(str(R.string.favorites_hint)) }
        else {
            item { PlayShuffleRow({ nav.hub.play(songs) }, { nav.hub.play(songs, shuffled = true) }) }
            songList(songs, nav)
        }
    }
}

@Composable
private fun PlaylistsScreen(nav: Nav) = Page(nav, str(R.string.playlists)) {
    item { NewPlaylistField(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { nav.store.update { s -> s.createPlaylist(it, emptyList()) } } }
    item { SectionTitle(str(R.string.smart)) }
    items(nav.store.data.smart, key = { "smart${it.id}" }) { p ->
        val d = nav.store.data
        val count = remember(p, d.plays, d.favorites) { p.songs(d, nav.lib, System.currentTimeMillis()).size }
        Row(Modifier.animateItem().fillMaxWidth().menuClick({ nav.open(Screen.SmartPage(p.id)) }, { smartMenu(nav, p) }).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).frost(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Accent)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(p.name, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(songsCount(count), color = Muted, fontSize = 13.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
        }
    }
    item { PillButton(str(R.string.new_smart), Icons.Rounded.AutoAwesome, Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()) { nav.open(Screen.SmartEdit(null)) } }
    if (nav.store.data.playlists.isNotEmpty()) item { SectionTitle(str(R.string.mine)) }
    items(nav.store.data.playlists, key = { it.id }) { p ->
        Row(Modifier.animateItem().fillMaxWidth().menuClick({ nav.open(Screen.PlaylistPage(p.id)) }, { playlistMenu(nav, p) }).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Cover(p.songs.firstNotNullOfOrNull(nav.lib::song), Modifier.size(56.dp), 12.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(p.name, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(songsCount(p.songs.size), color = Muted, fontSize = 13.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
        }
    }
}

@Composable
private fun PlaylistScreen(id: Long, nav: Nav) {
    val p = nav.store.data.playlists.firstOrNull { it.id == id } ?: return
    val songs = p.songs.mapNotNull(nav.lib::song)
    Page(nav, p.name) {
        if (songs.isEmpty()) item { Hint(str(R.string.playlist_empty_hint)) }
        else {
            item { PlayShuffleRow({ nav.hub.play(songs) }, { nav.hub.play(songs, shuffled = true) }) }
            songList(songs, nav, playlistId = p.id)
        }
    }
}

@Composable
private fun SmartScreen(id: Long, nav: Nav) {
    val d = nav.store.data
    val p = d.smart.firstOrNull { it.id == id } ?: return
    val songs = remember(p, d.plays, d.favorites) { p.songs(d, nav.lib, System.currentTimeMillis()) }
    Page(nav, p.name) {
        item { Text(p.rules.joinToString(" · ") { it.describe() }, Modifier.padding(horizontal = 20.dp), color = Muted, fontSize = 14.sp) }
        if (songs.isEmpty()) item { Hint(str(R.string.smart_empty)) }
        else {
            item { PlayShuffleRow({ nav.hub.play(songs) }, { nav.hub.play(songs, shuffled = true) }) }
            songList(songs, nav)
        }
    }
}

/** Name plus a switch per rule; rules with a number get a slider. Shows live how many songs match. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SmartEditScreen(id: Long?, nav: Nav) {
    val old = nav.store.data.smart.firstOrNull { it.id == id }
    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    val on = remember { mutableStateMapOf<RuleKind, Int>().apply { old?.rules?.forEach { put(it.kind, it.n) } } }
    var genre by rememberSaveable { mutableStateOf(old?.rules?.firstOrNull { it.kind == RuleKind.GENRE }?.text ?: "") }
    val rules = RuleKind.entries.mapNotNull { k -> on[k]?.let { Rule(k, it, if (k == RuleKind.GENRE) genre else "") } }
        .filter { it.kind != RuleKind.GENRE || genre.isNotBlank() }
    val d = nav.store.data
    val count = remember(rules, d.plays, d.favorites) { SmartPlaylist(0, "", rules).songs(d, nav.lib, System.currentTimeMillis()).size }
    Page(nav, if (old == null) str(R.string.new_smart) else str(R.string.edit)) {
        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().frost(RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 14.dp)) {
                if (name.isEmpty()) Text(str(R.string.name), color = Muted, fontSize = 16.sp)
                BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Accent))
            }
        }
        items(RuleKind.entries.filter { it != RuleKind.GENRE || nav.lib.genres.isNotEmpty() }, key = { it.name }) { k ->
            val n = on[k]
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(str(k.label), Modifier.weight(1f), color = Color.White, fontSize = 16.sp)
                    GlassToggle(n != null, { if (it) { on[k] = k.default; if (k == RuleKind.GENRE && genre.isBlank()) genre = nav.lib.genres.first().first } else on.remove(k) })
                }
                if (n != null && k.unit != null) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassSlider(n.toFloat(), { on[k] = it.roundToInt() }, k.range.first.toFloat()..k.range.last.toFloat(),
                        Modifier.weight(1f), step = 1f)
                    Text(if (k == RuleKind.PLAYS_MAX && n == 0) str(R.string.never) else "$n ${str(k.unit)}", Modifier.width(84.dp).padding(start = 12.dp),
                        color = Muted, fontSize = 14.sp, textAlign = TextAlign.End)
                }
                if (n != null && k == RuleKind.GENRE) FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    nav.lib.genres.take(40).forEach { (g, count) ->
                        val sel = g.equals(genre, ignoreCase = true)
                        Text("$g · $count", Modifier.bouncyClick { genre = g }.frost(RoundedCornerShape(50))
                            .background(if (sel) Accent.copy(alpha = 0.85f) else Color.Transparent, RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
        item { Hint(if (rules.isEmpty()) str(R.string.pick_rule) else str(R.string.matches, songsCount(count))) }
        item {
            PillButton(str(R.string.save), Icons.Rounded.Check, Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                if (rules.isEmpty()) nav.toast(str(R.string.pick_rule))
                else { nav.store.update { it.saveSmart(id, name, rules) }; nav.back() }
            }
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, Modifier.fillMaxWidth().padding(32.dp), color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center)

@Composable
private fun SearchScreen(nav: Nav) {
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val (songs, albums, artists) = remember(q) { nav.lib.search(q) }
    Page(nav, str(R.string.search)) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).frost(RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Muted)
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (q.isEmpty()) Text(str(R.string.search_hint), color = Muted, fontSize = 17.sp)
                    BasicTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp), cursorBrush = SolidColor(Accent))
                }
            }
        }
        if (artists.isNotEmpty()) {
            item { SectionTitle(str(R.string.artists)) }
            items(artists) { a ->
                Row(Modifier.fillMaxWidth().clickable { nav.open(Screen.ArtistPage(a)) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(Color.White.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, null, tint = Muted)
                    }
                    Text(a, Modifier.padding(start = 12.dp), color = Color.White, fontSize = 16.sp)
                }
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionTitle(str(R.string.albums)) }
            albumGrid(albums, nav)
        }
        if (songs.isNotEmpty()) {
            item { SectionTitle(str(R.string.songs)) }
            songList(songs, nav)
        }
        if (q.isNotBlank() && songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            item { Text(str(R.string.nothing_found), Modifier.fillMaxWidth().padding(32.dp), color = Muted, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun PermissionScreen(onAsk: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp).frost(RoundedCornerShape(28.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(48.dp), tint = Accent)
            Text(str(R.string.music_access), Modifier.padding(top = 14.dp), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(str(R.string.music_access_body), Modifier.padding(vertical = 10.dp),
                color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
            PillButton(str(R.string.allow), Icons.Rounded.MusicNote, Modifier.fillMaxWidth(), onAsk)
        }
    }
}

fun songsCount(n: Int) = "$n " + str(listOf(R.string.song_one, R.string.song_few, R.string.song_many)[pluralForm(n, appLanguage())])
fun albumsCount(n: Int) = "$n " + str(listOf(R.string.album_one, R.string.album_few, R.string.album_many)[pluralForm(n, appLanguage())])

private fun appLanguage() = App.ctx?.resources?.configuration?.locales?.get(0)?.language ?: "en"

/** Which word form a count takes: 0 = one, 1 = few, 2 = many. Russian: 1 / 2-4 / 5+ (11-14 are "many"); other languages: 1 / other. */
fun pluralForm(n: Int, lang: String): Int = when {
    lang != "ru" -> if (n == 1) 0 else 2
    n % 100 in 11..14 -> 2
    n % 10 == 1 -> 0
    n % 10 in 2..4 -> 1
    else -> 2
}
