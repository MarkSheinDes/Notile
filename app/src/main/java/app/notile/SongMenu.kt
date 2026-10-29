package app.notile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

/** Long-press menu for a song (shown by [ItemMenuOverlay]): stars, queue actions, playlists, favorites, navigation. */
fun songMenu(nav: Nav, song: Song, playlistId: Long?): ItemMenu {
    val store = nav.store
    fun toPlaylist() = ItemMenu(str(R.string.add_to_playlist_title), song.title, listOf(
        MenuAction(str(R.string.new_playlist), Icons.Rounded.Add) { store.update { it.createPlaylist("", listOf(song.id)) }; nav.toast(str(R.string.playlist_created)) },
    ) + store.data.playlists.map { p ->
        MenuAction(p.name, Icons.AutoMirrored.Rounded.QueueMusic) {
            store.update { it.addToPlaylist(p.id, listOf(song.id)) }; nav.toast(str(R.string.added_to, p.name))
        }
    })
    return ItemMenu(song.title, song.artist, listOfNotNull(
        MenuAction(str(R.string.play_next), Icons.Rounded.PlaylistPlay) { nav.hub.playNext(song); nav.toast(str(R.string.plays_next)) },
        MenuAction(str(R.string.add_to_queue), Icons.AutoMirrored.Rounded.QueueMusic) { nav.hub.addToQueue(song); nav.toast(str(R.string.added_to_queue)) },
        MenuAction(str(R.string.add_to_playlist), Icons.AutoMirrored.Rounded.PlaylistAdd, sub = ::toPlaylist),
        if (playlistId != null) MenuAction(str(R.string.remove_from_playlist), Icons.Rounded.RemoveCircleOutline) {
            store.update { it.removeFromPlaylist(playlistId, song.id) }
        } else null,
        MenuAction(if (store.data.isFavorite(song.id)) str(R.string.unfavorite) else str(R.string.favorite),
            if (store.data.isFavorite(song.id)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder) { store.update { it.toggleFavorite(song.id) } },
        MenuAction(str(R.string.go_album), Icons.Rounded.Album) { nav.open(Screen.AlbumPage(song.albumId)) },
        MenuAction(str(R.string.go_artist), Icons.Rounded.Person) {
            // artist pages are keyed by album artist, which can differ from the track artist
            val artist = if (song.artist in nav.lib.artists) song.artist else nav.lib.album(song.albumId)?.artist
            artist?.let { nav.open(Screen.ArtistPage(it)) }
        },
    ), rating = { store.data.ratings[song.id] ?: 0 }, onRate = { n -> store.update { it.rate(song.id, n) } })
}

@Composable
fun HeartButton(store: Store, id: Long, size: Int = 26) {
    val fav = store.data.isFavorite(id)
    Box(Modifier.size((size + 18).dp).bouncyClick { store.update { it.toggleFavorite(id) } }, contentAlignment = Alignment.Center) {
        Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) str(R.string.unfavorite) else str(R.string.favorite),
            Modifier.size(size.dp), tint = if (fav) Accent else Color.White)
    }
}

/** "New playlist" capsule: type a name, press Enter or "+". */
@Composable
fun NewPlaylistField(modifier: Modifier = Modifier, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(modifier.fillMaxWidth().frost(RoundedCornerShape(50)).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (name.isEmpty()) Text(str(R.string.new_playlist), color = Muted, fontSize = 16.sp)
            BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCreate(name); name = "" }))
        }
        Box(Modifier.size(40.dp).bouncyClick { onCreate(name); name = "" }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, str(R.string.create), tint = Accent)
        }
    }
}
