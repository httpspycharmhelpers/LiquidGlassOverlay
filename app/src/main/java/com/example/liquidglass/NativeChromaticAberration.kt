
package com.example.liquidglass

import com.liquidglass.app.R
import android.graphics.Bitmap

object NativeChromaticAberration {

    init {
        try {
            System.loadLibrary("nativegauss")
        } catch (_: Throwable) {
            
        }
    }

    
    external fun chromaticAberrationInplace(
        source: Bitmap,
        displacement: Bitmap,
        result: Bitmap,
        intensity: Float = 2.0f,
        scale: Float = 70.0f,
        redOffset: Float = 0.0f,
        greenOffset: Float = -0.05f,
        blueOffset: Float = -0.1f,
        useBilinear: Boolean = true
    )

    
    fun apply(
        source: Bitmap,
        displacement: Bitmap,
        intensity: Float = 2.0f,
        scale: Float = 70.0f,
        redOffset: Float = 0.0f,
        greenOffset: Float = -0.05f,
        blueOffset: Float = -0.1f,
        useBilinear: Boolean = true
    ): Bitmap {
        
        val result = Bitmap.createBitmap(
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )

        
        chromaticAberrationInplace(
            source,
            displacement,
            result,
            intensity,
            scale,
            redOffset,
            greenOffset,
            blueOffset,
            useBilinear
        )

        return result
    }

    
    fun applyWithDownsample(
        source: Bitmap,
        displacement: Bitmap,
        intensity: Float = 2.0f,
        scale: Float = 70.0f,
        downscale: Float = 0.5f,
        redOffset: Float = 0.0f,
        greenOffset: Float = -0.05f,
        blueOffset: Float = -0.1f
    ): Bitmap {
        val originalWidth = source.width
        val originalHeight = source.height
        
        
        val processWidth = (originalWidth * downscale).toInt().coerceAtLeast(1)
        val processHeight = (originalHeight * downscale).toInt().coerceAtLeast(1)
        
        
        val smallSource = if (downscale < 1.0f) {
            Bitmap.createScaledBitmap(source, processWidth, processHeight, true)
        } else {
            source
        }
        
        
        val smallDisplacement = if (downscale < 1.0f) {
            Bitmap.createScaledBitmap(displacement, processWidth, processHeight, true)
        } else {
            displacement
        }
        
        
        val smallResult = Bitmap.createBitmap(
            processWidth,
            processHeight,
            Bitmap.Config.ARGB_8888
        )

        
        
        val adjustedScale = scale * downscale
        val adjustedRedOffset = redOffset * intensity * downscale
        val adjustedGreenOffset = greenOffset * intensity * downscale
        val adjustedBlueOffset = blueOffset * intensity * downscale

        
        chromaticAberrationInplace(
            smallSource,
            smallDisplacement,
            smallResult,
            intensity,
            adjustedScale,
            adjustedRedOffset,
            adjustedGreenOffset,
            adjustedBlueOffset
        )
        
        
        if (smallSource != source) {
            smallSource.recycle()
        }
        if (smallDisplacement != displacement) {
            smallDisplacement.recycle()
        }
        
        
        val finalResult = if (downscale < 1.0f) {
            val upscaled = Bitmap.createScaledBitmap(
                smallResult,
                originalWidth,
                originalHeight,
                true
            )
            smallResult.recycle()
            upscaled
        } else {
            smallResult
        }
        
        return finalResult
    }

    
    fun validateBitmap(bitmap: Bitmap): Boolean {
        return bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.isMutable
    }

    
    fun validateDimensions(vararg bitmaps: Bitmap): Boolean {
        if (bitmaps.isEmpty()) return true
        
        val width = bitmaps[0].width
        val height = bitmaps[0].height
        
        return bitmaps.all { it.width == width && it.height == height }
    }
}

