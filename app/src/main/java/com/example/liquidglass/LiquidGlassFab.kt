
package com.example.liquidglass

import com.liquidglass.app.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import androidx.annotation.DrawableRes

open class LiquidGlassFab @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    
    val imageView: ImageView = ImageView(context)

    
    private var autoIconTint = true

    
    var icon: Drawable?
        get() = imageView.drawable
        set(value) {
            imageView.setImageDrawable(value)
        }

    init {
        isClickable = true
        isFocusable = true

        
        minimumWidth = dp(56)
        minimumHeight = dp(56)

        addView(imageView, LayoutParams(dp(24), dp(24), Gravity.CENTER))
        applyAutoIconTint(isOverLightBackground)

        attrs?.let { parseFabAttributes(context, it) }
    }

    private fun parseFabAttributes(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassFab)
        try {
            ta.getDrawable(R.styleable.LiquidGlassFab_android_src)?.let { imageView.setImageDrawable(it) }
            if (ta.hasValue(R.styleable.LiquidGlassFab_glassIconTint)) {
                setIconTint(ta.getColor(R.styleable.LiquidGlassFab_glassIconTint, Color.WHITE))
            }
        } finally {
            ta.recycle()
        }
    }

    
    fun setIconResource(@DrawableRes resId: Int) {
        imageView.setImageResource(resId)
    }

    
    fun setIconTint(color: Int) {
        autoIconTint = false
        imageView.imageTintList = ColorStateList.valueOf(color)
    }

    override fun onAppearanceChanged(isOverLight: Boolean) {
        if (autoIconTint) applyAutoIconTint(isOverLight)
    }

    private fun applyAutoIconTint(isOverLight: Boolean) {
        val color = if (isOverLight) 0xDE000000.toInt() else Color.WHITE
        imageView.imageTintList = ColorStateList.valueOf(color)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
