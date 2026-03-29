package com.shardclaw.app.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shardclaw.app.service.OpenClawService
import com.shardclaw.app.ui.theme.RethinkSans
import com.shardclaw.app.ui.theme.shardclawColors

@Composable
fun RestartDialog(
    context: Context,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Config Updated",
                fontFamily = RethinkSans,
                fontWeight = FontWeight.Bold,
                color = shardclawColors.TextPrimary,
            )
        },
        text = {
            Text(
                "Restart the agent to apply changes?",
                fontFamily = RethinkSans,
                fontSize = 14.sp,
                color = shardclawColors.TextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                OpenClawService.restart(context)
                onDismiss()
                Toast.makeText(context, "Agent restarting\u2026", Toast.LENGTH_SHORT).show()
            }) {
                Text(
                    "Restart Now",
                    fontFamily = RethinkSans,
                    fontWeight = FontWeight.Bold,
                    color = shardclawColors.Primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Later",
                    fontFamily = RethinkSans,
                    color = shardclawColors.TextDim,
                )
            }
        },
        containerColor = shardclawColors.Surface,
        shape = RoundedCornerShape(shardclawColors.CornerRadius),
    )
}
