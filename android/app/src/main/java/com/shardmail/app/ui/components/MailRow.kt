package com.shardmail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shardmail.app.data.local.MessageEntity
import com.shardmail.app.theme.FullShape
import com.shardmail.app.theme.HairlineDividerColor
import com.shardmail.app.theme.RecipientPillShape
import com.shardmail.app.theme.getAvatarColorPair

@Composable
fun MailRow(
    message: MessageEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onOtpCopied: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUnread = message.read == 0
    val avatarColors = getAvatarColorPair(message.fromAddr, message.toAddr)

    val senderInitial = (message.fromAddr.firstOrNull { it.isLetter() } ?: 'M').uppercaseChar().toString()
    val cleanSender = formatSender(message.fromAddr)
    val shortRecipient = formatRecipient(message.toAddr)
    val cleanTime = formatTime(message.date, message.createdAt)

    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedPressScale(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Checkbox in selection mode OR Circular Avatar
            if (isSelectionMode) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            } else {
                // 40dp Circular Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(FullShape)
                        .background(avatarColors.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = senderInitial,
                        style = MaterialTheme.typography.titleMedium,
                        color = avatarColors.foreground
                    )
                }
            }

            // Content column, 2dp gaps
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Row 1: Sender + Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = cleanSender,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = cleanTime,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Row 2: Subject
                Text(
                    text = message.subject.ifBlank { "(No Subject)" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Row 3: Preview (STRICTLY 1 line, ellipsis — never 2–3 lines)
                val previewText = message.text.replace("\n", " ").trim()
                if (previewText.isNotBlank()) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Row 4: Short recipient pill + OTP chip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Recipient pill: 11sp, 2x8dp padding, 6dp radius, surfaceContainer fill
                    Box(
                        modifier = Modifier
                            .clip(RecipientPillShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "to $shortRecipient",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // OTP Chip
                    val effectiveOtp = message.code?.takeIf { it.isNotBlank() }
                        ?: com.shardmail.app.util.OtpExtractor.extractVerificationCode(message.subject, message.text)
                    if (!effectiveOtp.isNullOrBlank()) {
                        OtpChip(
                            code = effectiveOtp,
                            onCopied = onOtpCopied
                        )
                    }
                }
            }
        }

        // Hairline bottom divider
        HorizontalDivider(
            color = HairlineDividerColor,
            thickness = 0.5.dp
        )
    }
}

private fun formatSender(fromAddr: String): String {
    val clean = fromAddr.trim()
    val match = Regex("""^(.*?)\s*<.*?>$""").find(clean)
    if (match != null) {
        val name = match.groupValues[1].trim().trim('"', '\'')
        if (name.isNotBlank()) return name
    }
    return clean.substringBefore('@')
}

private fun formatRecipient(toAddr: String): String {
    val clean = toAddr.trim()
    val email = if (clean.contains("<") && clean.contains(">")) {
        clean.substringAfter("<").substringBefore(">")
    } else {
        clean
    }
    return email.substringBefore('@')
}

private fun formatTime(dateHeader: String, createdAt: String): String {
    if (dateHeader.isNotBlank() && dateHeader.length > 5) {
        val timeRegex = Regex("""(\d{1,2}:\d{2})""")
        val match = timeRegex.find(dateHeader)
        if (match != null) {
            return match.groupValues[1]
        }
    }
    if (createdAt.length >= 16) {
        return createdAt.substring(11, 16)
    }
    return "Now"
}
