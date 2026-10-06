
package com.example.liquidglass

import com.liquidglass.app.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

open class LiquidGlassTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    
    class Tab internal constructor(internal val owner: LiquidGlassTabLayout) {

        internal val view = LinearLayout(owner.context)
        internal val iconView = ImageView(owner.context)
        internal val labelView = TextView(owner.context)

        var text: CharSequence? = null
            set(value) {
                field = value
                labelView.text = value
                labelView.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
                onContentChanged()
            }

        
        var icon: Drawable? = null
            set(value) {
                field = value
                iconView.setImageDrawable(value)
                iconView.visibility = if (value == null) View.GONE else View.VISIBLE
                onContentChanged()
            }

        
        var contentDescription: CharSequence? = null
            set(value) {
                field = value
                onContentChanged()
            }

        
        var tag: Any? = null

        
        val position: Int
            get() = owner.indexOfTab(this)

        val isSelected: Boolean
            get() {
                val p = position
                return p >= 0 && p == owner.selectedTabPosition
            }

        fun select() = owner.selectTab(this)

        init {
            val d = owner.resources.displayMetrics.density
            view.orientation = LinearLayout.HORIZONTAL
            view.gravity = Gravity.CENTER
            iconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            iconView.visibility = View.GONE
            view.addView(iconView, LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt()))
            labelView.textSize = 14f
            labelView.maxLines = 1
            labelView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            labelView.visibility = View.GONE
            view.addView(labelView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        private fun onContentChanged() {
            
            val gap = if (iconView.visibility == View.VISIBLE && labelView.visibility == View.VISIBLE) {
                (6 * owner.resources.displayMetrics.density).toInt()
            } else {
                0
            }
            (iconView.layoutParams as LinearLayout.LayoutParams).marginEnd = gap
            view.contentDescription = contentDescription ?: text
            owner.onTabContentChanged()
        }
    }

    fun interface OnTabSelectedListener {
        fun onTabSelected(tab: Tab)
    }

    fun interface OnTabReselectedListener {
        fun onTabReselected(tab: Tab)
    }

    
    var tabMode: Int = MODE_FIXED
        set(value) {
            if (field == value) return
            field = value
            scroller.fixed = value == MODE_FIXED
            tabs.forEach { applyTabLayout(it) }
            requestLayout()
        }

    
    var selectedTintColor: Int? = null
        set(value) {
            if (field != value) {
                field = value
                updateTabStyles()
            }
        }

    val tabCount: Int
        get() = tabs.size

    
    val selectedTabPosition: Int
        get() = selected

    private val tabs = ArrayList<Tab>()
    private var selected = -1

    private val scroller = TabScroller(context)
    private val tabsRow = LinearLayout(context)

    
    private val droplet = Droplet(context)

    
    private var indicatorPos = 0f
    private var indicatorAnimator: ValueAnimator? = null

    private var overLightAppearance = false

    private val selectedListeners = ArrayList<OnTabSelectedListener>()
    private val reselectedListeners = ArrayList<OnTabReselectedListener>()

    init {
        
        enablePressEffect = false

        val pad = dp(4)
        setPadding(pad, pad, pad, pad)

        tabsRow.orientation = LinearLayout.HORIZONTAL
        scroller.isHorizontalScrollBarEnabled = false
        scroller.overScrollMode = View.OVER_SCROLL_NEVER
        scroller.isFillViewport = true
        scroller.fixed = true
        scroller.addView(tabsRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scroller.setOnScrollChangeListener { _, _, _, _, _ ->
            
            placeDroplet(clamp = indicatorAnimator?.isRunning == true)
        }

        droplet.apply {
            enablePressEffect = false
            
            enableBackdropBlur = false
            cornerRadius = 999f
            bevelWidth = dpF(8)
            refractionHeight = dpF(4)
            dispersionStrength = 0.04f
            visibility = View.GONE
        }
        
        
        addView(droplet, LayoutParams(0, 0, Gravity.TOP or Gravity.LEFT))

        
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val r = cornerRadiiPx().minOrNull() ?: 0f
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        clipToOutline = true

        overLightAppearance = isOverLightBackground
        attrs?.let { parseTabLayoutAttributes(context, it) }
    }

    private fun parseTabLayoutAttributes(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassTabLayout)
        try {
            tabMode = if (ta.getInt(R.styleable.LiquidGlassTabLayout_glassTabMode, 0) == 1) {
                MODE_SCROLLABLE
            } else {
                MODE_FIXED
            }
            val entriesId = ta.getResourceId(R.styleable.LiquidGlassTabLayout_glassTabEntries, 0)
            if (entriesId != 0) resources.getTextArray(entriesId).forEach { addTab(it) }
        } finally {
            ta.recycle()
        }
    }

    

    fun newTab(): Tab = Tab(this)

    
    @JvmOverloads
    fun addTab(tab: Tab, setSelected: Boolean = tabs.isEmpty()) {
        require(tab.owner === this) { "Tab belongs to a different LiquidGlassTabLayout" }
        if (tab in tabs) return
        tabs += tab
        applyTabLayout(tab)
        tab.view.setOnClickListener { selectTab(tab) }
        tabsRow.addView(tab.view)
        updateTabStyles()
        if (setSelected) selectTab(tabs.size - 1, animate = false)
    }

    
    fun addTab(text: CharSequence): Tab = newTab().also {
        it.text = text
        addTab(it)
    }

    fun getTabAt(index: Int): Tab? = tabs.getOrNull(index)

    fun removeTabAt(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        tabs.removeAt(index)
        tabsRow.removeView(tab.view)
        when {
            tabs.isEmpty() -> {
                selected = -1
                indicatorAnimator?.cancel()
                droplet.visibility = View.GONE
            }
            index < selected -> {
                selected--
                indicatorPos = selected.toFloat()
            }
            index == selected -> {
                selected = -1
                selectTab(min(index, tabs.size - 1), animate = false)
            }
        }
        updateTabStyles()
    }

    fun removeAllTabs() {
        tabs.clear()
        tabsRow.removeAllViews()
        selected = -1
        indicatorAnimator?.cancel()
        droplet.visibility = View.GONE
    }

    internal fun indexOfTab(tab: Tab): Int = tabs.indexOf(tab)

    internal fun onTabContentChanged() {
        updateTabStyles()
        requestLayout()
    }

    fun addOnTabSelectedListener(listener: OnTabSelectedListener) {
        if (listener !in selectedListeners) selectedListeners += listener
    }

    fun removeOnTabSelectedListener(listener: OnTabSelectedListener) {
        selectedListeners -= listener
    }

    fun addOnTabReselectedListener(listener: OnTabReselectedListener) {
        if (listener !in reselectedListeners) reselectedListeners += listener
    }

    fun removeOnTabReselectedListener(listener: OnTabReselectedListener) {
        reselectedListeners -= listener
    }

    

    fun selectTab(tab: Tab) {
        val index = tabs.indexOf(tab)
        if (index >= 0) selectTab(index, animate = true)
    }

    
    @JvmOverloads
    fun selectTab(index: Int, animate: Boolean = true) {
        val tab = tabs.getOrNull(index) ?: return
        if (index == selected) {
            ArrayList(reselectedListeners).forEach { it.onTabReselected(tab) }
            return
        }
        val first = selected < 0
        selected = index
        updateTabStyles()
        if (animate && !first && isLaidOut) {
            animateIndicatorTo(index)
        } else {
            indicatorAnimator?.cancel()
            indicatorPos = index.toFloat()
            placeDroplet(clamp = true)
        }
        scrollToPosition(index.toFloat(), smooth = animate)
        ArrayList(selectedListeners).forEach { it.onTabSelected(tab) }
    }

    
    @JvmOverloads
    fun setScrollPosition(position: Int, positionOffset: Float, updateSelectedTab: Boolean = true) {
        if (position !in tabs.indices) return
        indicatorAnimator?.cancel()
        droplet.scaleX = 1f
        droplet.scaleY = 1f
        val offset = if (position < tabs.size - 1) positionOffset.coerceIn(0f, 1f) else 0f
        indicatorPos = position + offset
        if (updateSelectedTab) {
            val nearest = if (offset >= 0.5f) position + 1 else position
            if (nearest != selected) {
                selected = nearest
                updateTabStyles()
            }
        }
        placeDroplet(clamp = true)
        scrollToPosition(indicatorPos, smooth = false)
    }

    private fun updateTabStyles() {
        val selectedColor = selectedTintColor
            ?: if (overLightAppearance) 0xE6000000.toInt() else Color.WHITE
        val normalColor = if (overLightAppearance) 0x8C000000.toInt() else 0xB8FFFFFF.toInt()
        tabs.forEachIndexed { index, tab ->
            val color = if (index == selected) selectedColor else normalColor
            tab.labelView.setTextColor(color)
            tab.iconView.imageTintList = ColorStateList.valueOf(color)
            tab.view.isSelected = index == selected
        }
        
        droplet.invalidate()
    }

    override fun onAppearanceChanged(isOverLight: Boolean) {
        overLightAppearance = isOverLight
        droplet.overLight = isOverLight
        updateTabStyles()
    }

    private fun applyTabLayout(tab: Tab) {
        val fixed = tabMode == MODE_FIXED
        tab.view.layoutParams = if (fixed) {
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        } else {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val h = if (fixed) dp(8) else dp(16)
        tab.view.setPadding(h, dp(10), h, dp(10))
        tab.view.minimumWidth = if (fixed) 0 else dp(72)
        tab.labelView.ellipsize = if (fixed) android.text.TextUtils.TruncateAt.END else null
    }

    

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        invalidateOutline()
        
        if (indicatorAnimator?.isRunning != true) placeDroplet(clamp = true)
    }

    
    private fun indicatorGeometry(pos: Float): Pair<Float, Float> {
        val last = tabs.size - 1
        val i = floor(pos).toInt().coerceIn(0, last)
        val f = pos - i
        val a = tabs[i].view
        val next = if (i < last) tabs[i + 1].view else null
        return if (next != null) {
            
            (a.left + (next.left - a.left) * f) to (a.width + (next.width - a.width) * f)
        } else {
            
            (a.left + a.width * f) to a.width.toFloat()
        }
    }

    
    private fun placeDroplet(clamp: Boolean) {
        val ref = tabs.firstOrNull()?.view
        if (selected < 0 || ref == null || ref.width <= 0) {
            droplet.visibility = View.GONE
            return
        }
        droplet.visibility = View.VISIBLE
        val (x, w) = indicatorGeometry(indicatorPos)
        resizeDroplet(w.roundToInt().coerceAtLeast(1), ref.height)
        var tx = scroller.left + tabsRow.left + x - scroller.scrollX - paddingLeft
        if (clamp) tx = clampDropletX(tx, droplet.scaleX)
        droplet.translationX = tx
        droplet.translationY = (scroller.top + tabsRow.top + ref.top - paddingTop).toFloat()
        
        droplet.invalidate()
    }

    
    private fun resizeDroplet(w: Int, h: Int) {
        val lp = droplet.layoutParams as LayoutParams
        if (lp.width == w && lp.height == h && droplet.width == w && droplet.height == h) return
        lp.width = w
        lp.height = h
        droplet.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
        droplet.layout(paddingLeft, paddingTop, paddingLeft + w, paddingTop + h)
    }

    
    private fun clampDropletX(x: Float, scaleX: Float): Float {
        val w = droplet.width.toFloat()
        if (w <= 0f || scroller.width <= 0) return x
        val bulge = (scaleX - 1f) * w / 2f
        val minX = (scroller.left - paddingLeft) + bulge
        val maxX = (scroller.right - paddingLeft) - w - bulge
        return if (minX <= maxX) x.coerceIn(minX, maxX) else x
    }

    
    private fun animateIndicatorTo(index: Int) {
        indicatorAnimator?.cancel()
        val start = indicatorPos
        val dist = index - start
        if (abs(dist) < 0.001f) {
            indicatorPos = index.toFloat()
            placeDroplet(clamp = true)
            return
        }
        val stretch = 0.22f * min(1f, abs(dist) / 3f)
        val overshoot = OvershootInterpolator(1.1f)
        indicatorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val s = sin(PI.toFloat() * min(t * 1.15f, 1f))
                droplet.scaleX = 1f + stretch * s
                droplet.scaleY = 1f - stretch * 0.55f * s
                indicatorPos = start + dist * overshoot.getInterpolation(t)
                placeDroplet(clamp = true)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(animation: Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    
                    if (canceled) return
                    droplet.scaleX = 1f
                    droplet.scaleY = 1f
                    indicatorPos = index.toFloat()
                    placeDroplet(clamp = true)
                }
            })
            start()
        }
    }

    
    private fun scrollToPosition(pos: Float, smooth: Boolean) {
        if (tabMode != MODE_SCROLLABLE || tabs.isEmpty() || scroller.width <= 0) return
        val (x, w) = indicatorGeometry(pos)
        val viewport = scroller.width - scroller.paddingLeft - scroller.paddingRight
        val max = (tabsRow.width - viewport).coerceAtLeast(0)
        val target = (tabsRow.left + x + w / 2f - viewport / 2f).roundToInt().coerceIn(0, max)
        if (smooth) scroller.smoothScrollTo(target, 0) else scroller.scrollTo(target, 0)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Int): Float = v * resources.displayMetrics.density

    
    private class TabScroller(context: Context) : HorizontalScrollView(context) {
        var fixed = false

        override fun measureChildWithMargins(
            child: View,
            parentWidthMeasureSpec: Int,
            widthUsed: Int,
            parentHeightMeasureSpec: Int,
            heightUsed: Int
        ) {
            if (!fixed) {
                super.measureChildWithMargins(
                    child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed
                )
                return
            }
            val lp = child.layoutParams as MarginLayoutParams
            val w = (MeasureSpec.getSize(parentWidthMeasureSpec) - paddingLeft - paddingRight -
                lp.leftMargin - lp.rightMargin - widthUsed).coerceAtLeast(0)
            val heightSpec = getChildMeasureSpec(
                parentHeightMeasureSpec,
                paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin + heightUsed,
                lp.height
            )
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightSpec)
        }
    }

    
    private class Droplet(context: Context) : LiquidGlassView(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean = false
    }

    companion object {
        
        const val MODE_FIXED = 0

        
        const val MODE_SCROLLABLE = 1
    }
}
