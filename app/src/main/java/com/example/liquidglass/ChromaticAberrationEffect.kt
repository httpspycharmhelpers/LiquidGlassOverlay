
package com.example.liquidglass

import android.graphics.*
import android.util.Log
import kotlin.math.roundToInt


class ChromaticAberrationEffect {

    
    enum class PerformanceMode {
        KOTLIN,    
        CPP,       
        AUTO       
    }

    
    var performanceMode: PerformanceMode = PerformanceMode.AUTO

    
    
    
    var useBilinearInterpolation: Boolean = true

    
    private var cachedSourcePixels: IntArray? = null
    private var cachedMapPixels: IntArray? = null
    private var cachedResultPixels: IntArray? = null
    private var cachedSize = 0

    
    private val bitmapPool = BitmapPool.getInstance()
    
    
    fun apply(
        source: Bitmap,
        displacementMap: Bitmap,
        intensity: Float = 2f,
        scale: Float = 70f,
        downscale: Float = 0.5f,  
        redOffset: Float = 0f,
        greenOffset: Float = -0.05f,
        blueOffset: Float = -0.1f
    ): Bitmap {
        
        return when (selectImplementation(source, downscale)) {
            Implementation.CPP -> applyCpp(
                source, displacementMap, intensity, scale,
                downscale, redOffset, greenOffset, blueOffset
            )
            Implementation.KOTLIN -> applyKotlin(
                source, displacementMap, intensity, scale,
                downscale, redOffset, greenOffset, blueOffset
            )
        }
    }

    
    private fun selectImplementation(source: Bitmap, downscale: Float): Implementation {
        return when (performanceMode) {
            PerformanceMode.KOTLIN -> Implementation.KOTLIN
            PerformanceMode.CPP -> Implementation.CPP
            PerformanceMode.AUTO -> {
                
                
                
                
                val pixels = source.width * source.height
                if (pixels > 256 * 256 || downscale >= 0.5f) {
                    Implementation.CPP
                } else {
                    Implementation.KOTLIN
                }
            }
        }
    }

    
    private fun applyCpp(
        source: Bitmap,
        displacementMap: Bitmap,
        intensity: Float,
        scale: Float,
        downscale: Float,
        redOffset: Float,
        greenOffset: Float,
        blueOffset: Float
    ): Bitmap {
        try {
            val originalWidth = source.width
            val originalHeight = source.height

            
            val processWidth = (originalWidth * downscale).toInt().coerceAtLeast(1)
            val processHeight = (originalHeight * downscale).toInt().coerceAtLeast(1)

            
            val smallSource = if (downscale < 1.0f) {
                val temp = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(temp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)  
                val srcRect = Rect(0, 0, source.width, source.height)
                val dstRect = Rect(0, 0, processWidth, processHeight)
                canvas.drawBitmap(source, srcRect, dstRect, paint)
                temp
            } else {
                source.copy(Bitmap.Config.ARGB_8888, true) ?: source
            }

            
            val scaledMap = if (displacementMap.width != processWidth || displacementMap.height != processHeight) {
                val temp = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(temp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)  
                val srcRect = Rect(0, 0, displacementMap.width, displacementMap.height)
                val dstRect = Rect(0, 0, processWidth, processHeight)
                canvas.drawBitmap(displacementMap, srcRect, dstRect, paint)
                temp
            } else {
                displacementMap.copy(Bitmap.Config.ARGB_8888, true) ?: displacementMap
            }

            
            val result = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)

            
            
            val adjustedScale = scale * downscale
            val adjustedRedOffset = redOffset * intensity * downscale
            val adjustedGreenOffset = greenOffset * intensity * downscale
            val adjustedBlueOffset = blueOffset * intensity * downscale

            
            NativeChromaticAberration.chromaticAberrationInplace(
                source = smallSource,
                displacement = scaledMap,
                result = result,
                intensity = intensity,
                scale = adjustedScale,
                redOffset = adjustedRedOffset,
                greenOffset = adjustedGreenOffset,
                blueOffset = adjustedBlueOffset,
                useBilinear = useBilinearInterpolation
            )

            
            if (scaledMap != displacementMap) {
                bitmapPool.put(scaledMap)
            }
            if (smallSource != source) {
                bitmapPool.put(smallSource)
            }

            
            val finalResult = if (downscale < 1.0f) {
                val upscaled = bitmapPool.get(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(upscaled)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val srcRect = Rect(0, 0, processWidth, processHeight)
                val dstRect = Rect(0, 0, originalWidth, originalHeight)
                canvas.drawBitmap(result, srcRect, dstRect, paint)
                bitmapPool.put(result)
                upscaled
            } else {
                result
            }

            return finalResult
        } catch (e: Throwable) {
            Log.e(TAG, "C++ implementation failed, falling back to Kotlin", e)
            
            return applyKotlin(source, displacementMap, intensity, scale, downscale, redOffset, greenOffset, blueOffset)
        }
    }

    
    private fun applyKotlin(
        source: Bitmap,
        displacementMap: Bitmap,
        intensity: Float,
        scale: Float,
        downscale: Float,
        redOffset: Float,
        greenOffset: Float,
        blueOffset: Float
    ): Bitmap {
        val originalWidth = source.width
        val originalHeight = source.height

        
        val processWidth = (originalWidth * downscale).toInt().coerceAtLeast(1)
        val processHeight = (originalHeight * downscale).toInt().coerceAtLeast(1)

        
        val smallSource = if (downscale < 1.0f) {
            val temp = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(temp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)  
            val srcRect = android.graphics.Rect(0, 0, source.width, source.height)
            val dstRect = android.graphics.Rect(0, 0, processWidth, processHeight)
            canvas.drawBitmap(source, srcRect, dstRect, paint)
            temp
        } else {
            source
        }

        val result = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)

        
        val scaledMap = if (displacementMap.width != processWidth || displacementMap.height != processHeight) {
            val temp = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(temp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)  
            val srcRect = android.graphics.Rect(0, 0, displacementMap.width, displacementMap.height)
            val dstRect = android.graphics.Rect(0, 0, processWidth, processHeight)
            canvas.drawBitmap(displacementMap, srcRect, dstRect, paint)
            temp
        } else {
            displacementMap
        }

        
        val pixelCount = processWidth * processHeight
        if (cachedSize != pixelCount) {
            cachedSourcePixels = IntArray(pixelCount)
            cachedMapPixels = IntArray(pixelCount)
            cachedResultPixels = IntArray(pixelCount)
            cachedSize = pixelCount
        }

        val sourcePixels = cachedSourcePixels!!
        val mapPixels = cachedMapPixels!!
        val resultPixels = cachedResultPixels!!

        smallSource.getPixels(sourcePixels, 0, processWidth, 0, 0, processWidth, processHeight)
        scaledMap.getPixels(mapPixels, 0, processWidth, 0, 0, processWidth, processHeight)

        
        val scaleFactor = (scale * downscale) / 255f

        
        val actualRedOffset = redOffset * intensity * downscale
        val actualGreenOffset = greenOffset * intensity * downscale
        val actualBlueOffset = blueOffset * intensity * downscale

        
        for (y in 0 until processHeight) {
            for (x in 0 until processWidth) {
                val index = y * processWidth + x
                val mapColor = mapPixels[index]

                
                val baseDx = (Color.red(mapColor) - 128) * scaleFactor
                val baseDy = (Color.green(mapColor) - 128) * scaleFactor

                
                
                val baseLen = kotlin.math.sqrt(baseDx * baseDx + baseDy * baseDy)
                val dirX: Float
                val dirY: Float
                if (baseLen > 0.001f) {
                    dirX = baseDx / baseLen
                    dirY = baseDy / baseLen
                } else {
                    dirX = 0f
                    dirY = 0f
                }

                
                val rSrcX = x + baseDx + dirX * actualRedOffset
                val rSrcY = y + baseDy + dirY * actualRedOffset
                val gSrcX = x + baseDx + dirX * actualGreenOffset
                val gSrcY = y + baseDy + dirY * actualGreenOffset
                val bSrcX = x + baseDx + dirX * actualBlueOffset
                val bSrcY = y + baseDy + dirY * actualBlueOffset

                
                val r: Int
                val g: Int
                val b: Int

                if (useBilinearInterpolation) {
                    
                    val rColor = sampleBilinear(sourcePixels, processWidth, processHeight, rSrcX, rSrcY)
                    val gColor = sampleBilinear(sourcePixels, processWidth, processHeight, gSrcX, gSrcY)
                    val bColor = sampleBilinear(sourcePixels, processWidth, processHeight, bSrcX, bSrcY)
                    r = Color.red(rColor)
                    g = Color.green(gColor)
                    b = Color.blue(bColor)
                } else {
                    
                    val rColor = samplePixel(sourcePixels, processWidth, processHeight, rSrcX, rSrcY)
                    val gColor = samplePixel(sourcePixels, processWidth, processHeight, gSrcX, gSrcY)
                    val bColor = samplePixel(sourcePixels, processWidth, processHeight, bSrcX, bSrcY)
                    r = Color.red(rColor)
                    g = Color.green(gColor)
                    b = Color.blue(bColor)
                }

                val a = Color.alpha(sourcePixels[index])

                resultPixels[index] = Color.argb(a, r, g, b)
            }
        }

        result.setPixels(resultPixels, 0, processWidth, 0, 0, processWidth, processHeight)

        
        if (scaledMap != displacementMap) {
            bitmapPool.put(scaledMap)
        }
        if (smallSource != source) {
            bitmapPool.put(smallSource)
        }

        
        val finalResult = if (downscale < 1.0f) {
            val upscaled = bitmapPool.get(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(upscaled)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val srcRect = android.graphics.Rect(0, 0, processWidth, processHeight)
            val dstRect = android.graphics.Rect(0, 0, originalWidth, originalHeight)
            canvas.drawBitmap(result, srcRect, dstRect, paint)
            bitmapPool.put(result)
            upscaled
        } else {
            result
        }

        return finalResult
    }

    
    private fun sampleBilinear(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        
        if (x < 0 || x >= width - 1 || y < 0 || y >= height - 1) {
            val clampedX = x.roundToInt().coerceIn(0, width - 1)
            val clampedY = y.roundToInt().coerceIn(0, height - 1)
            return pixels[clampedY * width + clampedX]
        }

        
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)

        val fx = x - x0  
        val fy = y - y0  

        
        val c00 = pixels[y0 * width + x0]  
        val c10 = pixels[y0 * width + x1]  
        val c01 = pixels[y1 * width + x0]  
        val c11 = pixels[y1 * width + x1]  

        
        val a = interpolateChannel(Color.alpha(c00), Color.alpha(c10), Color.alpha(c01), Color.alpha(c11), fx, fy)
        val r = interpolateChannel(Color.red(c00), Color.red(c10), Color.red(c01), Color.red(c11), fx, fy)
        val g = interpolateChannel(Color.green(c00), Color.green(c10), Color.green(c01), Color.green(c11), fx, fy)
        val b = interpolateChannel(Color.blue(c00), Color.blue(c10), Color.blue(c01), Color.blue(c11), fx, fy)

        return Color.argb(a, r, g, b)
    }

    
    private fun interpolateChannel(c00: Int, c10: Int, c01: Int, c11: Int, fx: Float, fy: Float): Int {
        
        val c0 = c00 * (1 - fx) + c10 * fx  
        val c1 = c01 * (1 - fx) + c11 * fx  

        
        val result = c0 * (1 - fy) + c1 * fy

        return result.roundToInt().coerceIn(0, 255)
    }

    
    private fun samplePixel(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        val clampedX = x.roundToInt().coerceIn(0, width - 1)
        val clampedY = y.roundToInt().coerceIn(0, height - 1)
        return pixels[clampedY * width + clampedX]
    }

    
    @Deprecated("Use optimized version in apply() method")
    private fun displaceChannel(
        source: Bitmap,
        displacementMap: Bitmap,
        scale: Float,
        offset: Float
    ): Bitmap {
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val actualScale = (scale + scale * offset) / 255f

        for (y in 0 until height) {
            for (x in 0 until width) {
                
                val mapColor = displacementMap.getPixel(x, y)
                val dx = (Color.red(mapColor) - 128) * actualScale
                val dy = (Color.green(mapColor) - 128) * actualScale

                
                val srcX = (x + dx).roundToInt().coerceIn(0, width - 1)
                val srcY = (y + dy).roundToInt().coerceIn(0, height - 1)

                
                result.setPixel(x, y, source.getPixel(srcX, srcY))
            }
        }

        return result
    }
    
    
    fun applyWithEdgeMask(
        source: Bitmap,
        displacementMap: Bitmap,
        intensity: Float = 2f,
        scale: Float = 70f
    ): Bitmap {
        val width = source.width
        val height = source.height
        
        
        val edgeMask = generateRadialEdgeMask(width, height, intensity)
        
        
        val aberrated = apply(source, displacementMap, intensity, scale)
        
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val maskValue = Color.alpha(edgeMask.getPixel(x, y)) / 255f
                
                val original = source.getPixel(x, y)
                val aberratedColor = aberrated.getPixel(x, y)
                
                
                val r = lerp(Color.red(original), Color.red(aberratedColor), maskValue)
                val g = lerp(Color.green(original), Color.green(aberratedColor), maskValue)
                val b = lerp(Color.blue(original), Color.blue(aberratedColor), maskValue)
                val a = Color.alpha(original)
                
                result.setPixel(x, y, Color.argb(a, r, g, b))
            }
        }
        
        edgeMask.recycle()
        aberrated.recycle()
        
        return result
    }
    
    
    private fun generateRadialEdgeMask(
        width: Int,
        height: Int,
        intensity: Float
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = kotlin.math.max(width, height) / 2f
        
        
        val startOffset = kotlin.math.max(30f, 80f - intensity * 2f) / 100f
        
        val gradient = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(
                Color.argb(0, 0, 0, 0),      
                Color.argb(0, 0, 0, 0),      
                Color.argb(255, 255, 255, 255)  
            ),
            floatArrayOf(0f, startOffset, 1f),
            Shader.TileMode.CLAMP
        )
        
        val paint = Paint()
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        return mask
    }
    
    
    private fun lerp(a: Int, b: Int, t: Float): Int {
        return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
    }

    
    fun cleanup() {
        
        cachedSourcePixels = null
        cachedMapPixels = null
        cachedResultPixels = null
        cachedSize = 0
    }

    
    private enum class Implementation {
        KOTLIN, CPP
    }

    companion object {
        private const val TAG = "ChromaticAberration"
    }
}

