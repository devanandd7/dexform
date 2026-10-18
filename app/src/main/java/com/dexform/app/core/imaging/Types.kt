package com.dexform.app.core.imaging

class TargetNotReachable(message: String) : Exception(message)

enum class FitMode {
    CROP_FILL,
    FIT_WHITE_PADDING,
    STRETCH
}

data class Encoded(
    val bytes: ByteArray,
    val quality: Int,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Encoded
        return bytes.contentEquals(other.bytes) && quality == other.quality && width == other.width && height == other.height
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + quality
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

enum class Fmt {
    JPG,
    PNG,
    PDF,
    OTHER
}

data class FileInfo(
    val sizeBytes: Long,
    val format: Fmt,
    val width: Int?,
    val height: Int?
)

data class Requirement(
    val maxBytes: Long? = null,
    val minBytes: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val formats: Set<Fmt> = emptySet(),
    val confidence: Float = 0f,
    val source: String = ""
)

class JpegPage(
    val jpeg: ByteArray,
    val w: Int,
    val h: Int
)
