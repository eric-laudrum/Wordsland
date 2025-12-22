package com.example.wordsland

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

// Callback for moving items in the RecyclerView
interface ItemMoveListener {
    fun onItemMove(fromPosition: Int, toPosition: Int)
}

// Letter Tray / Player's hand
class LetterTrayAdapter(
    private var letters: MutableList<Char>,
    private val isSwapMode: Boolean,
    private val selectedForSwap: Set<Int>,
    private val onTileClick: (Int) -> Unit
) : RecyclerView.Adapter<LetterTrayAdapter.ViewHolder>(), ItemMoveListener {

    // ViewHolder class to wrap the TileView
    inner class ViewHolder(val tileView: TileView) : RecyclerView.ViewHolder(tileView) {
        init {
            // Handle tile taps events
            tileView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onTileClick(adapterPosition)
                }
            }
        }
    }

    // Inflate and create TileView for each letter
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val tileView = inflater.inflate(R.layout.list_item_tile, parent, false) as TileView
        return ViewHolder(tileView)
    }

    // Return the number of tiles in the tray
    override fun getItemCount() = letters.size


    // Bind letter data to the TileView
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val letter = letters[position]

        // Set the letter and visibility
        holder.tileView.text = letter.toString()
        holder.tileView.visibility = View.VISIBLE


        // Swap letters is ACTIVE
        if (isSwapMode) {

            // Disable dragging during swap mode
            holder.tileView.setOnLongClickListener(null)
            holder.tileView.setOnTouchListener(null)

            // Highlight the tiles to swap
            when {
                selectedForSwap.contains(position) ->
                    // Bright highlight for active tiles
                    holder.tileView.setBackgroundResource(
                        R.drawable.swap_tile_highlight_background)
                else ->
                    // Dim highlight to indicate swap mode is active
                    holder.tileView.setBackgroundResource(
                        R.drawable.swap_tile_background)
            }
        } else {
            // Normal Play Mode (Swap inactive)
            // Set the background to default
            holder.tileView.setBackgroundResource(R.drawable.cell_background) // Use your default background

            // Enable drag to place tiles from tray to board
            holder.tileView.setOnLongClickListener(null)
            holder.tileView.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {

                    // Create drag data for the letter
                    val letterChar = (view as TileView).text.toString()
                    val item = ClipData.Item(letterChar)
                    val dragData = ClipData(
                        "TRAY_DRAG",
                        arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                        item
                    )

                    // Store the adapter position for later removal
                    dragData.description.extras = PersistableBundle().apply { putInt("position", position) }

                    // Build a drag shadow
                    val shadowBuilder = View.DragShadowBuilder(view)

                    // Start the drag
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        view.startDragAndDrop(dragData, shadowBuilder, view, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        view.startDrag(dragData, shadowBuilder, view, 0)
                    }
                    true
                } else {
                    false
                }
            }
        }
    }
    // Replace all letters in the tray and refresh the list
    fun updateLetters(newLetters: List<Char>) {
        this.letters = newLetters.toMutableList()
        notifyDataSetChanged()
    }

    // Reorder tiles in the tray
    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(letters, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }
}