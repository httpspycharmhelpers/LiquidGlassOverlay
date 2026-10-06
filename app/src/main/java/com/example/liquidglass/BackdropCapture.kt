
package com.example.liquidglass

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent

internal class BackdropCapture {

    private companion object {
        
        var depth = 0
    }

    
    private val hostLocation = IntArray(2)
    private val childLocation = IntArray(2)
    private val nestedHidden = ArrayList<View>()

    
    fun draw(canvas: Canvas, source: View, glass: View) {
        drawLevel(canvas, source, glass)
    }

    private fun drawLevel(canvas: Canvas, host: View, glass: View) {
        
        
        
        val branch = childOnPathTo(host, glass)
        drawContent(canvas, host, branch ?: glass)
        if (branch == null || branch === glass) return

        
        host.getLocationOnScreen(hostLocation)
        branch.getLocationOnScreen(childLocation)
        val dx = (childLocation[0] - hostLocation[0]).toFloat()
        val dy = (childLocation[1] - hostLocation[1]).toFloat()
        val clip = (host as? ViewGroup)?.clipChildren != false

        val save = canvas.save()
        if (clip) canvas.clipRect(0f, 0f, host.width.toFloat(), host.height.toFloat())
        canvas.translate(dx, dy)
        if (clip) canvas.clipRect(0f, 0f, branch.width.toFloat(), branch.height.toFloat())
        drawLevel(canvas, branch, glass)
        canvas.restoreToCount(save)
    }

    
    private fun drawContent(canvas: Canvas, host: View, hidden: View) {
        val save = canvas.save()
        
        
        canvas.translate(-host.scrollX.toFloat(), -host.scrollY.toFloat())
        
        hidden.setTransitionVisibility(View.INVISIBLE)
        val nested = depth > 0
        if (nested) hideOtherGlass(host, hidden)
        depth++
        try {
            host.draw(canvas)
        } finally {
            depth--
            if (nested) {
                for (v in nestedHidden) v.setTransitionVisibility(View.VISIBLE)
                nestedHidden.clear()
            }
            hidden.setTransitionVisibility(View.VISIBLE)
            canvas.restoreToCount(save)
        }
    }

    
    private fun hideOtherGlass(host: View, except: View) {
        if (host !is ViewGroup) return
        for (i in 0 until host.childCount) {
            val child = host.getChildAt(i)
            if (child === except || child !is LiquidGlassView) continue
            if (child.visibility == View.VISIBLE) {
                child.setTransitionVisibility(View.INVISIBLE)
                nestedHidden.add(child)
            }
        }
    }

    
    private fun childOnPathTo(host: View, descendant: View): View? {
        var child: View = descendant
        var p: ViewParent = descendant.parent
        while (p is View) {
            if (p === host) return child
            child = p
            p = ViewParentOf(p)
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> ViewParentOf(v: T): ViewParent = v.parent
}
