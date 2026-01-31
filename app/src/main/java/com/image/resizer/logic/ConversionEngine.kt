package com.image.resizer.logic

import android.content.Context
import android.content.ContentValues
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import kotlin.math.max
import kotlin.math.sqrt

data class ConversionOutput(
    val fileName: String,
    val location: String,
    val sizeBytes: Long,
    val uri: Uri? = null,
    val isBestEffort: Boolean = false,
    val actualFormat: String = ""
)

private data class BitmapEntry(
    val bitmap: Bitmap,
    val originalName: String,
    val pageNo: Int,
    val totalPages: Int,
    val sourceFormat: String
)

class UnsupportedFormatException(val format: String, val reason: String) :
    Exception("Format $format not supported: $reason")

object ConversionEngine {

    fun isFormatSupported(format: String): Boolean {
        return when (format.lowercase()) {
            "jpg", "jpeg", "pdf" -> true
            else -> false
        }
    }

    suspend fun compressAndSave(
        context: Context,
        uris: List<Uri>,
        targetSizeBytes: Long,
        targetFormat: String?,
        isHighMode: Boolean = true,
        newWidth: Int? = null,
        newHeight: Int? = null,
        customName: String? = null,
        isMergeMode: Boolean = true,
        isTotalSize: Boolean = true,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<ConversionOutput> = withContext(Dispatchers.IO) {

        val finalOutputs = mutableListOf<ConversionOutput>()
        val bitmapEntries = mutableListOf<BitmapEntry>()
        val targetFmt = targetFormat?.lowercase() ?: "jpg"

        if (!isFormatSupported(targetFmt)) {
            throw UnsupportedFormatException(targetFmt, "Only JPG and PDF formats supported.")
        }

        val useRGB565 = (targetFmt == "jpg" || targetFmt == "jpeg") && isHighMode

        // STEP 1: LOAD INPUTS
        uris.forEachIndexed { index, uri ->
            val mimeTypeInput = context.contentResolver.getType(uri) ?: ""
            val inputFileName = FileUtil.getFileName(context, uri).substringBeforeLast(".")
            onProgress((index.toFloat() / uris.size) * 0.10f, "Loading ${index + 1}...")

            if (mimeTypeInput == "application/pdf") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    val renderer = PdfRenderer(fd)
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val b = createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            b.eraseColor(Color.WHITE)
                            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmapEntries.add(BitmapEntry(b, inputFileName, i + 1, renderer.pageCount, "pdf"))
                        }
                    }
                    renderer.close()
                }
            } else {
                decodeNativeSampled(context, uri, 4096, 4096, useRGB565)?.let {
                    bitmapEntries.add(BitmapEntry(it, inputFileName, 1, 1, "img"))
                }
            }
        }

        if (bitmapEntries.isEmpty()) return@withContext emptyList()

        // STEP 2: PROCESS & SAVE
        if (targetFmt == "pdf") {

            val pdfDoc = PdfDocument()

            val totalBudget = if (targetSizeBytes > 0L) targetSizeBytes else 0L
            val effectiveBudget = (totalBudget - 10240).coerceAtLeast(2048)

            val perPageBudget =
                if (totalBudget == 0L) 0L
                else if (isTotalSize) (effectiveBudget / bitmapEntries.size)
                else effectiveBudget

            var bestEffortGlobal = false

            // ============================================================
            // ✅ UPDATED FIXED FUNCTION (JPEG Pre-Degrade for PDF)
            // ============================================================
            fun processBitmapForPdf(inputBmp: Bitmap, budget: Long): Bitmap {

                if (budget <= 0) return inputBmp

                // =====================================================
                // ✅ PDF Inflate Compensation (More Aggressive)
                // =====================================================
                // PdfDocument usually inflates bitmap ~2x
                // So we compress JPEG to ~40% of page budget
                val jpegBudget = (budget * 0.40).toLong()

                // =====================================================
                // Step 1: Hard JPEG Degrade
                // =====================================================
                val jpegResult = aggressiveCompressToTarget(
                    inputBmp,
                    Bitmap.CompressFormat.JPEG,
                    jpegBudget
                )

                if (jpegResult.second) bestEffortGlobal = true

                var degradedBmp = BitmapFactory.decodeByteArray(
                    jpegResult.first,
                    0,
                    jpegResult.first.size
                )

                // =====================================================
                // Step 2: Extra Pixel Downscale Safety Net
                // =====================================================
                // If bitmap is still too large resolution-wise,
                // reduce pixels aggressively.
                val maxPixelsAllowed = (budget / 2.2).toLong()
                val currentPixels = degradedBmp.width.toLong() * degradedBmp.height.toLong()

                if (currentPixels > maxPixelsAllowed) {

                    val scale = sqrt(maxPixelsAllowed.toFloat() / currentPixels)

                    val newW = (degradedBmp.width * scale).toInt().coerceAtLeast(1)
                    val newH = (degradedBmp.height * scale).toInt().coerceAtLeast(1)

                    val scaledDown = degradedBmp.scale(newW, newH)

                    degradedBmp.recycle()
                    degradedBmp = scaledDown

                    bestEffortGlobal = true
                }

                return degradedBmp
            }

            // ============================================================
            // MERGE MODE
            // ============================================================
            if (isMergeMode) {

                bitmapEntries.forEachIndexed { i, entry ->

                    val processedBmp = processBitmapForPdf(entry.bitmap, perPageBudget)

                    val page = pdfDoc.startPage(
                        PdfDocument.PageInfo.Builder(
                            processedBmp.width,
                            processedBmp.height,
                            i + 1
                        ).create()
                    )

                    page.canvas.drawBitmap(processedBmp, 0f, 0f, null)
                    pdfDoc.finishPage(page)

                    processedBmp.recycle()
                    entry.bitmap.recycle()

                    onProgress(
                        0.2f + (i.toFloat() / bitmapEntries.size) * 0.7f,
                        "Merging PDF ${i + 1}..."
                    )
                }

                val out = ByteArrayOutputStream()
                pdfDoc.writeTo(out)
                pdfDoc.close()

                saveByteArray(context, out.toByteArray(), customName ?: "merged", "pdf")?.let {
                    finalOutputs.add(it.copy(isBestEffort = bestEffortGlobal, actualFormat = "pdf"))
                }

            } else {

                // ============================================================
                // SEPARATE PDF MODE
                // ============================================================
                bitmapEntries.forEachIndexed { i, entry ->

                    val singlePdf = PdfDocument()

                    val processedBmp = processBitmapForPdf(entry.bitmap, perPageBudget)

                    val page = singlePdf.startPage(
                        PdfDocument.PageInfo.Builder(
                            processedBmp.width,
                            processedBmp.height,
                            1
                        ).create()
                    )

                    page.canvas.drawBitmap(processedBmp, 0f, 0f, null)
                    singlePdf.finishPage(page)

                    val out = ByteArrayOutputStream()
                    singlePdf.writeTo(out)
                    singlePdf.close()

                    val name =
                        if (!customName.isNullOrBlank()) "${customName}_${i + 1}"
                        else "${entry.originalName}_${i + 1}"

                    saveByteArray(context, out.toByteArray(), name, "pdf")?.let {
                        finalOutputs.add(it.copy(isBestEffort = true, actualFormat = "pdf"))
                    }

                    processedBmp.recycle()
                    entry.bitmap.recycle()
                }
            }

        } else {

            // JPG OUTPUT (UNCHANGED)
            val totalBudget = if (targetSizeBytes > 0L) targetSizeBytes else 0L

            bitmapEntries.forEachIndexed { i, entry ->

                val budget =
                    if (totalBudget == 0L) 0L
                    else if (isTotalSize) (totalBudget / bitmapEntries.size)
                    else totalBudget

                val res =
                    if (!isHighMode)
                        smoothCompressToTarget(entry.bitmap, Bitmap.CompressFormat.JPEG, budget)
                    else
                        aggressiveCompressToTarget(entry.bitmap, Bitmap.CompressFormat.JPEG, budget)

                val name =
                    if (!customName.isNullOrBlank()) "${customName}_${i + 1}"
                    else entry.originalName

                saveByteArray(context, res.first, name, "jpg")?.let {
                    finalOutputs.add(it.copy(isBestEffort = res.second, actualFormat = "jpg"))
                }

                entry.bitmap.recycle()
            }
        }

        onProgress(1f, "Complete")
        finalOutputs
    }

    // ============================================================
    // Smooth + Aggressive Compression Algorithms (UNCHANGED)
    // ============================================================

    private fun smoothCompressToTarget(bitmap: Bitmap, format: Bitmap.CompressFormat, targetBytes: Long): Pair<ByteArray, Boolean> {
        var scale = 1.0f
        var bestData: ByteArray? = null

        while (scale > 0.05f) {
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale == 1.0f) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)

            var lowQ = 10
            var highQ = 90
            var found = false
            while (lowQ <= highQ) {
                val midQ = (lowQ + highQ) / 2
                val out = ByteArrayOutputStream()
                if (scaled.compress(format, midQ, out)) {
                    val data = out.toByteArray()
                    if (data.size <= targetBytes) {
                        bestData = data
                        lowQ = midQ + 1
                        found = true
                    } else highQ = midQ - 1
                } else break
            }
            if (found) {
                if (scaled != bitmap) scaled.recycle()
                return bestData!! to (scale < 1.0f)
            }
            if (scaled != bitmap) scaled.recycle()
            scale *= 0.85f
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(format, 1, out)
        return out.toByteArray() to true
    }

    private fun aggressiveCompressToTarget(bitmap: Bitmap, format: Bitmap.CompressFormat, targetBytes: Long): Pair<ByteArray, Boolean> {
        var scale = 1.0f
        var bestData: ByteArray? = null
        var lastSize = Long.MAX_VALUE

        while (scale > 0.01f) {
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale == 1.0f) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)

            var lowQ = 40
            var highQ = 95
            var scaleBest: ByteArray? = null
            while (lowQ <= highQ) {
                val midQ = (lowQ + highQ) / 2
                val out = ByteArrayOutputStream()
                if (scaled.compress(format, midQ, out)) {
                    val data = out.toByteArray()
                    if (data.size <= targetBytes) {
                        scaleBest = data
                        lowQ = midQ + 1
                    } else highQ = midQ - 1
                } else break
            }

            if (scaleBest != null) {
                if (scaled != bitmap) scaled.recycle()
                return scaleBest to (scale < 1.0f)
            }

            val minOut = ByteArrayOutputStream()
            scaled.compress(format, 1, minOut)
            val minD = minOut.toByteArray()
            if (minD.size < lastSize) {
                lastSize = minD.size.toLong()
                bestData = minD
            }

            if (scaled != bitmap) scaled.recycle()
            scale *= 0.6f
        }

        return (bestData ?: run {
            val out = ByteArrayOutputStream()
            bitmap.compress(format, 1, out)
            out.toByteArray()
        }) to true
    }

    // Helpers unchanged
    private fun decodeNativeSampled(context: Context, uri: Uri, reqW: Int, reqH: Int, useRGB565: Boolean): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            var s = 1
            if (options.outHeight > reqH || options.outWidth > reqW) {
                val h = options.outHeight / 2
                val w = options.outWidth / 2
                while (h / s >= reqH && w / s >= reqW) s *= 2
            }
            options.inSampleSize = s
            options.inJustDecodeBounds = false
            options.inPreferredConfig = if (useRGB565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveByteArray(context: Context, data: ByteArray, name: String, ext: String): ConversionOutput? {
        val mime = if (ext.lowercase() == "pdf") "application/pdf" else "image/jpeg"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (ext == "pdf") "Documents/ImagePro" else "Pictures/ImagePro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (ext == "pdf") MediaStore.Files.getContentUri("external") else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, cv) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
        if (Build.VERSION.SDK_INT >= 29) {
            cv.clear()
            cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, cv, null, null)
        }
        return ConversionOutput("$name.$ext", if (ext == "pdf") "Documents/ImagePro" else "Pictures/ImagePro", data.size.toLong(), uri)
    }
}
