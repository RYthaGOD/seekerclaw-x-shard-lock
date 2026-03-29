package com.shardclaw.app.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shardclaw.app.ui.theme.RethinkSans
import com.shardclaw.app.ui.theme.shardclawColors

@Composable
fun SkillDetailScreen(
    skill: SkillInfo,
    onBack: () -> Unit,
    onExport: (() -> Unit)? = null,
) {
    val shape = remember { RoundedCornerShape(shardclawColors.CornerRadius) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(shardclawColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "← Skills",
                fontFamily = RethinkSans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = shardclawColors.Primary,
                modifier = Modifier.clickable(onClickLabel = "Back to skills list", onClick = onBack),
            )
            if (onExport != null) {
                Text(
                    text = "Export",
                    fontFamily = RethinkSans,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = shardclawColors.Accent,
                    modifier = Modifier
                        .clickable(onClickLabel = "Export skill", onClick = onExport)
                        .padding(4.dp),
                )
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = shardclawColors.CardBorder,
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: avatar + name + version
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkillAvatar(skill = skill, size = 56, emojiFontSize = 32)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = skill.name,
                        fontFamily = RethinkSans,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = shardclawColors.TextPrimary,
                    )
                    if (skill.version.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "v${skill.version.removePrefix("v").removePrefix("V")}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = shardclawColors.TextDim,
                        )
                    }
                }
            }

            // Type
            InfoSection(label = "TYPE", shape = shape) {
                Text(
                    text = if (skill.isDefault) "Default (bundled)" else "Added by user",
                    fontFamily = RethinkSans,
                    fontSize = 14.sp,
                    color = shardclawColors.TextPrimary,
                )
            }

            // Description
            if (skill.description.isNotEmpty()) {
                InfoSection(label = "DESCRIPTION", shape = shape) {
                    Text(
                        text = skill.description,
                        fontFamily = RethinkSans,
                        fontSize = 14.sp,
                        color = shardclawColors.TextPrimary,
                        lineHeight = 22.sp,
                    )
                }
            }

            // Triggers
            InfoSection(label = "TRIGGERS", shape = shape) {
                if (skill.triggers.isEmpty()) {
                    Text(
                        text = "Semantic — AI picks this skill based on description",
                        fontFamily = RethinkSans,
                        fontSize = 13.sp,
                        color = shardclawColors.TextDim,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        skill.triggers.forEach { trigger ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(shardclawColors.Accent),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = trigger,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = shardclawColors.TextPrimary,
                                )
                            }
                        }
                    }
                }
            }

            // Diagnostics
            if (skill.warnings.isNotEmpty()) {
                InfoSection(label = "DIAGNOSTICS", shape = shape) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        skill.warnings.forEach { warning ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "⚠",
                                    fontSize = 13.sp,
                                    color = shardclawColors.Warning,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = warning,
                                    fontFamily = RethinkSans,
                                    fontSize = 13.sp,
                                    color = shardclawColors.Warning,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                }
            }

            // File path
            InfoSection(label = "FILE", shape = shape) {
                Text(
                    text = skill.filePath,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = shardclawColors.TextDim,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun InfoSection(
    label: String,
    shape: RoundedCornerShape,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(shardclawColors.Surface, shape)
            .padding(16.dp),
    ) {
        Text(
            text = label,
            fontFamily = RethinkSans,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = shardclawColors.TextDim,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}
