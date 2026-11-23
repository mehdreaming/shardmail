package com.shardmail.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shardmail.app.data.local.SecurePreferences
import com.shardmail.app.data.repository.MailRepository
import com.shardmail.app.theme.FullShape
import com.shardmail.app.ui.components.pressScale
import kotlinx.coroutines.launch

fun validateWorkerUrlInput(url: String): String? {
    if (url.isBlank()) {
        return "Worker URL cannot be empty"
    }
    if (url != url.trim()) {
        return "Worker URL has leading or trailing spaces — please trim"
    }
    if (url.contains("<subdomain>") || url.contains("<") || url.contains(">") || url.contains("...")) {
        return "Worker URL contains placeholders (<subdomain> or ...)"
    }
    if (!url.startsWith("https://")) {
        return "Worker URL must start with https://"
    }
    return null
}

@Composable
fun SetupScreen(
    securePreferences: SecurePreferences,
    repository: MailRepository,
    onContinue: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var domain by remember { mutableStateOf(securePreferences.customDomain) }
    var workerUrl by remember { mutableStateOf(securePreferences.workerUrl) }
    var adminPassword by remember { mutableStateOf(securePreferences.adminPassword) }
    var resendApiKey by remember { mutableStateOf(securePreferences.resendApiKey) }
    var cloudflareApiToken by remember { mutableStateOf(securePreferences.cloudflareApiToken) }

    var showAdminPassword by remember { mutableStateOf(false) }
    var showResendKey by remember { mutableStateOf(false) }
    var showCfToken by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var isSavingKeys by remember { mutableStateOf(false) }
    var testSuccessMessage by remember { mutableStateOf<String?>(null) }
    var testErrorMessage by remember { mutableStateOf<String?>(null) }
    var isTestSuccessful by remember { mutableStateOf(false) }

    fun runTestConnection() {
        val urlError = validateWorkerUrlInput(workerUrl)
        if (urlError != null) {
            testErrorMessage = urlError
            testSuccessMessage = null
            isTestSuccessful = false
            onShowSnackbar(urlError)
            return
        }

        // Save inputs locally first immediately
        repository.saveKeysLocally(
            domain = domain,
            workerUrl = workerUrl,
            adminPassword = adminPassword,
            resendApiKey = resendApiKey,
            cloudflareApiToken = cloudflareApiToken
        )

        isTestingConnection = true
        testErrorMessage = null
        testSuccessMessage = null

        scope.launch {
            val result = repository.testConnection()
            isTestingConnection = false
            if (result.isSuccess) {
                val health = result.getOrNull()!!
                testSuccessMessage = "Connected — ${health.messages} messages"
                testErrorMessage = null
                isTestSuccessful = true
                onShowSnackbar("Connected — ${health.messages} messages")
            } else {
                testErrorMessage = result.exceptionOrNull()?.message ?: "Connection failed"
                testSuccessMessage = null
                isTestSuccessful = false
                onShowSnackbar(testErrorMessage ?: "Connection failed")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .padding(bottom = 120.dp), // Ample bottom padding so error card and Continue button are never covered by snackbars
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Screen Title & Subtitle
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Setup API Keys",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Connect your Cloudflare Worker backend to start receiving emails.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Form Fields in exact order:
        // 1. Your domain (required)
        SetupInputField(
            label = "YOUR DOMAIN",
            placeholder = "mail.example.com",
            value = domain,
            onValueChange = {
                domain = it
            }
        )

        // 2. Worker API URL + one-line hint (Horizontally scrollable, shows full URL)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SetupInputField(
                label = "WORKER API URL",
                placeholder = "https://<worker-name>.<subdomain>.workers.dev",
                value = workerUrl,
                onValueChange = {
                    workerUrl = it
                    isTestSuccessful = false
                }
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Deploy the included Worker template (see README) and paste its URL here.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Admin password (masked by default, eye-off = hidden)
        SetupInputField(
            label = "ADMIN PASSWORD",
            placeholder = "Enter your ADMIN_TOKEN secret",
            value = adminPassword,
            onValueChange = {
                adminPassword = it
            },
            isPassword = true,
            showPassword = showAdminPassword,
            onTogglePassword = { showAdminPassword = !showAdminPassword }
        )

        // 4. Resend API key (optional)
        SetupInputField(
            label = "RESEND API KEY (OPTIONAL)",
            placeholder = "re_...",
            value = resendApiKey,
            onValueChange = { resendApiKey = it },
            isPassword = true,
            showPassword = showResendKey,
            onTogglePassword = { showResendKey = !showResendKey }
        )

        // 5. Cloudflare API token (optional)
        SetupInputField(
            label = "CLOUDFLARE API TOKEN (OPTIONAL)",
            placeholder = "CF Email Routing Token",
            value = cloudflareApiToken,
            onValueChange = { cloudflareApiToken = it },
            isPassword = true,
            showPassword = showCfToken,
            onTogglePassword = { showCfToken = !showCfToken }
        )

        // Action Buttons: Save Keys & Test Connection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Save Keys Button: ALWAYS saves locally first, then attempts sync
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(FullShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
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
                                onShowSnackbar("Keys saved securely")
                            } else {
                                val err = pushRes.exceptionOrNull()?.message ?: "Worker not synced"
                                onShowSnackbar("Keys saved locally. Warning: $err")
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSavingKeys) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Save Keys",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Test Connection Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(FullShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .pressScale {
                        runTestConnection()
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
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Test Status Readout Box
        if (testSuccessMessage != null || testErrorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (testSuccessMessage != null) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.errorContainer
                    )
                    .padding(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (testSuccessMessage != null) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                        contentDescription = null,
                        tint = if (testSuccessMessage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = testSuccessMessage ?: testErrorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (testSuccessMessage != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Continue Button: Enabled if Test Connection succeeded, or if keys are configured
        val isContinueEnabled = isTestSuccessful && domain.isNotBlank()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(FullShape)
                .background(
                    if (isContinueEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .then(
                    if (isContinueEnabled) {
                        Modifier.pressScale {
                            // Ensure saved locally
                            repository.saveKeysLocally(
                                domain = domain,
                                workerUrl = workerUrl,
                                adminPassword = adminPassword,
                                resendApiKey = resendApiKey,
                                cloudflareApiToken = cloudflareApiToken
                            )
                            scope.launch {
                                repository.pushSettingsToWorker()
                            }
                            securePreferences.onboardingDone = true
                            onContinue()
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isContinueEnabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SetupInputField(
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
                            // Eye-off = hidden (masked), Eye = visible (unmasked)
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
