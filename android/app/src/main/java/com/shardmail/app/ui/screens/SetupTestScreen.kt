package com.shardmail.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shardmail.app.data.local.SecurePreferences
import com.shardmail.app.data.remote.HealthResponse
import com.shardmail.app.data.repository.MailRepository
import com.shardmail.app.theme.FullShape
import com.shardmail.app.ui.components.pressScale
import kotlinx.coroutines.launch

@Composable
fun SetupTestScreen(
    securePreferences: SecurePreferences,
    repository: MailRepository,
    onShowSnackbar: (String) -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isCheckingHealth by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<HealthResponse?>(null) }
    var healthError by remember { mutableStateOf<String?>(null) }

    val defaultTestAddress = if (securePreferences.customDomain.isNotBlank()) {
        "test@${securePreferences.customDomain}"
    } else {
        "test@example.com"
    }
    var testEmailAddress by remember { mutableStateOf(defaultTestAddress) }
    var isInjectingTest by remember { mutableStateOf(false) }

    fun checkHealth() {
        if (!securePreferences.isWorkerUrlValid()) {
            healthError = "Worker URL not set"
            return
        }
        isCheckingHealth = true
        healthError = null
        scope.launch {
            val res = repository.testConnection()
            isCheckingHealth = false
            if (res.isSuccess) {
                healthResult = res.getOrNull()
                val msgCount = healthResult?.messages ?: 0
                onShowSnackbar("Connected — $msgCount messages")
            } else {
                healthError = res.exceptionOrNull()?.message ?: "Connection failed"
                onShowSnackbar(healthError ?: "Connection failed")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (securePreferences.isWorkerUrlValid()) {
            checkHealth()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 120.dp), // 120dp bottom clearance for nav bar
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top bar with Hamburger menu button and Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Open navigation menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Setup & Test",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Verify worker connection and test inbound email parsing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { checkHealth() },
                modifier = Modifier.size(40.dp)
            ) {
                if (isCheckingHealth) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh connection status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Section 1: Health Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(
                    if (healthResult != null) MaterialTheme.colorScheme.surfaceContainerHigh
                    else if (healthError != null) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (healthResult != null) Icons.Outlined.CheckCircle
                    else if (healthError != null) Icons.Outlined.Error
                    else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = if (healthResult != null) MaterialTheme.colorScheme.primary
                    else if (healthError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = if (healthResult != null) "Worker Status: Online"
                    else if (healthError != null) "Worker Status: Offline / Error"
                    else "Worker Status: Checking...",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (healthResult != null) MaterialTheme.colorScheme.onSurface
                    else if (healthError != null) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (healthResult != null) {
                Text(
                    text = "Connected — ${healthResult!!.messages} messages in D1 database  •  ${healthResult!!.aliases} active aliases",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!healthResult!!.domain.isNullOrBlank()) {
                    Text(
                        text = "Configured Domain: ${healthResult!!.domain}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (healthError != null) {
                Text(
                    text = healthError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                Text(
                    text = "Worker API URL: ${securePreferences.workerUrl.ifBlank { "Not set (configure in Keys tab)" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 2: Inject Test Email Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Inject Test Email",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Sends a mock email with a 6-digit verification code directly into your D1 database to test real-time parsing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = testEmailAddress,
                    onValueChange = { testEmailAddress = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(FullShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pressScale {
                        isInjectingTest = true
                        scope.launch {
                            val res = repository.testInject(testEmailAddress)
                            isInjectingTest = false
                            if (res.isSuccess) {
                                onShowSnackbar("Test email injected! Check your Inbox.")
                                checkHealth()
                            } else {
                                val err = repository.mapError(res.exceptionOrNull())
                                onShowSnackbar("Test inject failed: $err")
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isInjectingTest) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Inject test mail",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // Section 3: Cloudflare Email Routing Setup Note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Cloudflare Catch-All Verification",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ensure your Cloudflare Email Routing Catch-all rule for your custom domain is Active and sends to Worker 'shardmail-worker'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
