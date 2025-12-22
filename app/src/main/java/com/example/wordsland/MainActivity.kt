package com.example.wordsland

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

//  ----------------------  Enums  ----------------------
enum class TileRewardType {
    VOWELS, CONSONANTS, ANY
}

//  ----------------------  Cell States  ----------------------
sealed class CellState {
    object Empty : CellState()
    object Start : CellState()
    object Obstacle : CellState()
    object Target : CellState()

    // Special tiles
    data class MoreTiles(val count: Int, val type: TileRewardType) : CellState()

    // Player tiles (pre-validation)
    data class Letter(val char: Char) : CellState()

    // Player tiles (post-validation)
    data class LockedLetter (val char: Char): CellState()
}

// ----------------------------- Main Activity -------------------
class MainActivity : AppCompatActivity() {

    //  ----------------------  Game Configuration  ----------------------
    private val handSize = 8
    private val numColumns = 15
    private val numRows = 24

    //  ----------------------  Game Stats  ----------------------
    private val validWords = mutableSetOf<String>()
    private var isFirstWordPlayed = false
    private var startTileCoords: Pair<Int, Int>? = null
    private var targetTileCoords: Pair<Int, Int>? = null

    private val tileBag = mutableListOf<Char>()
    private val playerLetters = mutableListOf<Char>()

    private var currentRound = 1

    // Logical representation of the grid
    private val gridModel = Array(numRows) {
        Array<CellState>(numColumns) {
            CellState.Empty
        }
    }

    // Store the value of cells covered by tiles
    private val coveredCells = mutableMapOf<Pair<Int, Int>, CellState>()


    //  ----------------------  UI Elements  ----------------------
    private lateinit var gridLayout: GridLayout
    private lateinit var cellViews: Array<Array<TextView>>
    private lateinit var letterTrayRecyclerView: RecyclerView

    private lateinit var roundCounterTextView: TextView
    private lateinit var tilesLeftCounterTextView: TextView
    private lateinit var swapButton: Button
    private lateinit var recallButton: Button



    //  ----------------------  Swap Tiles  ----------------------
    private var isSwapModeActive = false
    private val tilesToSwap = mutableSetOf<Int>()


    //  ----------------------  Lifecycle  ----------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        gridLayout = findViewById(R.id.grid)
        recallButton = findViewById(R.id.recall_button)
        swapButton = findViewById(R.id.swap_button)

        roundCounterTextView = findViewById(R.id.round_counter_text)
        tilesLeftCounterTextView = findViewById(R.id.tiles_left_counter_text)
        letterTrayRecyclerView = findViewById(R.id.letter_tray_recycler_view)
        val enterButton: Button = findViewById(R.id.enter_word_button)

        initializeTileBag()

        // Draw first hand
        if (playerLetters.isEmpty()) {
            val lettersToDraw = minOf(handSize, tileBag.size)
            repeat(lettersToDraw) {
                playerLetters.add(tileBag.removeAt(0))
            }
        }

        // Set Listeners
        letterTrayRecyclerView.setOnDragListener(letterTrayDragListener)
        enterButton.setOnClickListener { checkWord() }
        recallButton.setOnClickListener { recallLetters() }
        swapButton.setOnClickListener { handleSwapButtonClick() }

        // Delay grid creation until after layout is measured
        gridLayout.post {
            val maxCellWidth = gridLayout.width / numColumns
            val maxCellHeight = gridLayout.height / numRows
            val cellSize = minOf(maxCellWidth, maxCellHeight)

            gridLayout.columnCount = numColumns
            gridLayout.rowCount = numRows

            // Create the main game grid
            createVisualGrid(cellSize)
            initializeGridModel()
            render()
        }

        // Load the dictionary asynchronously
        loadDictionary()
    }

    //  ----------------------  Tile Bag  ----------------------
    // Initialize the tile bag
    private fun initializeTileBag(){
        tileBag.clear()

        // 196 Tiles to start
        // Letter distribution
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
        updateCounters()
    }

    //  ----------------------  Rendering  ----------------------
    private fun render(){
        renderGridFromModel()
        renderLetterTray()
        updateCounters()
    } // Render entire UI
    private fun renderGridFromModel() {
        if (!::cellViews.isInitialized) return

        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                val cellView = cellViews[row][col]
                val cellState = gridModel[row][col]
                val background = cellView.background.mutate()

                cellView.setOnLongClickListener(null)
                cellView.setOnTouchListener(null)

                when (cellState) {
                    is CellState.Empty -> {
                        cellView.text = ""
                        background.setTint(Color.DKGRAY)
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
                    is CellState.MoreTiles -> {
                        cellView.text = "+${cellState.count}"
                        background.setTint(Color.MAGENTA)
                    }
                    is CellState.Letter -> {
                        cellView.text = cellState.char.toString()
                        background.setTint(Color.YELLOW)

                        // Enable dragging letters already placed on the grid
                        cellView.setOnTouchListener { view, event ->
                            if (event.action == MotionEvent.ACTION_DOWN) {
                                val data = ClipData(
                                    "GRID_DRAG",
                                    arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                                    ClipData.Item("$row,$col")
                                )
                                val shadow = View.DragShadowBuilder(view)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    view.startDragAndDrop(data, shadow, view, 0)
                                } else {
                                    @Suppress("DEPRECATION")
                                    view.startDrag(data, shadow, view, 0)
                                }
                                true
                            } else false
                        }
                    }
                    is CellState.LockedLetter -> {
                        cellView.text = cellState.char.toString()
                        background.setTint(Color.rgb(204, 184, 73))
                    }
                }
            }
        }
    } // Update grid UI to match grid model
    private fun createVisualGrid(cellSize: Int) {
        gridLayout.removeAllViews()
        gridLayout.rowCount = numRows
        gridLayout.columnCount = numColumns
        cellViews = Array(numRows){ Array(numColumns){ TextView(this) } }

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
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.cell_background)
                }
                cellView.setOnDragListener(gridCellDragListener)
                cellViews[row][col] = cellView
                gridLayout.addView(cellView)
            }
        }
    } // Create a grid of TextViews
    private fun initializeGridModel() {
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                gridModel[row][col] = CellState.Empty
            }
        }

        val availableCoordinates = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                availableCoordinates.add(Pair(row, col))
            }
        }
        availableCoordinates.shuffle()

        val startCoords = availableCoordinates.removeAt(0)
        gridModel[startCoords.first][startCoords.second] = CellState.Start
        this.startTileCoords = startCoords

        val targetCoords = availableCoordinates.removeAt(0)
        gridModel[targetCoords.first][targetCoords.second] = CellState.Target
        this.targetTileCoords = targetCoords

        val numberOfObstacles = 20
        if(availableCoordinates.size < 2 + numberOfObstacles){
            Log.e("WordsLand", "Error: Grid is too small to place Start, Target, and all Obstacles")
            return
        }

        for (i in 0 until numberOfObstacles) {
            if (availableCoordinates.isEmpty()) break
            val obstacleCoords = availableCoordinates.removeAt(0)
            gridModel[obstacleCoords.first][obstacleCoords.second] = CellState.Obstacle
        }

        // Place "More Tiles" tiles
        val numberOfMoreTiles = 3
        val rewardCounts = listOf(2, 5, 10)
        for (i in 0 until numberOfMoreTiles) {
            if (availableCoordinates.isEmpty()) break
            val moreTilesCoords = availableCoordinates.removeAt(0)
            val randomCount = rewardCounts.random()
            val randomType = TileRewardType.values().random()
            gridModel[moreTilesCoords.first][moreTilesCoords.second] = CellState.MoreTiles(randomCount, randomType)
        }
    } // Randomly place tiles on the grid


    //  ----------------------  Dictionary  ----------------------
    private fun loadDictionary(){
        Thread{
            try{
                val inputStream = resources.openRawResource(R.raw.dictionary)
                inputStream.bufferedReader().use{reader ->
                    validWords.addAll(reader.readLines().map{ it.trim().uppercase() })
                }
                Log.d("Dictionary", "Successfully Loaded ${validWords.size} words.")
            } catch(e: Exception){
                Log.d("Dictionary", "Error loading dictionary", e)
            }
        }.start()
    }

    private fun checkWord() {
        val newLetterCells = mutableListOf<Pair<Pair<Int, Int>, Char>>()
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                val cellState = gridModel[row][col]
                if (cellState is CellState.Letter) {
                    newLetterCells.add(Pair(Pair(row, col), cellState.char))
                }
            }
        }

        if (newLetterCells.isEmpty()) {
            Toast.makeText(this, "Place some letters to start", Toast.LENGTH_SHORT).show()
            return
        }

        val isHorizontal = newLetterCells.all { it.first.first == newLetterCells.first().first.first }
        val isVertical = newLetterCells.all { it.first.second == newLetterCells.first().first.second }

        if (!isHorizontal && !isVertical) {
            Toast.makeText(this, "Letters must be in a single straight line!", Toast.LENGTH_SHORT).show()
            return
        }

        val (startCellRow, startCellCol) = newLetterCells.first().first
        val allWordCells = mutableListOf<Pair<Pair<Int, Int>, Char>>()
        val wordBuilder = StringBuilder()

        if (isHorizontal) {
            var currentCol = startCellCol
            while (currentCol > 0) {
                val cellState = gridModel[startCellRow][currentCol - 1]
                if (cellState is CellState.Letter || cellState is CellState.LockedLetter) {
                    currentCol--
                } else {
                    break
                }
            }
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
            while (currentRow > 0) {
                val cellState = gridModel[currentRow - 1][startCellCol]
                if (cellState is CellState.Letter || cellState is CellState.LockedLetter) {
                    currentRow--
                } else {
                    break
                }
            }
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
            val lockedLettersUsed = allWordCells.count { cell -> newLetterCells.none { it.first == cell.first } }
            val incorporatesLockedLetter = lockedLettersUsed > 0
            var isAdjacentToLockedLetter = false

            if (!incorporatesLockedLetter) {
                for ((newCoords, _) in newLetterCells) {
                    val (row, col) = newCoords
                    if ((row > 0 && gridModel[row - 1][col] is CellState.LockedLetter) ||
                        (row < numRows - 1 && gridModel[row + 1][col] is CellState.LockedLetter) ||
                        (col > 0 && gridModel[row][col - 1] is CellState.LockedLetter) ||
                        (col < numColumns - 1 && gridModel[row][col + 1] is CellState.LockedLetter)) {
                        isAdjacentToLockedLetter = true
                        break
                    }
                }
            }
            if (!incorporatesLockedLetter && !isAdjacentToLockedLetter) {
                isPlacementValid = false
                placementErrorMsg = "New words must connect to an existing letter."
            }
        }

        if (isWordValid && isPlacementValid) {
            Toast.makeText(this, "'$word' is a valid word!", Toast.LENGTH_SHORT).show()

            newLetterCells.forEach { (coords, char) ->
                val originalState = coveredCells[coords]
                if (originalState is CellState.MoreTiles) {
                    addTilesToBag(originalState.count, originalState.type)
                }
                gridModel[coords.first][coords.second] = CellState.LockedLetter(char)
            }

            coveredCells.clear()
            isFirstWordPlayed = true

            val hasReachedTarget = newLetterCells.any { (coords, _) -> coords == targetTileCoords }
            if (hasReachedTarget) {
                showNextRoundDialog()
                return
            }

            val tilesNeeded = handSize - playerLetters.size
            val lettersToDraw = minOf(tilesNeeded, tileBag.size)
            if (lettersToDraw > 0) {
                repeat(lettersToDraw) { playerLetters.add(tileBag.removeAt(0)) }
            }
        } else {
            val errorMsg = if (!isWordValid) "'$word' is not a valid word." else placementErrorMsg
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
        render()
    } // Validate words
    private fun addTilesToBag(count: Int, type: TileRewardType) {
        val vowels = "AEIOU"
        val consonants = "BCDFGHJKLMNPQRSTVWXYZ"
        repeat(count) {
            val charToAdd = when (type) {
                TileRewardType.VOWELS -> vowels.random()
                TileRewardType.CONSONANTS -> consonants.random()
                TileRewardType.ANY -> (vowels + consonants).random()
            }
            tileBag.add(charToAdd)
        }
        tileBag.shuffle()
        Toast.makeText(this, "Added $count ${type.name.lowercase()} tiles to the bag!", Toast.LENGTH_SHORT).show()
        updateCounters()
    } // Adds bonus tiles to the tile bag
    private fun updateCounters() {
        roundCounterTextView.text = "Round: $currentRound"
        tilesLeftCounterTextView.text = "Tiles Left: ${tileBag.size}"
        Log.d("UpdateCounters", "Round: $currentRound, Tiles Left: ${tileBag.size}")
    } // Update the UI counters for round and remaining tiles

    private fun findViewCoordinates(view: View): Pair<Int, Int>? {
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                if (cellViews[row][col] == view) {
                    return Pair(row, col)
                }
            }
        }
        return null
    } //

    private fun recallLetters() {
        val recalledLetters = mutableListOf<Char>()
        var wasRecalled = false
        for (row in 0 until numRows) {
            for (col in 0 until numColumns) {
                if (gridModel[row][col] is CellState.Letter) {
                    wasRecalled = true
                    recalledLetters.add((gridModel[row][col] as CellState.Letter).char)
                    val coords = Pair(row, col)
                    gridModel[row][col] = coveredCells.getOrDefault(coords, CellState.Empty)
                    coveredCells.remove(coords)
                }
            }
        }
        playerLetters.addAll(recalledLetters)
        if (wasRecalled) {
            Toast.makeText(this, "Tiles returned.", Toast.LENGTH_SHORT).show()
        }
        render()
    }
    private fun renderLetterTray(){
        letterTrayRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        letterTrayRecyclerView.adapter = LetterTrayAdapter(
            letters = playerLetters,
            isSwapMode = isSwapModeActive,
            selectedForSwap = tilesToSwap,
            onTileClick = { position ->
                if (isSwapModeActive) {
                    if (tilesToSwap.contains(position)) {
                        tilesToSwap.remove(position)
                    } else {
                        tilesToSwap.add(position)
                    }
                    renderLetterTray()
                }
            }
        )
    }
    private fun showNextRoundDialog() {
        AlertDialog.Builder(this)
            .setTitle("Congratulations!")
            .setMessage("You have reached the target and won the round!")
            .setPositiveButton("Next Round") { _, _ ->
                startNewRound()
            }
            .setNegativeButton("Close") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    } // End of game dialogue box
    private fun startNewRound() {
        currentRound++
        isFirstWordPlayed = false
        initializeGridModel()
        initializeTileBag()
        playerLetters.clear()
        val lettersToDraw = minOf(handSize, tileBag.size)
        repeat(lettersToDraw) {
            playerLetters.add(tileBag.removeAt(0))
        }
        render()
    } // Reset game state and start a new round
    private fun handleSwapButtonClick() {
        if (!isSwapModeActive) {
            isSwapModeActive = true
            swapButton.text = "Confirm Swap"
            recallButton.isEnabled = false
            tilesToSwap.clear()
        } else {
            if (tilesToSwap.isNotEmpty()) {
                val lettersToReturn = tilesToSwap.map { playerLetters[it] }.toMutableList()
                tileBag.addAll(lettersToReturn)
                tileBag.shuffle()
                tilesToSwap.sortedDescending().forEach { playerLetters.removeAt(it) }
                val lettersToDraw = minOf(lettersToReturn.size, tileBag.size)
                repeat(lettersToDraw) {
                    playerLetters.add(tileBag.removeAt(0))
                }
            }

            isSwapModeActive = false
            swapButton.text = "Swap"
            recallButton.isEnabled = true
            tilesToSwap.clear()
        }

        render()
    } // Toggle swap mode on and off
    private val gridCellDragListener = View.OnDragListener { view, event ->
        val destinationCell = view as TextView

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
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
                destinationCell.alpha = 1.0f
                val destinationCoords = findViewCoordinates(destinationCell) ?: return@OnDragListener false
                val (destRow, destCol) = destinationCoords
                val originalState = gridModel[destRow][destCol]

                if (originalState !is CellState.Empty &&
                    originalState !is CellState.Start &&
                    originalState !is CellState.Target &&
                    originalState !is CellState.MoreTiles) {
                    return@OnDragListener false
                }

                when (event.clipDescription.label) {
                    "TRAY_DRAG" -> {
                        val position = event.clipData.description.extras.getInt("position")
                        val droppedLetter = playerLetters.removeAt(position)
                        coveredCells[destinationCoords] = originalState
                        gridModel[destRow][destCol] = CellState.Letter(droppedLetter)
                        (event.localState as? View)?.visibility = View.INVISIBLE
                    }
                    "GRID_DRAG" -> {
                        val item: ClipData.Item = event.clipData.getItemAt(0)
                        val (sourceRow, sourceCol) = item.text.toString().split(",").map { it.toInt() }
                        val sourceState = gridModel[sourceRow][sourceCol]
                        if (sourceState is CellState.Letter) {
                            coveredCells[destinationCoords] = originalState
                            gridModel[destRow][destCol] = sourceState
                            val sourceCoords = Pair(sourceRow, sourceCol)
                            gridModel[sourceRow][sourceCol] = coveredCells.getOrDefault(sourceCoords, CellState.Empty)
                            coveredCells.remove(sourceCoords)
                        }
                    }
                }
                render()
                true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                destinationCell.alpha = 1.0f
                if (!event.result) {
                    (event.localState as? View)?.visibility = View.VISIBLE
                }
                true
            }
            else -> false
        }
    } // Drag listener for grid cells
    private val letterTrayDragListener = View.OnDragListener{ view, event ->
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                        event.clipDescription.label == "GRID_DRAG"
            }

            DragEvent.ACTION_DRAG_ENTERED -> {
                view.setBackgroundColor(Color.parseColor("#DDDDDD"))
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                view.setBackgroundColor(Color.TRANSPARENT)
                true
            }
            DragEvent.ACTION_DROP -> {
                view.setBackgroundColor(Color.TRANSPARENT)
                val item: ClipData.Item = event.clipData.getItemAt(0)
                val (sourceRow, sourceCol) = item.text.toString().split(",").map { it.toInt() }
                val sourceState = gridModel[sourceRow][sourceCol]
                if (sourceState is CellState.Letter) {
                    playerLetters.add(sourceState.char)
                    val sourceCoords = Pair(sourceRow, sourceCol)
                    gridModel[sourceRow][sourceCol] = coveredCells.getOrDefault(sourceCoords, CellState.Empty)
                    coveredCells.remove(sourceCoords)
                    render()
                }
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                view.setBackgroundColor(Color.TRANSPARENT)
                if (!event.result) {
                    (event.localState as? View)?.visibility = View.VISIBLE
                }
                true
            }
            else -> false
        }
    } // Drag listener for letter tray
}
