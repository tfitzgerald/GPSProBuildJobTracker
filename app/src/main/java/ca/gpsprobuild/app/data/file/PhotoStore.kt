package ca.gpsprobuild.app.data.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredPhoto(
    val fileName: String,
    val thumbFileName: String,
    val contentHash: String,
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long
)

/**
 * Owns everything on disk under `filesDir/photos`.
 *
 * Two decisions worth knowing:
 *
 * Originals are downscaled to 2560px on the long edge at q85. A 500-photo job then
 * sits around 400 MB rather than several gigabytes, which matters because the whole
 * lot has to fit inside a ZIP backup and move between phones as a sync packet.
 *
 * Files are named by UUID and recorded with a SHA-256 of their bytes. The hash is
 * what makes packet transfer deduplicate for free — a photo already on the other
 * device is recognised and skipped rather than sent twice.
 */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root: File get() = File(context.filesDir, "photos").apply { mkdirs() }
    private val thumbsDir: File get() = File(root, "thumbs").apply { mkdirs() }

    fun fileFor(fileName: String): File = File(root, fileName)
    fun thumbFor(thumbFileName: String): File = File(thumbsDir, thumbFileName)

    /** A temp target for the camera intent, exposed through FileProvider. */
    fun newCameraTarget(): Pair<File, Uri> {
        val file = File(root, "capture-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    suspend fun importFromUri(uri: Uri, jobId: Long?): StoredPhoto? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            store(bytes, jobId)
        }.getOrNull()
    }

    suspend fun importFromFile(file: File, jobId: Long?): StoredPhoto? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching null
            val result = store(file.readBytes(), jobId)
            // The camera wrote a full-size original; the stored copy replaces it.
            file.delete()
            result
        }.getOrNull()
    }

    private fun store(bytes: ByteArray, jobId: Long?): StoredPhoto? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE)
            }
        ) ?: return null

        val rotated = applyExifRotation(bytes, decoded)
        val scaled = scaleToMaxEdge(rotated, MAX_EDGE)

        val subDir = File(root, jobId?.toString() ?: "unfiled").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val relativeName = "${subDir.name}/$id.jpg"
        val target = File(root, relativeName).apply { parentFile?.mkdirs() }

        target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }

        val thumbName = "$id.jpg"
        val thumb = scaleToMaxEdge(scaled, THUMB_EDGE)
        File(thumbsDir, thumbName).outputStream().use {
            thumb.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it)
        }

        val result = StoredPhoto(
            fileName = relativeName,
            thumbFileName = thumbName,
            contentHash = sha256(target.readBytes()),
            widthPx = scaled.width,
            heightPx = scaled.height,
            fileSizeBytes = target.length()
        )

        if (thumb != scaled) thumb.recycle()
        if (scaled != rotated) scaled.recycle()
        if (rotated != decoded) rotated.recycle()
        decoded.recycle()

        return result
    }

    fun delete(fileName: String, thumbFileName: String) {
        runCatching { File(root, fileName).delete() }
        runCatching { File(thumbsDir, thumbFileName).delete() }
    }

    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxEdge) return source
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Phone cameras record orientation in EXIF rather than rotating the pixels, so
     * skipping this leaves every portrait site photo lying on its side.
     */
    private fun applyExifRotation(bytes: ByteArray, bitmap: Bitmap): Bitmap = runCatching {
        val orientation = bytes.inputStream().use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return@runCatching bitmap
        }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_EDGE = 2560
        const val THUMB_EDGE = 512
        const val QUALITY = 85
        const val THUMB_QUALITY = 80
    }
}
