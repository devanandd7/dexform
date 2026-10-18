package com.dexform.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dexform.app.core.imaging.Fmt
import com.dexform.app.core.imaging.Requirement

@Entity(tableName = "site_rules")
data class SiteRuleEntity(
    @PrimaryKey val key: String,
    val maxBytes: Long? = null,
    val minBytes: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val formatsCsv: String = "",
    val lastUsedMs: Long = System.currentTimeMillis()
) {
    fun toRequirement(): Requirement {
        val fmts = formatsCsv.split(",")
            .mapNotNull { name ->
                try {
                    Fmt.valueOf(name.trim())
                } catch (_: Exception) {
                    null
                }
            }.toSet()
        return Requirement(
            maxBytes = maxBytes,
            minBytes = minBytes,
            widthPx = widthPx,
            heightPx = heightPx,
            formats = fmts,
            confidence = 0.9f,
            source = "cached_rule"
        )
    }

    companion object {
        fun fromRequirement(key: String, req: Requirement): SiteRuleEntity {
            return SiteRuleEntity(
                key = key,
                maxBytes = req.maxBytes,
                minBytes = req.minBytes,
                widthPx = req.widthPx,
                heightPx = req.heightPx,
                formatsCsv = req.formats.joinToString(",") { it.name },
                lastUsedMs = System.currentTimeMillis()
            )
        }
    }
}
