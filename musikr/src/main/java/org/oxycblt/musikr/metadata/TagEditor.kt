/*
 * Copyright (c) 2026 Auxio Project
 * TagEditor.kt is part of Auxio.
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

package org.oxycblt.musikr.metadata

/**
 * Writes tags to audio files via TagLib.
 *
 * Any field left `null` is preserved. An empty string clears the field.
 *
 * Note: [editTags] is blocking and must be called off the main thread.
 */
object TagEditor {
    /**
     * @param path Absolute filesystem path of the audio file. The caller is responsible for
     *   having write access to it.
     * @return true if the file was saved and is still readable afterwards.
     */
    fun editTags(
        path: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        albumArtist: String? = null,
        genre: String? = null,
        year: String? = null,
        track: String? = null,
        disc: String? = null,
        comment: String? = null,
    ): Boolean =
        TagLibJNI.writeTags(
            path = path,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            year = year,
            track = track,
            disc = disc,
            comment = comment,
        )
}
