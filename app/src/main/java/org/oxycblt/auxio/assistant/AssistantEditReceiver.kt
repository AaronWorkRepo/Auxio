/*
 * Copyright (c) 2026 Auxio Project
 * AssistantEditReceiver.kt is part of Auxio.
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

package org.oxycblt.auxio.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import kotlin.concurrent.thread
import org.oxycblt.auxio.util.resolveAudioPath
import org.oxycblt.musikr.metadata.TagEditor

/**
 * Headless tag-editing endpoint for the user's AI assistant.
 *
 * Action: `org.oxycblt.auxio.action.EDIT_TAGS`
 *
 * Extras: `path` (absolute file path) or `uri` (content URI), plus any of `title`, `artist`,
 * `album`, `albumArtist`, `genre`, `year`, `track`, `disc`, `comment`. Omitted fields are left
 * unchanged. Writing requires "All files access" (MANAGE_EXTERNAL_STORAGE).
 */
class AssistantEditReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EDIT_TAGS) {
            return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        thread(name = "AssistantEdit") {
            try {
                val path =
                    intent.getStringExtra("path")?.takeIf { it.isNotBlank() }
                        ?: intent.getStringExtra("uri")?.let {
                            resolveAudioPath(appContext, Uri.parse(it))
                        }
                if (path == null) {
                    Log.e(TAG, "No usable path or uri in edit request")
                    return@thread
                }
                val success =
                    TagEditor.editTags(
                        path = path,
                        title = intent.getStringExtra("title"),
                        artist = intent.getStringExtra("artist"),
                        album = intent.getStringExtra("album"),
                        albumArtist = intent.getStringExtra("albumArtist"),
                        genre = intent.getStringExtra("genre"),
                        year = intent.getStringExtra("year"),
                        track = intent.getStringExtra("track"),
                        disc = intent.getStringExtra("disc"),
                        comment = intent.getStringExtra("comment"),
                    )
                Log.i(TAG, "editTags success=$success path=$path")
                if (success) {
                    MediaScannerConnection.scanFile(appContext, arrayOf(path), null, null)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Tag edit failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_EDIT_TAGS = "org.oxycblt.auxio.action.EDIT_TAGS"
        private const val TAG = "AssistantEdit"
    }
}
