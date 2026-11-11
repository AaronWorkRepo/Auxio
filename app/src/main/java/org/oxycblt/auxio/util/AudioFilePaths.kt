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
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File

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

    // File Picker (SAF) libraries expose external-storage document URIs, which encode the real
    // path as "<volumeId>:<relative path>".
    if (uri.authority == "com.android.externalstorage.documents") {
        try {
            // "tree/<id>" points at a folder; "tree/<id>/document/<docId>" and "document/<docId>"
            // point at a file. Only the latter should be used for tag writing.
            val segments = uri.pathSegments
            val hasDocumentSegment =
                (segments.size >= 4 && segments[0] == "tree" && segments[2] == "document") ||
                    (segments.size >= 2 && segments[0] == "document")
            val documentId =
                if (hasDocumentSegment) {
                    DocumentsContract.getDocumentId(uri)
                } else {
                    DocumentsContract.getTreeDocumentId(uri)
                }
            val separator = documentId.indexOf(':')
            if (separator > 0) {
                val volumeId = documentId.substring(0, separator)
                val relative = documentId.substring(separator + 1)
                val base =
                    when (volumeId) {
                        "primary" -> "/storage/emulated/0"
                        else -> "/storage/$volumeId"
                    }
                val candidate = "$base/$relative"
                if (File(candidate).exists()) {
                    return candidate
                }
                Log.w(TAG, "SAF document path does not exist: $candidate")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to decode SAF document id for $uri", e)
        }
    }

    var dataPath: String? = null
    try {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (index >= 0) {
                        dataPath = cursor.getString(index)?.takeIf { it.isNotEmpty() }
                    }
                }
            }
    } catch (e: Exception) {
        Log.w(TAG, "DATA column query failed", e)
    }

    if (dataPath != null && File(dataPath!!).exists()) {
        return dataPath
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        var relative: String? = null
        var name: String? = null
        var volume: String? = null
        try {
            context.contentResolver
                .query(
                    uri,
                    arrayOf(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.Audio.Media.VOLUME_NAME,
                    ),
                    null,
                    null,
                    null,
                )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        relative = cursor.getString(0)
                        name = cursor.getString(1)
                        volume = cursor.getString(2)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "relative_path query failed", e)
        }

        if (relative != null && name != null) {
            val base =
                when (volume) {
                    null, "external_primary" -> "/storage/emulated/0"
                    else -> "/storage/$volume"
                }
            val candidate = "$base/$relative$name"
            if (File(candidate).exists()) {
                return candidate
            }
            Log.w(TAG, "Candidate path does not exist: $candidate")
        }
    }

    Log.w(TAG, "Unable to resolve a filesystem path for $uri (DATA=$dataPath)")
    // Return the DATA path even if it is missing so callers report a save failure rather than
    // pretending the location is unsupported.
    return dataPath
}
