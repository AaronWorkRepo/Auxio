/*
 * Copyright (c) 2021 Auxio Project
 * RootPreferenceFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicSettings
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.locations.LocationMode
import org.oxycblt.auxio.settings.ui.WrappedDialogPreference
import org.oxycblt.auxio.util.navigateSafe
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.fs.Location
import org.oxycblt.musikr.fs.Volume
import org.oxycblt.musikr.fs.mediastore.MediaStore
import timber.log.Timber as L

/**
 * The [PreferenceFragmentCompat] that displays the root settings list.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class RootPreferenceFragment : BasePreferenceFragment(R.xml.preferences_root) {
    private val musicModel: MusicViewModel by activityViewModels()
    @Inject lateinit var musicSettings: MusicSettings
    private var folderPicker: ActivityResultLauncher<Uri?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        folderPicker =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                setLibraryFolder(uri)
            }

        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onResume() {
        super.onResume()
        updateLibraryFolderSummary()
    }

    // Local customization: point the library at a single folder picked by the user. This keeps
    // MediaStore as the backend, so tag editing still works.
    private fun setLibraryFolder(uri: Uri?) {
        uri ?: return
        val context = requireContext()
        val location = Location.Unopened.from(context, uri)
        if (location.path.volume is Volume.ThirdParty) {
            context.showToast(R.string.err_bad_location)
            return
        }
        val current = musicSettings.mediaStoreQuery
        musicSettings.mediaStoreQuery =
            MediaStore.Query(
                mode = MediaStore.FilterMode.INCLUDE,
                filtered = listOf(location),
                excludeNonMusic = current.excludeNonMusic,
                excludedUris = musicSettings.hiddenUris,
            )
        musicSettings.locationMode = LocationMode.MEDIA_STORE
        // A full rescan (withCache=false) already rolls the library revision.
        musicModel.rescan()
        context.showToast(R.string.msg_library_folder_set)
        updateLibraryFolderSummary()
    }

    private fun updateLibraryFolderSummary() {
        val preference =
            findPreference<Preference>(getString(R.string.set_key_library_folder)) ?: return
        preference.summary =
            when {
                musicSettings.locationMode == LocationMode.SAF ->
                    getString(R.string.set_library_folder_saf)
                musicSettings.mediaStoreQuery.mode == MediaStore.FilterMode.INCLUDE ->
                    getString(R.string.set_library_folder_include)
                else -> getString(R.string.set_library_folder_desc)
            }
    }

    override fun onOpenDialogPreference(preference: WrappedDialogPreference) {
        when (preference.key) {
            getString(R.string.set_key_music_dirs) -> {
                findNavController()
                    .navigateSafe(RootPreferenceFragmentDirections.musicLocationsSettings())
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        // Hook generic preferences to their specified preferences
        // TODO: These seem like good things to put into a side navigation view, if I choose to
        //  do one.
        when (preference.key) {
            getString(R.string.set_key_ui) -> {
                L.d("Navigating to UI preferences")
                findNavController().navigateSafe(RootPreferenceFragmentDirections.uiPreferences())
            }
            getString(R.string.set_key_personalize) -> {
                L.d("Navigating to personalization preferences")
                findNavController()
                    .navigateSafe(RootPreferenceFragmentDirections.personalizePreferences())
            }
            getString(R.string.set_key_music) -> {
                L.d("Navigating to music preferences")
                findNavController()
                    .navigateSafe(RootPreferenceFragmentDirections.musicPreferences())
            }
            getString(R.string.set_key_audio) -> {
                L.d("Navigating to audio preferences")
                findNavController().navigateSafe(RootPreferenceFragmentDirections.audioPeferences())
            }
            getString(R.string.set_key_library_folder) -> {
                folderPicker?.launch(null)
            }
            getString(R.string.set_key_reindex) -> musicModel.refresh()
            getString(R.string.set_key_rescan) -> musicModel.rescan()
            else -> return super.onPreferenceTreeClick(preference)
        }

        return true
    }
}
