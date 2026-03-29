package com.shardclaw.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shardclaw.app.ui.theme.RethinkSans
import com.shardclaw.app.ui.theme.shardclawColors

@Composable
fun ProviderSectionLabel(title: String) {
    Text(
        text = title,
        fontFamily = RethinkSans,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = shardclawColors.TextSecondary,
        letterSpacing = 1.sp,
    )
}

@Composable
fun ProviderConfigField(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    info: String? = null,
    isRequired: Boolean = false,
) {
    var showInfo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (isRequired) Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$label, required"
                } else Modifier,
            ) {
                Text(
                    text = label,
                    fontFamily = RethinkSans,
                    fontSize = 12.sp,
                    color = shardclawColors.TextDim,
                )
                if (isRequired) {
                    Text(
                        text = " *",
                        fontSize = 12.sp,
                        color = shardclawColors.Error,
                    )
                }
                if (info != null) {
                    IconButton(
                        onClick = { showInfo = true },
                        modifier = Modifier.size(20.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "More info about $label",
                            tint = shardclawColors.TextDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            if (onClick != null) {
                Text(
                    text = "Edit",
                    fontFamily = RethinkSans,
                    fontSize = 12.sp,
                    color = shardclawColors.TextInteractive,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = RethinkSans,
            fontSize = 14.sp,
            color = shardclawColors.TextPrimary,
        )
    }
    if (showDivider) {
        HorizontalDivider(
            color = shardclawColors.TextDim.copy(alpha = 0.1f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    if (showInfo && info != null) {
        ProviderInfoDialog(title = label, message = info, onDismiss = { showInfo = false })
    }
}

@Composable
fun ProviderInfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(shardclawColors.CornerRadius)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontFamily = RethinkSans,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = shardclawColors.TextPrimary,
            )
        },
        text = {
            Text(
                text = message,
                fontFamily = RethinkSans,
                fontSize = 13.sp,
                color = shardclawColors.TextSecondary,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Got it",
                    fontFamily = RethinkSans,
                    fontWeight = FontWeight.Bold,
                    color = shardclawColors.Primary,
                )
            }
        },
        containerColor = shardclawColors.Surface,
        shape = shape,
    )
}

@Composable
fun ProviderEditDialog(
    editField: String?,
    editLabel: String,
    editValue: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(shardclawColors.CornerRadius)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Edit $editLabel",
                fontFamily = RethinkSans,
                fontWeight = FontWeight.Bold,
                color = shardclawColors.TextPrimary,
            )
        },
        text = {
            Column {
                if (editField == "anthropicApiKey" || editField == "setupToken" || editField == "telegramBotToken") {
                    Text(
                        "Changing this requires an agent restart.",
                        fontFamily = RethinkSans,
                        fontSize = 12.sp,
                        color = shardclawColors.Warning,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = editValue,
                    onValueChange = onValueChange,
                    label = { Text(editLabel, fontFamily = RethinkSans, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = editField != "anthropicApiKey" && editField != "setupToken",
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = shardclawColors.TextPrimary,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = shardclawColors.Primary,
                        unfocusedBorderColor = shardclawColors.TextDim.copy(alpha = 0.3f),
                        cursorColor = shardclawColors.Primary,
                        focusedTextColor = shardclawColors.TextPrimary,
                        unfocusedTextColor = shardclawColors.TextPrimary
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
            ) {
                Text(
                    "Save",
                    fontFamily = RethinkSans,
                    fontWeight = FontWeight.Bold,
                    color = shardclawColors.ActionPrimary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    fontFamily = RethinkSans,
                    color = shardclawColors.TextDim,
                )
            }
        },
        containerColor = shardclawColors.Surface,
        shape = shape,
    )
}
