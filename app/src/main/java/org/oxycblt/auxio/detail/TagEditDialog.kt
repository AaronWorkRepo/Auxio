/*
 * Copyright (c) 2026 Auxio Project
 * TagEditDialog.kt is part of Auxio.
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

import android.app.Dialog
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.concurrent.thread
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogEditTagsBinding
import org.oxycblt.auxio.music.MusicSettings
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.locations.LocationMode
import org.oxycblt.auxio.util.resolveAudioPath
import org.oxycblt.musikr.metadata.TagEditor

/**
 * Edits the tags of a single audio file. Launched from [SongDetailDialog].
 *
 * Empty fields clear the corresponding tag; fields the user did not touch are still written with
 * their current value (or cleared if they were unknown).
 */
@AndroidEntryPoint
class TagEditDialog : DialogFragment() {
    private val musicModel: MusicViewModel by activityViewModels()
    @Inject lateinit var musicSettings: MusicSettings
    private lateinit var binding: DialogEditTagsBinding
    // Resolved up-front: the dialog can outlive the fragment/activity lifecycle states in which
    // requireContext()/requireArguments() would throw.
    private lateinit var appContext: android.content.Context
    private lateinit var uriString: String

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        appContext = context.applicationContext
        uriString = requireArguments().getString(ARG_URI).orEmpty()
        binding = DialogEditTagsBinding.inflate(layoutInflater)
        val args = requireArguments()

        setText(binding.editTitle, args.getString(ARG_TITLE))
        setText(binding.editArtist, args.getString(ARG_ARTIST))
        setText(binding.editAlbum, args.getString(ARG_ALBUM))
        setText(binding.editAlbumArtist, args.getString(ARG_ALBUM_ARTIST))
        setText(binding.editGenre, args.getString(ARG_GENRE))
        setText(binding.editDate, args.getString(ARG_DATE))
        setText(binding.editTrack, args.getString(ARG_TRACK))
        setText(binding.editDisc, args.getString(ARG_DISC))

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lbl_edit_tags)
            .setView(binding.root)
            .setPositiveButton(R.string.lbl_save) { _, _ -> save() }
            .setNegativeButton(R.string.lbl_cancel, null)
            .create()
    }

    private fun save() {
        val context = appContext
        // Writing tags needs raw file access; without it TagLib can open but not save.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(context, R.string.err_need_all_files, Toast.LENGTH_LONG).show()
            return
        }
        val uri = Uri.parse(uriString)
        val path = resolveAudioPath(context, uri)
        if (path == null) {
            Log.w("TagEditDialog", "No filesystem path for $uri")
            Toast.makeText(context, R.string.err_no_edit_path, Toast.LENGTH_LONG).show()
            return
        }
        Log.i("TagEditDialog", "Editing $path")
        // Resolve the activity-scoped ViewModel while the dialog is still attached; the save
        // callback runs after dismissal.
        val viewModel = musicModel
        // A full SAF reindex takes several seconds; only refresh automatically for the fast
        // MediaStore backend.
        val autoRefresh = musicSettings.locationMode == LocationMode.MEDIA_STORE

        // Only write fields the user actually changed, so untouched tags (which Auxio may have
        // decoded with a lossy charset) are never rewritten.
        val title = changed(binding.editTitle, ARG_TITLE)
        val artist = changed(binding.editArtist, ARG_ARTIST)
        val album = changed(binding.editAlbum, ARG_ALBUM)
        val albumArtist = changed(binding.editAlbumArtist, ARG_ALBUM_ARTIST)
        val genre = changed(binding.editGenre, ARG_GENRE)
        val date = changed(binding.editDate, ARG_DATE)
        val track = changed(binding.editTrack, ARG_TRACK)
        val disc = changed(binding.editDisc, ARG_DISC)

        thread(name = "TagEdit") {
            val success =
                try {
                    TagEditor.editTags(
                        path = path,
                        title = title,
                        artist = artist,
                        album = album,
                        albumArtist = albumArtist,
                        genre = genre,
                        year = date,
                        track = track,
                        disc = disc,
                    )
                } catch (t: Throwable) {
                    false
                }
            if (success) {
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
            Handler(Looper.getMainLooper())
                .post {
                    if (success && autoRefresh) {
                        // Reload the library so the new tags are reflected in the UI.
                        viewModel.refresh()
                    }
                    val message =
                        when {
                            !success -> R.string.err_tags_failed
                            autoRefresh -> R.string.msg_tags_saved
                            else -> R.string.msg_tags_saved_rescan
                        }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setText(edit: TextInputEditText, value: String?) {
        edit.setText(value.orEmpty())
    }

    private fun text(edit: TextInputEditText) = edit.text?.toString()?.trim().orEmpty()

    private fun changed(edit: TextInputEditText, argKey: String): String? {
        val original = arguments?.getString(argKey).orEmpty()
        val current = text(edit)
        return if (current == original) null else current
    }

    companion object {
        private const val ARG_URI = "uri"
        private const val ARG_TITLE = "title"
        private const val ARG_ARTIST = "artist"
        private const val ARG_ALBUM = "album"
        private const val ARG_ALBUM_ARTIST = "album_artist"
        private const val ARG_GENRE = "genre"
        private const val ARG_DATE = "date"
        private const val ARG_TRACK = "track"
        private const val ARG_DISC = "disc"

        fun newInstance(
            uri: String,
            title: String,
            artist: String,
            album: String,
            albumArtist: String,
            genre: String,
            date: String,
            track: String,
            disc: String,
        ) =
            TagEditDialog()
                .apply {
                    arguments =
                        bundleOf(
                            ARG_URI to uri,
                            ARG_TITLE to title,
                            ARG_ARTIST to artist,
                            ARG_ALBUM to album,
                            ARG_ALBUM_ARTIST to albumArtist,
                            ARG_GENRE to genre,
                            ARG_DATE to date,
                            ARG_TRACK to track,
                            ARG_DISC to disc,
                        )
                }
    }
}
