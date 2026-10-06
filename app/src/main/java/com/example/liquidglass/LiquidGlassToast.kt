
package com.example.liquidglass

import com.liquidglass.app.R
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

class LiquidGlassToast private constructor(private val activity: Activity) {

    
    val textView: TextView = TextView(activity)
    val imageView: ImageView = ImageView(activity)

    
    val glass: LiquidGlassView = ToastGlassView(activity)

    
    var duration: Int = LENGTH_SHORT

    
    var durationMillis: Long = 0L

    private var gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    private var xOffset = 0
    private var yOffset = dp(64)

    
    private var autoColors = true
    private var iconTintEnabled = true

    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { cancel() }
    private var dismissing = false

    private val windowManager: WindowManager = activity.windowManager

    
    private val frame = FrameLayout(activity)

    private val windowParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION
        
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        format = PixelFormat.TRANSLUCENT
        
        
        
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        
        windowAnimations = 0
        title = "LiquidGlassToast"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            
            fitInsetsTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.ime()
        }
    }

    
    private val backdrop = WindowStackBackdrop(activity)

    
    private var raising = false
    private var raisePending = false
    private var raiseCount = 0
    private val raiseRunnable = Runnable { raise() }

    
    private val coverCheck = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (frame.parent == null) return
            if (!raisePending && !dismissing && raiseCount < MAX_RAISES &&
                AppWindows.isCovered(frame)
            ) {
                
                raisePending = true
                handler.post(raiseRunnable)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    init {
        
        glass.apply {
            cornerRadius = 999f
            bevelWidth = dpF(14f)
            refractionHeight = dpF(7f)
            edgeSoftness = dpF(3f)
            blurAmount = 0.25f
            enableAdaptiveTint = true
            enableDynamicBackground = true
            enablePressEffect = false
            isClickable = false
            isFocusable = false
            minimumHeight = dp(44)
            backdropSource = backdrop
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.visibility = View.GONE
        row.addView(imageView, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) })
        textView.textSize = 14f
        textView.maxLines = 2
        textView.ellipsize = TextUtils.TruncateAt.END
        textView.includeFontPadding = false
        textView.maxWidth = dp(320)
        row.addView(textView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        glass.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
        frame.addView(glass)
        frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                
                if (!raising) releaseWindow()
            }
        })
        applyAutoColors(glass.isOverLightBackground)
    }

    fun setText(text: CharSequence): LiquidGlassToast {
        textView.text = text
        return this
    }

    fun setText(@StringRes resId: Int): LiquidGlassToast = setText(activity.getText(resId))

    
    fun setIcon(icon: Drawable?): LiquidGlassToast {
        imageView.setImageDrawable(icon)
        imageView.visibility = if (icon == null) View.GONE else View.VISIBLE
        applyIconTint()
        return this
    }

    fun setIconResource(@DrawableRes resId: Int): LiquidGlassToast =
        setIcon(if (resId == 0) null else ContextCompat.getDrawable(activity, resId))

    fun setIconTintEnabled(enabled: Boolean): LiquidGlassToast {
        iconTintEnabled = enabled
        applyIconTint()
        return this
    }

    
    fun setGravity(gravity: Int, xOffset: Int, yOffset: Int): LiquidGlassToast {
        this.gravity = gravity
        this.xOffset = xOffset
        this.yOffset = yOffset
        return this
    }

    fun setDuration(duration: Int): LiquidGlassToast {
        this.duration = duration
        return this
    }

    
    fun setTextColor(color: Int): LiquidGlassToast {
        autoColors = false
        textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        textView.setTextColor(color)
        applyIconTint()
        return this
    }

    fun show(): LiquidGlassToast {
        
        if (activity.isDestroyed) return this
        current?.takeIf { it !== this }?.cancel(animate = false)
        current = this
        handler.removeCallbacks(hideRunnable)
        dismissing = false
        raiseCount = 0

        applyLayout()
        syncSecureFlag()
        if (frame.parent == null) {
            if (!addWindow()) {
                if (current === this) current = null
                return this
            }
            animateIn()
        } else {
            windowManager.updateViewLayout(frame, windowParams)
            glass.animate().cancel()
            glass.alpha = 1f
            glass.scaleX = 1f
            glass.scaleY = 1f
            glass.translationY = 0f
        }
        glass.announceForAccessibility(textView.text)
        handler.postDelayed(hideRunnable, effectiveDurationMillis())
        return this
    }

    
    fun cancel() = cancel(animate = true)

    private fun cancel(animate: Boolean) {
        handler.removeCallbacks(hideRunnable)
        if (current === this) current = null
        if (frame.parent == null) return
        if (!animate || !glass.isAttachedToWindow) {
            removeWindow()
            return
        }
        if (dismissing) return
        dismissing = true
        glass.animate()
            .alpha(0f)
            .scaleX(EXIT_SCALE)
            .scaleY(EXIT_SCALE)
            .setDuration(EXIT_MS)
            .setInterpolator(AccelerateInterpolator(1.2f))
            .setUpdateListener { glass.invalidate() }
            .withEndAction { removeWindow() }
            .start()
    }

    private fun effectiveDurationMillis(): Long = when {
        durationMillis > 0L -> durationMillis
        duration == LENGTH_LONG -> LONG_MS
        else -> SHORT_MS
    }

    
    private fun applyLayout() {
        val decor = activity.window.decorView
        frame.layoutDirection = decor.layoutDirection
        val edge = dp(16)
        val pad = dp(SLIDE_DP)
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.leftMargin = edge
        lp.rightMargin = edge
        lp.topMargin = pad
        lp.bottomMargin = pad
        glass.translationX = 0f
        when (Gravity.getAbsoluteGravity(gravity, decor.layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.LEFT -> {
                lp.gravity = Gravity.LEFT
                lp.leftMargin = edge + xOffset
            }
            Gravity.RIGHT -> {
                lp.gravity = Gravity.RIGHT
                lp.rightMargin = edge + xOffset
            }
            else -> {
                lp.gravity = Gravity.CENTER_HORIZONTAL
                glass.translationX = xOffset.toFloat()
            }
        }
        glass.layoutParams = lp

        
        when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> {
                windowParams.gravity = Gravity.TOP
                windowParams.y = yOffset - pad
            }
            Gravity.BOTTOM -> {
                windowParams.gravity = Gravity.BOTTOM
                windowParams.y = yOffset - pad
            }
            else -> {
                windowParams.gravity = Gravity.CENTER_VERTICAL
                windowParams.y = yOffset
            }
        }
    }

    
    private fun syncSecureFlag() {
        val secure = WindowManager.LayoutParams.FLAG_SECURE
        windowParams.flags = if (activity.window.attributes.flags and secure != 0) {
            windowParams.flags or secure
        } else {
            windowParams.flags and secure.inv()
        }
    }

    private fun addWindow(): Boolean {
        try {
            windowManager.addView(frame, windowParams)
        } catch (e: RuntimeException) {
            
            Log.w(TAG, "Unable to add the toast window", e)
            return false
        }
        
        val decor = activity.window.decorView
        val location = IntArray(2)
        decor.getLocationOnScreen(location)
        backdrop.layout(0, 0, location[0] + decor.width, location[1] + decor.height)
        backdrop.ownWindow = frame
        registerLifecycle()
        Choreographer.getInstance().postFrameCallback(coverCheck)
        return true
    }

    
    private fun raise() {
        raisePending = false
        if (frame.parent == null || dismissing) return
        raiseCount++
        raising = true
        try {
            windowManager.removeViewImmediate(frame)
            syncSecureFlag()
            windowManager.addView(frame, windowParams)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to re-add the toast window", e)
        } finally {
            raising = false
        }
        
        if (frame.parent == null) releaseWindow()
    }

    private fun removeWindow() {
        if (frame.parent != null) {
            try {
                windowManager.removeViewImmediate(frame)
            } catch (e: RuntimeException) {
                
            }
        }
        releaseWindow()
    }

    
    private fun releaseWindow() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(raiseRunnable)
        raisePending = false
        Choreographer.getInstance().removeFrameCallback(coverCheck)
        unregisterLifecycle()
        backdrop.ownWindow = null
        glass.animate().cancel()
        dismissing = false
        if (current === this) current = null
    }

    
    private fun registerLifecycle() {
        if (lifecycleCallbacks != null) return
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(a: Activity) {
                if (a === activity) cancel(animate = false)
            }

            override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
        }
        activity.application.registerActivityLifecycleCallbacks(callbacks)
        lifecycleCallbacks = callbacks
    }

    private fun unregisterLifecycle() {
        val callbacks = lifecycleCallbacks ?: return
        activity.application.unregisterActivityLifecycleCallbacks(callbacks)
        lifecycleCallbacks = null
    }

    private fun animateIn() {
        
        val slide = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> -dpF(SLIDE_DP.toFloat())
            else -> dpF(SLIDE_DP.toFloat())
        }
        glass.alpha = 0f
        glass.scaleX = ENTER_SCALE
        glass.scaleY = ENTER_SCALE
        glass.translationY = slide
        glass.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(ENTER_MS)
            .setInterpolator(DecelerateInterpolator(1.6f))
            
            .setUpdateListener { glass.invalidate() }
            .withEndAction(null)
            .start()
    }

    private fun applyIconTint() {
        if (!iconTintEnabled) {
            imageView.clearColorFilter()
            return
        }
        imageView.setColorFilter(textView.currentTextColor)
    }

    
    private fun applyAutoColors(isOverLight: Boolean) {
        if (isOverLight) {
            textView.setTextColor(0xDE000000.toInt())
            textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            textView.setTextColor(Color.WHITE)
            textView.setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        applyIconTint()
    }

    
    private inner class ToastGlassView(context: Context) : LiquidGlassView(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean = false

        override fun onAppearanceChanged(isOverLight: Boolean) {
            if (autoColors) applyAutoColors(isOverLight)
        }
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * activity.resources.displayMetrics.density

    companion object {
        const val LENGTH_SHORT = Toast.LENGTH_SHORT
        const val LENGTH_LONG = Toast.LENGTH_LONG

        private const val TAG = "LiquidGlassToast"
        private const val SHORT_MS = 2000L
        private const val LONG_MS = 3500L
        private const val ENTER_MS = 260L
        private const val EXIT_MS = 180L
        private const val ENTER_SCALE = 0.94f
        private const val EXIT_SCALE = 0.94f

        
        private const val SLIDE_DP = 24

        
        private const val MAX_RAISES = 8

        private var current: LiquidGlassToast? = null

        @JvmStatic
        @JvmOverloads
        fun makeText(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT): LiquidGlassToast =
            LiquidGlassToast(findActivity(context)).apply {
                setText(text)
                this.duration = duration
            }

        @JvmStatic
        @JvmOverloads
        fun makeText(context: Context, @StringRes resId: Int, duration: Int = LENGTH_SHORT): LiquidGlassToast =
            makeText(context, context.getText(resId), duration)

        
        @JvmStatic
        fun cancelCurrent() {
            current?.cancel()
        }

        private fun findActivity(context: Context): Activity {
            var c: Context? = context
            while (c != null) {
                if (c is Activity) return c
                c = (c as? ContextWrapper)?.baseContext
            }
            throw IllegalArgumentException(
                "LiquidGlassToast needs an Activity context: the toast window is attached to the " +
                    "activity so the glass can refract the activity and its dialogs beneath it"
            )
        }
    }
}
