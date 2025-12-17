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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.nio.file.Files
import java.nio.file.Files.lines

//  ----------------------  Classes  ----------------------
sealed class CellState {
    object Empty : CellState()
    object Start : CellState()
    object Obstacle : CellState()
    object Target : CellState()
    data class Letter(val char: Char) : CellState() // newly placed letters
    data class LockedLetter (val char: Char): CellState() // entered words
}
class MainActivity : AppCompatActivity() {
    // In your Activity or a ViewModel
    private val numColumns = 15
    private val numRows = 24
    private val validWords = mutableSetOf<String>()
    private var isFirstWordPlayed = false
    private var startTileCoords: Pair<Int, Int>? = null
    private val tileBag = mutableListOf<Char>()
    private val gridModel = Array(numRows) {
        Array<CellState>(numColumns) {
            CellState.Empty
        }
    }

    //  ----------------------  Properties  ----------------------
    private lateinit var gridLayout: GridLayout
    private lateinit var cellViews: Array<Array<TextView>>
    private lateinit var letterTrayRecycler: RecyclerView
    private lateinit var letterTrayAdapter: LetterTrayAdapter
    private val playerLetters = mutableListOf<Char>()


    //  ----------------------  Create  ----------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set views
        gridLayout = findViewById(R.id.grid)
        letterTrayRecycler = findViewById(R.id.letter_tray_recycler)
        val enterButton: Button = findViewById(R.id.enter_word_button)
        val returnButton: Button = findViewById(R.id.return_tiles_button)

        initializeTileBag()

        // Draw first hand
        if(playerLetters.isEmpty()){
            val lettersToDraw = minOf(7, tileBag.size)
            for(i in 0 until lettersToDraw){
                playerLetters.add(tileBag.removeAt(0))
            }
        }

        // Initialize and set up the RecyclerView Adapter
        letterTrayAdapter = LetterTrayAdapter(playerLetters.toMutableList())
        letterTrayRecycler.adapter = letterTrayAdapter
        letterTrayRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // Set Listeners
        letterTrayRecycler.setOnDragListener(letterTrayDragListener)

        enterButton.setOnClickListener{
            checkWord()
        }

        returnButton.setOnClickListener{
            recallLetters()
        }

        // Create grid & perform final render
        gridLayout.post {
            // Calculate cell size.
            val maxCellWidth = gridLayout.width / numColumns
            val maxCellHeight = gridLayout.height / numRows
            val cellSize = minOf(maxCellWidth, maxCellHeight)

            // Set Column and Row counts based on cell size
            gridLayout.columnCount = numColumns
            gridLayout.rowCount = numRows

            // Initialize the data model.
            initializeGridModel()

            // Create the grid
            createVisualGrid(cellSize)

            // Render the final state.
            render()
        }

        // Load dictionary in the background
        loadDictionary()
    }

    //  ----------------------  Functions  ----------------------
    private fun initializeTileBag(){
        tileBag.clear()

        val tileDistribution = mapOf(
            'A' to 18, 'B' to 4, 'C' to 4, 'D' to 8, 'E' to 24, 'F' to 4,
            'G' to 6, 'H' to 4, 'I' to 18, 'J' to 2, 'K' to 2, 'L' to 8,
            'M' to 4, 'N' to 12, 'O' to 16, 'P' to 4, 'Q' to 2, 'R' to 12,
            'S' to 8, 'T' to 12, 'U' to 8, 'V' to 4, 'W' to 4, 'X' to 2,
            'Y' to 4, 'Z' to 2
        )
        for ((letter, count) in tileDistribution){
            repeat(count){
                tileBag.add(letter)
            }
        }
        tileBag.shuffle()
        Log.d("TileBag", "Initialized tile bag with ${tileBag.size} tiles")
    }
    private fun renderGridFromModel() {
        //
        if(!::cellViews.isInitialized) return

        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                val cellView = cellViews[row][col]
                val cellState = gridModel[row][col]

                // Change colour of the cell
                val background = cellView.background.mutate() // Use mutate to avoid changing all instances

                // Remove long click listener
                cellView.setOnLongClickListener (null)

                when (cellState) {
                    is CellState.Empty, is CellState.Start, is CellState.Target, is CellState.Obstacle -> {
                        // This block handles all non-letter cells
                        cellView.setTextColor(Color.BLACK) // Default text color
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

                            else -> {}
                        }
                    }

                    is CellState.Letter -> {
                        cellView.text = cellState.char.toString()
                        background.setTint(Color.YELLOW)
                        cellView.setTextColor(Color.BLACK)

                        cellView.setOnLongClickListener { view ->
                            val dataString = "$row,$col"
                            val item = ClipData.Item(dataString)

                            val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
                            val data = ClipData("GRID_DRAG", mimeTypes, item)

                            val dragShadow = View.DragShadowBuilder(view)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                view.startDragAndDrop(data, dragShadow, view, 0)
                            } else {
                                @Suppress("DEPRECATION")
                                view.startDrag(data, dragShadow, view, 0)
                            }
                            // Consume listener
                            true
                        }
                    }
                    // Locked letters
                    is CellState.LockedLetter -> {
                        cellView.text = cellState.char.toString()
                        background.setTint(Color.rgb(204, 184, 73))
                        cellView.setTextColor(Color.BLACK)
                        // No onClick listener because its not draggable
                    }
                }
            }
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
    private fun createVisualGrid(cellSize: Int) {
        gridLayout.removeAllViews()

        // Set column and row count on Grid
        gridLayout.rowCount = numRows
        gridLayout.columnCount = numColumns

        cellViews = Array(numRows){
            Array(numColumns){
                TextView(this)
            }
        }

        // Create Grid
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                val cellView = TextView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {

                        width = cellSize
                        height = cellSize

                        rowSpec = GridLayout.spec(row)
                        columnSpec = GridLayout.spec(col)

                        setMargins(0, 0, 0, 0)
                    }
                    gravity = Gravity.CENTER
                    textSize = 8f
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
        this.startTileCoords = startCoords

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
    private fun loadDictionary(){
        Thread{
            try{
                val inputStream = resources.openRawResource(R.raw.dictionary)

                inputStream.bufferedReader().use{reader ->
                    val lines = reader.readLines()

                    validWords.addAll(lines.map{ it.trim().uppercase() })

                }

                Log.d("Dictionary", "Successfully Loaded ${validWords.size} words.")
            } catch(e: Exception){
                Log.d("Dictionary", "Error loading dictionary", e)
            }
        }.start()
    }
    private fun checkWord() {
        // Get all newly placed letters.
        val newLetterCells = mutableListOf<Pair<Pair<Int, Int>, Char>>()
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                if (gridModel[row][col] is CellState.Letter) {
                    newLetterCells.add(Pair(Pair(row, col), (gridModel[row][col] as CellState.Letter).char))
                }
            }
        }

        // If no new letters were placed, notify player and exit
        if (newLetterCells.isEmpty()) {
            Toast.makeText(this, "Place some letters to start", Toast.LENGTH_SHORT).show()
            return
        }

        // Determine word orientation based on the new letters.
        val isHorizontal = newLetterCells.all { it.first.first == newLetterCells.first().first.first }
        val isVertical = newLetterCells.all { it.first.second == newLetterCells.first().first.second }

        // If letters are not in a straight line -> Reject placement
        if (!isHorizontal && !isVertical) {
            Toast.makeText(this, "Letters must be in a single straight line!", Toast.LENGTH_SHORT).show()
            return // On failure, do nothing & prevent final render() from being called
        }

        // Build the full word including any connected existing letters
        val (startCellRow, startCellCol) = newLetterCells.first().first
        val allWordCells = mutableListOf<Pair<Pair<Int, Int>, Char>>()
        val wordBuilder = StringBuilder()

        if (isHorizontal) {
            var currentCol = startCellCol
            // Scan left to the beginning of the word
            while (currentCol > 0) {
                val cellState = gridModel[startCellRow][currentCol - 1]
                if (cellState is CellState.Letter || cellState is CellState.LockedLetter) {
                    currentCol--
                } else {
                    break
                }
            }
            // Scan right, building the word
            while (currentCol < numColumns) {
                val cellState = gridModel[startCellRow][currentCol]
                val char = when (cellState) {
                    is CellState.Letter -> cellState.char
                    is CellState.LockedLetter -> cellState.char
                    else -> null
                }
                if (char != null) {
                    wordBuilder.append(char)
                    allWordCells.add(Pair(Pair(startCellRow, currentCol), char))
                    currentCol++
                } else {
                    break
                }
            }
        } else { // Vertical word
            var currentRow = startCellRow
            // Scan up to the beginning of the word
            while (currentRow > 0) {
                val cellState = gridModel[currentRow - 1][startCellCol]
                if (cellState is CellState.Letter || cellState is CellState.LockedLetter) {
                    currentRow--
                } else {
                    break
                }
            }
            // Scan down, building the word
            while (currentRow < numRows) {
                val cellState = gridModel[currentRow][startCellCol]
                val char = when (cellState) {
                    is CellState.Letter -> cellState.char
                    is CellState.LockedLetter -> cellState.char
                    else -> null
                }
                if (char != null) {
                    wordBuilder.append(char)
                    allWordCells.add(Pair(Pair(currentRow, startCellCol), char))
                    currentRow++
                } else {
                    break
                }
            }
        }

        val word = wordBuilder.toString()
        val lockedLettersUsed = allWordCells.count { cell -> newLetterCells.none { it.first == cell.first } }

        // Validate the word length and placement rules
        if (word.length < 2 && newLetterCells.size > 1) { // Allow single letter additions if they form a longer word
            Toast.makeText(this, "Words must be at least 2 letters long", Toast.LENGTH_SHORT).show()
            render()
            return
        }

        val isWordValid = validWords.contains(word)
        var isPlacementValid = true
        var placementErrorMsg = ""

        if (word.length < 2) {
            isPlacementValid = false
            placementErrorMsg = "Words must be at least 2 letters long."
        } else if (!isFirstWordPlayed) {
            if (allWordCells.none { it.first == startTileCoords }) {
                isPlacementValid = false
                placementErrorMsg = "The first word must cover the Start (S) tile!"
            }
        } else{
            val incorporatesLockedLetter = lockedLettersUsed > 0
            var isAdjacentToLockedLetter = false

            if (!incorporatesLockedLetter) {
                for ((newCoords, _) in newLetterCells) {
                    val (row, col) = newCoords
                    // Check for a LockedLetter up, down, left, and right
                    if ((row > 0 && gridModel[row - 1][col] is CellState.LockedLetter) ||
                        (row < numRows - 1 && gridModel[row + 1][col] is CellState.LockedLetter) ||
                        (col > 0 && gridModel[row][col - 1] is CellState.LockedLetter) ||
                        (col < numColumns - 1 && gridModel[row][col + 1] is CellState.LockedLetter)) {
                        isAdjacentToLockedLetter = true
                        break // Found a connection, no need to check further
                    }
                }
            }
            if (!incorporatesLockedLetter && !isAdjacentToLockedLetter) {
                isPlacementValid = false
                placementErrorMsg = "New words must connect to an existing letter."
            }

        }


        // Results
        if (isWordValid && isPlacementValid) {
            // SUCCESS
            Toast.makeText(this, "'$word' is a valid word!", Toast.LENGTH_LONG).show()

            // Create a mutable copy of the current hand to work with.
            val tempHand = playerLetters.toMutableList()

            // For each letter that was staged on the board, remove ONE instance from the temp hand.
            newLetterCells.forEach { (_, char) ->
                tempHand.remove(char)
            }

            // Clear original hand and repopulate it from the temp hand.
            playerLetters.clear()
            playerLetters.addAll(tempHand)

            // Lock letters on grid
            newLetterCells.forEach { (coords, char) -> gridModel[coords.first][coords.second] = CellState.LockedLetter(char) }
            isFirstWordPlayed = true

            // Replenish player's hand up to 7 tiles
            val tilesNeeded = 7 - playerLetters.size
            val lettersToDraw = minOf(tilesNeeded, tileBag.size)
            if (lettersToDraw > 0) {
                repeat(lettersToDraw) { playerLetters.add(tileBag.removeAt(0)) }
            }
        } else {
            // FAILURE
            val errorMsg = if (!isWordValid) "'$word' is not a valid word." else placementErrorMsg
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
        render() // Render state of the game
    }
    private fun render(){
        renderGridFromModel()
        renderLetterTray()

        // Update data in adapter
        if (::letterTrayAdapter.isInitialized) {
            letterTrayAdapter.updateLetters(playerLetters)
        }
    }
    private fun recallLetters() {
        val recalledLetters = mutableListOf<Char>()
        var wasRecalled = false
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                if (gridModel[row][col] is CellState.Letter) {
                    wasRecalled = true

                    // Reset grid cell
                    if (startTileCoords?.first == row && startTileCoords?.second == col) {
                        gridModel[row][col] = CellState.Start
                    } else {
                        gridModel[row][col] = CellState.Empty
                    }
                }
            }
        }

        // Notify user
        if (wasRecalled) {
            Toast.makeText(this, "Tiles returned.", Toast.LENGTH_SHORT).show()
        }
        render() // Update grid and letter tray
    }
    private fun renderLetterTray(){
        if (::letterTrayAdapter.isInitialized) {
            letterTrayAdapter.updateLetters(playerLetters.toList())
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

                // Only allow drops on empty cells and the starting tile
                if (gridModel[destRow][destCol] !is CellState.Empty && gridModel[destRow][destCol] !is CellState.Start) {
                    return@OnDragListener false
                }

                // Check where the drag came from
                when (event.clipDescription.label) {
                    "TRAY_DRAG" -> {
                        // Get the letter from the ClipData
                        val item: ClipData.Item = event.clipData.getItemAt(0)
                        val droppedLetter = item.text.toString().first()

                        // Stage the letter on the grid model.
                        gridModel[destRow][destCol] = CellState.Letter(droppedLetter)

                        // Hide the original view from the tray.
                        (event.localState as? View)?.visibility = View.INVISIBLE
                    }
                    "GRID_DRAG" -> {
                        // Move a letter from another grid cell
                        val item: ClipData.Item = event.clipData.getItemAt(0)
                        val (sourceRow, sourceCol) = item.text.toString().split(",").map { it.toInt() }

                        val sourceState = gridModel[sourceRow][sourceCol]
                        if (sourceState is CellState.Letter) {
                            gridModel[destRow][destCol] = sourceState
                            gridModel[sourceRow][sourceCol] = CellState.Empty
                        }
                    }
                }

                renderGridFromModel() // Update grid
                true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                // Always reset cell's appearance.
                destinationCell.alpha = 1.0f

                // If the drop was not successful, the original view must become visible again.
                if (!event.result) {
                    (event.localState as? View)?.visibility = View.VISIBLE
                }
                true
            }
            else -> false
        }
    }
    private val letterTrayDragListener = View.OnDragListener{ view, event ->
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                // Drag from grid to player's hand
                event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                        event.clipDescription.label == "GRID_DRAG"
            }

            DragEvent.ACTION_DRAG_ENTERED -> {
                // Change the tray's background color
                view.setBackgroundColor(Color.parseColor("#DDDDDD")) // A light gray
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                // Reset the visual cue
                view.setBackgroundColor(Color.TRANSPARENT)
                true
            }
            DragEvent.ACTION_DROP -> {
                // Reset the visual cue
                view.setBackgroundColor(Color.TRANSPARENT)

                // Get the coordinates of the tile that was being dragged from the grid
                val item: ClipData.Item = event.clipData.getItemAt(0)
                val (sourceRow, sourceCol) = item.text.toString().split(",").map { it.toInt() }

                val sourceState = gridModel[sourceRow][sourceCol]
                if (sourceState is CellState.Letter) {
                    if (startTileCoords?.first == sourceRow && startTileCoords?.second == sourceCol) {
                        gridModel[sourceRow][sourceCol] = CellState.Start
                    } else {
                        gridModel[sourceRow][sourceCol] = CellState.Empty
                    }

                    // Update UI
                    render()
                }
                true // Successful drop
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                // Reset background
                view.setBackgroundColor(Color.TRANSPARENT)
                // Make tile view visible again if drop failed
                if (!event.result) {
                    (event.localState as? View)?.visibility = View.VISIBLE
                }
                true
            }
            else -> false
        }
    }
}
// Classes
class LetterTrayAdapter(private val letters: MutableList<Char>) :
    RecyclerView.Adapter<LetterTrayAdapter.LetterViewHolder>() {
    class LetterViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    fun updateLetters(newLetters: List<Char>) {
        // Clear the adapter's internal list
        this.letters.clear()
        // Add all the items from the new list
        this.letters.addAll(newLetters)
        // Notify the RecyclerView that the data has changed
        notifyDataSetChanged()
    }

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

        holder.textView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val letterChar = (view as TextView).text.toString()

                // Prepare the data to be dragged
                val clipText = letterChar
                val item = ClipData.Item(clipText)
                val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
                val clipData = ClipData("TRAY_DRAG", mimeTypes, item)

                // Add the letter's position to the ClipData so we know which one to remove later.
                val extras = PersistableBundle().apply { putInt("position", holder.adapterPosition) }
                clipData.description.extras = extras

                // Instantiate the drag shadow builder.
                val dragShadow = View.DragShadowBuilder(view)

                // Start the drag.
                view.startDragAndDrop(clipData, dragShadow, view, 0)

                // Hide the original view immediately to give the feel of "picking it up".
                view.visibility = View.INVISIBLE

                // Return true to indicate we've handled the touch event.
                true
            } else {
                // Return false for all other touch actions so they are not consumed.
                false
            }
        }
    }

    override fun getItemCount() = letters.size
}