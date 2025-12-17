package com.example.wordsland

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class LetterTrayAdapter(private var letters: List<Char>) : RecyclerView.Adapter<LetterTrayAdapter.LetterViewHolder>() {

    // The ViewHolder holds a reference to the custom TileView from the layout.
    class LetterViewHolder(val tileView: TileView) : RecyclerView.ViewHolder(tileView)

    // This method is called by the RecyclerView to create a new ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LetterViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        // It inflates the layout file we created.
        val tileView = inflater.inflate(R.layout.list_item_tile, parent, false) as TileView
        return LetterViewHolder(tileView)
    }

    // This method binds the data (a letter) to the ViewHolder.
    override fun onBindViewHolder(holder: LetterViewHolder, position: Int) {
        val letter = letters[position]
        holder.tileView.text = letter.toString()
        // We must reset visibility because RecyclerView reuses views.
        holder.tileView.visibility = View.VISIBLE

        // Define the drag action once in a reusable lambda.
        val startDragAction = { view: View ->
            val letterChar = (view as TileView).text.toString()

            val clipText = letterChar
            val item = ClipData.Item(clipText)
            val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
            val clipData = ClipData("TRAY_DRAG", mimeTypes, item)

            // Pass the letter's position for reliable state updates.
            val extras = PersistableBundle().apply { putInt("position", holder.adapterPosition) }
            clipData.description.extras = extras

            val dragShadow = View.DragShadowBuilder(view)
            view.startDragAndDrop(clipData, dragShadow, view, 0)
            view.visibility = View.INVISIBLE
        }

        // For touchscreen users: start drag on touch down.
        holder.tileView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startDragAction(view)
                true // Event handled
            } else {
                false // Event not handled
            }
        }

        // For accessibility users: connect performClick() to the drag action.
        holder.tileView.setOnTileDragListener {
            startDragAction(holder.tileView)
        }
    }

    // Returns the total number of items in the list.
    override fun getItemCount() = letters.size

    // A public method to update the data in the adapter.
    fun updateLetters(newLetters: List<Char>) {
        this.letters = newLetters
        notifyDataSetChanged() // Tell RecyclerView to redraw itself
    }
}