package com.shardmail.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shardmail.app.data.local.MessageEntity
import com.shardmail.app.data.repository.MailRepository
import com.shardmail.app.theme.FullShape
import com.shardmail.app.ui.components.AccountEmptyMailState
import com.shardmail.app.ui.components.ErrorEmptyState
import com.shardmail.app.ui.components.MailRow
import com.shardmail.app.ui.components.SearchEmptyState
import com.shardmail.app.ui.components.SearchPill
import com.shardmail.app.ui.components.SectionHeader
import com.shardmail.app.ui.components.ShardFilterChip
import com.shardmail.app.ui.components.ShimmerSkeletonRows
import com.shardmail.app.ui.components.UnifiedInboxEmptyState
import kotlinx.coroutines.launch

enum class MailFilter {
    ALL,
    UNREAD,
    HAS_OTP
}

@Composable
fun InboxScreen(
    repository: MailRepository,
    selectedAccountFilter: String?,
    onClearAccountFilter: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onShowSnackbar: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onInjectTestMail: (String) -> Unit,
    onMenuClick: () -> Unit = {},
    onCheckKeys: () -> Unit = {},
    customDomain: String = "",
    lastErrorMessage: String? = null,
    onSelectionModeChange: (Boolean) -> Unit = {},
    onBackFromAccountInbox: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(MailFilter.ALL) }
    var hasInitiallyLoaded by remember { mutableStateOf(false) }

    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedMessageIds.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var lastMessageClickTime by remember { mutableStateOf(0L) }

    LaunchedEffect(isSelectionMode) {
        onSelectionModeChange(isSelectionMode)
    }

    // System Back button exits selection mode without deleting
    BackHandler(enabled = isSelectionMode) {
        selectedMessageIds = emptySet()
    }

    // System Back button exits per-account inbox back to Accounts
    BackHandler(enabled = !isSelectionMode && !selectedAccountFilter.isNullOrBlank()) {
        onBackFromAccountInbox()
    }

    val messagesFlow = remember(searchQuery, selectedAccountFilter) {
        repository.getMessages(search = searchQuery, toAddress = selectedAccountFilter ?: "")
    }
    val allMessages by messagesFlow.collectAsState(initial = emptyList())

    if (allMessages.isNotEmpty() || !isRefreshing) {
        hasInitiallyLoaded = true
    }

    val filteredMessages = remember(allMessages, activeFilter) {
        when (activeFilter) {
            MailFilter.ALL -> allMessages
            MailFilter.UNREAD -> allMessages.filter { it.read == 0 }
            MailFilter.HAS_OTP -> allMessages.filter { msg ->
                val code = msg.code?.takeIf { it.isNotBlank() }
                    ?: com.shardmail.app.util.OtpExtractor.extractVerificationCode(msg.subject, msg.text)
                !code.isNullOrBlank()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 8.dp)
    ) {
        if (!selectedAccountFilter.isNullOrBlank() && !isSelectionMode) {
            // Proper Top App Bar with back arrow for per-account inbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBackFromAccountInbox,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedAccountFilter,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Account inbox",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sticky Search Pill OR Selection Top Bar (50dp height)
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (isSelectionMode) {
                // Selection Mode Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(FullShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { selectedMessageIds = emptySet() }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Cancel selection",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "${selectedMessageIds.size} selected",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val visibleIds = filteredMessages.map { it.id }.toSet()
                        val allSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedMessageIds }
                        TextButton(
                            onClick = {
                                selectedMessageIds = if (allSelected) emptySet() else visibleIds
                            }
                        ) {
                            Text(
                                text = if (allSelected) "Deselect" else "Select all",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            enabled = selectedMessageIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete selected emails",
                                tint = if (selectedMessageIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            } else {
                SearchPill(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = if (!selectedAccountFilter.isNullOrBlank()) "Search in ${selectedAccountFilter.substringBefore('@')}..." else "Search in mail",
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onMenuClick = onMenuClick,
                    leadingIcon = if (!selectedAccountFilter.isNullOrBlank()) Icons.Outlined.Search else Icons.Outlined.Menu,
                    leadingIconContentDescription = if (!selectedAccountFilter.isNullOrBlank()) "Search" else "Open navigation menu",
                    leadingIconClickable = selectedAccountFilter.isNullOrBlank()
                )
            }
        }

        // Section Header: "ALL INBOXES" or active alias like "sable-403@"
        val headerTitle = if (!selectedAccountFilter.isNullOrBlank()) {
            val prefix = selectedAccountFilter.substringBefore('@')
            "$prefix@"
        } else {
            "ALL INBOXES"
        }

        SectionHeader(
            title = headerTitle,
            onClearTitle = if (!selectedAccountFilter.isNullOrBlank()) onClearAccountFilter else null,
            chipsContent = {
                ShardFilterChip(
                    selected = activeFilter == MailFilter.ALL,
                    onClick = { activeFilter = MailFilter.ALL },
                    label = "All"
                )
                ShardFilterChip(
                    selected = activeFilter == MailFilter.UNREAD,
                    onClick = { activeFilter = MailFilter.UNREAD },
                    label = "Unread"
                )
                ShardFilterChip(
                    selected = activeFilter == MailFilter.HAS_OTP,
                    onClick = { activeFilter = MailFilter.HAS_OTP },
                    label = "Has OTP"
                )
            }
        )

        // Content Area: Shimmer on initial load, Empty states, Error state, or Mail rows
        if (!hasInitiallyLoaded && isRefreshing) {
            ShimmerSkeletonRows(rowsCount = 3)
        } else if (filteredMessages.isEmpty()) {
            if (lastErrorMessage != null && allMessages.isEmpty()) {
                ErrorEmptyState(
                    errorMessage = lastErrorMessage,
                    onRetry = onRefresh,
                    onCheckKeys = onCheckKeys
                )
            } else if (searchQuery.isNotBlank() || activeFilter != MailFilter.ALL) {
                SearchEmptyState(
                    onClearFilters = {
                        searchQuery = ""
                        activeFilter = MailFilter.ALL
                    }
                )
            } else if (!selectedAccountFilter.isNullOrBlank()) {
                AccountEmptyMailState(
                    accountAddress = selectedAccountFilter,
                    onRefresh = onRefresh,
                    onViewAllMail = onClearAccountFilter,
                    onCopied = { onShowSnackbar("Copied address: $it") }
                )
            } else {
                val testRecipient = if (customDomain.isNotBlank()) "test@$customDomain" else "test@example.com"
                UnifiedInboxEmptyState(
                    onRefresh = onRefresh,
                    onSendTest = { onInjectTestMail(testRecipient) }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp) // 120dp bottom clearance for nav and FAB
            ) {
                items(filteredMessages, key = { it.id }) { message ->
                    MailRow(
                        message = message,
                        isSelectionMode = isSelectionMode,
                        isSelected = message.id in selectedMessageIds,
                        onClick = {
                            if (isSelectionMode) {
                                selectedMessageIds = if (message.id in selectedMessageIds) {
                                    selectedMessageIds - message.id
                                } else {
                                    selectedMessageIds + message.id
                                }
                            } else {
                                val now = System.currentTimeMillis()
                                if (now - lastMessageClickTime >= 350L) {
                                    lastMessageClickTime = now
                                    onOpenMessage(message.id)
                                }
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedMessageIds = setOf(message.id)
                            }
                        },
                        onOtpCopied = { code ->
                            onShowSnackbar("Copied verification code: $code")
                        }
                    )
                }
            }
        }
    }

    // Confirmation dialog for bulk delete
    if (showDeleteConfirmDialog) {
        val count = selectedMessageIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete emails?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Delete $count selected email${if (count == 1) "" else "s"}? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToDelete = selectedMessageIds
                        showDeleteConfirmDialog = false
                        selectedMessageIds = emptySet()
                        scope.launch {
                            repository.deleteMessages(idsToDelete)
                            onShowSnackbar("$count email${if (count == 1) "" else "s"} deleted")
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}
