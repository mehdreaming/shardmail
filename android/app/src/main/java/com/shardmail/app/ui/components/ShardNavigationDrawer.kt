package com.shardmail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shardmail.app.theme.FullShape
import com.shardmail.app.theme.HairlineDividerColor
import com.shardmail.app.ui.navigation.Screen

enum class DrawerDestination(val screen: Screen, val label: String) {
    INBOX(Screen.Inbox, "Inbox"),
    ACCOUNTS(Screen.Accounts, "Accounts"),
    KEYS(Screen.Keys, "Keys"),
    SETUP_TEST(Screen.SetupTest, "Setup & Test"),
    ABOUT(Screen.About, "About")
}

@Composable
fun ShardDrawerContent(
    currentRoute: String?,
    unreadCount: Int,
    accountCount: Int,
    domain: String,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier
            .width(310.dp)
            .fillMaxHeight(),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mail,
                        contentDescription = "ShardMail",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "ShardMail",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (domain.isNotBlank()) domain else "Custom-Domain Inbox",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = HairlineDividerColor, thickness = 0.5.dp)

            // Drawer Items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DrawerNavigationItem(
                    label = DrawerDestination.INBOX.label,
                    icon = Icons.Outlined.Inbox,
                    isSelected = currentRoute == Screen.Inbox.route,
                    badge = if (unreadCount > 0) "$unreadCount" else null,
                    badgeHighlight = true,
                    onClick = { onNavigate(Screen.Inbox) }
                )

                DrawerNavigationItem(
                    label = DrawerDestination.ACCOUNTS.label,
                    icon = Icons.Outlined.AccountCircle,
                    isSelected = currentRoute == Screen.Accounts.route,
                    badge = if (accountCount > 0) "$accountCount" else null,
                    badgeHighlight = false,
                    onClick = { onNavigate(Screen.Accounts) }
                )

                DrawerNavigationItem(
                    label = DrawerDestination.KEYS.label,
                    icon = Icons.Outlined.Key,
                    isSelected = currentRoute == Screen.Keys.route,
                    onClick = { onNavigate(Screen.Keys) }
                )

                DrawerNavigationItem(
                    label = DrawerDestination.SETUP_TEST.label,
                    icon = Icons.Outlined.Build,
                    isSelected = currentRoute == Screen.SetupTest.route,
                    onClick = { onNavigate(Screen.SetupTest) }
                )

                DrawerNavigationItem(
                    label = DrawerDestination.ABOUT.label,
                    icon = Icons.Outlined.Info,
                    isSelected = currentRoute == Screen.About.route,
                    onClick = { onNavigate(Screen.About) }
                )
            }
        }
    }
}

@Composable
private fun DrawerNavigationItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    badge: String? = null,
    badgeHighlight: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(FullShape)
            .background(backgroundColor)
            .pressScale(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )

        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(FullShape)
                    .background(
                        if (badgeHighlight) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (badgeHighlight) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
