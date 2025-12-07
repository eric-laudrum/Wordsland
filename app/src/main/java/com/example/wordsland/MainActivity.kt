package com.example.wordsland

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
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

//  ----------------------  Classes  ----------------------
sealed class CellState {
    object Empty : CellState()
    object Start : CellState()
    object Obstacle : CellState()
    object Target : CellState()
    data class Letter(val char: Char) : CellState()
}
class MainActivity : AppCompatActivity() {
    // In your Activity or a ViewModel
    private val numColumns = 16
    private val numRows = 20


    private val gridModel = Array(numRows) {
        Array<CellState>(numColumns) {
            CellState.Empty
        }
    }

    //  ----------------------  Initialize Varibales  ----------------------
    private lateinit var gridLayout: GridLayout
    private lateinit var cellViews: Array<Array<TextView>>
    private lateinit var letterTrayRecycler: RecyclerView
    private lateinit var letterTrayAdapter: LetterTrayAdapter
    private val playerLetters = mutableListOf<Char>()


    //  ----------------------  Create  ----------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridLayout = findViewById(R.id.grid)

        cellViews = Array(numRows) {
            Array(numColumns) {
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

    //  ----------------------  Functions  ----------------------
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
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                val cellView = cellViews[row][col]
                val cellState = gridModel[row][col]

                // Change colour of the cell
                val background = cellView.background.mutate() // Use mutate to avoid changing all instances

                // Remove long click listener
                cellView.setOnLongClickListener (null)
                cellView.isLongClickable = false


                when (cellState) {
                    is CellState.Empty, is CellState.Start, is CellState.Target, is CellState.Obstacle -> {
                        // This block handles all non-letter cells
                        cellView.setTextColor(Color.BLACK) // Default text color
                        when (cellState) {
                            is CellState.Empty -> {
                                cellView.text = ""
                                background.setTint(ContextCompat.getColor(this, android.R.color.darker_gray))
                            }
                            is CellState.Start -> {
                                cellView.text = "S"
                                background.setTint(Color.CYAN)
                            }
                            is CellState.Target -> {
                                cellView.text = "T"
                                background.setTint(Color.GREEN)
                            }
                            is CellState.Obstacle -> {
                                cellView.text = "X"
                                background.setTint(Color.BLACK)
                                cellView.setTextColor(Color.WHITE)
                            }

                            is CellState.Letter -> TODO()
                        }
                    }

                    is CellState.Letter -> {
                        cellView.text = cellState.char.toString()
                        background.setTint(Color.YELLOW)
                        cellView.setTextColor(Color.BLACK)

                        // Enable dragging for this letter tile
                        cellView.setOnLongClickListener { view ->
                            // The data payload will be the original row and column
                            val dataString = "$row,$col"
                            val item = ClipData.Item(dataString)
                            val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)

                            // The label tells our drop listener that this came from the grid
                            val data = ClipData("GRID_DRAG", mimeTypes, item)

                            val dragShadow = View.DragShadowBuilder(view)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                view.startDragAndDrop(data, dragShadow, view, 0)
                            } else {
                                @Suppress("DEPRECATION")
                                view.startDrag(data, dragShadow, view, 0)
                            }
                            true
                        }
                    }
                }
            }
        }
    }

    private val gridCellDragListener = View.OnDragListener { view, event ->
        val destinationCell = view as TextView

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                // Accept drags for both TRAY and GRID types
                val description = event.clipDescription
                (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) &&
                        (description.label == "TRAY_DRAG" || description.label == "GRID_DRAG")
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                destinationCell.alpha = 0.5f
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                destinationCell.alpha = 1.0f
                true
            }
            DragEvent.ACTION_DROP -> {
                destinationCell.alpha = 1.0f // Reset appearance

                val destinationCoords = findViewCoordinates(destinationCell) ?: return@OnDragListener false
                val (destRow, destCol) = destinationCoords

                // Only allow drops on empty cells
                if (gridModel[destRow][destCol] !is CellState.Empty) {
                    Toast.makeText(this, "Cell is not empty!", Toast.LENGTH_SHORT).show()
                    return@OnDragListener false // Drop failed
                }

                // Handle tile drop
                renderGridFromModel()
                return@OnDragListener true

                // Check where the drag came from
                when (event.clipDescription.label) {
                    "TRAY_DRAG" -> {
                        // Dropping a letter from the tray
                        val item: ClipData.Item = event.clipData.getItemAt(0)
                        val droppedLetter = item.text.toString().first()
                        val letterPosition = event.clipDescription.extras.getInt("position")

                        // Update grid model TODO: make this work.
                        gridModel[destRow][destCol] = CellState.Letter(droppedLetter)

                        // Remove from tray
                        if (letterPosition < playerLetters.size) {
                            playerLetters.removeAt(letterPosition)
                            letterTrayAdapter.notifyItemRemoved(letterPosition)
                            letterTrayAdapter.notifyItemRangeChanged(letterPosition, playerLetters.size)
                        }
                    }
                    "GRID_DRAG" -> {
                        // Move a letter from another grid cell
                        val item: ClipData.Item = event.clipData.getItemAt(0)
                        val sourceCoordsString = item.text.toString().split(",")
                        val sourceRow = sourceCoordsString[0].toInt()
                        val sourceCol = sourceCoordsString[1].toInt()

                        // Get the letter from the source cell
                        val sourceState = gridModel[sourceRow][sourceCol]
                        if (sourceState is CellState.Letter) {
                            // Move the letter
                            gridModel[destRow][destCol] = sourceState // Place it in the new spot
                            gridModel[sourceRow][sourceCol] = CellState.Empty // Clear the old spot
                        }
                    }
                }

                // Re-render the entire grid to show the changes
                renderGridFromModel()
                // Drop successful
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                // Make sure cells are visible after the drag ends
                if(!event.result){
                    (event.localState as? View)?.visibility = View.VISIBLE
                }

                // Successfully ended
                destinationCell.alpha = 1.0f
                true
            }
            else -> false
        }
    }
    private fun findViewCoordinates(view: View): Pair<Int, Int>? {
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                if (cellViews[row][col] == view) {
                    return Pair(row, col)
                }
            }
        }
        return null
    }
    private fun createVisualGrid() {
        gridLayout.removeAllViews()
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
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
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                gridModel[row][col] = CellState.Empty
            }}

        // Create a list of all possible coordinates
        val availableCoordinates = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
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
            val currentPosition = holder.adapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnLongClickListener false

            val clipText = letters[currentPosition].toString()
            val item = ClipData.Item(clipText)
            val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)

            // We now use the "TRAY_DRAG" label as intended.
            val data = ClipData("TRAY_DRAG", mimeTypes, item)

            val extras = PersistableBundle()
            extras.putInt("position", currentPosition)
            data.description.extras = extras

            val dragShadow = View.DragShadowBuilder(view)

            // Start the drag. The view passed as the 3rd argument is the `localState`.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                view.startDragAndDrop(data, dragShadow, view, 0)
            } else {
                @Suppress("DEPRECATION")
                view.startDrag(data, dragShadow, view, 0)
            }

            // Make the original view invisible. This is correct.
            view.visibility = View.INVISIBLE
            true
        }
    }

    override fun getItemCount() = letters.size
}