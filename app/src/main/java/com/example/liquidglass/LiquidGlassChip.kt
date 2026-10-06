
package com.example.liquidglass

import com.liquidglass.app.R
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

open class LiquidGlassChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(chip: LiquidGlassChip, isChecked: Boolean)
    }

    
    val chipIconView: ImageView = ImageView(context)

    
    val textView: TextView = TextView(context)

    
    val closeIconView: ImageView = ImageView(context)

    private val row = LinearLayout(context)

    
    private var autoColors = true
    private var contentColor = Color.WHITE

    private var checked = false
    private var checkedFraction = 0f
    private var checkedAnimator: ValueAnimator? = null
    private var checkedListener: OnCheckedChangeListener? = null

    private val checkIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.liquid_glass_ic_check)

    var text: CharSequence
        get() = textView.text
        set(value) {
            textView.text = value
            updateCloseDescription()
        }

    
    var chipIcon: Drawable? = null
        set(value) {
            field = value
            updateLeadingIcon()
        }

    
    var isChipIconTintEnabled = true
        set(value) {
            field = value
            applyContentColor()
        }

    
    var isCloseIconVisible = false
        set(value) {
            field = value
            closeIconView.visibility = if (value) View.VISIBLE else View.GONE
            updatePadding()
        }

    
    var closeIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.liquid_glass_ic_close)
        set(value) {
            field = value
            closeIconView.setImageDrawable(value)
            applyContentColor()
        }

    
    var isCheckable = false
        set(value) {
            field = value
            if (!value && checked) isChecked = false
        }

    
    var isChecked: Boolean
        get() = checked
        set(value) {
            if (value && !isCheckable) return
            if (value == checked) return
            checked = value
            animateChecked(value)
            updateLeadingIcon()
            applyAutoColors()
            checkedListener?.onCheckedChanged(this, value)
            (parent as? LiquidGlassChipGroup)?.onChipCheckedChanged(this, value)
        }

    
    var isCheckedIconVisible = true
        set(value) {
            field = value
            updateLeadingIcon()
        }

    
    var checkedTint: Int = DEFAULT_CHECKED_TINT
        set(value) {
            field = value
            invalidate()
            applyAutoColors()
        }

    override val effectiveGlassTint: Int
        get() = when {
            checkedFraction <= 0f -> glassTint
            checkedFraction >= 1f -> checkedTint
            else -> blendArgb(glassTint, checkedTint, checkedFraction)
        }

    init {
        
        
        isClickable = true
        isFocusable = true

        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = dp(32)

        chipIconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        chipIconView.visibility = View.GONE
        row.addView(chipIconView, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) })

        textView.textSize = 14f
        textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textView.maxLines = 1
        textView.includeFontPadding = false
        row.addView(textView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        
        closeIconView.scaleType = ImageView.ScaleType.CENTER
        closeIconView.setImageDrawable(closeIcon)
        closeIconView.visibility = View.GONE
        row.addView(closeIconView, LinearLayout.LayoutParams(dp(26), dp(32)).apply { marginStart = dp(2) })

        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        updatePadding()
        applyAutoColors()

        attrs?.let { parseChipAttributes(context, it) }
    }

    private fun parseChipAttributes(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassChip)
        try {
            ta.getText(R.styleable.LiquidGlassChip_android_text)?.let { text = it }
            val sizePx = ta.getDimensionPixelSize(R.styleable.LiquidGlassChip_android_textSize, 0)
            if (sizePx > 0) textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx.toFloat())
            val iconRes = ta.getResourceId(R.styleable.LiquidGlassChip_glassChipIcon, 0)
            if (iconRes != 0) chipIcon = ContextCompat.getDrawable(context, iconRes)
            isCloseIconVisible = ta.getBoolean(R.styleable.LiquidGlassChip_glassCloseIconVisible, isCloseIconVisible)
            isCheckedIconVisible = ta.getBoolean(R.styleable.LiquidGlassChip_glassCheckedIconVisible, isCheckedIconVisible)
            checkedTint = ta.getColor(R.styleable.LiquidGlassChip_glassCheckedTint, checkedTint)
            isCheckable = ta.getBoolean(R.styleable.LiquidGlassChip_android_checkable, isCheckable)
            if (ta.getBoolean(R.styleable.LiquidGlassChip_android_checked, false)) setCheckedImmediately()
            if (ta.hasValue(R.styleable.LiquidGlassChip_android_textColor)) {
                setTextColor(ta.getColor(R.styleable.LiquidGlassChip_android_textColor, Color.WHITE))
            }
        } finally {
            ta.recycle()
        }
    }

    fun setChipIconResource(resId: Int) {
        chipIcon = if (resId == 0) null else ContextCompat.getDrawable(context, resId)
    }

    fun setCloseIconResource(resId: Int) {
        closeIcon = if (resId == 0) null else ContextCompat.getDrawable(context, resId)
    }

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        checkedListener = listener
    }

    
    fun setOnCloseIconClickListener(listener: OnClickListener?) {
        if (listener == null) {
            closeIconView.setOnClickListener(null)
            closeIconView.isClickable = false
        } else {
            closeIconView.setOnClickListener { listener.onClick(this) }
        }
    }

    
    fun setTextSize(sp: Float) {
        textView.textSize = sp
    }

    
    fun setTextColor(color: Int) {
        autoColors = false
        textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        contentColor = color
        textView.setTextColor(color)
        applyContentColor()
    }

    fun toggle() {
        if (isCheckable) isChecked = !checked
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        
        if (!isEnabled) return isClickable
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        if (!isEnabled) return false
        if (isCheckable) {
            val target = !checked
            
            val group = parent as? LiquidGlassChipGroup
            if (target || group == null || group.canUncheck(this)) isChecked = target
        }
        return super.performClick()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        
        row.alpha = if (enabled) 1f else 0.38f
        closeIconView.isEnabled = enabled
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = if (isCheckable) CompoundButton::class.java.name else Button::class.java.name
        info.isCheckable = isCheckable
        @Suppress("DEPRECATION")
        info.isChecked = checked
    }

    override fun onAppearanceChanged(isOverLight: Boolean) {
        applyAutoColors()
    }

    
    private fun setCheckedImmediately() {
        if (!isCheckable) return
        checked = true
        checkedFraction = 1f
        updateLeadingIcon()
        applyAutoColors()
        invalidate()
    }

    private fun animateChecked(target: Boolean) {
        checkedAnimator?.cancel()
        val end = if (target) 1f else 0f
        if (!isAttachedToWindow) {
            checkedFraction = end
            invalidate()
            return
        }
        checkedAnimator = ValueAnimator.ofFloat(checkedFraction, end).apply {
            duration = CHECK_ANIM_MS
            addUpdateListener {
                checkedFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun updateLeadingIcon() {
        val icon = if (checked && isCheckedIconVisible) checkIcon else chipIcon
        chipIconView.setImageDrawable(icon)
        chipIconView.visibility = if (icon == null) View.GONE else View.VISIBLE
        updatePadding()
        applyContentColor()
    }

    
    private fun updatePadding() {
        val start = if (chipIconView.visibility == View.VISIBLE) dp(8) else dp(14)
        val end = if (closeIconView.visibility == View.VISIBLE) dp(2) else dp(14)
        row.setPaddingRelative(start, 0, end, 0)
    }

    
    private fun applyAutoColors() {
        if (!autoColors) return
        val lightBehind = if (checked) {
            luminance(checkedTint) > 0.6f
        } else {
            isOverLightBackground
        }
        if (lightBehind) {
            contentColor = 0xDE000000.toInt()
            textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            contentColor = Color.WHITE
            textView.setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        textView.setTextColor(contentColor)
        applyContentColor()
    }

    private fun applyContentColor() {
        
        val showingCheck = checked && isCheckedIconVisible
        if (showingCheck || isChipIconTintEnabled) {
            chipIconView.setColorFilter(contentColor)
        } else {
            chipIconView.clearColorFilter()
        }
        closeIconView.setColorFilter(contentColor)
    }

    private fun updateCloseDescription() {
        closeIconView.contentDescription =
            context.getString(R.string.liquid_glass_chip_close_description, textView.text)
    }

    private fun luminance(color: Int): Float =
        (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f

    
    private fun blendArgb(from: Int, to: Int, t: Float): Int {
        val a0 = Color.alpha(from) / 255f
        val a1 = Color.alpha(to) / 255f
        val a = a0 + (a1 - a0) * t
        if (a <= 0f) return Color.TRANSPARENT
        fun ch(c0: Int, c1: Int) = ((c0 * a0 + (c1 * a1 - c0 * a0) * t) / a).roundToInt().coerceIn(0, 255)
        return Color.argb(
            (a * 255f).roundToInt(),
            ch(Color.red(from), Color.red(to)),
            ch(Color.green(from), Color.green(to)),
            ch(Color.blue(from), Color.blue(to))
        )
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        
        const val DEFAULT_CHECKED_TINT = 0x660A84FF
        private const val CHECK_ANIM_MS = 160L
    }
}
