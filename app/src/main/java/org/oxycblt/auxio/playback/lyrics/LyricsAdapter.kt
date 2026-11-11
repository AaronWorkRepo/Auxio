/*
 * Copyright (c) 2026 Auxio Project
 * LyricsAdapter.kt is part of Auxio.
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

package org.oxycblt.auxio.playback.lyrics

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.databinding.ItemLyricsBinding
import org.oxycblt.musikr.metadata.LyricsReader

/** Displays the lines of a song's embedded lyrics, highlighting the currently sung line. */
class LyricsAdapter : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {
    private val lines = mutableListOf<LyricsReader.Line>()
    private var currentIndex = -1

    fun submit(newLines: List<LyricsReader.Line>) {
        lines.clear()
        lines.addAll(newLines)
        currentIndex = -1
        notifyDataSetChanged()
    }

    /** Highlights [index] (or clears when negative). Returns the previously highlighted index. */
    fun setCurrentIndex(index: Int) {
        if (index == currentIndex) {
            return
        }
        val previous = currentIndex
        currentIndex = index
        if (previous in lines.indices) {
            notifyItemChanged(previous)
        }
        if (index in lines.indices) {
            notifyItemChanged(index)
        }
    }

    /** Index of the last line whose timestamp is at or before [positionMs]. */
    fun indexForPosition(positionMs: Long): Int {
        var result = -1
        for (i in lines.indices) {
            val time = lines[i].timeMs ?: continue
            if (time <= positionMs) {
                result = i
            } else {
                break
            }
        }
        return result
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemLyricsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(lines[position], position == currentIndex)
    }

    override fun getItemCount() = lines.size

    class ViewHolder(private val binding: ItemLyricsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(line: LyricsReader.Line, isCurrent: Boolean) {
            binding.lyricsLine.text = line.text
            binding.lyricsLine.alpha = if (isCurrent) 1f else 0.55f
            binding.lyricsLine.setTypeface(
                null,
                if (isCurrent) Typeface.BOLD else Typeface.NORMAL,
            )
        }
    }
}
