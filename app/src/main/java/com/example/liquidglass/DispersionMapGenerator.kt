
package com.example.liquidglass

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max
import kotlin.math.abs


object DispersionMapGenerator {

    private const val TAG = "DispersionMapGenerator"

    
    enum class Shape {
        CIRCLE,      
        RECTANGLE,   
        ELLIPSE      
    }

    
    fun generateEdgeDistanceMapFromAlpha(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        Log.d(TAG, "Generating edge distance map from alpha channel: ${width}x${height}")

        
        val edgePixels = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = source.getPixel(x, y)
                val alpha = Color.alpha(pixel)

                
                val isEdge = alpha < 255 || isAlphaEdge(source, x, y)
                if (isEdge) {
                    edgePixels.add(Pair(x, y))
                }
            }
        }

        Log.d(TAG, "Detected ${edgePixels.size} edge pixels")

        
        if (edgePixels.isEmpty()) {
            bitmap.eraseColor(Color.WHITE)
            Log.w(TAG, "No edge pixels detected, returning white map")
            return bitmap
        }

        
        
        val maxDist = sqrt((width * width + height * height).toFloat()) / 2f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = source.getPixel(x, y)
                val alpha = Color.alpha(pixel)

                
                if (alpha < 10) {
                    bitmap.setPixel(x, y, Color.rgb(0, 0, 0))
                    continue
                }

                
                val distToLeft = x.toFloat()
                val distToRight = (width - 1 - x).toFloat()
                val distToTop = y.toFloat()
                val distToBottom = (height - 1 - y).toFloat()

                val minDistance = min(min(distToLeft, distToRight), min(distToTop, distToBottom))

                
                val normalizedDistance = (minDistance / maxDist * 255f).coerceIn(0f, 255f).toInt()

                bitmap.setPixel(x, y, Color.rgb(normalizedDistance, normalizedDistance, normalizedDistance))
            }
        }

        Log.d(TAG, "Edge distance map generated successfully")
        return bitmap
    }

    
    private fun isAlphaEdge(source: Bitmap, x: Int, y: Int): Boolean {
        val width = source.width
        val height = source.height
        val centerAlpha = Color.alpha(source.getPixel(x, y))

        
        val neighbors = listOf(
            Pair(x - 1, y),
            Pair(x + 1, y),
            Pair(x, y - 1),
            Pair(x, y + 1)
        )

        for ((nx, ny) in neighbors) {
            if (nx in 0 until width && ny in 0 until height) {
                val neighborAlpha = Color.alpha(source.getPixel(nx, ny))
                
                if (abs(centerAlpha - neighborAlpha) > 10) {
                    return true
                }
            }
        }

        return false
    }

    
    fun generateEdgeDistanceMap(
        width: Int,
        height: Int,
        shape: Shape = Shape.CIRCLE,
        centerX: Float = width / 2f,
        centerY: Float = height / 2f,
        maxDistance: Float? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        
        val maxDist = maxDistance ?: when (shape) {
            Shape.CIRCLE -> min(width, height) / 2f
            Shape.RECTANGLE -> sqrt((width / 2f) * (width / 2f) + (height / 2f) * (height / 2f))
            Shape.ELLIPSE -> max(width, height) / 2f
        }

        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY

                
                val distance = when (shape) {
                    Shape.CIRCLE -> {
                        
                        sqrt(dx * dx + dy * dy)
                    }
                    Shape.RECTANGLE -> {
                        
                        val distToLeft = x.toFloat()
                        val distToRight = (width - 1 - x).toFloat()
                        val distToTop = y.toFloat()
                        val distToBottom = (height - 1 - y).toFloat()
                        min(min(distToLeft, distToRight), min(distToTop, distToBottom))
                    }
                    Shape.ELLIPSE -> {
                        
                        val normalizedDx = dx / (width / 2f)
                        val normalizedDy = dy / (height / 2f)
                        sqrt(normalizedDx * normalizedDx + normalizedDy * normalizedDy) * maxDist
                    }
                }

                
                
                val normalizedDistance = (distance / maxDist * 255f).coerceIn(0f, 255f).toInt()

                
                bitmap.setPixel(x, y, Color.rgb(normalizedDistance, normalizedDistance, normalizedDistance))
            }
        }

        return bitmap
    }

    
    fun generateRadialNormalMap(
        width: Int,
        height: Int,
        centerX: Float = width / 2f,
        centerY: Float = height / 2f,
        invert: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val len = sqrt(dx * dx + dy * dy)

                
                val nx = if (len > 0f) dx / len else 0f
                val ny = if (len > 0f) dy / len else 0f

                
                val finalNx = if (invert) -nx else nx
                val finalNy = if (invert) -ny else ny

                
                
                val r = ((finalNx + 1f) * 0.5f * 255f).coerceIn(0f, 255f).toInt()
                val g = ((finalNy + 1f) * 0.5f * 255f).coerceIn(0f, 255f).toInt()
                val b = 128  

                
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        return bitmap
    }

    
    fun generateGradientEdgeMap(
        width: Int,
        height: Int,
        edgeWidth: Float = 50f,
        shape: Shape = Shape.CIRCLE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val centerX = width / 2f
        val centerY = height / 2f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY

                
                val distance = when (shape) {
                    Shape.CIRCLE -> {
                        val radius = min(width, height) / 2f
                        val distFromCenter = sqrt(dx * dx + dy * dy)
                        max(0f, radius - distFromCenter)
                    }
                    Shape.RECTANGLE -> {
                        val distToLeft = x.toFloat()
                        val distToRight = (width - 1 - x).toFloat()
                        val distToTop = y.toFloat()
                        val distToBottom = (height - 1 - y).toFloat()
                        min(min(distToLeft, distToRight), min(distToTop, distToBottom))
                    }
                    Shape.ELLIPSE -> {
                        val radiusX = width / 2f
                        val radiusY = height / 2f
                        val normalizedDist = sqrt((dx / radiusX) * (dx / radiusX) + (dy / radiusY) * (dy / radiusY))
                        max(0f, 1f - normalizedDist) * min(radiusX, radiusY)
                    }
                }

                
                val normalizedDistance = (distance / edgeWidth * 255f).coerceIn(0f, 255f).toInt()

                bitmap.setPixel(x, y, Color.rgb(normalizedDistance, normalizedDistance, normalizedDistance))
            }
        }

        return bitmap
    }

    
    fun generateCustomEdgeMap(
        width: Int,
        height: Int,
        maxDistance: Float = 200f,
        sdf: (x: Float, y: Float) -> Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                
                val distance = sdf(x.toFloat(), y.toFloat())

                
                val normalizedDistance = ((distance / maxDistance) * 255f).coerceIn(0f, 255f).toInt()

                bitmap.setPixel(x, y, Color.rgb(normalizedDistance, normalizedDistance, normalizedDistance))
            }
        }

        return bitmap
    }

    
    fun generateGlassSphereMap(width: Int, height: Int): Pair<Bitmap, Bitmap> {
        val edgeMap = generateEdgeDistanceMap(width, height, Shape.CIRCLE)
        val normalMap = generateRadialNormalMap(width, height)
        return Pair(edgeMap, normalMap)
    }

    
    fun generateGlassRectangleMap(width: Int, height: Int): Pair<Bitmap, Bitmap> {
        val edgeMap = generateEdgeDistanceMap(width, height, Shape.RECTANGLE)
        val normalMap = generateRadialNormalMap(width, height)
        return Pair(edgeMap, normalMap)
    }

    
    fun generateEdgeGlowMap(width: Int, height: Int, edgeWidth: Float = 50f): Bitmap {
        return generateGradientEdgeMap(width, height, edgeWidth, Shape.CIRCLE)
    }

    
    fun visualizeEdgeDistanceMap(edgeMap: Bitmap): Bitmap {
        val width = edgeMap.width
        val height = edgeMap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = edgeMap.getPixel(x, y)
                val value = Color.red(pixel) / 255f

                
                val r = (value * 255f).toInt()
                val g = ((1f - kotlin.math.abs(value - 0.5f) * 2f) * 255f).toInt()
                val b = ((1f - value) * 255f).toInt()

                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        return result
    }

    
    fun visualizeNormalMap(normalMap: Bitmap): Bitmap {
        
        return normalMap.copy(Bitmap.Config.ARGB_8888, false)
    }
}

