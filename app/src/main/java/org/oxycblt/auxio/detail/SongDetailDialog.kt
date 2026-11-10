/*
 * Copyright (c) 2022 Auxio Project
 * SongDetailDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.detail

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSongDetailBinding
import org.oxycblt.auxio.detail.list.SongProperty
import org.oxycblt.auxio.detail.list.SongPropertyAdapter
import org.oxycblt.auxio.list.adapter.UpdateInstructions
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.tag.Name
import timber.log.Timber as L

/**
 * A [ViewBindingMaterialDialogFragment] that shows information about a Song.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class SongDetailDialog : ViewBindingMaterialDialogFragment<DialogSongDetailBinding>() {
    private val detailModel: DetailViewModel by activityViewModels()
    // Information about what song to display is initially within the navigation arguments
    // as a UID, as that is the only safe way to parcel an song.
    private val args: SongDetailDialogArgs by navArgs()
    private val detailAdapter = SongPropertyAdapter()
    private var song: Song? = null

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogSongDetailBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        super.onConfigDialog(builder)
        builder
            .setTitle(R.string.lbl_props)
            .setPositiveButton(R.string.lbl_ok, null)
            // Local customization: edit the song's tags from the properties panel.
            .setNeutralButton(R.string.lbl_edit) { _, _ -> showEditTags() }
    }

    override fun onBindingCreated(binding: DialogSongDetailBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        binding.detailProperties.adapter = detailAdapter
        // DetailViewModel handles most initialization from the navigation argument.
        detailModel.setSong(args.songUid)
        detailModel.toShow.consume()
        collectImmediately(detailModel.currentSong, ::updateSong)
        collectImmediately(detailModel.currentSongProperties, ::updateSongProperties)
    }

    private fun updateSong(song: Song?) {
        if (song == null) {
            L.d("No song to show, navigating away")
            findNavController().navigateUp()
            return
        }
        this.song = song
    }

    private fun showEditTags() {
        val song = song ?: return
        val date =
            song.date?.let { date ->
                buildString {
                    append(date.year)
                    date.month?.let { append("-").append(it.toString().padStart(2, '0')) }
                    date.day?.let { append("-").append(it.toString().padStart(2, '0')) }
                }
            } ?: ""
        TagEditDialog.newInstance(
                uri = song.uri.toString(),
                title = (song.name as? Name.Known)?.raw.orEmpty(),
                artist =
                    song.artists
                        .mapNotNull { (it.name as? Name.Known)?.raw }
                        .joinToString(", "),
                album = (song.album.name as? Name.Known)?.raw.orEmpty(),
                albumArtist =
                    song.album.artists
                        .mapNotNull { (it.name as? Name.Known)?.raw }
                        .joinToString(", "),
                genre =
                    song.genres.mapNotNull { (it.name as? Name.Known)?.raw }.joinToString(", "),
                date = date,
                track = song.track?.toString() ?: "",
                disc = song.disc?.number?.toString() ?: "",
            )
            // NOTE(local): use the parent manager; this dialog is dismissed when the neutral
            // button is pressed, which would also tear down a child-fragment dialog.
            .show(parentFragmentManager, "edit_tags")
    }

    private fun updateSongProperties(songProperties: List<SongProperty>) {
        detailAdapter.update(songProperties, UpdateInstructions.Replace(0))
    }
}
