package com.dexform.app.core.requirement

import com.dexform.app.core.imaging.Fmt
import com.dexform.app.core.imaging.KB
import com.dexform.app.core.imaging.MB
import com.dexform.app.core.imaging.Requirement

object RequirementParser {
    private const val NUM = """(\d+(?:\.\d+)?)"""
    private const val UNIT = """(kb|mb)"""
    private val range = Regex("""(?:between\s+)?$NUM\s*$UNIT?\s*(?:-|to|and|से)\s*$NUM\s*$UNIT""")
    private val maxBefore = Regex("""(?:max(?:imum)?|up\s?to|not\s+more\s+than|not\s+exceed(?:ing)?|below|less\s+than|under|within|at\s+most|<=|≤|अधिकतम)[^\d]{0,25}$NUM\s*$UNIT""")
    private val maxAfter = Regex("""$NUM\s*$UNIT\s*(?:से\s*कम|तक|or\s+less|or\s+below)""")
    private val minBefore = Regex("""(?:min(?:imum)?|at\s+least|not\s+less\s+than|>=|≥|न्यूनतम)[^\d]{0,25}$NUM\s*$UNIT""")
    private val bare = Regex("""$NUM\s*$UNIT""")
    private val dims = Regex("""(\d{2,4})\s*x\s*(\d{2,4})(?:\s*(px|pixels?|cm|mm|inch(?:es)?|in)\b)?""")
    private val widthHeight = Regex("""width\D{0,10}(\d{2,4})\D{1,15}height\D{0,10}(\d{2,4})""")

    private fun normalize(s: String) = s.lowercase()
        .replace('×', 'x').replace('*', 'x').replace(Regex("""\bby\b"""), "x")
        .replace('–', '-').replace('—', '-')
        .replace(Regex("""\bk\.\s?b\.?"""), "kb").replace(Regex("""\bm\.\s?b\.?"""), "mb")
        .replace("केबी", "kb").replace("एमबी", "mb")
        .replace(Regex("""\s+"""), " ").trim()

    private fun bytes(n: String, u: String): Long =
        (n.toDouble() * (if (u == "mb") MB else KB)).toLong()

    fun formatsFrom(t: String): Set<Fmt> = buildSet {
        if (Regex("""jpe?g""").containsMatchIn(t)) add(Fmt.JPG)
        if (t.contains("png")) add(Fmt.PNG)
        if (t.contains("pdf")) add(Fmt.PDF)
        if (t.contains("image/*")) {
            add(Fmt.JPG)
            add(Fmt.PNG)
        }
    }

    private class SizeRes(val min: Long?, val max: Long?, val conf: Float)

    private fun parseSize(t: String): SizeRes? {
        range.find(t)?.let { m ->
            val (a, u1, b, u2) = m.destructured
            val unit2 = u2
            val unit1 = u1.ifBlank { unit2 }
            return SizeRes(bytes(a, unit1), bytes(b, unit2), 0.9f)
        }
        var min: Long? = null
        var max: Long? = null
        maxBefore.find(t)?.let { max = bytes(it.groupValues[1], it.groupValues[2]) }
        if (max == null) maxAfter.find(t)?.let { max = bytes(it.groupValues[1], it.groupValues[2]) }
        minBefore.find(t)?.let { min = bytes(it.groupValues[1], it.groupValues[2]) }
        if (min != null || max != null) return SizeRes(min, max, 0.9f)
        bare.find(t)?.let { return SizeRes(null, bytes(it.groupValues[1], it.groupValues[2]), 0.5f) }
        return null
    }

    private fun parseDims(t: String): Pair<Int, Int>? {
        dims.find(t)?.let { m ->
            val unit = m.groupValues[3]
            if (unit == "cm" || unit == "mm" || unit.startsWith("inch") || unit == "in") return null // physical size
            val w = m.groupValues[1].toInt()
            val h = m.groupValues[2].toInt()
            if (w in 30..5000 && h in 30..5000) return w to h
        }
        widthHeight.find(t)?.let { m ->
            val w = m.groupValues[1].toInt()
            val h = m.groupValues[2].toInt()
            if (w in 30..5000 && h in 30..5000) return w to h
        }
        return null
    }

    /** hints are ordered nearest/most specific first. First hint with a size wins; numbers are never merged across hints. */
    fun parse(accept: String?, hints: List<String>): Requirement {
        val formats = mutableSetOf<Fmt>()
        val acc = accept?.lowercase().orEmpty()
        if (acc.isNotBlank()) formats += formatsFrom(acc)

        var size: SizeRes? = null
        var px: Pair<Int, Int>? = null
        var source = ""
        for (h in hints) {
            val t = normalize(h)
            if (size == null) parseSize(t)?.let {
                size = it
                source = h
                formats += formatsFrom(t)
            }
            if (px == null) parseDims(t)?.let { px = it }
            if (size != null && px != null) break
        }
        if (size == null && formats.isEmpty()) {
            hints.firstOrNull { formatsFrom(normalize(it)).isNotEmpty() }?.let {
                formats += formatsFrom(normalize(it))
                source = it
            }
        }
        val conf = when {
            size != null -> size!!.conf
            formats.isNotEmpty() && acc.isNotBlank() -> 0.6f
            formats.isNotEmpty() -> 0.5f
            else -> 0f
        }
        return Requirement(size?.max, size?.min, px?.first, px?.second, formats, conf, source)
    }
}
