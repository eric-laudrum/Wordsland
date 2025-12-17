package com.example.wordsland

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import android.view.View

// This code is correct Kotlin and belongs in a .kt file
class TileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var onTileDragListener: (() -> Unit)? = null

    fun setOnTileDragListener(listener: () -> Unit) {
        this.onTileDragListener = listener
    }

    override fun performClick(): Boolean {
        // First, call the superclass implementation. This is important for accessibility.
        super.performClick()

        // Now, trigger our custom drag action.
        onTileDragListener?.invoke()

        // Return true to indicate the click was handled.
        return true
    }
}