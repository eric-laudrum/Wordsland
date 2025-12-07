package com.example.wordsland

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Features of a single cell in the grid
sealed class CellState {
    object Empty : CellState()
    object Start : CellState()
    object Obstacle : CellState()
    object Target : CellState()
    data class Letter(val char: Char) : CellState()
}
class MainActivity : AppCompatActivity() {
    // In your Activity or a ViewModel
    private val gridSize = 16
    private val gridModel = Array(gridSize) {
        Array<CellState>(gridSize) {
            CellState.Empty
        }
    }

    // Initialize Variables
    private lateinit var gridLayout: GridLayout
    private lateinit var cellViews: Array<Array<TextView>>
    private lateinit var letterTrayRecycler: RecyclerView
    private lateinit var letterTrayAdapter: LetterTrayAdapter
    private val playerLetters = mutableListOf<Char>()


    // Create
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridLayout = findViewById(R.id.grid)

        cellViews = Array(gridSize) {
            Array(gridSize) {
                TextView(this)
            }
        }

        // Letter Tray / RecyclerView
        letterTrayRecycler = findViewById(R.id.letter_tray_recycler)
        setupLetterTray()

        initializeGridModel()
        createVisualGrid() // and drag listener
        renderGridFromModel()
    }

    // Functions
    private fun setupLetterTray() {
        // Generate 7 random letters for the player
        for (i in 0 until 7) {
            playerLetters.add(('A'..'Z').random())
        }
        // Setup RecyclerView
        letterTrayAdapter = LetterTrayAdapter(playerLetters)
        letterTrayRecycler.adapter = letterTrayAdapter
        letterTrayRecycler.layoutManager =
            LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL, false
            )
    }
    private fun renderGridFromModel() {
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val cellView = cellViews[row][col]
                val cellState = gridModel[row][col]

                // Change colour of the cell
                val background =
                    cellView.background.mutate() // Use mutate to avoid changing all instances

                when (cellState) {
                    is CellState.Empty -> {
                        cellView.text = ""
                        background.setTint(
                            ContextCompat.getColor(
                                this,
                                android.R.color.darker_gray
                            )
                        )
                    }

                    is CellState.Start ->{
                        cellView.text = "S"
                        background.setTint(Color.CYAN)
                        cellView.setTextColor(Color.BLACK)
                    }

                    is CellState.Obstacle -> {
                        cellView.text = "X"
                        background.setTint(Color.BLACK)
                        cellView.setTextColor(Color.WHITE)
                    }

                    is CellState.Target -> {
                        cellView.text = "T"
                        background.setTint(Color.GREEN)
                        cellView.setTextColor(Color.BLACK)
                    }

                    is CellState.Letter -> {
                        cellView.text = cellState.char.toString()
                        background.setTint(Color.YELLOW)
                        cellView.setTextColor(Color.BLACK)
                    }
                }
            }
        }
    }
    private val gridCellDragListener = View.OnDragListener { view, event ->
        val destinationCell = view as TextView

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                // Check if the event has the data we expect (a letter)
                event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                // Optional: Highlight the cell when a letter is dragged over it
                destinationCell.alpha = 0.5f
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                // Un-highlight when the drag stops
                destinationCell.alpha = 1.0f
                true
            }
            DragEvent.ACTION_DROP -> {
                // The tile is dropped
                destinationCell.alpha = 1.0f // Reset alpha

                val item: ClipData.Item = event.clipData.getItemAt(0)
                val droppedLetter = item.text.toString().first()
                // Safely get position, provide a default if label is null or not a number
                val letterPosition = event.clipDescription.label?.toString()?.toIntOrNull() ?: -1

                if (letterPosition == -1) {
                    return@OnDragListener false // Invalid drag data, fail the drop
                }

                // Find the coordinates of the destination cell
                val destinationCoords = findViewCoordinates(destinationCell)

                if (destinationCoords != null) {
                    val (row, col) = destinationCoords

                    // Check if the cell is empty
                    if (gridModel[row][col] is CellState.Empty) {
                        // Update data model
                        gridModel[row][col] = CellState.Letter(droppedLetter)

                        // Update visuals
                        renderGridFromModel()

                        // Remove letter from player's tray
                        if (letterPosition < playerLetters.size) {
                            playerLetters.removeAt(letterPosition)
                            letterTrayAdapter.notifyItemRemoved(letterPosition)

                            // This helps the adapter recalculate subsequent positions
                            letterTrayAdapter.notifyItemRangeChanged(letterPosition, playerLetters.size)
                        }
                        // Return true for a successful drop
                        true
                    } else {
                        Toast.makeText(this, "Cell is not empty!", Toast.LENGTH_SHORT).show()
                        // Return false for a failed drop
                        false
                    }
                } else {
                    false
                }
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                destinationCell.alpha = 1.0f
                true
            }
            else -> false
        }
    }
    private fun findViewCoordinates(view: View): Pair<Int, Int>? {
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                if (cellViews[row][col] == view) {
                    return Pair(row, col)
                }
            }
        }
        return null
    }
    private fun createVisualGrid() {
        gridLayout.removeAllViews()
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val cellView = TextView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        rowSpec = GridLayout.spec(row, 1, 1f)
                        columnSpec = GridLayout.spec(col, 1, 1f)
                        width = 0
                        height = 0
                        setMargins(2, 2, 2, 2)
                    }
                    gravity = Gravity.CENTER
                    textSize = 12f
                    // Apply the new rounded square background
                    background =
                        ContextCompat.getDrawable(this@MainActivity, R.drawable.cell_background)
                }

                // --- Set a listener to receive drag events ---
                cellView.setOnDragListener(gridCellDragListener)

                // Store and add the view
                cellViews[row][col] = cellView
                gridLayout.addView(cellView)
            }
        }
    }
    private fun initializeGridModel() {

        // Clear grid to start fresh
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                gridModel[row][col] = CellState.Empty
            }}

        // Create a list of all possible coordinates
        val availableCoordinates = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                availableCoordinates.add(Pair(row, col))
            }
        }
        // Shuffle the list to make the selection random.
        availableCoordinates.shuffle()

        // Place the Start point
        val startCoords = availableCoordinates.removeAt(0)
        gridModel[startCoords.first][startCoords.second] = CellState.Start

        // Place the Target point
        val targetCoords = availableCoordinates.removeAt(0)
        gridModel[targetCoords.first][targetCoords.second] = CellState.Target

        // Place the Obstacles
        val numberOfObstacles = 10

        if(availableCoordinates.size < 2 + numberOfObstacles){
            Log.e("WordsLand", "Error: Grid is too small to place Start, Target, and all Obstacles")
            return
        }

        for (i in 0 until numberOfObstacles) {
            if (availableCoordinates.isEmpty()) break

            val obstacleCoords = availableCoordinates.removeAt(0)
            gridModel[obstacleCoords.first][obstacleCoords.second] = CellState.Obstacle
        }
    }
}
// Classes
class LetterTrayAdapter(private val letters: List<Char>) :
    RecyclerView.Adapter<LetterTrayAdapter.LetterViewHolder>() {

    class LetterViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LetterViewHolder {
        val context = parent.context
        val textView = TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                150,
                150
            )
            gravity = Gravity.CENTER
            textSize = 24f
            setTextColor(Color.BLACK)

            // Use same tile shape for cell
            background = ContextCompat.getDrawable(context, R.drawable.cell_background)
            // Set initial color
            background.mutate().setTint(Color.LTGRAY)
        }
        return LetterViewHolder(textView)
    }

    override fun onBindViewHolder(holder: LetterViewHolder, position: Int) {
        val letter = letters[position]
        holder.textView.text = letter.toString()

        // Set up the drag-and-drop starter
        holder.itemView.setOnLongClickListener { view ->
            // Data to be sent with the drag
            val clipText = letter.toString()
            val item = ClipData.Item(clipText)
            val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)

            // Pass letter's position to label which to remove
            val data = ClipData(position.toString(), mimeTypes, item)

            // Shadow of the view being dragged
            val dragShadow = View.DragShadowBuilder(view)

            // Start drag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                view.startDragAndDrop(data, dragShadow, view, 0)
            } else {
                @Suppress("DEPRECATION")
                view.startDrag(data, dragShadow, view, 0)
            }

            // Hide the original view
            view.visibility = View.INVISIBLE
            true
        }

        // Reset the visibility if the drag is cancelled
        holder.itemView.setOnDragListener { view, event ->
            if (event.action == DragEvent.ACTION_DRAG_ENDED) {
                view.visibility = View.VISIBLE
            }
            true
        }
    }

    override fun getItemCount() = letters.size
}