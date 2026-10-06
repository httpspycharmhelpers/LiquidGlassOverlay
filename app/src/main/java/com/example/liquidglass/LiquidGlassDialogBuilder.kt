
package com.example.liquidglass

import com.liquidglass.app.R
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder


fun interface GlassConfigurator {
    fun configure(glass: LiquidGlassView)
}

class LiquidGlassDialogBuilder @JvmOverloads constructor(
    context: Context,
    themeResId: Int = 0,
    private val cornerRadiusDp: Float = 28f,
    private val glassBlurAmount: Float = DEFAULT_BLUR,
    private val animateShow: Boolean = true,
    private val dimBehind: Boolean = false,
    private val glassSetup: (LiquidGlassView.() -> Unit)? = null
) : MaterialAlertDialogBuilder(context, themeResId) {

    var glass: LiquidGlassView? = null
        private set

    
    var overLightTextColor: Int = OVER_LIGHT_TEXT
    var overDarkTextColor: Int = Color.WHITE

    private var glassReadyCallback: GlassConfigurator? = null

    private var alertDialog: AlertDialog? = null

    
    fun configureGlass(callback: GlassConfigurator): LiquidGlassDialogBuilder {
        glassReadyCallback = callback
        return this
    }

    override fun create(): AlertDialog {
        val dialog = super.create()
        alertDialog = dialog
        val decor = dialog.window?.decorView
        if (decor != null) {
            decor.viewTreeObserver.addOnPreDrawListener(
                object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        decor.viewTreeObserver.takeIf { it.isAlive }
                            ?.removeOnPreDrawListener(this)
                        installGlass(dialog)
                        return glass == null
                    }
                }
            )
        }
        return dialog
    }

    
    fun dismissAnimated() {
        val dialog = alertDialog ?: return
        val glassView = glass
        if (glassView == null || !glassView.isAttachedToWindow) {
            dialog.dismiss()
            return
        }
        
        dialog.window?.setWindowAnimations(0)
        glassView.animate()
            .alpha(0f)
            .scaleX(EXIT_SCALE)
            .scaleY(EXIT_SCALE)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.4f))
            .setUpdateListener { glassView.invalidate() }
            .withEndAction { dialog.dismiss() }
            .start()
    }

    private fun installGlass(dialog: AlertDialog) {
        val window = dialog.window ?: return
        val content = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val panel = content.getChildAt(0) ?: return
        if (panel is LiquidGlassView) return

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(if (dimBehind) DEFAULT_DIM else 0f)
        
        
        
        
        window.setWindowAnimations(R.style.Animation_LiquidGlass_Dialog)
        clearPanelBackgrounds(panel)

        val originalLp = panel.layoutParams
        val glassView = object : LiquidGlassView(context) {
            override fun onAppearanceChanged(isOverLight: Boolean) {
                applyTextColors(panel, isOverLight)
            }
        }.apply {
            cornerRadius = cornerRadiusDp * resources.displayMetrics.density
            enableDynamicBackground = true
            
            
            
            material = GlassMaterial.REGULAR
            enableAdaptiveTint = true
            blurAmount = glassBlurAmount
            backdropSource = context.findActivity()?.findViewById(android.R.id.content)
            layoutParams = buildGlassLayoutParams(content, originalLp)
            alpha = if (animateShow) 0f else 1f
            scaleX = if (animateShow) ENTER_SCALE else 1f
            scaleY = if (animateShow) ENTER_SCALE else 1f
            glassSetup?.invoke(this)
            glassReadyCallback?.configure(this)
        }
        glass = glassView
        
        glassView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                glass = null
                alertDialog = null
            }
        })

        val innerLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        (originalLp as? ViewGroup.MarginLayoutParams)?.let {
            innerLp.setMargins(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin)
        }

        content.removeViewAt(0)
        glassView.addView(panel, innerLp)
        content.addView(glassView)
        
        applyTextColors(panel, glassView.isOverLightBackground)
        runEnterAnimation()
    }

    
    private fun buildGlassLayoutParams(
        content: ViewGroup,
        originalLp: ViewGroup.LayoutParams?
    ): FrameLayout.LayoutParams {
        val maxWidthPx = (MAX_WIDTH_DP * context.resources.displayMetrics.density).toInt()
        val margins = originalLp as? ViewGroup.MarginLayoutParams
        val parentWidth = content.measuredWidth.takeIf { it > 0 }
            ?: content.width.takeIf { it > 0 }
            ?: maxWidthPx
        val available = parentWidth - (margins?.leftMargin ?: 0) - (margins?.rightMargin ?: 0)
        return FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            margins?.let { setMargins(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin) }
            width = minOf(maxWidthPx, available.coerceAtLeast(1))
            gravity = Gravity.CENTER
        }
    }

    private fun runEnterAnimation() {
        val glassView = glass ?: return
        if (!animateShow) {
            glassView.alpha = 1f
            glassView.scaleX = 1f
            glassView.scaleY = 1f
            return
        }
        glassView.post {
            glassView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(ENTER_DURATION_MS)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .setUpdateListener { glassView.invalidate() }
                .start()
        }
    }

    
    private fun clearPanelBackgrounds(view: View) {
        if (view !is ViewGroup) return
        if (view.background != null) view.background = null
        for (i in 0 until view.childCount) clearPanelBackgrounds(view.getChildAt(i))
    }

    
    private fun applyTextColors(panel: View, isOverLight: Boolean) {
        val color = if (isOverLight) overLightTextColor else overDarkTextColor
        val targets = listOfNotNull(
            panel.findViewById<TextView>(androidx.appcompat.R.id.alertTitle),
            panel.findViewById<TextView>(android.R.id.message),
            panel.findViewById<TextView>(android.R.id.button1),
            panel.findViewById<TextView>(android.R.id.button2),
            panel.findViewById<TextView>(android.R.id.button3)
        )
        targets.forEach { tv ->
            tv.setTextColor(color)
            if (isOverLight) {
                tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            } else {
                tv.setShadowLayer(8f, 0f, 2f, Color.BLACK)
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private companion object {
        const val ENTER_SCALE = 0.86f
        const val EXIT_SCALE = 0.88f
        const val ENTER_DURATION_MS = 250L
        const val EXIT_DURATION_MS = 150L
        const val DEFAULT_DIM = 0.32f

        
        const val DEFAULT_BLUR = 0.6f
        const val OVER_LIGHT_TEXT = 0xDE000000.toInt()

        
        const val MAX_WIDTH_DP = 380f
    }
}
