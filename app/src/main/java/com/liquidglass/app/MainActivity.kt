package com.liquidglass.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.liquidglass.app.overlay.OverlayService
import java.io.File


class MainActivity : Activity() {

    private var launched = false
    private var captureRequested = false
    private lateinit var status: TextView

    companion object {
        private const val REQ_CAPTURE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashHandler()

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            text = "正在检查悬浮窗权限…"
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xEE000000.toInt())
            addView(status)
        }
        setContentView(root)

        if (hasOverlayPermission()) {
            requestCapture()
        } else {
            status.text = "未授予悬浮窗权限，正在打开系统授权页…"
            requestOverlayPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!launched && !captureRequested && hasOverlayPermission()) {
            requestCapture()
        }
    }

    
    private fun requestCapture() {
        captureRequested = true
        status.text = "请允许录屏，以便玻璃折射真实屏幕…"
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
        } catch (t: Throwable) {
            logCrash("requestCapture", t)
            status.text = "无法请求录屏：${t.message}\n玻璃将没有真实背景"
            startOverlay(0, null)
        }
    }

    @Deprecated("旧版结果回调，兼容各版本")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                startOverlay(resultCode, data)
            } else {
                status.text = "未授权录屏，玻璃将没有真实背景"
                startOverlay(0, null)
            }
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (t: Throwable) {
                logCrash("openSettings", t)
                status.text = "无法打开授权页：${t.message}"
                try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Throwable) {}
            }
        }
    }

    private fun startOverlay(resultCode: Int, data: Intent?) {
        launched = true
        try {
            if (!hasOverlayPermission()) {
                status.text = "未授予悬浮窗权限"
                finish()
                return
            }
            val i = Intent(this, OverlayService::class.java)
            if (data != null) {
                i.putExtra(OverlayService.EXTRA_CAPTURE_CODE, resultCode)
                i.putExtra(OverlayService.EXTRA_CAPTURE_DATA, data)
            }
            startService(i)
            status.text = if (data != null)
                "悬浮窗已开启，可返回桌面拖动玻璃"
            else
                "悬浮窗已开启（无录屏授权，无真实背景）"
        } catch (t: Throwable) {
            logCrash("startService", t)
            status.text = "启动失败：${t.javaClass.simpleName}\n${t.message}"
            return
        }
        
        status.postDelayed({ finish() }, 1200)
    }

    private fun installCrashHandler() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { logCrash("uncaught", e) } catch (_: Throwable) {}
            handler?.uncaughtException(t, e)
        }
    }

    private fun logCrash(tag: String, t: Throwable) {
        try {
            val dir = File(getExternalFilesDir(null), "crash")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "crash.log")
            val sw = java.io.PrintWriter(java.io.FileOutputStream(f, true))
            sw.println("=== $tag @ ${System.currentTimeMillis()} ===")
            t.printStackTrace(sw)
            sw.flush()
            sw.close()
        } catch (_: Throwable) {}
    }
}