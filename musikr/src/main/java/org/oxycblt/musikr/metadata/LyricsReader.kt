/*
 * Copyright (c) 2026 Auxio Project
 * LyricsReader.kt is part of Auxio.
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

/**
 * Reads lyrics embedded in audio tags.
 *
 * Supports ID3v2 synchronized lyrics (SYLT) and unsynchronized lyrics (USLT / LYRICS property for
 * MP4, Vorbis and APE). [read] is blocking and must be called off the main thread.
 */
object LyricsReader {
    data class Line(val timeMs: Long?, val text: String)

    data class Lyrics(val lines: List<Line>) {
        /** Whether the lines carry timestamps and can be highlighted during playback. */
        val isSynced = lines.any { it.timeMs != null }
    }

    private const val TIME_SEPARATOR = '\u0001'

    /**
     * @param displayName File name used to help format detection (not opened itself).
     * @param fis Stream of the audio file. The caller owns and closes it.
     * @return parsed lyrics, or null when the file has none.
     */
    fun read(displayName: String, fis: FileInputStream): Lyrics? {
        val raw = TagLibJNI.readLyrics(displayName, fis) ?: return null
        val lines =
            raw.mapNotNull { entry ->
                val separator = entry.indexOf(TIME_SEPARATOR)
                if (separator > 0) {
                    val time = entry.substring(0, separator).toLongOrNull()
                        ?: return@mapNotNull null
                    Line(time, entry.substring(separator + 1))
                } else {
                    Line(null, entry)
                }
            }
                .filter { it.text.isNotBlank() }
        if (lines.isEmpty()) {
            return null
        }
        val synced = lines.any { it.timeMs != null }
        return Lyrics(if (synced) lines.sortedBy { it.timeMs } else lines)
    }
}
