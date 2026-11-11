/*
 * Copyright (c) 2024 Auxio Project
 * TagLibJNI.kt is part of Auxio.
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

import java.io.FileInputStream
import org.oxycblt.musikr.fs.File

internal object TagLibJNI {
    init {
        System.loadLibrary("tagJNI")
    }

    /**
     * Open a file and extract a tag.
     *
     * Note: This method is blocking and should be handled as such if calling from a coroutine.
     */
    fun open(deviceFile: File, fis: FileInputStream): MetadataResult {
        val inputStream = NativeInputStream(deviceFile, fis)
        val tag = openNative(inputStream)
        inputStream.close()
        return tag
    }

    private external fun openNative(inputStream: NativeInputStream): MetadataResult

    /**
     * Read embedded lyrics. Timed lines are "timeMs\u0001text", unsynchronized lines are plain.
     *
     * Note: This method is blocking and should be called off the main thread.
     */
    fun readLyrics(displayName: String, fis: FileInputStream): Array<String>? {
        val stream = NativeInputStream(fis, displayName)
        return readLyricsNative(stream)
    }

    private external fun readLyricsNative(inputStream: NativeInputStream): Array<String>?

    /**
     * Write tags to a file. Null values leave a field untouched, empty strings remove it.
     *
     * Note: This method is blocking and should be called off the main thread.
     */
    fun writeTags(
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
        writeNative(path, title, artist, album, albumArtist, genre, year, track, disc, comment)

    private external fun writeNative(
        path: String,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
        genre: String?,
        year: String?,
        track: String?,
        disc: String?,
        comment: String?,
    ): Boolean
}
