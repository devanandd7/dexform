package com.dexform.app.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.dexform.app.core.imaging.BitmapLoader
import com.dexform.app.core.imaging.FileInfo
import com.dexform.app.core.imaging.FitMode
import com.dexform.app.core.imaging.Fmt
import com.dexform.app.core.imaging.KB
import com.dexform.app.core.imaging.Requirement
import com.dexform.app.core.imaging.TargetNotReachable
import com.dexform.app.core.imaging.compressToMax
import com.dexform.app.core.imaging.ensureMin
import com.dexform.app.core.imaging.imagesToPdf
import com.dexform.app.core.imaging.pdfFirstPageToBitmap
import com.dexform.app.core.imaging.readInfo
import com.dexform.app.core.imaging.resizeExact
import com.dexform.app.core.imaging.shrinkPdf
import com.dexform.app.core.imaging.toJpeg
import com.dexform.app.core.imaging.toPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Processed(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val fmt: Fmt,
    val width: Int?,
    val height: Int?,
    val originalSize: Long,
    val converted: Boolean,
    val warnings: List<String> = emptyList()
)

object UploadProcessor {

    private fun Fmt.isImage() = this == Fmt.JPG || this == Fmt.PNG

    /** Which format should the output have? */
    fun pickTarget(inputs: List<Fmt>, req: Requirement): Fmt {
        val allowed = req.formats
        val first = inputs.firstOrNull() ?: Fmt.JPG
        return when {
            allowed.isEmpty() -> if (first == Fmt.OTHER) Fmt.JPG else first
            first in allowed -> first
            Fmt.JPG in allowed && first != Fmt.PDF -> Fmt.JPG
            Fmt.JPG in allowed || Fmt.PNG in allowed -> if (Fmt.JPG in allowed) Fmt.JPG else Fmt.PNG
            Fmt.PDF in allowed -> Fmt.PDF
            else -> first
        }
    }

    private fun safeName(prefix: String, bytes: Long, ext: String) =
        "${prefix}_${(bytes / KB).coerceAtLeast(1)}kb.$ext".replace(Regex("[^A-Za-z0-9_.-]"), "_")

    suspend fun process(
        ctx: Context,
        uris: List<Uri>,
        req: Requirement,
        multiple: Boolean,
        mergeAll: Boolean
    ): List<Processed> = withContext(Dispatchers.Default) {
        // Tool 1: File Checker (read type, format, size, dimensions)
        val rawInfos = uris.map { it to readInfo(ctx, it) }
        val targetFmt = pickTarget(rawInfos.map { it.second.format }, req)
        val outDir = File(ctx.cacheDir, "processed").apply { mkdirs() }

        // Compute effective per-file maxBytes
        val fileCount = rawInfos.size
        val effectiveMaxBytes = if (req.maxBytes != null && fileCount > 1) {
            req.maxBytes / fileCount
        } else {
            req.maxBytes
        }
        val effectiveReq = req.copy(maxBytes = effectiveMaxBytes)

        // Case: Form wants 1 PDF, user selected multiple images -> Merge into 1 PDF
        if (targetFmt == Fmt.PDF && rawInfos.size > 1 && rawInfos.all { it.second.format.isImage() } && (mergeAll || !multiple)) {
            val pdfBytes = imagesToPdf(ctx, rawInfos.map { it.first }, effectiveReq.maxBytes)
            val name = safeName("document", pdfBytes.size.toLong(), "pdf")
            return@withContext listOf(
                write(ctx, outDir, name, pdfBytes, Fmt.PDF, null, null, rawInfos.sumOf { it.second.sizeBytes })
            )
        }

        // Sequential Multi-Tool Pipeline per file
        rawInfos.map { (uri, info) ->
            var currentFmt = info.format
            var currentBytes: ByteArray? = null
            var currentBmp: Bitmap? = null
            val originalSize = info.sizeBytes

            // Tool 2: Format Converter & Type Normalization
            when {
                currentFmt.isImage() && targetFmt == Fmt.PDF -> {
                    currentBytes = imagesToPdf(ctx, listOf(uri), effectiveReq.maxBytes)
                    currentFmt = Fmt.PDF
                }
                currentFmt == Fmt.PDF && targetFmt.isImage() -> {
                    currentBmp = pdfFirstPageToBitmap(ctx, uri)
                    currentFmt = Fmt.JPG
                }
                currentFmt == Fmt.PDF && targetFmt == Fmt.PDF -> {
                    // Keep as PDF
                }
                else -> {
                    currentBmp = BitmapLoader.load(ctx, uri, 2000)
                    currentFmt = targetFmt
                }
            }

            // Tool 3: Pixel Resizer / Cropper (if target is image and dimensions requested)
            if (currentBmp != null) {
                val hasDims = req.widthPx != null || req.heightPx != null
                if (hasDims) {
                    val targetW = req.widthPx ?: (currentBmp.width * (req.heightPx!!.toFloat() / currentBmp.height)).toInt().coerceAtLeast(1)
                    val targetH = req.heightPx ?: (currentBmp.height * (req.widthPx!!.toFloat() / currentBmp.width)).toInt().coerceAtLeast(1)

                    val isSignature = targetW < 200 || targetH < 100
                    val fitMode = if (isSignature) FitMode.FIT_WHITE_PADDING else FitMode.CROP_FILL

                    currentBmp = resizeExact(currentBmp, targetW, targetH, fitMode)
                }
            }

            // Tool 4: Size Targeter (Compress / Increase Size / Shrink PDF)
            val finalBytes: ByteArray
            var finalW: Int? = null
            var finalH: Int? = null

            if (currentFmt == Fmt.PDF) {
                finalBytes = if (currentBytes != null) {
                    currentBytes
                } else if (effectiveReq.maxBytes != null && (info.sizeBytes <= 0 || info.sizeBytes > effectiveReq.maxBytes)) {
                    shrinkPdf(ctx, uri, effectiveReq.maxBytes)
                } else {
                    ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                }
            } else {
                val bmp = currentBmp ?: BitmapLoader.load(ctx, uri, 2000)
                finalW = bmp.width
                finalH = bmp.height

                val lockDims = req.widthPx != null && req.heightPx != null
                finalBytes = when {
                    targetFmt == Fmt.PNG -> pngWithLimit(bmp, effectiveReq.maxBytes, lockDims)
                    effectiveReq.maxBytes != null -> {
                        val enc = compressToMax(bmp, effectiveReq.maxBytes, lockDims)
                        if (effectiveReq.minBytes != null && enc.bytes.size < effectiveReq.minBytes) {
                            ensureMin(bmp, effectiveReq.minBytes, effectiveReq.maxBytes, lockDims)
                        } else {
                            enc.bytes
                        }
                    }
                    effectiveReq.minBytes != null -> ensureMin(bmp, effectiveReq.minBytes, Long.MAX_VALUE, lockDims)
                    else -> if (targetFmt == Fmt.PNG) toPng(bmp) else toJpeg(bmp)
                }
            }

            // Tool 5: Safe File Exposer
            val ext = if (currentFmt == Fmt.PDF) "pdf" else if (targetFmt == Fmt.PNG) "png" else "jpg"
            val prefix = if (currentFmt == Fmt.PDF) "document" else "photo"
            val name = safeName(prefix, finalBytes.size.toLong(), ext)

            write(ctx, outDir, name, finalBytes, currentFmt, finalW, finalH, originalSize)
        }
    }

    private fun jpegForRequirement(bmp: Bitmap, req: Requirement, lock: Boolean): ByteArray = when {
        req.maxBytes != null -> {
            val e = compressToMax(bmp, req.maxBytes, lock)
            if (req.minBytes != null && e.bytes.size < req.minBytes) {
                ensureMin(bmp, req.minBytes, req.maxBytes, lock)
            } else {
                e.bytes
            }
        }
        req.minBytes != null -> ensureMin(bmp, req.minBytes, Long.MAX_VALUE, lock)
        else -> toJpeg(bmp)
    }

    private fun pngWithLimit(src: Bitmap, maxBytes: Long?, lock: Boolean): ByteArray {
        var b = src
        var out = toPng(b)
        while (maxBytes != null && out.size > maxBytes * 0.97) {
            if (lock || maxOf(b.width, b.height) < 150) {
                throw TargetNotReachable("PNG cannot be that small. Try JPG.")
            }
            b = Bitmap.createScaledBitmap(b, (b.width * 0.9).toInt(), (b.height * 0.9).toInt(), true)
            out = toPng(b)
        }
        return out
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        return try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment ?: "file"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    private fun write(
        ctx: Context, dir: File, name: String, bytes: ByteArray, fmt: Fmt, w: Int?, h: Int?, originalSize: Long
    ): Processed {
        val f = File(dir, name).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        return Processed(uri, name, bytes.size.toLong(), fmt, w, h, originalSize, true)
    }
}
