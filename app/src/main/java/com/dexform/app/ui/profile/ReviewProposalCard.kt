package com.dexform.app.ui.profile

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Conf { HIGH, MEDIUM, LOW }

data class ProposedChange(
    val fieldName: String,
    val oldValue: String?,
    var newValue: String,
    val docType: String,
    val sourceUri: Uri,
    val conf: Conf,
    val isConflict: Boolean,
    var accepted: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewProposalCard(
    docType: String,
    proposals: List<ProposedChange>,
    onSaveAccepted: (List<ProposedChange>) -> Unit
) {
    val items = remember(proposals) { proposals.toMutableList() }
    var isSaved by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Review Proposed Changes ($docType)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (proposals.isEmpty()) {
                Text("No new or conflicting fields found for this document.", style = MaterialTheme.typography.bodyMedium)
            } else {
                for (prop in items) {
                    var checked by remember { mutableStateOf(prop.accepted) }
                    var editedVal by remember { mutableStateOf(prop.newValue) }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    checked = it
                                    prop.accepted = it
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(prop.fieldName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (prop.isConflict) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                "Conflict",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (prop.oldValue != null && prop.isConflict) {
                                    Text("Current: ${prop.oldValue}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }

                                OutlinedTextField(
                                    value = editedVal,
                                    onValueChange = {
                                        editedVal = it
                                        prop.newValue = it
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isSaved = true
                        onSaveAccepted(items.filter { it.accepted })
                    },
                    enabled = !isSaved,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaved) "Saved to Profile" else "Save Accepted Changes to Profile")
                }
            }
        }
    }
}
