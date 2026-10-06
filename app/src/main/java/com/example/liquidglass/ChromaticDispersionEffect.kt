
package com.example.liquidglass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Log
import kotlin.math.min


class ChromaticDispersionEffect {

    companion object {
        private const val TAG = "ChromaticDispersion"

        
        object Presets {
            
            data class Glass(
                val refThickness: Float = 100f,
                val refFactor: Float = 1.5f,
                val refDispersion: Float = 7f
            )

            
            data class Diamond(
                val refThickness: Float = 80f,
                val refFactor: Float = 2.4f,
                val refDispersion: Float = 15f
            )

            
            data class Crystal(
                val refThickness: Float = 120f,
                val refFactor: Float = 1.8f,
                val refDispersion: Float = 10f
            )

            
            data class Rainbow(
                val refThickness: Float = 150f,
                val refFactor: Float = 1.3f,
                val refDispersion: Float = 20f
            )

            
            data class Subtle(
                val refThickness: Float = 200f,
                val refFactor: Float = 1.2f,
                val refDispersion: Float = 3f
            )
        }
    }

    
    var useBilinearInterpolation: Boolean = true

    
    var devicePixelRatio: Float = 1.0f

    
    private var cachedEdgeMap: Bitmap? = null
    private var cachedNormalMap: Bitmap? = null
    private var cachedSize = Pair(0, 0)

    
    fun apply(
        source: Bitmap,
        refThickness: Float = 100f,
        refFactor: Float = 1.5f,
        refDispersion: Float = 7f,
        downscale: Float = 0.5f,
        cornerRadius: Float = 0f,
        useNormalMap: Boolean = false,
        cornerRadii: FloatArray? = null
    ): Bitmap {
        val startTime = System.currentTimeMillis()

        
        val processWidth = (source.width * downscale).toInt()
        val processHeight = (source.height * downscale).toInt()

        Log.d(TAG, "Processing ${source.width}x${source.height} -> ${processWidth}x${processHeight} (downscale=$downscale)")

        val smallSource = if (downscale < 1.0f) {
            Bitmap.createScaledBitmap(source, processWidth, processHeight, true)
        } else {
            source
        }

        
        val maxRadius = min(processWidth, processHeight) / 2f
        val clippedSource = if (cornerRadii != null) {
            
            applyRoundedCornerClip(smallSource, FloatArray(8) { min(cornerRadii[it] * downscale, maxRadius) })
        } else if (cornerRadius > 0) {
            
            val scaledRadius = cornerRadius * downscale
            val clampedRadius = min(scaledRadius, maxRadius)
            applyRoundedCornerClip(smallSource, clampedRadius)
        } else {
            smallSource
        }

        
        val edgeMap = if (cachedEdgeMap != null && cachedSize == Pair(processWidth, processHeight)) {
            cachedEdgeMap!!
        } else {
            Log.d(TAG, "Generating edge distance map from alpha channel...")
            val map = DispersionMapGenerator.generateEdgeDistanceMapFromAlpha(clippedSource)
            cachedEdgeMap = map
            cachedSize = Pair(processWidth, processHeight)
            map
        }

        
        val normalMap = if (useNormalMap) {
            if (cachedNormalMap != null && cachedSize == Pair(processWidth, processHeight)) {
                cachedNormalMap!!
            } else {
                Log.d(TAG, "Generating normal map...")
                val map = DispersionMapGenerator.generateRadialNormalMap(processWidth, processHeight)
                cachedNormalMap = map
                map
            }
        } else {
            null
        }

        
        val result = Bitmap.createBitmap(processWidth, processHeight, Bitmap.Config.ARGB_8888)

        
        val processingStart = System.currentTimeMillis()
        try {
            NativeChromaticDispersion.chromaticDispersionInplace(
                source = smallSource,
                edgeDistance = edgeMap,
                normalMap = normalMap,
                result = result,
                refThickness = refThickness * downscale,  
                refFactor = refFactor,
                refDispersion = refDispersion,
                dpr = devicePixelRatio,
                useBilinear = useBilinearInterpolation
            )
        } catch (e: Throwable) {
            Log.e(TAG, "native dispersion failed, skip", e)
            return source
        }
        val processingTime = System.currentTimeMillis() - processingStart

        
        val finalResult = if (downscale < 1.0f) {
            val upscaled = Bitmap.createScaledBitmap(result, source.width, source.height, true)
            result.recycle()
            upscaled
        } else {
            result
        }

        
        if (downscale < 1.0f && smallSource != source) {
            smallSource.recycle()
        }

        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Dispersion effect applied: processing=${processingTime}ms, total=${totalTime}ms")

        return finalResult
    }

    
    fun applyPreset(
        source: Bitmap,
        preset: String,
        downscale: Float = 0.5f,
        cornerRadius: Float = 0f
    ): Bitmap {
        return when (preset.lowercase()) {
            "glass" -> {
                val p = Presets.Glass()
                apply(source, p.refThickness, p.refFactor, p.refDispersion, downscale, cornerRadius)
            }
            "diamond" -> {
                val p = Presets.Diamond()
                apply(source, p.refThickness, p.refFactor, p.refDispersion, downscale, cornerRadius)
            }
            "crystal" -> {
                val p = Presets.Crystal()
                apply(source, p.refThickness, p.refFactor, p.refDispersion, downscale, cornerRadius)
            }
            "rainbow" -> {
                val p = Presets.Rainbow()
                apply(source, p.refThickness, p.refFactor, p.refDispersion, downscale, cornerRadius)
            }
            "subtle" -> {
                val p = Presets.Subtle()
                apply(source, p.refThickness, p.refFactor, p.refDispersion, downscale, cornerRadius)
            }
            else -> {
                Log.w(TAG, "Unknown preset: $preset, using default (glass)")
                val p = Presets.Glass()
                apply(source, p.refThickness, p.refFactor, p.refDispersion, downscale, cornerRadius)
            }
        }
    }

    
    private fun applyRoundedCornerClip(source: Bitmap, cornerRadius: Float): Bitmap =
        applyRoundedCornerClip(source, FloatArray(8) { cornerRadius })

    
    private fun applyRoundedCornerClip(source: Bitmap, radii: FloatArray): Bitmap {
        val width = source.width
        val height = source.height

        
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        
        val path = Path()
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        path.addRoundRect(rect, radii, Path.Direction.CW)

        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        canvas.drawPath(path, paint)

        
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)

        Log.d(TAG, "Applied rounded corner clip: radii=${radii.joinToString()}")
        return output
    }

    
    fun clearCache() {
        cachedEdgeMap?.recycle()
        cachedEdgeMap = null
        cachedNormalMap?.recycle()
        cachedNormalMap = null
        cachedSize = Pair(0, 0)
    }

    
    fun getRecommendedDownscale(width: Int, height: Int): Float {
        val pixels = width * height
        return when {
            pixels > 2_000_000 -> 0.25f  // > 2MP: 0.25x
            pixels > 1_000_000 -> 0.5f   // > 1MP: 0.5x
            else -> 0.75f                // <= 1MP: 0.75x
        }
    }
}

