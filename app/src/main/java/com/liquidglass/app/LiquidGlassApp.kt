package com.liquidglass.app

import android.app.Application
import java.io.File
import java.io.PrintWriter


class LiquidGlassApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val dir = File(getExternalFilesDir(null), "crash")
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, "uncaught.log")
                val pw = PrintWriter(java.io.FileOutputStream(f, true))
                pw.println("===== ${java.util.Date()} | thread=${t.name} =====")
                e.printStackTrace(pw)
                pw.flush()
                pw.close()
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }
}