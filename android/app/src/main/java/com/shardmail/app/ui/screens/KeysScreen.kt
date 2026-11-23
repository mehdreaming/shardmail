package com.shardmail.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shardmail.app.data.local.SecurePreferences
import com.shardmail.app.data.remote.HealthResponse
import com.shardmail.app.data.repository.MailRepository
import com.shardmail.app.theme.FullShape
import com.shardmail.app.ui.components.pressScale
import kotlinx.coroutines.launch

@Composable
fun KeysScreen(
    securePreferences: SecurePreferences,
    repository: MailRepository,
    onShowSnackbar: (String) -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var domain by remember { mutableStateOf(securePreferences.customDomain) }
    var workerUrl by remember { mutableStateOf(securePreferences.workerUrl) }
    var adminPassword by remember { mutableStateOf(securePreferences.adminPassword) }
    var resendApiKey by remember { mutableStateOf(securePreferences.resendApiKey) }
    var cloudflareApiToken by remember { mutableStateOf(securePreferences.cloudflareApiToken) }

    var showAdminPassword by remember { mutableStateOf(false) }
    var showResendKey by remember { mutableStateOf(false) }
    var showCfToken by remember { mutableStateOf(false) }

    var isSavingKeys by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<HealthResponse?>(null) }
    var healthError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
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
                    text = "API & Keys",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Keys and tokens are encrypted and stored in Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section: Connection Settings (ONLY the key fields)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Your Domain
            InputFieldBlock(
                label = "YOUR DOMAIN",
                placeholder = "mail.example.com",
                value = domain,
                onValueChange = { domain = it }
            )

            // Worker URL
            InputFieldBlock(
                label = "WORKER API URL",
                placeholder = "https://<worker-name>.<subdomain>.workers.dev",
                value = workerUrl,
                onValueChange = { workerUrl = it }
            )

            // Admin Password (masked by default, eye-off = hidden)
            InputFieldBlock(
                label = "ADMIN PASSWORD",
                placeholder = "Enter your ADMIN_TOKEN secret",
                value = adminPassword,
                onValueChange = { adminPassword = it },
                isPassword = true,
                showPassword = showAdminPassword,
                onTogglePassword = { showAdminPassword = !showAdminPassword }
            )

            // Resend API Key (Optional)
            InputFieldBlock(
                label = "RESEND API KEY (OPTIONAL)",
                placeholder = "re_...",
                value = resendApiKey,
                onValueChange = { resendApiKey = it },
                isPassword = true,
                showPassword = showResendKey,
                onTogglePassword = { showResendKey = !showResendKey }
            )

            // Cloudflare API Token (Optional)
            InputFieldBlock(
                label = "CLOUDFLARE API TOKEN (OPTIONAL)",
                placeholder = "CF Email Routing Token",
                value = cloudflareApiToken,
                onValueChange = { cloudflareApiToken = it },
                isPassword = true,
                showPassword = showCfToken,
                onTogglePassword = { showCfToken = !showCfToken }
            )
        }

        // Action Buttons: Save & Test Connection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Save Button: Saves locally FIRST immediately, then syncs
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(FullShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pressScale {
                        val urlError = validateWorkerUrlInput(workerUrl)
                        if (urlError != null) {
                            onShowSnackbar(urlError)
                            return@pressScale
                        }

                        // Save locally FIRST
                        repository.saveKeysLocally(
                            domain = domain,
                            workerUrl = workerUrl,
                            adminPassword = adminPassword,
                            resendApiKey = resendApiKey,
                            cloudflareApiToken = cloudflareApiToken
                        )

                        isSavingKeys = true
                        scope.launch {
                            val pushRes = repository.pushSettingsToWorker()
                            isSavingKeys = false
                            if (pushRes.isSuccess) {
                                onShowSnackbar("Settings saved & API client updated")
                            } else {
                                val err = pushRes.exceptionOrNull()?.message ?: "Worker sync pending"
                                onShowSnackbar("Keys saved locally. Warning: $err")
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSavingKeys) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Save Keys",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Test Connection Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(FullShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .pressScale {
                        val urlError = validateWorkerUrlInput(workerUrl)
                        if (urlError != null) {
                            healthError = urlError
                            onShowSnackbar(urlError)
                            return@pressScale
                        }

                        // Save current values locally first
                        repository.saveKeysLocally(
                            domain = domain,
                            workerUrl = workerUrl,
                            adminPassword = adminPassword,
                            resendApiKey = resendApiKey,
                            cloudflareApiToken = cloudflareApiToken
                        )

                        isTestingConnection = true
                        healthError = null
                        scope.launch {
                            val res = repository.testConnection()
                            isTestingConnection = false
                            if (res.isSuccess) {
                                healthResult = res.getOrNull()
                                val msgCount = healthResult?.messages ?: 0
                                onShowSnackbar("Connected — $msgCount messages")
                            } else {
                                healthError = res.exceptionOrNull()?.message ?: "Connection failed"
                                onShowSnackbar(healthError ?: "Connection failed")
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Test Connection",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Health Status Readout Box
        if (healthResult != null || healthError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (healthResult != null) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.errorContainer
                    )
                    .padding(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (healthResult != null) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                        contentDescription = null,
                        tint = if (healthResult != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (healthResult != null) "Connected — ${healthResult!!.messages} messages" else "Connection Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (healthResult != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (healthResult != null) {
                            Text(
                                text = "D1 Messages: ${healthResult!!.messages}  •  Aliases: ${healthResult!!.aliases}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (healthError != null) {
                            Text(
                                text = healthError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputFieldBlock(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .focusRequester(focusRequester),
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword && !showPassword) KeyboardType.Password else KeyboardType.Text
            ),
            visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            focusRequester.requestFocus()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }

                    if (isPassword && onTogglePassword != null) {
                        IconButton(
                            onClick = onTogglePassword,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (!showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (!showPassword) "Password is hidden (click to show)" else "Password is visible (click to hide)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}
