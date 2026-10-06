
package com.example.liquidglass

import com.liquidglass.app.R
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

open class LiquidGlassListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    
    enum class Position { SINGLE, FIRST, MIDDLE, LAST }

    val leadingImageView: ImageView = ImageView(context)
    val headlineTextView: TextView = TextView(context)
    val supportingTextView: TextView = TextView(context)
    val trailingTextView: TextView = TextView(context)
    val trailingImageView: ImageView = ImageView(context)

    private val container = LinearLayout(context)
    private val row = LinearLayout(context)
    private val textColumn = LinearLayout(context)
    private val expandSection = LinearLayout(context)

    
    private val contentSlot = FrameLayout(context)

    
    private var autoColors = true

    private var expandAnimator: ValueAnimator? = null

    
    var groupCornerRadius: Float = dpF(20f)
        set(value) {
            field = value.coerceAtLeast(0f)
            applyShape()
        }

    
    var innerCornerRadius: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
            applyShape()
        }

    var position: Position = Position.SINGLE
        set(value) {
            field = value
            applyShape()
        }

    
    fun updatePosition(index: Int, count: Int) {
        position = positionFor(index, count)
    }

    
    var contentView: View? = null
        set(value) {
            if (field === value) return
            contentSlot.removeAllViews()
            field = value
            if (value != null) {
                (value.parent as? ViewGroup)?.removeView(value)
                val lp = value.layoutParams
                contentSlot.addView(value, when (lp) {
                    is FrameLayout.LayoutParams -> lp
                    is MarginLayoutParams -> FrameLayout.LayoutParams(lp)
                    null -> FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                    else -> FrameLayout.LayoutParams(lp)
                })
            }
            row.visibility = if (value == null) View.VISIBLE else View.GONE
            contentSlot.visibility = if (value == null) View.GONE else View.VISIBLE
            updateMinHeight()
        }

    var headline: CharSequence
        get() = headlineTextView.text
        set(value) {
            headlineTextView.text = value
        }

    var supportingText: CharSequence?
        get() = supportingTextView.text
        set(value) {
            supportingTextView.text = value
            supportingTextView.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
            updateMinHeight()
        }

    var trailingText: CharSequence?
        get() = trailingTextView.text
        set(value) {
            trailingTextView.text = value
            trailingTextView.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

    var leadingIcon: Drawable?
        get() = leadingImageView.drawable
        set(value) {
            leadingImageView.setImageDrawable(value)
            leadingImageView.visibility = if (value == null) View.GONE else View.VISIBLE
        }

    var trailingIcon: Drawable?
        get() = trailingImageView.drawable
        set(value) {
            trailingImageView.setImageDrawable(value)
            trailingImageView.visibility = if (value == null) View.GONE else View.VISIBLE
        }

    

    
    var expandedView: View? = null
        set(value) {
            if (field === value) return
            expandSection.removeAllViews()
            field = value
            if (value != null) {
                expandSection.addView(value, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                if (isExpanded) {
                    expandSection.visibility = View.VISIBLE
                    expandSection.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                    requestLayout()
                }
            } else {
                expandSection.visibility = View.GONE
                isExpandedInternal = false
            }
        }

    
    var expandOnClick = true

    
    var rotateTrailingIconOnExpand = true

    
    var expandDuration = 280L

    private var isExpandedInternal = false

    val isExpanded: Boolean
        get() = isExpandedInternal

    
    var onExpandedChanged: ((Boolean) -> Unit)? = null

    fun toggleExpanded(animate: Boolean = true) = setExpanded(!isExpandedInternal, animate)

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        if (expandedView == null) return
        if (expanded == isExpandedInternal) return
        isExpandedInternal = expanded
        expandAnimator?.cancel()

        val lp = expandSection.layoutParams
        val startH = if (expandSection.visibility == View.VISIBLE) expandSection.height else 0
        val targetH = if (expanded) measureExpandedHeight() else 0

        if (rotateTrailingIconOnExpand && trailingImageView.visibility == View.VISIBLE) {
            trailingImageView.animate()
                .rotation(if (expanded) 180f else 0f)
                .setDuration(if (animate) expandDuration else 0L)
                .start()
        }

        if (!animate) {
            expandSection.visibility = if (expanded) View.VISIBLE else View.GONE
            lp.height = if (expanded) LinearLayout.LayoutParams.WRAP_CONTENT else 0
            expandSection.layoutParams = lp
            requestLayout()
            onExpandedChanged?.invoke(expanded)
            return
        }

        expandSection.visibility = View.VISIBLE
        lp.height = startH
        expandSection.layoutParams = lp
        expandAnimator = ValueAnimator.ofInt(startH, targetH).apply {
            duration = expandDuration
            
            interpolator = if (expanded) OvershootInterpolator(0.6f) else DecelerateInterpolator()
            addUpdateListener {
                lp.height = (it.animatedValue as Int).coerceAtLeast(0)
                expandSection.layoutParams = lp
                invalidate()
            }
            doOnEnd {
                if (expanded) {
                    lp.height = LinearLayout.LayoutParams.WRAP_CONTENT
                } else {
                    lp.height = 0
                    expandSection.visibility = View.GONE
                }
                expandSection.layoutParams = lp
                requestLayout()
            }
            start()
        }
        onExpandedChanged?.invoke(expanded)
    }

    private fun measureExpandedHeight(): Int {
        val innerWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val widthSpec = if (innerWidth > 0) {
            MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY)
        } else {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }
        
        val lp = expandSection.layoutParams
        val saved = lp.height
        lp.height = LinearLayout.LayoutParams.WRAP_CONTENT
        expandSection.layoutParams = lp
        expandSection.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val h = expandSection.measuredHeight
        lp.height = saved
        expandSection.layoutParams = lp
        return h
    }

    override fun performClick(): Boolean {
        if (expandOnClick && expandedView != null) toggleExpanded(true)
        return super.performClick()
    }

    init {
        isClickable = true
        isFocusable = true
        
        enablePressEffect = false
        
        
        bevelWidth = dpF(14f)
        refractionHeight = dpF(7f)
        edgeSoftness = dpF(3f)
        blurAmount = 0.10f

        val hPad = dp(16)
        container.orientation = LinearLayout.VERTICAL

        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(hPad, dp(12), hPad, dp(12))

        leadingImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        leadingImageView.visibility = View.GONE
        row.addView(leadingImageView, LinearLayout.LayoutParams(dp(24), dp(24)).apply {
            marginEnd = dp(16)
        })

        textColumn.orientation = LinearLayout.VERTICAL
        textColumn.gravity = Gravity.CENTER_VERTICAL
        headlineTextView.textSize = 16f
        headlineTextView.maxLines = 1
        
        headlineTextView.includeFontPadding = false
        supportingTextView.textSize = 14f
        supportingTextView.includeFontPadding = false
        supportingTextView.maxLines = 2
        supportingTextView.visibility = View.GONE
        textColumn.addView(headlineTextView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        textColumn.addView(supportingTextView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) })
        row.addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        trailingTextView.textSize = 12f
        trailingTextView.typeface = Typeface.DEFAULT_BOLD
        trailingTextView.maxLines = 1
        trailingTextView.visibility = View.GONE
        row.addView(trailingTextView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(16) })

        trailingImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        trailingImageView.visibility = View.GONE
        row.addView(trailingImageView, LinearLayout.LayoutParams(dp(24), dp(24)).apply {
            marginStart = dp(16)
        })

        container.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        contentSlot.visibility = View.GONE
        container.addView(contentSlot, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        expandSection.orientation = LinearLayout.VERTICAL
        expandSection.setPadding(hPad, 0, hPad, dp(16))
        expandSection.visibility = View.GONE
        container.addView(expandSection, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        ))

        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        updateMinHeight()
        applyAutoColors(isOverLightBackground)
        applyShape()

        attrs?.let { parseItemAttributes(context, it) }
    }

    private fun parseItemAttributes(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassListItem)
        try {
            ta.getText(R.styleable.LiquidGlassListItem_glassHeadline)?.let { headline = it }
            ta.getText(R.styleable.LiquidGlassListItem_glassSupportingText)?.let { supportingText = it }
            ta.getText(R.styleable.LiquidGlassListItem_glassTrailingText)?.let { trailingText = it }
            val leadRes = ta.getResourceId(R.styleable.LiquidGlassListItem_glassLeadingIcon, 0)
            if (leadRes != 0) leadingIcon = ContextCompat.getDrawable(context, leadRes)
            val trailRes = ta.getResourceId(R.styleable.LiquidGlassListItem_glassTrailingIcon, 0)
            if (trailRes != 0) trailingIcon = ContextCompat.getDrawable(context, trailRes)
            if (ta.hasValue(R.styleable.LiquidGlassListItem_glassGroupCornerRadius)) {
                groupCornerRadius = ta.getDimension(R.styleable.LiquidGlassListItem_glassGroupCornerRadius, groupCornerRadius)
            }
            if (ta.hasValue(R.styleable.LiquidGlassListItem_glassInnerCornerRadius)) {
                innerCornerRadius = ta.getDimension(R.styleable.LiquidGlassListItem_glassInnerCornerRadius, innerCornerRadius)
            }
            position = when (ta.getInt(R.styleable.LiquidGlassListItem_glassListPosition, 0)) {
                1 -> Position.FIRST
                2 -> Position.MIDDLE
                3 -> Position.LAST
                else -> Position.SINGLE
            }
            if (ta.hasValue(R.styleable.LiquidGlassListItem_android_textColor)) {
                setTextColor(ta.getColor(R.styleable.LiquidGlassListItem_android_textColor, Color.WHITE))
            }
        } finally {
            ta.recycle()
        }
    }

    fun setLeadingIconResource(resId: Int) {
        leadingIcon = if (resId == 0) null else ContextCompat.getDrawable(context, resId)
    }

    fun setTrailingIconResource(resId: Int) {
        trailingIcon = if (resId == 0) null else ContextCompat.getDrawable(context, resId)
    }

    
    fun setHeadlineTextSize(sp: Float) {
        headlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    
    fun setTextColor(color: Int) {
        autoColors = false
        val secondary = Color.argb(
            (Color.alpha(color) * 0.7f).toInt(), Color.red(color), Color.green(color), Color.blue(color)
        )
        headlineTextView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        supportingTextView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        headlineTextView.setTextColor(color)
        supportingTextView.setTextColor(secondary)
        trailingTextView.setTextColor(secondary)
        leadingImageView.setColorFilter(color)
        trailingImageView.setColorFilter(secondary)
    }

    override fun onAppearanceChanged(isOverLight: Boolean) {
        if (autoColors) applyAutoColors(isOverLight)
    }

    
    private fun applyAutoColors(isOverLight: Boolean) {
        val primary: Int
        val secondary: Int
        if (isOverLight) {
            primary = 0xDE000000.toInt()
            secondary = 0x99000000.toInt()
            headlineTextView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            supportingTextView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            primary = Color.WHITE
            secondary = 0xB3FFFFFF.toInt()
            headlineTextView.setShadowLayer(8f, 0f, 2f, Color.BLACK)
            supportingTextView.setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        headlineTextView.setTextColor(primary)
        supportingTextView.setTextColor(secondary)
        trailingTextView.setTextColor(secondary)
        leadingImageView.setColorFilter(primary)
        trailingImageView.setColorFilter(secondary)
    }

    
    private fun applyShape() {
        val g = groupCornerRadius
        val i = innerCornerRadius
        when (position) {
            Position.SINGLE -> {
                setCornerRadii(g, g, g, g)
                setFlatEdges(top = false, right = false, bottom = false, left = false)
            }
            Position.FIRST -> {
                setCornerRadii(g, g, i, i)
                setFlatEdges(top = false, right = false, bottom = true, left = false)
            }
            Position.MIDDLE -> {
                setCornerRadii(i, i, i, i)
                setFlatEdges(top = true, right = false, bottom = true, left = false)
            }
            Position.LAST -> {
                setCornerRadii(i, i, g, g)
                setFlatEdges(top = true, right = false, bottom = false, left = false)
            }
        }
    }

    
    override fun onFinishInflate() {
        super.onFinishInflate()
        val children = (0 until childCount).map { getChildAt(it) }.filter { it !== container }
        if (children.isEmpty()) return
        children.forEach { removeView(it) }
        contentView = children.singleOrNull() ?: FrameLayout(context).apply {
            
            children.forEach { addView(it, it.layoutParams) }
        }
    }

    private fun updateMinHeight() {
        if (contentView != null) {
            minimumHeight = 0
            return
        }
        minimumHeight = dp(if (supportingTextView.visibility == View.VISIBLE) 72 else 56)
    }

    private inline fun ValueAnimator.doOnEnd(crossinline block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * resources.displayMetrics.density

    companion object {
        
        @JvmStatic
        fun applyGroupPositions(items: List<LiquidGlassListItem>) {
            val count = items.size
            items.forEachIndexed { index, item -> item.position = positionFor(index, count) }
        }

        
        @JvmStatic
        fun positionFor(index: Int, count: Int): Position = when {
            count <= 1 -> Position.SINGLE
            index == 0 -> Position.FIRST
            index == count - 1 -> Position.LAST
            else -> Position.MIDDLE
        }
    }
}
