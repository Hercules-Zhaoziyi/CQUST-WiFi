package top.msfxp.schedule.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

// 位图读取、采样缩放与旋转校正工具
object ImageBitmapHelper {

    // 从 Uri 解码并纠正方向的位图
    fun decodeAndCorrectOrientation(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val tempFile = File(context.cacheDir, "img_decode_${System.currentTimeMillis()}.tmp")
        return try {
            val copySuccess = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    true
                }
            } ?: false

            if (!copySuccess || !tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return null
            }

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, boundsOptions)

            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDimension || boundsOptions.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions) ?: run {
                tempFile.delete()
                return null
            }

            val matrix = Matrix()
            runCatching {
                val exif = ExifInterface(tempFile.absolutePath)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }
            }

            val finalBitmap = if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                rotated
            } else {
                bitmap
            }
            tempFile.delete()
            finalBitmap
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }
}
