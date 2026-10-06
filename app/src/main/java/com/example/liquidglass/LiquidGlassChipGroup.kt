
package com.example.liquidglass

import com.liquidglass.app.R
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

open class LiquidGlassChipGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    fun interface OnCheckedStateChangeListener {
        fun onCheckedChanged(group: LiquidGlassChipGroup, checkedIds: List<Int>)
    }

    
    var chipSpacingHorizontal: Int = dp(8)
        set(value) {
            field = value.coerceAtLeast(0)
            requestLayout()
        }

    
    var chipSpacingVertical: Int = dp(8)
        set(value) {
            field = value.coerceAtLeast(0)
            requestLayout()
        }

    
    var isSingleLine = false
        set(value) {
            field = value
            requestLayout()
        }

    
    var isSingleSelection = false
        set(value) {
            field = value
            if (value) {
                val first = checkedChips().firstOrNull()
                if (first != null) onChipCheckedChanged(first, true)
            }
        }

    
    var isSelectionRequired = false

    
    var backdropSource: View? = null
        set(value) {
            field = value
            pendingBackdropSourceId = 0
            forEachChip { it.backdropSource = value }
        }

    
    var enableDynamicBackground: Boolean = false
        set(value) {
            field = value
            forEachChip { it.enableDynamicBackground = value }
        }

    
    private var pendingBackdropSourceId = 0

    private var checkedListener: OnCheckedStateChangeListener? = null

    
    private var updatingChecks = false

    
    val checkedChipIds: List<Int>
        get() = checkedChips().map { it.id }

    
    val checkedChipId: Int
        get() = if (isSingleSelection) checkedChips().firstOrNull()?.id ?: View.NO_ID else View.NO_ID

    init {
        attrs?.let { parseGroupAttributes(context, it) }
    }

    private fun parseGroupAttributes(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassChipGroup)
        try {
            chipSpacingHorizontal = ta.getDimensionPixelSize(
                R.styleable.LiquidGlassChipGroup_glassChipSpacingHorizontal, chipSpacingHorizontal
            )
            chipSpacingVertical = ta.getDimensionPixelSize(
                R.styleable.LiquidGlassChipGroup_glassChipSpacingVertical, chipSpacingVertical
            )
            isSingleLine = ta.getBoolean(R.styleable.LiquidGlassChipGroup_glassSingleLine, isSingleLine)
            isSingleSelection = ta.getBoolean(R.styleable.LiquidGlassChipGroup_glassSingleSelection, isSingleSelection)
            isSelectionRequired = ta.getBoolean(R.styleable.LiquidGlassChipGroup_glassSelectionRequired, isSelectionRequired)
            pendingBackdropSourceId = ta.getResourceId(R.styleable.LiquidGlassChipGroup_backdropSourceId, 0)
        } finally {
            ta.recycle()
        }
    }

    
    fun setChipSpacing(px: Int) {
        chipSpacingHorizontal = px
        chipSpacingVertical = px
    }

    fun setOnCheckedStateChangeListener(listener: OnCheckedStateChangeListener?) {
        checkedListener = listener
    }

    
    fun check(id: Int) {
        (findViewById<View>(id) as? LiquidGlassChip)?.takeIf { it.parent === this }?.isChecked = true
    }

    
    fun clearCheck() {
        val before = checkedChips()
        if (before.isEmpty()) return
        updatingChecks = true
        try {
            before.forEach { it.isChecked = false }
        } finally {
            updatingChecks = false
        }
        notifyCheckedChanged()
    }

    
    internal fun canUncheck(chip: LiquidGlassChip): Boolean =
        !isSelectionRequired || !chip.isChecked || checkedChips().size > 1

    internal fun onChipCheckedChanged(chip: LiquidGlassChip, checked: Boolean) {
        if (updatingChecks) return
        if (checked && isSingleSelection) {
            updatingChecks = true
            try {
                forEachChip { if (it !== chip && it.isChecked) it.isChecked = false }
            } finally {
                updatingChecks = false
            }
        }
        notifyCheckedChanged()
    }

    private fun notifyCheckedChanged() {
        checkedListener?.onCheckedChanged(this, checkedChipIds)
    }

    private fun checkedChips(): List<LiquidGlassChip> {
        val result = ArrayList<LiquidGlassChip>()
        forEachChip { if (it.isChecked) result += it }
        return result
    }

    private inline fun forEachChip(block: (LiquidGlassChip) -> Unit) {
        for (i in 0 until childCount) {
            (getChildAt(i) as? LiquidGlassChip)?.let(block)
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
        if (child !is LiquidGlassChip) return
        
        if (child.id == View.NO_ID) child.id = View.generateViewId()
        backdropSource?.let { child.backdropSource = it }
        if (enableDynamicBackground) child.enableDynamicBackground = true
        if (child.isChecked) onChipCheckedChanged(child, true)
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child is LiquidGlassChip && child.isChecked) notifyCheckedChanged()
    }

    

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val limit = if (isSingleLine || widthMode == MeasureSpec.UNSPECIFIED) {
            Int.MAX_VALUE
        } else {
            widthSize - paddingEnd
        }
        var x = paddingStart
        var y = paddingTop
        var lineHeight = 0
        var maxRight = paddingStart
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = child.layoutParams as MarginLayoutParams
            val w = child.measuredWidth + lp.marginStart + lp.marginEnd
            val h = child.measuredHeight + lp.topMargin + lp.bottomMargin
            if (x > paddingStart && x + w > limit) {
                x = paddingStart
                y += lineHeight + chipSpacingVertical
                lineHeight = 0
            }
            x += w
            maxRight = max(maxRight, x)
            lineHeight = max(lineHeight, h)
            x += chipSpacingHorizontal
        }
        setMeasuredDimension(
            resolveSize(maxRight + paddingEnd, widthMeasureSpec),
            resolveSize(y + lineHeight + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val limit = if (isSingleLine) Int.MAX_VALUE else width - paddingEnd
        var x = paddingStart
        var y = paddingTop
        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as MarginLayoutParams
            val w = child.measuredWidth + lp.marginStart + lp.marginEnd
            val h = child.measuredHeight + lp.topMargin + lp.bottomMargin
            if (x > paddingStart && x + w > limit) {
                x = paddingStart
                y += lineHeight + chipSpacingVertical
                lineHeight = 0
            }
            
            val start = x + lp.marginStart
            val left = if (rtl) width - start - child.measuredWidth else start
            val top = y + lp.topMargin
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
            x += w + chipSpacingHorizontal
            lineHeight = max(lineHeight, h)
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams =
        if (p is MarginLayoutParams) MarginLayoutParams(p) else MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "LiquidGlassChipGroup"
    }
}
