
package com.example.liquidglass

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread


class AsyncRenderer(
    private val renderCallback: (RenderParams) -> Bitmap?
) {
    companion object {
        private const val TAG = "AsyncRenderer"
    }
    
    
    private val frontBuffer = AtomicReference<Bitmap?>(null)
    private val backBuffer = AtomicReference<Bitmap?>(null)
    
    
    private val isRendering = AtomicBoolean(false)
    private val shouldStop = AtomicBoolean(false)
    private var renderThread: Thread? = null
    
    
    private val pendingParams = AtomicReference<RenderParams?>(null)
    
    
    data class RenderParams(
        val bounds: RectF,
        val blurRadius: Float,
        val displacementScale: Float,
        val aberrationIntensity: Float,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    
    fun requestRender(params: RenderParams): Bitmap? {
        
        pendingParams.set(params)
        
        
        if (!isRendering.get()) {
            startRenderThread()
        }
        
        
        return frontBuffer.get()
    }
    
    
    fun getCurrentFrame(): Bitmap? {
        return frontBuffer.get()
    }
    
    
    private fun startRenderThread() {
        if (isRendering.compareAndSet(false, true)) {
            renderThread = thread(name = "LiquidGlass-Renderer") {
                try {
                    renderLoop()
                } catch (e: Exception) {
                    Log.e(TAG, "渲染线程异常: ${e.message}", e)
                } finally {
                    isRendering.set(false)
                }
            }
        }
    }
    
    
    private fun renderLoop() {
        while (!shouldStop.get()) {
            
            val params = pendingParams.getAndSet(null)

            if (params == null) {
                
                break
            }

            
            val startTime = System.nanoTime()
            val result = try {
                renderCallback(params)
            } catch (e: Exception) {
                Log.e(TAG, "渲染失败: ${e.message}", e)
                null
            }
            val duration = (System.nanoTime() - startTime) / 1_000_000f

            if (result != null) {
                
                
                val oldBackBuffer = backBuffer.getAndSet(result)

                
                val oldFrontBuffer = frontBuffer.getAndSet(result)

                
                
                if (oldFrontBuffer != null && oldFrontBuffer != oldBackBuffer) {
                    oldFrontBuffer.recycle()
                }

                Log.d(TAG, "异步渲染完成: ${duration}ms")
            }
        }
    }
    
    
    fun stop() {
        shouldStop.set(true)
        
        
        renderThread?.join(1000)
        
        
        frontBuffer.getAndSet(null)?.recycle()
        backBuffer.getAndSet(null)?.recycle()
        
        Log.d(TAG, "异步渲染器已停止")
    }
    
    
    fun isRendering(): Boolean {
        return isRendering.get()
    }
}

