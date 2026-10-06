package com.liquidglass.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView


class OverlayService : Service() {

    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    
    private var glass: LiquidGlassView? = null
    private var glassParams: WindowManager.LayoutParams? = null

    
    private var panelRoot: View? = null
    private var panelParams: WindowManager.LayoutParams? = null

    
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    @Volatile private var screenBitmap: Bitmap? = null
    private var captureW = 0
    private var captureH = 0
    private val captureHandler by lazy { Handler(Looper.getMainLooper()) }
    private var lastFrameMs = 0L
    
    @Volatile private var captureExcluded = false

    private var lastDownX = 0f
    private var lastDownY = 0f
    private var lastWinX = 0
    private var lastWinY = 0
    private var mode = MODE_NONE
    private var initialDist = 0f
    private var initialW = 0
    private var initialH = 0

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_DRAG = 1
        private const val MODE_PINCH = 2
        private const val MODE_RESIZE = 3
        private const val MIN_W = 120
        private const val MIN_H = 100
        private const val MAX_W = 1600
        private const val MAX_H = 1600
        private const val CHANNEL_ID = "liquidglass_overlay"

        const val EXTRA_CAPTURE_CODE = "capture_code"
        const val EXTRA_CAPTURE_DATA = "capture_data"

        
        private const val CAPTURE_SCALE = 0.4f

        
        private const val CAPTURE_INTERVAL_MS = 70L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForegroundCompat()   
        } catch (_: Throwable) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        
        val code = intent?.getIntExtra(EXTRA_CAPTURE_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra(EXTRA_CAPTURE_DATA, Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_CAPTURE_DATA)
        if (data != null && projection == null) {
            try {
                startProjection(code, data)
            } catch (t: Throwable) {
                android.util.Log.e("OverlayService", "startProjection failed", t)
            }
        }
        try {
            if (glass == null) showGlass()
        } catch (t: Throwable) {
            android.widget.Toast.makeText(this, "主玻璃创建失败: ${t.javaClass.simpleName}\n${t.message}", android.widget.Toast.LENGTH_LONG).show()
        }
        try {
            if (panelRoot == null) showPanel()
        } catch (t: Throwable) {
            android.widget.Toast.makeText(this, "面板创建失败: ${t.javaClass.simpleName}\n${t.message}", android.widget.Toast.LENGTH_LONG).show()
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "液态玻璃悬浮窗", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1001, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("液态玻璃悬浮窗")
            .setOngoing(true)
            .build()
    }

    

    private fun showGlass() {
        
        
        val v = LiquidGlassView(this).apply {
            enableDynamicBackground = true
            useShaderPipeline = true          
            material = GlassMaterial.REGULAR
            cornerRadius = 48f                
            refractionHeight = 200f
            bevelWidth = 40f
            dispersionStrength = 0.16f
            blurAmount = 0.055f
            saturation = 150f
            enableEdgeHighlight = true
            enableSensorHighlight = true
            enableAdaptiveTint = true
            setOnTouchListener(GlassTouchHandler())
        }

        
        v.addOnLayoutChangeListener { _, _, _, w, h, _, _, _, _ ->
            if (w > 0 && h > 0) {
                val r = minDim(w, h) * 0.25f
                v.setCornerRadii(r, r, r, r)
            }
        }

        glass = v

        
        v.setCustomBackdropCapture { bounds -> cropScreenRegion(v, bounds) }

        val lp = WindowManager.LayoutParams(
            360,
            220,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = dp(120)
        }
        glassParams = lp
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            throw e
        }
        
        excludeFromCapture(v)
    }

    
    private fun excludeFromCapture(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        view.post {
            try {
                val attachInfo = View::class.java.getDeclaredField("mAttachInfo")
                    .apply { isAccessible = true }.get(view) ?: return@post
                val vrField = attachInfo.javaClass.getDeclaredField("mViewRootImpl")
                vrField.isAccessible = true
                val viewRoot = vrField.get(attachInfo) ?: return@post
                val sc = try {
                    viewRoot.javaClass.getMethod("getSurfaceControl")
                        .invoke(viewRoot) as? android.view.SurfaceControl
                } catch (t: Throwable) { null } ?: return@post

                val txnCls = Class.forName("android.view.SurfaceControl\$Transaction")
                val txn = txnCls.getConstructor().newInstance()
                var applied: String? = null
                for (name in arrayOf("setSkipCapture", "setSkipScreenshot")) {
                    try {
                        txnCls.getMethod(name, android.view.SurfaceControl::class.java)
                            .invoke(txn, sc)
                        applied = name
                        break
                    } catch (_: Throwable) {}
                }
                if (applied != null) {
                    txnCls.getMethod("apply").invoke(txn)
                    captureExcluded = true
                    android.util.Log.i("OverlayService", "excluded from capture via $applied")
                } else {
                    android.util.Log.w("OverlayService", "no setSkipCapture/setSkipScreenshot available")
                }
            } catch (t: Throwable) {
                android.util.Log.w("OverlayService", "excludeFromCapture failed: ${t.message}")
            }
        }
    }

    private fun minDim(w: Int, h: Int): Int = if (w < h) w else h

    
    private inner class GlassTouchHandler : View.OnTouchListener {
        private var edgeL = false
        private var edgeR = false
        private var edgeT = false
        private var edgeB = false
        private var rDownX = 0f
        private var rDownY = 0f
        private var rStartW = 0
        private var rStartH = 0
        private var rStartX = 0
        private var rStartY = 0
        private val holdRunnable = Runnable {
            mode = MODE_NONE
            togglePanel()
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = glassParams
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastDownX = e.rawX
                    lastDownY = e.rawY
                    v.postDelayed(holdRunnable, 650)
                    if (p != null) {
                        lastWinX = p.x
                        lastWinY = p.y
                        val edge = dp(28)
                        val lx = e.x
                        val ly = e.y
                        edgeL = lx < edge
                        edgeR = lx > p.width - edge
                        edgeT = ly < edge
                        edgeB = ly > p.height - edge
                        if (edgeL || edgeR || edgeT || edgeB) {
                            mode = MODE_RESIZE
                            rDownX = e.rawX
                            rDownY = e.rawY
                            rStartW = p.width
                            rStartH = p.height
                            rStartX = p.x
                            rStartY = p.y
                        } else {
                            mode = MODE_DRAG
                        }
                    }
                    return false
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    v.removeCallbacks(holdRunnable)
                    if (e.pointerCount == 2 && p != null) {
                        mode = MODE_PINCH
                        initialDist = spacing(e)
                        initialW = p.width
                        initialH = p.height
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX - lastDownX) > dp(10) ||
                        kotlin.math.abs(e.rawY - lastDownY) > dp(10)) {
                        v.removeCallbacks(holdRunnable)
                    }
                    when (mode) {
                        MODE_DRAG -> if (p != null) {
                            val nx = lastWinX + (e.rawX - lastDownX).toInt()
                            val ny = lastWinY + (e.rawY - lastDownY).toInt()
                            p.x = clampX(nx, p.width)
                            p.y = clampY(ny, p.height)
                            try {
                                wm.updateViewLayout(v, p)
                                lastWinX = p.x
                                lastWinY = p.y
                            } catch (_: Throwable) {}
                            return false
                        }
                        MODE_RESIZE -> if (p != null) {
                            val dx = (e.rawX - rDownX).toInt()
                            val dy = (e.rawY - rDownY).toInt()
                            var newW = rStartW
                            var newH = rStartH
                            var newX = rStartX
                            var newY = rStartY
                            val maxW = screenW - dp(8)
                            val maxH = screenH - dp(8)
                            if (edgeL) {
                                newW = (rStartW - dx).coerceIn(MIN_W, maxW)
                                newX = rStartX + (rStartW - newW)
                            } else if (edgeR) {
                                newW = (rStartW + dx).coerceIn(MIN_W, maxW)
                            }
                            if (edgeT) {
                                newH = (rStartH - dy).coerceIn(MIN_H, maxH)
                                newY = rStartY + (rStartH - newH)
                            } else if (edgeB) {
                                newH = (rStartH + dy).coerceIn(MIN_H, maxH)
                            }
                            p.width = newW
                            p.height = newH
                            p.x = clampX(newX, newW)
                            p.y = clampY(newY, newH)
                            try { wm.updateViewLayout(v, p) } catch (_: Throwable) {}
                            return true
                        }
                        MODE_PINCH -> if (p != null) {
                            val dist = spacing(e)
                            if (dist > 0) {
                                val scale = dist / initialDist
                                p.width = (initialW * scale).toInt().coerceIn(MIN_W, minOf(MAX_W, screenW - 16))
                                p.height = (initialH * scale).toInt().coerceIn(MIN_H, minOf(MAX_H, screenH - 16))
                                p.x = clampX(p.x, p.width)
                                p.y = clampY(p.y, p.height)
                                try { wm.updateViewLayout(v, p) } catch (_: Throwable) {}
                            }
                            return true
                        }
                    }
                    return false
                }
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(holdRunnable)
                    mode = MODE_NONE
                    return false
                }
            }
            return false
        }
    }

    

    private fun showPanel() {
        val g = glass ?: return

        
        val panel = SettingsPanel(this, g).build()

        
        val title = TextView(this).apply {
            text = "≡  Debug Panel"
            textSize = 13f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), 0, dp(8))
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 15f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { hidePanel() }
        }
        val handle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(closeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(panel)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpF(18)
                setColor(0xFFF2F2F7.toInt())
            }
            addView(handle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(scroll)
        }
        handle.setOnTouchListener(PanelDragHandler(root))

        panelRoot = root

        val lp = WindowManager.LayoutParams(
            minOf(dp(320), screenW - dp(24)),
            dp(520),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - width - dp(12)
            y = dp(60)
        }
        panelParams = lp
        wm.addView(root, lp)
        excludeFromCapture(root)
    }

    private fun hidePanel() {
        panelRoot?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        panelRoot = null
        panelParams = null
    }

    private fun togglePanel() {
        if (panelRoot == null) {
            try { showPanel() } catch (t: Throwable) {
                android.util.Log.e("OverlayService", "showPanel failed", t)
            }
        } else {
            hidePanel()
        }
    }

    

    
    private inner class PanelDragHandler(private val view: View) : View.OnTouchListener {
        private var pDownX = 0f
        private var pDownY = 0f
        private var pWinX = 0
        private var pWinY = 0
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pDownX = e.rawX
                    pDownY = e.rawY
                    panelParams?.let { p ->
                        pWinX = p.x
                        pWinY = p.y
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> panelParams?.let { p ->
                    val nx = pWinX + (e.rawX - pDownX).toInt()
                    val ny = pWinY + (e.rawY - pDownY).toInt()
                    p.x = clampX(nx, p.width)
                    p.y = clampY(ny, p.height)
                    try {
                        wm.updateViewLayout(view, p)
                        pWinX = p.x
                        pWinY = p.y
                    } catch (_: Throwable) {}
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    private fun spacing(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val x = e.getX(0) - e.getX(1)
        val y = e.getY(0) - e.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }

    private val screenW: Int
        get() = resources.displayMetrics.widthPixels
    private val screenH: Int
        get() = resources.displayMetrics.heightPixels

    private fun clampX(x: Int, winW: Int): Int = x.coerceIn(-8, screenW - winW - 8)
    private fun clampY(y: Int, winH: Int): Int = y.coerceIn(-8, screenH - winH - 8)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Int): Float = v * resources.displayMetrics.density

    

    private fun startProjection(resultCode: Int, data: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(
                    1001, buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (_: Throwable) {}
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(resultCode, data) ?: return
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopProjection()
            }
        }, captureHandler)
        startCapture(mp)
    }

    private fun startCapture(mp: MediaProjection) {
        val dm = resources.displayMetrics
        captureW = (dm.widthPixels * CAPTURE_SCALE).toInt().coerceAtLeast(1)
        captureH = (dm.heightPixels * CAPTURE_SCALE).toInt().coerceAtLeast(1)
        val reader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val now = SystemClock.uptimeMillis()
            if (now - lastFrameMs < CAPTURE_INTERVAL_MS) {
                try { image.close() } catch (_: Throwable) {}
                return@setOnImageAvailableListener
            }
            lastFrameMs = now
            try {
                val bmp = imageToBitmap(image, captureW, captureH)
                if (bmp != null) {
                    val old = screenBitmap
                    screenBitmap = bmp
                    old?.recycle()
                    glass?.postInvalidate()
                }
            } catch (_: Throwable) {
            } finally {
                try { image.close() } catch (_: Throwable) {}
            }
        }, captureHandler)
        virtualDisplay = mp.createVirtualDisplay(
            "liquidglass-capture",
            captureW, captureH, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, captureHandler
        )
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        val plane = planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        bmp.copyPixelsFromBuffer(plane.buffer)
        if (rowPadding == 0) return bmp
        val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
        bmp.recycle()
        return cropped
    }

    
    private fun cropScreenRegion(glassView: View, bounds: RectF): Bitmap? {
        val full = screenBitmap ?: return null
        if (captureW <= 0 || captureH <= 0) return null
        val loc = IntArray(2)
        glassView.getLocationOnScreen(loc)
        val l = ((loc[0] + bounds.left) * captureW / screenW).toInt().coerceIn(0, full.width - 1)
        val t = ((loc[1] + bounds.top) * captureH / screenH).toInt().coerceIn(0, full.height - 1)
        val r = ((loc[0] + bounds.right) * captureW / screenW).toInt().coerceIn(l + 1, full.width)
        val b = ((loc[1] + bounds.bottom) * captureH / screenH).toInt().coerceIn(t + 1, full.height)
        val outW = bounds.width().toInt().coerceAtLeast(1)
        val outH = bounds.height().toInt().coerceAtLeast(1)
        return try {
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawBitmap(full, Rect(l, t, r, b), Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
            out
        } catch (_: Throwable) {
            null
        }
    }

    private fun stopProjection() {
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        virtualDisplay = null
        imageReader = null
        projection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProjection()
        try { screenBitmap?.recycle() } catch (_: Throwable) {}
        screenBitmap = null
        try { panelRoot?.let { wm.removeView(it) } } catch (_: Throwable) {}
        try { glass?.let { wm.removeView(it) } } catch (_: Throwable) {}
        panelRoot = null
        glass = null
    }
}