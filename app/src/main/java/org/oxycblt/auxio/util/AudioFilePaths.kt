/*
 * Copyright (c) 2026 Auxio Project
 * AudioFilePaths.kt is part of Auxio.
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

package org.oxycblt.auxio.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

private const val TAG = "AudioFilePaths"

/**
 * Resolves a [Uri] to an absolute filesystem path, for operations that need random-access writes
 * (TagLib). Requires "All files access" (MANAGE_EXTERNAL_STORAGE).
 *
 * @return the path, or null if it cannot be resolved (e.g. SAF document URIs).
 */
fun resolveAudioPath(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") {
        return uri.path
    }

    try {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (index >= 0) {
                        cursor.getString(index)?.takeIf { it.isNotEmpty() }?.let {
                            return it
                        }
                    }
                }
            }
    } catch (e: Exception) {
        Log.w(TAG, "DATA column query failed", e)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            context.contentResolver
                .query(
                    uri,
                    arrayOf(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                    ),
                    null,
                    null,
                    null,
                )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val relative = cursor.getString(0) ?: return null
                        val name = cursor.getString(1) ?: return null
                        return "/storage/emulated/0/$relative$name"
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "relative_path query failed", e)
        }
    }
    return null
}
