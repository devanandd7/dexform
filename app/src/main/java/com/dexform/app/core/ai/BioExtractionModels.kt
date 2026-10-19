package com.dexform.app.core.ai

import com.dexform.app.data.db.Tier
import java.util.UUID

data class ProposedBioField(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val label: String,
    var value: String,
    var tier: Tier, // BIO (profile level) or SUB (document/instance level)
    val isCoreProfileField: Boolean = false,
    val coreFieldName: String? = null,
    var isSelected: Boolean = true,
    val isDuplicate: Boolean = false
)

data class DocumentProposal(
    val id: String = UUID.randomUUID().toString(),
    val docType: String,
    val docTitle: String,
    val fields: List<ProposedBioField>,
    val imagePath: String? = null,
    var isSaved: Boolean = false
)
