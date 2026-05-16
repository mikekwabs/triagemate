package com.triagemate.chps.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

data class ImageQualityResult(
    val isAcceptable: Boolean,
    val reason: String? = null
)

object ImageQualityChecker {

    suspend fun check(imageUri: Uri, context: Context): ImageQualityResult = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(context, imageUri)
            ?: return@withContext ImageQualityResult(false, "Unable to read captured photo")

        if (bitmap.width < 200 || bitmap.height < 200) {
            return@withContext ImageQualityResult(false, "Photo is too small. Please retake a clearer image.")
        }

        val brightness = averageLuminance(bitmap)
        when {
            brightness < 30.0 -> return@withContext ImageQualityResult(false, "Photo is too dark. Please retake with more light.")
            brightness > 240.0 -> return@withContext ImageQualityResult(false, "Photo appears overexposed. Please retake with less glare.")
        }

        val blurVariance = laplacianVariance(bitmap)
        if (blurVariance < 100.0) {
            return@withContext ImageQualityResult(false, "Photo is blurry. Please retake and hold the camera steady.")
        }

        ImageQualityResult(true, null)
    }

    private fun openStream(context: Context, uri: Uri): InputStream? =
        if (uri.scheme == "file" || uri.scheme == null) {
            uri.path?.let { File(it).takeIf { f -> f.exists() }?.inputStream() }
        } else {
            context.contentResolver.openInputStream(uri)
        }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = openStream(context, uri) ?: return@runCatching null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 384, 384)
            }

            openStream(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
    }

    private fun calculateSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                sampleSize *= 2
            }
        }
        return max(1, sampleSize)
    }

    private fun averageLuminance(bitmap: Bitmap): Double {
        val scaled = scaleForAnalysis(bitmap)
        var sum = 0.0
        var count = 0
        for (y in 0 until scaled.height step 2) {
            for (x in 0 until scaled.width step 2) {
                sum += luminance(scaled.getPixel(x, y))
                count++
            }
        }
        return if (count == 0) 0.0 else sum / count
    }

    private fun laplacianVariance(bitmap: Bitmap): Double {
        val scaled = scaleForAnalysis(bitmap)
        if (scaled.width < 3 || scaled.height < 3) return 0.0

        val values = ArrayList<Double>(scaled.width * scaled.height)
        for (y in 0 until scaled.height) {
            for (x in 0 until scaled.width) {
                values.add(luminance(scaled.getPixel(x, y)))
            }
        }

        fun index(x: Int, y: Int): Int = y * scaled.width + x

        val laplacians = ArrayList<Double>()
        for (y in 1 until scaled.height - 1) {
            for (x in 1 until scaled.width - 1) {
                val center = values[index(x, y)]
                val lap = 4 * center -
                    values[index(x - 1, y)] -
                    values[index(x + 1, y)] -
                    values[index(x, y - 1)] -
                    values[index(x, y + 1)]
                laplacians.add(lap)
            }
        }

        if (laplacians.isEmpty()) return 0.0
        val mean = laplacians.sum() / laplacians.size
        return laplacians.sumOf { value ->
            val diff = value - mean
            diff * diff
        } / laplacians.size
    }

    private fun scaleForAnalysis(bitmap: Bitmap): Bitmap {
        val targetWidth = min(bitmap.width, 256)
        val targetHeight = min(bitmap.height, 256)
        return if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }
    }

    private fun luminance(color: Int): Double {
        val r = (color shr 16 and 0xFF)
        val g = (color shr 8 and 0xFF)
        val b = (color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
