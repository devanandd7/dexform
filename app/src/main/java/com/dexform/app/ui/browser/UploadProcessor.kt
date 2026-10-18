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
import com.dexform.app.core.imaging.needsConversion
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
        val infos = uris.map { it to readInfo(ctx, it) }
        val target = pickTarget(infos.map { it.second.format }, req)
        val outDir = File(ctx.cacheDir, "processed").apply { mkdirs() }

        // Compute per-file budget if multiple files are selected
        val fileCount = infos.size
        val effectiveMaxBytes = if (req.maxBytes != null && fileCount > 1) {
            (req.maxBytes / fileCount)
        } else {
            req.maxBytes
        }
        val effectiveReq = req.copy(maxBytes = effectiveMaxBytes)

        // Case: form wants ONE PDF, user picked several images -> merge
        if (target == Fmt.PDF && infos.size > 1 && infos.all { it.second.format.isImage() } && (mergeAll || !multiple)) {
            val bytes = imagesToPdf(ctx, infos.map { it.first }, effectiveReq.maxBytes)
            return@withContext listOf(
                write(
                    ctx, outDir, safeName("document", bytes.size.toLong(), "pdf"), bytes, Fmt.PDF, null, null,
                    infos.sumOf { it.second.sizeBytes }
                )
            )
        }

        infos.map { (uri, info) ->
            val needsConv = needsConversion(info, effectiveReq) || target != info.format
            if (!needsConv) {
                return@map passThrough(ctx, uri, info, outDir)
            }

            val bytes: ByteArray
            val ext: String
            val prefix: String
            var w: Int? = null
            var h: Int? = null

            when {
                info.format.isImage() && target == Fmt.PDF -> {
                    bytes = imagesToPdf(ctx, listOf(uri), effectiveReq.maxBytes)
                    ext = "pdf"
                    prefix = "document"
                }
                info.format == Fmt.PDF && target == Fmt.PDF -> {
                    bytes = if (effectiveReq.maxBytes != null && (info.sizeBytes <= 0 || info.sizeBytes > effectiveReq.maxBytes)) {
                        shrinkPdf(ctx, uri, effectiveReq.maxBytes)
                    } else {
                        ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    }
                    ext = "pdf"
                    prefix = "document"
                }
                else -> { // image -> image, or PDF -> image
                    var bmp = if (info.format == Fmt.PDF) {
                        pdfFirstPageToBitmap(ctx, uri)
                    } else {
                        BitmapLoader.load(ctx, uri, 2000)
                    }
                    val lock = req.widthPx != null && req.heightPx != null
                    if (lock) {
                        bmp = resizeExact(bmp, req.widthPx!!, req.heightPx!!, FitMode.CROP_FILL)
                    }
                    bytes = when (target) {
                        Fmt.PNG -> pngWithLimit(bmp, effectiveReq.maxBytes, lock)
                        else -> jpegForRequirement(bmp, effectiveReq, lock)
                    }
                    ext = if (target == Fmt.PNG) "png" else "jpg"
                    prefix = "photo"
                    w = bmp.width
                    h = bmp.height
                }
            }
            write(
                ctx, outDir, safeName(prefix, bytes.size.toLong(), ext), bytes,
                if (ext == "pdf") Fmt.PDF else target, w, h, info.sizeBytes
            )
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

    private fun passThrough(ctx: Context, uri: Uri, info: FileInfo, outDir: File): Processed {
        val name = displayName(ctx, uri)
        val unsafe = Regex("[^A-Za-z0-9_.-]").containsMatchIn(name)
        if (!unsafe) {
            return Processed(uri, name, info.sizeBytes, info.format, info.width, info.height, info.sizeBytes, false)
        }
        val ext = name.substringAfterLast('.', "").lowercase().ifBlank { "jpg" }
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        return write(ctx, outDir, safeName("file", bytes.size.toLong(), ext), bytes, info.format, info.width, info.height, info.sizeBytes)
            .copy(converted = false)
    }

    private fun write(
        ctx: Context, dir: File, name: String, bytes: ByteArray, fmt: Fmt, w: Int?, h: Int?, originalSize: Long
    ): Processed {
        val f = File(dir, name).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        return Processed(uri, name, bytes.size.toLong(), fmt, w, h, originalSize, true)
    }
}
