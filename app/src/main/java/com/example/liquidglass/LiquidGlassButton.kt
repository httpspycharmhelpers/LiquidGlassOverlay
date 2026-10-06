
package com.example.liquidglass

import com.liquidglass.app.R
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

open class LiquidGlassButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    
    val textView: TextView = TextView(context)

    
    private var autoTextColor = true

    
    var text: CharSequence
        get() = textView.text
        set(value) {
            textView.text = value
        }

    init {
        isClickable = true
        isFocusable = true

        textView.textSize = 17f
        textView.typeface = Typeface.DEFAULT_BOLD
        textView.gravity = Gravity.CENTER
        textView.maxLines = 1
        val h = dp(26)
        val v = dp(14)
        textView.setPadding(h, v, h, v)
        addView(textView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        applyAutoTextColor(isOverLightBackground)

        attrs?.let { parseButtonAttributes(context, it) }
    }

    private fun parseButtonAttributes(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassButton)
        try {
            ta.getText(R.styleable.LiquidGlassButton_android_text)?.let { textView.text = it }
            val sizePx = ta.getDimensionPixelSize(R.styleable.LiquidGlassButton_android_textSize, 0)
            if (sizePx > 0) textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx.toFloat())
            if (ta.hasValue(R.styleable.LiquidGlassButton_android_textColor)) {
                setTextColor(ta.getColor(R.styleable.LiquidGlassButton_android_textColor, Color.WHITE))
            }
        } finally {
            ta.recycle()
        }
    }

    
    fun setTextSize(sp: Float) {
        textView.textSize = sp
    }

    
    fun setTextColor(color: Int) {
        autoTextColor = false
        textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        textView.setTextColor(color)
    }

    override fun onAppearanceChanged(isOverLight: Boolean) {
        if (autoTextColor) applyAutoTextColor(isOverLight)
    }

    
    private fun applyAutoTextColor(isOverLight: Boolean) {
        if (isOverLight) {
            textView.setTextColor(0xDE000000.toInt())
            textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            textView.setTextColor(Color.WHITE)
            textView.setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
