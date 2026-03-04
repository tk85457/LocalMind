package com.localmind.app.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Utility to handle image picking, resizing, and preparation for VLM usage
 */
object ImagePickerManager {

    private const val MAX_IMAGE_DIMENSION = 336 // standard CLIP/LLaVA dimension

    /**
     * Read image from URI and convert to optimized byte array for native injection
     */
    suspend fun prepareImageForInference(
        context: Context,
        uri: Uri
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Failed to open image stream"))

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
                ?: return@withContext Result.failure(Exception("Failed to decode bitmap"))

            // Resize to standard Multimodal dimension for optimal CLIP processing
            val resizedBitmap = resizeBitmap(originalBitmap, MAX_IMAGE_DIMENSION)

            val outputStream = ByteArrayOutputStream()
            // Using JPEG for compatibility with mtmd_helper_bitmap_init_from_buf (which uses stb_image)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)

            val bytes = outputStream.toByteArray()

            // Cleanup
            originalBitmap.recycle()
            if (resizedBitmap != originalBitmap) {
                resizedBitmap.recycle()
            }

            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / aspectRatio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
