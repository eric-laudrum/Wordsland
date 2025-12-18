package com.example.wordsland

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class LetterTrayAdapter(
    private var letters: List<Char>,
    private val isSwapMode: Boolean,
    private val selectedForSwap: Set<Int>,
    private val onTileClick: (Int) -> Unit
) : RecyclerView.Adapter<LetterTrayAdapter.ViewHolder>() {

    inner class ViewHolder(val tileView: TileView) : RecyclerView.ViewHolder(tileView) {
        init {
            // This click listener now correctly handles clicks in swap mode
            tileView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onTileClick(adapterPosition)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        // Ensure you are using a layout file that contains a TileView
        val tileView = inflater.inflate(R.layout.list_item_tile, parent, false) as TileView
        return ViewHolder(tileView)
    }

    override fun getItemCount() = letters.size

    // 2. onBindViewHolder MUST BE UPDATED
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val letter = letters[position]
        holder.tileView.text = letter.toString()
        holder.tileView.visibility = View.VISIBLE

        // 3. ADD LOGIC TO HANDLE SWAP MODE VS. NORMAL MODE
        if (isSwapMode) {
            // --- IN SWAP MODE ---
            // A. Disable dragging
            holder.tileView.setOnLongClickListener(null)
            holder.tileView.setOnTouchListener(null)

            // B. Set background based on whether the tile is selected for swapping
            when {
                selectedForSwap.contains(position) ->
                    // Bright highlight for selected tiles
                    holder.tileView.setBackgroundResource(R.drawable.swap_tile_highlight_background)
                else ->
                    // Light highlight for all other tiles in swap mode
                    holder.tileView.setBackgroundResource(R.drawable.swap_tile_background)
            }
        } else {
            // --- IN NORMAL (DRAG) MODE ---
            // A. Set the default background
            holder.tileView.setBackgroundResource(R.drawable.cell_background) // Use your default background

            // B. Re-enable dragging logic
            holder.tileView.setOnLongClickListener { view ->
                val letterChar = (view as TileView).text.toString()
                val item = ClipData.Item(letterChar)
                val dragData = ClipData(
                    "TRAY_DRAG",
                    arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                    item
                )
                // Pass the adapter position so we can remove the tile from the hand
                dragData.description.extras = PersistableBundle().apply { putInt("position", position) }
                val shadowBuilder = View.DragShadowBuilder(view)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    view.startDragAndDrop(dragData, shadowBuilder, view, 0)
                } else {
                    @Suppress("DEPRECATION")
                    view.startDrag(dragData, shadowBuilder, view, 0)
                }
                true
            }
        }
    }

    fun updateLetters(newLetters: List<Char>) {
        this.letters = newLetters
        notifyDataSetChanged()
    }
}