
package com.example.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi

class ScrollEdgeBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    
    enum class Edge { TOP, BOTTOM }

    var edge = Edge.TOP
        set(value) {
            if (field != value) {
                field = value
                gradientsDirty = true
                invalidate()
            }
        }

    
    var maxBlurRadius = 40f
        set(value) {
            val clamped = value.coerceIn(0f, 100f)
            if (field != clamped) {
                field = clamped
                effectsDirty = true
                invalidate()
            }
        }

    
    private var contentNode: RenderNode? = null
    private var weakNode: RenderNode? = null
    private var strongNode: RenderNode? = null
    private var effectsDirty = true
    private var gradientsDirty = true
    private var lastEffectRadius = -1f

    private val maskPaintWide = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val maskPaintNarrow = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val scrimPaint = Paint()

    private val location = IntArray(2)
    private val parentLocation = IntArray(2)
    private var isCapturing = false

    private var boundScrollView: View? = null
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { invalidate() }

    init {
        
        isClickable = false
        isFocusable = false
    }

    
    fun bindScrollView(scrollView: View) {
        unbindScrollView()
        boundScrollView = scrollView
        if (isAttachedToWindow) {
            scrollView.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        }
    }

    fun unbindScrollView() {
        boundScrollView?.viewTreeObserver?.takeIf { it.isAlive }
            ?.removeOnScrollChangedListener(scrollListener)
        boundScrollView = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        boundScrollView?.viewTreeObserver?.addOnScrollChangedListener(scrollListener)
    }

    override fun onDetachedFromWindow() {
        boundScrollView?.viewTreeObserver?.takeIf { it.isAlive }
            ?.removeOnScrollChangedListener(scrollListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            contentNode?.discardDisplayList()
            weakNode?.discardDisplayList()
            strongNode?.discardDisplayList()
        }
        contentNode = null
        weakNode = null
        strongNode = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradientsDirty = true
        effectsDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (isCapturing) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            canvas.isHardwareAccelerated &&
            maxBlurRadius > 0.5f &&
            drawProgressiveBlur(canvas)
        ) {
            return
        }

        
        drawScrimFallback(canvas)
    }

    

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawProgressiveBlur(canvas: Canvas): Boolean {
        val parent = parent as? View ?: return false
        val w = width
        val h = height
        val margin = (maxBlurRadius * 2f).toInt().coerceAtLeast(8)

        val content = contentNode ?: RenderNode("ScrollEdgeContent").also { contentNode = it }
        val weak = weakNode ?: RenderNode("ScrollEdgeWeak").also { weakNode = it }
        val strong = strongNode ?: RenderNode("ScrollEdgeStrong").also { strongNode = it }

        
        getLocationInWindow(location)
        parent.getLocationInWindow(parentLocation)
        val offsetX = (location[0] - parentLocation[0]).toFloat()
        val offsetY = (location[1] - parentLocation[1]).toFloat()

        content.setPosition(0, 0, w, h + 2 * margin)
        val rc = content.beginRecording(w, h + 2 * margin)
        try {
            rc.translate(-offsetX, margin - offsetY)
            isCapturing = true
            setTransitionVisibility(INVISIBLE)
            try {
                parent.draw(rc)
            } finally {
                setTransitionVisibility(VISIBLE)
                isCapturing = false
            }
        } catch (_: Exception) {
            content.endRecording()
            return false
        }
        content.endRecording()

        
        if (effectsDirty || lastEffectRadius != maxBlurRadius) {
            weak.setRenderEffect(
                RenderEffect.createBlurEffect(
                    maxBlurRadius * 0.35f, maxBlurRadius * 0.35f, Shader.TileMode.CLAMP
                )
            )
            strong.setRenderEffect(
                RenderEffect.createBlurEffect(maxBlurRadius, maxBlurRadius, Shader.TileMode.CLAMP)
            )
            lastEffectRadius = maxBlurRadius
            effectsDirty = false
        }
        recordProxy(weak, content, w, h, margin)
        recordProxy(strong, content, w, h, margin)

        
        if (gradientsDirty) {
            rebuildGradients(w.toFloat(), h.toFloat())
            gradientsDirty = false
        }

        
        val bounds = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())

        var save = canvas.saveLayer(bounds, null)
        canvas.drawRenderNode(weak)
        canvas.drawRect(bounds, maskPaintWide)
        canvas.restoreToCount(save)

        save = canvas.saveLayer(bounds, null)
        canvas.drawRenderNode(strong)
        canvas.drawRect(bounds, maskPaintNarrow)
        canvas.restoreToCount(save)

        return true
    }

    
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun recordProxy(proxy: RenderNode, content: RenderNode, w: Int, h: Int, margin: Int) {
        proxy.setPosition(0, -margin, w, h + margin)
        val c = proxy.beginRecording(w, h + 2 * margin)
        try {
            c.drawRenderNode(content)
        } finally {
            proxy.endRecording()
        }
    }

    private fun rebuildGradients(w: Float, h: Float) {
        val top = edge == Edge.TOP
        
        maskPaintWide.shader = LinearGradient(
            0f, if (top) 0f else h, 0f, if (top) h else 0f,
            intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, 0.25f, 1f),
            Shader.TileMode.CLAMP
        )
        
        maskPaintNarrow.shader = LinearGradient(
            0f, if (top) 0f else h, 0f, if (top) h * 0.55f else h * 0.45f,
            intArrayOf(Color.WHITE, Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        
        scrimPaint.shader = LinearGradient(
            0f, if (top) 0f else h, 0f, if (top) h else 0f,
            intArrayOf(0x66000000, Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
    }

    

    private fun drawScrimFallback(canvas: Canvas) {
        if (gradientsDirty) {
            rebuildGradients(width.toFloat(), height.toFloat())
            gradientsDirty = false
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
    }
}
