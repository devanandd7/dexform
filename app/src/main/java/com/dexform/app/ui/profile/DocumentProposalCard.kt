package com.dexform.app.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexform.app.core.ai.DocumentProposal
import com.dexform.app.core.ai.ProposedBioField
import com.dexform.app.data.db.Tier

@Composable
fun DocumentProposalCard(
    proposal: DocumentProposal,
    isDark: Boolean,
    isSaved: Boolean,
    isSaving: Boolean,
    onToggleField: (fieldId: String, isSelected: Boolean) -> Unit,
    onToggleTier: (fieldId: String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDark) Color(0xFF1E1E24) else Color(0xFFF1F5F9)
    val cardBorder = if (isDark) Color(0xFF2E3240) else Color(0xFFCBD5E1)
    val primaryAccent = Color(0xFF2563EB)
    val bioAccent = Color(0xFF059669)
    val subAccent = Color(0xFFD97706)
    val titleColor = if (isDark) Color.White else Color(0xFF0F172A)
    val textColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val secondaryColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val bioFields = proposal.fields.filter { it.tier == Tier.BIO }
    val subFields = proposal.fields.filter { it.tier == Tier.SUB }
    val selectedCount = proposal.fields.count { it.isSelected }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(primaryAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = proposal.docTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = titleColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = primaryAccent.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "AI Extracted",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Review extracted bio fields and save to your profile",
                        fontSize = 11.sp,
                        color = secondaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = cardBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            // Primary Bio Section
            if (bioFields.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = bioAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Primary Bio (Permanent / Profile)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = bioAccent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = bioAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${bioFields.size} fields",
                            fontSize = 9.sp,
                            color = bioAccent,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                bioFields.forEach { field ->
                    FieldProposalRow(
                        field = field,
                        isDark = isDark,
                        textColor = textColor,
                        secondaryColor = secondaryColor,
                        accentColor = bioAccent,
                        onToggleSelected = { onToggleField(field.id, it) },
                        onToggleTier = { onToggleTier(field.id) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Sub-Bio Section
            if (subFields.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = subAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sub-Bio (Document Instance)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = subAccent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = subAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${subFields.size} fields",
                            fontSize = 9.sp,
                            color = subAccent,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                subFields.forEach { field ->
                    FieldProposalRow(
                        field = field,
                        isDark = isDark,
                        textColor = textColor,
                        secondaryColor = secondaryColor,
                        accentColor = subAccent,
                        onToggleSelected = { onToggleField(field.id, it) },
                        onToggleTier = { onToggleTier(field.id) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Save Action Button
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onSave,
                enabled = !isSaved && !isSaving && selectedCount > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaved) Color(0xFF16A34A) else primaryAccent,
                    disabledContainerColor = if (isSaved) Color(0xFF16A34A).copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.3f)
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving to Bio...", fontSize = 13.sp, color = Color.White)
                } else if (isSaved) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Saved to Profile & Bio ✓",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.Outlined.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Save $selectedCount Field${if (selectedCount > 1) "s" else ""} to Bio & Profile",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldProposalRow(
    field: ProposedBioField,
    isDark: Boolean,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    onToggleSelected: (Boolean) -> Unit,
    onToggleTier: () -> Unit
) {
    val rowBg = if (field.isSelected) {
        if (isDark) Color(0xFF262A36) else Color(0xFFE8EEF5)
    } else {
        Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = rowBg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = field.isSelected,
                onCheckedChange = onToggleSelected,
                colors = CheckboxDefaults.colors(checkedColor = accentColor),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = field.label,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (field.isCoreProfileField) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Profile Field",
                                fontSize = 8.sp,
                                color = Color(0xFF0284C7),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = accentColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (field.tier == Tier.BIO) "New Bio Field" else "Sub Field",
                                fontSize = 8.sp,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (field.isDuplicate) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF64748B).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Already Set",
                                fontSize = 8.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = field.value,
                    fontSize = 13.sp,
                    color = textColor,
                    fontWeight = FontWeight.Normal
                )
            }

            // Quick toggle button between Bio <-> Sub
            IconButton(
                onClick = onToggleTier,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = "Switch Tier",
                    tint = secondaryColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
