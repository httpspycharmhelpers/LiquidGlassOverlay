
package com.example.liquidglass

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import java.lang.reflect.Field
import kotlin.math.roundToInt

internal class WindowStackBackdrop(context: Context) : View(context) {

    
    var ownWindow: View? = null

    private val layers = ArrayList<View>()
    private val selfLocation = IntArray(2)
    private val rootLocation = IntArray(2)

    override fun onDraw(canvas: Canvas) {
        val own = ownWindow ?: return
        AppWindows.collectBelow(own, layers)
        getLocationOnScreen(selfLocation)
        try {
            for (i in layers.indices) {
                val root = layers[i]
                val lp = root.layoutParams as? WindowManager.LayoutParams
                
                if (i > 0 && lp != null && lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                    val dim = (lp.dimAmount.coerceIn(0f, 1f) * 255f).roundToInt()
                    if (dim > 0) canvas.drawColor(Color.argb(dim, 0, 0, 0))
                }
                root.getLocationOnScreen(rootLocation)
                val save = canvas.save()
                canvas.translate(
                    (rootLocation[0] - selfLocation[0]).toFloat(),
                    (rootLocation[1] - selfLocation[1]).toFloat()
                )
                
                canvas.clipRect(0, 0, root.width, root.height)
                
                canvas.translate(-root.scrollX.toFloat(), -root.scrollY.toFloat())
                root.draw(canvas)
                canvas.restoreToCount(save)
            }
        } finally {
            
            layers.clear()
        }
    }
}


internal object AppWindows {

    private const val TAG = "AppWindows"

    private var legacyGlobal: Any? = null
    private var legacyViews: Field? = null
    private var legacyTried = false

    private val ownRect = Rect()
    private val otherRect = Rect()
    private val location = IntArray(2)

    
    fun roots(): List<View> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return WindowInspector.getGlobalWindowViews()
        }
        return legacyRoots()
    }

    
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun legacyRoots(): List<View> {
        if (!legacyTried) {
            legacyTried = true
            try {
                val cls = Class.forName("android.view.WindowManagerGlobal")
                legacyGlobal = cls.getMethod("getInstance").invoke(null)
                legacyViews = cls.getDeclaredField("mViews").apply { isAccessible = true }
            } catch (e: Exception) {
                Log.w(TAG, "WindowManagerGlobal.mViews unavailable", e)
            }
        }
        val field = legacyViews ?: return emptyList()
        return try {
            (field.get(legacyGlobal) as? List<*>)?.filterIsInstance<View>() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    
    fun collectBelow(own: View, out: MutableList<View>) {
        out.clear()
        val token = params(own)?.token ?: return
        val roots = roots()
        for (root in topLevel(roots, token)) {
            if (root === own) return
            if (!isVisible(root)) continue
            out += root
            addSubWindows(root, roots, out)
        }
    }

    
    fun isCovered(own: View): Boolean {
        val token = params(own)?.token ?: return false
        own.getLocationOnScreen(location)
        ownRect.set(location[0], location[1], location[0] + own.width, location[1] + own.height)
        var passedOwn = false
        for (root in topLevel(roots(), token)) {
            if (root === own) {
                passedOwn = true
                continue
            }
            if (!passedOwn || !isVisible(root)) continue
            val lp = params(root) ?: continue
            if (lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0 && lp.dimAmount > 0f) {
                return true
            }
            root.getLocationOnScreen(location)
            otherRect.set(location[0], location[1], location[0] + root.width, location[1] + root.height)
            if (Rect.intersects(ownRect, otherRect)) return true
        }
        return false
    }

    
    private fun topLevel(roots: List<View>, token: IBinder): List<View> {
        val result = ArrayList<View>(roots.size)
        var baseCount = 0
        for (root in roots) {
            val lp = params(root) ?: continue
            if (lp.token != token || isSubWindow(lp)) continue
            
            if (lp.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION) {
                result.add(baseCount++, root)
            } else {
                result += root
            }
        }
        return result
    }

    private fun addSubWindows(parent: View, roots: List<View>, out: MutableList<View>) {
        val token = parent.windowToken ?: return
        for (root in roots) {
            val lp = params(root) ?: continue
            if (isSubWindow(lp) && lp.token == token && isVisible(root)) {
                out += root
                addSubWindows(root, roots, out)
            }
        }
    }

    private fun params(root: View): WindowManager.LayoutParams? =
        root.layoutParams as? WindowManager.LayoutParams

    private fun isSubWindow(lp: WindowManager.LayoutParams): Boolean =
        lp.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW &&
            lp.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW

    private fun isVisible(root: View): Boolean = root.isShown && root.width > 0 && root.height > 0
}
