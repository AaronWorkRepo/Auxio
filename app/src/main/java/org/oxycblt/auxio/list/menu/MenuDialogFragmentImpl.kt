/*
 * Copyright (c) 2023 Auxio Project
 * MenuDialogFragmentImpl.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.list.menu

import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlin.concurrent.thread
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogMenuBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.list.ListViewModel
import org.oxycblt.auxio.music.MusicSettings
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.playback.formatDurationMs
import org.oxycblt.auxio.util.getPlural
import org.oxycblt.auxio.util.resolveAudioPath
import org.oxycblt.auxio.util.share
import org.oxycblt.auxio.util.showToast
import timber.log.Timber as L
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song

/**
 * [MenuDialogFragment] implementation for a [Song].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class SongMenuDialogFragment : MenuDialogFragment<Menu.ForSong>() {
    override val menuModel: MenuViewModel by activityViewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    @Inject lateinit var musicSettings: MusicSettings
    private val args: SongMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    // Nothing to disable in song menus.
    override fun getDisabledItemIds(menu: Menu.ForSong) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForSong) {
        val context = requireContext()
        binding.menuCover.bind(menu.song)
        binding.menuType.text = getString(R.string.lbl_song)
        binding.menuName.text = menu.song.name.resolve(context)
        binding.menuInfo.text = menu.song.artists.resolveNames(context)
    }

    override fun onClick(item: MenuItem, menu: Menu.ForSong) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.playExplicit(menu.song, menu.playWith)
            R.id.action_shuffle -> playbackModel.shuffleExplicit(menu.song, menu.playWith)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.song)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.song)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.song)
            R.id.action_artist_details -> detailModel.showArtist(menu.song)
            R.id.action_album_details -> detailModel.showAlbum(menu.song.album)
            R.id.action_share -> requireContext().share(menu.song)
            R.id.action_detail -> detailModel.showSong(menu.song)
            R.id.action_remove_from_library -> confirmRemoveFromLibrary(menu.song)
            else -> error("Unexpected menu item selected $item")
        }
    }

    // Local customization: hide non-music audio from the library, optionally deleting the file.
    private fun confirmRemoveFromLibrary(song: Song) {
        val context = requireContext()
        // The menu bottom sheet is detached as soon as the item is clicked, so resolve everything
        // that needs the activity/fragment up-front. The confirmation dialog outlives it.
        val appContext = context.applicationContext
        val viewModel = musicModel
        val hiddenUris = musicSettings.hiddenUris
        val checkbox =
            CheckBox(context).apply {
                setText(R.string.lbl_also_delete_file)
                val padding = resources.getDimensionPixelSize(R.dimen.spacing_mid_large)
                setPadding(padding, 0, padding, 0)
            }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lbl_remove_from_library)
            .setMessage(
                context.getString(
                    R.string.lbl_remove_from_library_msg,
                    song.name.resolve(context),
                )
            )
            .setView(checkbox)
            .setPositiveButton(R.string.lbl_remove) { _, _ ->
                removeFromLibrary(song, checkbox.isChecked, appContext, viewModel, hiddenUris)
            }
            .setNegativeButton(R.string.lbl_cancel, null)
            .show()
    }

    private fun removeFromLibrary(
        song: Song,
        deleteFile: Boolean,
        appContext: android.content.Context,
        viewModel: MusicViewModel,
        hiddenUris: Set<String>,
    ) {
        val uri = song.uri
        musicSettings.hiddenUris = hiddenUris + uri.toString()
        Toast.makeText(appContext, R.string.lng_removed_from_library, Toast.LENGTH_SHORT).show()
        viewModel.refresh()

        if (!deleteFile) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(appContext, R.string.err_need_all_files, Toast.LENGTH_LONG).show()
            return
        }

        thread(name = "DeleteSong") {
            var success = false
            try {
                success = appContext.contentResolver.delete(uri, null, null) > 0
            } catch (e: Exception) {
                L.w(e, "MediaStore delete failed")
            }
            if (!success) {
                try {
                    val path = resolveAudioPath(appContext, uri)
                    if (path != null) {
                        success = File(path).delete()
                        if (success) {
                            MediaScannerConnection.scanFile(appContext, arrayOf(path), null, null)
                        }
                    }
                } catch (e: Exception) {
                    L.w(e, "Direct file delete failed")
                }
            }
            if (!success) {
                Handler(Looper.getMainLooper())
                    .post {
                        Toast.makeText(appContext, R.string.err_delete_failed, Toast.LENGTH_SHORT)
                            .show()
                    }
            }
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [AlbumMenuDialogFragment].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class AlbumMenuDialogFragment : MenuDialogFragment<Menu.ForAlbum>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: AlbumMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    // Nothing to disable in album menus.
    override fun getDisabledItemIds(menu: Menu.ForAlbum) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForAlbum) {
        val context = requireContext()
        binding.menuCover.bind(menu.album)
        binding.menuType.text = menu.album.releaseType.resolve(context)
        binding.menuName.text = menu.album.name.resolve(context)
        binding.menuInfo.text = menu.album.artists.resolveNames(context)
    }

    override fun onClick(item: MenuItem, menu: Menu.ForAlbum) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.album)
            R.id.action_shuffle -> playbackModel.shuffle(menu.album)
            R.id.action_detail -> detailModel.showAlbum(menu.album)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.album)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.album)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_artist_details -> detailModel.showArtist(menu.album)
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.album)
            R.id.action_share -> requireContext().share(menu.album)
            else -> error("Unexpected menu item selected $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Artist].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class ArtistMenuDialogFragment : MenuDialogFragment<Menu.ForArtist>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: ArtistMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForArtist) =
        if (menu.artist.songs.isEmpty()) {
            // Disable any operations that require some kind of songs to work with, as there won't
            // be any in an empty artist.
            setOf(
                R.id.action_play,
                R.id.action_shuffle,
                R.id.action_play_next,
                R.id.action_queue_add,
                R.id.action_playlist_add,
                R.id.action_share,
            )
        } else {
            setOf()
        }

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForArtist) {
        val context = requireContext()
        binding.menuCover.bind(menu.artist)
        binding.menuType.text = getString(R.string.lbl_artist)
        binding.menuName.text = menu.artist.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                if (menu.artist.explicitAlbums.isNotEmpty()) {
                    context.getPlural(R.plurals.fmt_album_count, menu.artist.explicitAlbums.size)
                } else {
                    context.getString(R.string.def_album_count)
                },
                if (menu.artist.songs.isNotEmpty()) {
                    context.getPlural(R.plurals.fmt_song_count, menu.artist.songs.size)
                } else {
                    getString(R.string.def_song_count)
                },
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForArtist) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.artist)
            R.id.action_shuffle -> playbackModel.shuffle(menu.artist)
            R.id.action_detail -> detailModel.showArtist(menu.artist)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.artist)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.artist)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.artist)
            R.id.action_share -> requireContext().share(menu.artist)
            else -> error("Unexpected menu item $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Genre].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class GenreMenuDialogFragment : MenuDialogFragment<Menu.ForGenre>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: GenreMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForGenre) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForGenre) {
        val context = requireContext()
        binding.menuCover.bind(menu.genre)
        binding.menuType.text = getString(R.string.lbl_genre)
        binding.menuName.text = menu.genre.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                context.getPlural(R.plurals.fmt_artist_count, menu.genre.artists.size),
                context.getPlural(R.plurals.fmt_song_count, menu.genre.songs.size),
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForGenre) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.genre)
            R.id.action_shuffle -> playbackModel.shuffle(menu.genre)
            R.id.action_detail -> detailModel.showGenre(menu.genre)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.genre)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.genre)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.genre)
            R.id.action_share -> requireContext().share(menu.genre)
            else -> error("Unexpected menu item $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Playlist].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class PlaylistMenuDialogFragment : MenuDialogFragment<Menu.ForPlaylist>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: PlaylistMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForPlaylist) =
        if (menu.playlist.songs.isEmpty()) {
            // Disable any operations that require some kind of songs to work with, as there won't
            // be any in an empty playlist.
            setOf(
                R.id.action_play,
                R.id.action_shuffle,
                R.id.action_play_next,
                R.id.action_queue_add,
                R.id.action_playlist_add,
                R.id.action_export,
                R.id.action_share,
            )
        } else {
            setOf()
        }

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForPlaylist) {
        val context = requireContext()
        binding.menuCover.bind(menu.playlist)
        binding.menuType.text = getString(R.string.lbl_playlist)
        binding.menuName.text = menu.playlist.name.resolve(context)
        binding.menuInfo.text =
            if (menu.playlist.songs.isNotEmpty()) {
                context.getPlural(R.plurals.fmt_song_count, menu.playlist.songs.size)
            } else {
                getString(R.string.def_song_count)
            }
    }

    override fun onClick(item: MenuItem, menu: Menu.ForPlaylist) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.playlist)
            R.id.action_shuffle -> playbackModel.shuffle(menu.playlist)
            R.id.action_detail -> detailModel.showPlaylist(menu.playlist)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.playlist)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.playlist)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_rename -> musicModel.renamePlaylist(menu.playlist)
            R.id.action_import -> musicModel.importPlaylist(target = menu.playlist)
            R.id.action_export -> musicModel.exportPlaylist(menu.playlist)
            R.id.action_delete -> musicModel.deletePlaylist(menu.playlist)
            R.id.action_share -> requireContext().share(menu.playlist)
            else -> error("Unexpected menu item $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Song] selection.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class SelectionMenuDialogFragment : MenuDialogFragment<Menu.ForSelection>() {
    override val menuModel: MenuViewModel by activityViewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: SelectionMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    // Nothing to disable in song menus.
    override fun getDisabledItemIds(menu: Menu.ForSelection) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForSelection) {
        binding.menuCover.bind(
            menu.songs,
            getString(R.string.desc_selection_image),
            R.drawable.ic_song_24,
        )
        binding.menuType.text = getString(R.string.lbl_selection)
        binding.menuName.text =
            requireContext().getPlural(R.plurals.fmt_song_count, menu.songs.size)
        binding.menuInfo.text = menu.songs.sumOf { it.durationMs }.formatDurationMs(true)
    }

    override fun onClick(item: MenuItem, menu: Menu.ForSelection) {
        listModel.dropSelection()
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.songs)
            R.id.action_shuffle -> playbackModel.shuffle(menu.songs)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.songs)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.songs)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.songs)
            R.id.action_share -> requireContext().share(menu.songs)
            else -> error("Unexpected menu item selected $item")
        }
    }
}
