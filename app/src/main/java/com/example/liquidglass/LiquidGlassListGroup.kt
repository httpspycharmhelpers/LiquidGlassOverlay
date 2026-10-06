
package com.example.liquidglass

import com.liquidglass.app.R
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout

open class LiquidGlassListGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Style { MERGED, SEPARATED }

    var style: Style = Style.MERGED
        set(value) {
            if (field == value) return
            field = value
            applyPositions()
        }

    
    var itemSpacing: Int = dp(8)
        set(value) {
            field = value.coerceAtLeast(0)
            applyPositions()
        }

    
    var cornerRadius: Float = AUTO_CORNER
        set(value) {
            field = value
            applyPositions()
        }

    
    var backdropSource: View? = null
        set(value) {
            field = value
            pendingBackdropSourceId = 0
            forEachItem { it.backdropSource = value }
        }

    
    var enableDynamicBackground: Boolean = false
        set(value) {
            field = value
            forEachItem { it.enableDynamicBackground = value }
        }

    
    private var pendingBackdropSourceId = 0

    init {
        orientation = VERTICAL
        attrs?.let { parseGroupAttributes(context, it) }
    }

    private fun parseGroupAttributes(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassListGroup)
        try {
            style = if (ta.getInt(R.styleable.LiquidGlassListGroup_glassListStyle, 0) == 1) {
                Style.SEPARATED
            } else {
                Style.MERGED
            }
            itemSpacing = ta.getDimensionPixelSize(R.styleable.LiquidGlassListGroup_glassItemSpacing, itemSpacing)
            if (ta.hasValue(R.styleable.LiquidGlassListGroup_cornerRadius)) {
                cornerRadius = ta.getDimension(R.styleable.LiquidGlassListGroup_cornerRadius, cornerRadius)
            }
            pendingBackdropSourceId = ta.getResourceId(R.styleable.LiquidGlassListGroup_backdropSourceId, 0)
        } finally {
            ta.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        
        if (backdropSource == null && pendingBackdropSourceId != 0) {
            val id = pendingBackdropSourceId
            rootView?.findViewById<View>(id)?.let { backdropSource = it }
                ?: Log.w(TAG, "backdropSourceId 未在视图树中找到")
            pendingBackdropSourceId = 0
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child is LiquidGlassListItem) {
            backdropSource?.let { child.backdropSource = it }
            if (enableDynamicBackground) child.enableDynamicBackground = true
        }
        applyPositions()
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        applyPositions()
    }

    
    fun applyPositions() {
        val radius = if (cornerRadius >= 0f) {
            cornerRadius
        } else {
            dpF(if (style == Style.MERGED) 20f else 12f)
        }
        var itemCount = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c is LiquidGlassListItem && c.visibility != View.GONE) itemCount++
        }
        var itemIndex = 0
        var visibleIndex = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val gap = if (visibleIndex == 0 || style == Style.MERGED) 0 else itemSpacing
            val lp = child.layoutParams as? MarginLayoutParams
            if (lp != null && lp.topMargin != gap) {
                lp.topMargin = gap
                child.layoutParams = lp
            }
            if (child is LiquidGlassListItem) {
                val pos = if (style == Style.MERGED) {
                    LiquidGlassListItem.positionFor(itemIndex, itemCount)
                } else {
                    LiquidGlassListItem.Position.SINGLE
                }
                if (child.groupCornerRadius != radius) child.groupCornerRadius = radius
                if (child.position != pos) child.position = pos
                itemIndex++
            }
            visibleIndex++
        }
    }

    private inline fun forEachItem(block: (LiquidGlassListItem) -> Unit) {
        for (i in 0 until childCount) {
            (getChildAt(i) as? LiquidGlassListItem)?.let(block)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * resources.displayMetrics.density

    companion object {
        private const val TAG = "LiquidGlassListGroup"

        
        const val AUTO_CORNER = -1f
    }
}
