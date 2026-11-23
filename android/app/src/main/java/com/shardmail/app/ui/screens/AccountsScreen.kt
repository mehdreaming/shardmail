package com.shardmail.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shardmail.app.data.local.AliasEntity
import com.shardmail.app.data.repository.MailRepository
import com.shardmail.app.theme.FullShape
import com.shardmail.app.ui.components.AccountCard
import com.shardmail.app.ui.components.AccountsEmptyState
import com.shardmail.app.ui.components.SearchPill
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    repository: MailRepository,
    onOpenAccountInbox: (String) -> Unit,
    onOpenCreateSheet: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    val aliasesFlow = remember { repository.getAllAliases() }
    val allAliases by aliasesFlow.collectAsState(initial = emptyList())

    val allMessages by repository.getMessages().collectAsState(initial = emptyList())

    val filteredAliases = remember(allAliases, searchQuery) {
        if (searchQuery.isBlank()) {
            allAliases
        } else {
            val q = searchQuery.trim().lowercase()
            allAliases.filter { it.prefix.lowercase().contains(q) || it.address.lowercase().contains(q) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 8.dp)
    ) {
        // Sticky Search Pill (placeholder "Search accounts")
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SearchPill(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search accounts",
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onMenuClick = onMenuClick
            )
        }

        // Header: "ACTIVE ACCOUNTS" + count pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ACTIVE ACCOUNTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .clip(FullShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${allAliases.size}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        if (filteredAliases.isEmpty()) {
            AccountsEmptyState(onCreateAccount = onOpenCreateSheet)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp, top = 4.dp)
            ) {
                items(filteredAliases, key = { it.id }) { alias ->
                    val count = allMessages.count { msg ->
                        msg.toAddr.equals(alias.address, ignoreCase = true) ||
                        msg.toAddr.startsWith("${alias.prefix}@", ignoreCase = true)
                    }

                    AccountCard(
                        alias = alias,
                        mailCount = count,
                        onOpenInbox = { onOpenAccountInbox(alias.address) },
                        onDelete = {
                            scope.launch {
                                repository.deleteAlias(alias.id)
                                onShowSnackbar("Deleted alias ${alias.prefix}")
                            }
                        },
                        onCopied = { onShowSnackbar("Copied address: $it") }
                    )
                }
            }
        }
    }
}
