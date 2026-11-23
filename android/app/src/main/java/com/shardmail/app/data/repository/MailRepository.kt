package com.shardmail.app.data.repository

import com.shardmail.app.data.local.AliasDao
import com.shardmail.app.data.local.AliasEntity
import com.shardmail.app.data.local.MessageDao
import com.shardmail.app.data.local.MessageEntity
import com.shardmail.app.data.local.SecurePreferences
import com.shardmail.app.data.remote.CreateAliasRequest
import com.shardmail.app.data.remote.HealthResponse
import com.shardmail.app.data.remote.LoginRequest
import com.shardmail.app.data.remote.NetworkClient
import com.shardmail.app.data.remote.TestInjectRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class InitialLoadResult(
    val success: Boolean,
    val cachedMessageCount: Int,
    val error: String? = null
)

class MailRepository(
    private val messageDao: MessageDao,
    private val aliasDao: AliasDao,
    private val securePreferences: SecurePreferences
) {
    private val networkClient = NetworkClient(securePreferences)

    fun invalidateClient() {
        networkClient.invalidate()
    }

    fun mapError(throwable: Throwable?): String {
        if (throwable == null) return "Unknown error"
        val msg = throwable.message ?: ""
        return when {
            msg.contains("1101") ->
                "Worker returned Error 1101 — this worker has no HTTP API, the backend is not deployed"

            msg.contains("Worker URL not set", ignoreCase = true) ||
            msg.contains("Worker URL is not configured", ignoreCase = true) -> "Worker URL not set"

            msg.contains("401", ignoreCase = true) ||
            msg.contains("Unauthorized", ignoreCase = true) ||
            msg.contains("Invalid admin password", ignoreCase = true) ||
            msg.contains("Wrong admin password", ignoreCase = true) -> "Wrong admin password (401)"

            throwable is UnknownHostException ||
            msg.contains("Unable to resolve host", ignoreCase = true) -> "Could not connect — check the URL"

            throwable is ConnectException ||
            msg.contains("Failed to connect", ignoreCase = true) -> "Could not connect — check the URL"

            throwable is SocketTimeoutException ||
            msg.contains("timeout", ignoreCase = true) -> "Could not connect — connection timed out"

            msg.contains("502", ignoreCase = true) -> "Worker returned HTTP 502 (Bad Gateway)"
            msg.contains("503", ignoreCase = true) -> "Worker returned HTTP 503 (Service Unavailable)"

            else -> msg.ifBlank { "Could not connect — check the URL" }
        }
    }

    fun getMessages(search: String = "", toAddress: String = ""): Flow<List<MessageEntity>> {
        val baseFlow = messageDao.getAllMessages()

        val accountFilteredFlow = if (toAddress.isBlank()) {
            baseFlow
        } else {
            val targetAddr = toAddress.trim().lowercase()
            val targetPrefix = targetAddr.substringBefore('@')
            baseFlow.map { list ->
                list.filter { msg ->
                    val addr = msg.toAddr.trim().lowercase()
                    addr == targetAddr ||
                    addr.startsWith("$targetPrefix@") ||
                    addr.contains("<$targetAddr>") ||
                    addr.contains(targetAddr) ||
                    addr.contains("$targetPrefix@")
                }
            }
        }

        return if (search.isBlank()) {
            accountFilteredFlow
        } else {
            accountFilteredFlow.map { list ->
                val q = search.trim().lowercase()
                list.filter { msg ->
                    val code = msg.code?.takeIf { it.isNotBlank() }
                        ?: com.shardmail.app.util.OtpExtractor.extractVerificationCode(msg.subject, msg.text)
                    msg.subject.lowercase().contains(q) ||
                    msg.fromAddr.lowercase().contains(q) ||
                    msg.toAddr.lowercase().contains(q) ||
                    msg.text.lowercase().contains(q) ||
                    (code != null && code.contains(q))
                }
            }
        }
    }

    fun observeMessageById(id: String): Flow<MessageEntity?> {
        return messageDao.observeMessageById(id)
    }

    fun getUnreadCount(): Flow<Int> {
        return messageDao.getUnreadCount()
    }

    suspend fun getCachedMessageCount(): Int {
        return messageDao.getMessageCount()
    }

    suspend fun getCachedAliasCount(): Int {
        return aliasDao.getAliasCount()
    }

    suspend fun refreshMessages(toAddress: String? = null): Result<Unit> {
        if (!securePreferences.isWorkerUrlValid()) {
            return Result.failure(Exception("Worker URL not set"))
        }

        val api = networkClient.createApi()
            ?: return Result.failure(Exception("Worker URL not set"))

        return try {
            val response = api.getMessages(to = toAddress)
            if (response.isSuccessful && response.body()?.ok == true) {
                val dtos = response.body()?.messages ?: emptyList()
                val entities = dtos.map { dto ->
                    val effectiveCode = dto.code?.takeIf { it.isNotBlank() }
                        ?: com.shardmail.app.util.OtpExtractor.extractVerificationCode(dto.subject, dto.text ?: "")
                    MessageEntity(
                        id = dto.id,
                        fromAddr = dto.from_addr,
                        toAddr = dto.to_addr,
                        subject = dto.subject,
                        text = dto.text ?: "",
                        html = "",
                        code = effectiveCode,
                        date = dto.date ?: "",
                        createdAt = dto.created_at ?: "",
                        read = dto.read
                    )
                }
                messageDao.insertMessages(entities)
                Result.success(Unit)
            } else if (response.code() == 401) {
                Result.failure(Exception("Wrong admin password (401)"))
            } else {
                val errBody = try { response.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
                val errorMsg = if (errBody.contains("1101")) {
                    "Worker returned Error 1101 — this worker has no HTTP API, the backend is not deployed"
                } else {
                    response.body()?.error ?: "HTTP ${response.code()}: ${errBody.take(80).ifBlank { response.message() }}"
                }
                Result.failure(Exception(mapError(Exception(errorMsg))))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e)))
        }
    }

    suspend fun fetchMessageDetail(id: String): Result<MessageEntity> {
        messageDao.markAsRead(id)

        if (!securePreferences.isWorkerUrlValid()) {
            return Result.failure(Exception("Worker URL not set"))
        }

        val api = networkClient.createApi()
            ?: return Result.failure(Exception("Worker URL not set"))

        return try {
            val response = api.getMessageById(id)
            if (response.isSuccessful && response.body()?.ok == true) {
                val dto = response.body()?.message
                    ?: return Result.failure(Exception("Empty message body"))

                val effectiveCode = dto.code?.takeIf { it.isNotBlank() }
                    ?: com.shardmail.app.util.OtpExtractor.extractVerificationCode(dto.subject, dto.text ?: "")
                val entity = MessageEntity(
                    id = dto.id,
                    fromAddr = dto.from_addr,
                    toAddr = dto.to_addr,
                    subject = dto.subject,
                    text = dto.text ?: "",
                    html = dto.html ?: "",
                    code = effectiveCode,
                    date = dto.date ?: "",
                    createdAt = dto.created_at ?: "",
                    read = 1
                )
                messageDao.insertMessage(entity)
                Result.success(entity)
            } else if (response.code() == 401) {
                Result.failure(Exception("Wrong admin password (401)"))
            } else {
                val errorMsg = response.body()?.error ?: "HTTP ${response.code()}: ${response.message()}"
                Result.failure(Exception(mapError(Exception(errorMsg))))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e)))
        }
    }

    suspend fun deleteMessage(id: String): Result<Unit> {
        messageDao.deleteMessage(id)
        val api = networkClient.createApi() ?: return Result.success(Unit)
        return try {
            api.deleteMessage(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun deleteMessages(ids: Set<String>): Result<Unit> {
        for (id in ids) {
            messageDao.deleteMessage(id)
        }
        val api = networkClient.createApi() ?: return Result.success(Unit)
        return try {
            for (id in ids) {
                try {
                    api.deleteMessage(id)
                } catch (_: Exception) {}
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    fun getAllAliases(): Flow<List<AliasEntity>> {
        return aliasDao.getAllAliases()
    }

    suspend fun refreshAliases(): Result<Unit> {
        if (!securePreferences.isWorkerUrlValid()) {
            return Result.failure(Exception("Worker URL not set"))
        }

        val api = networkClient.createApi()
            ?: return Result.failure(Exception("Worker URL not set"))

        return try {
            val response = api.getAliases()
            if (response.isSuccessful && response.body()?.ok == true) {
                val dtos = response.body()?.aliases ?: emptyList()
                val entities = dtos.map { dto ->
                    AliasEntity(
                        id = dto.id,
                        prefix = dto.prefix,
                        address = dto.address,
                        createdAt = dto.created_at
                    )
                }
                aliasDao.insertAliases(entities)
                Result.success(Unit)
            } else if (response.code() == 401) {
                Result.failure(Exception("Wrong admin password (401)"))
            } else {
                val errorMsg = response.body()?.error ?: "HTTP ${response.code()}: ${response.message()}"
                Result.failure(Exception(mapError(Exception(errorMsg))))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e)))
        }
    }

    suspend fun createAlias(prefix: String): Result<AliasEntity> {
        if (!securePreferences.isWorkerUrlValid()) {
            return Result.failure(Exception("Worker URL not set"))
        }

        val api = networkClient.createApi()
            ?: return Result.failure(Exception("Worker URL not set"))

        return try {
            val response = api.createAlias(CreateAliasRequest(prefix))
            if (response.isSuccessful && response.body()?.ok == true) {
                val dto = response.body()?.alias
                    ?: return Result.failure(Exception("No alias returned"))
                val entity = AliasEntity(
                    id = dto.id,
                    prefix = dto.prefix,
                    address = dto.address,
                    createdAt = dto.created_at
                )
                aliasDao.insertAlias(entity)
                Result.success(entity)
            } else if (response.code() == 401) {
                Result.failure(Exception("Wrong admin password (401)"))
            } else {
                val errorMsg = response.body()?.error ?: "Failed to create alias"
                Result.failure(Exception(mapError(Exception(errorMsg))))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e)))
        }
    }

    suspend fun deleteAlias(id: String): Result<Unit> {
        aliasDao.deleteAlias(id)
        val api = networkClient.createApi() ?: return Result.success(Unit)
        return try {
            api.deleteAlias(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun fetchSettings(): Result<Map<String, String>> {
        if (!securePreferences.isWorkerUrlValid()) {
            return Result.failure(Exception("Worker URL not set"))
        }

        val api = networkClient.createApi()
            ?: return Result.failure(Exception("Worker URL not set"))

        return try {
            val response = api.getSettings()
            if (response.isSuccessful && response.body()?.ok == true) {
                val settings = response.body()?.settings ?: emptyMap()
                val remoteDomain = settings["domain"]
                if (!remoteDomain.isNullOrBlank() && securePreferences.customDomain.isBlank()) {
                    securePreferences.customDomain = remoteDomain
                }
                Result.success(settings)
            } else if (response.code() == 401) {
                Result.failure(Exception("Wrong admin password (401)"))
            } else {
                val errorMsg = response.body()?.error ?: "Failed to fetch settings"
                Result.failure(Exception(mapError(Exception(errorMsg))))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e)))
        }
    }

    suspend fun updateSettings(settings: Map<String, String>): Result<Unit> {
        if (!securePreferences.isWorkerUrlValid()) {
            return Result.failure(Exception("Worker URL not set"))
        }

        val api = networkClient.createApi()
            ?: return Result.failure(Exception("Worker URL not set"))

        return try {
            val response = api.updateSettings(settings)
            if (response.isSuccessful && response.body()?.ok == true) {
                Result.success(Unit)
            } else if (response.code() == 401) {
                Result.failure(Exception("Wrong admin password (401)"))
            } else {
                val errBody = try { response.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
                val errorMsg = "HTTP ${response.code()}: ${errBody.take(80).ifBlank { response.message() }}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e)))
        }
    }

    fun saveKeysLocally(
        domain: String,
        workerUrl: String,
        adminPassword: String,
        resendApiKey: String = "",
        cloudflareApiToken: String = ""
    ) {
        securePreferences.customDomain = domain.trim().lowercase()
        securePreferences.workerUrl = workerUrl.trim()
        securePreferences.adminPassword = adminPassword.trim()
        securePreferences.resendApiKey = resendApiKey.trim()
        securePreferences.cloudflareApiToken = cloudflareApiToken.trim()
        invalidateClient()
    }

    suspend fun pushSettingsToWorker(): Result<Unit> {
        if (!securePreferences.isWorkerUrlValid()) {
            return Result.failure(Exception("Worker URL not set"))
        }

        val settingsMap = mutableMapOf<String, String>()
        if (securePreferences.customDomain.isNotBlank()) {
            settingsMap["domain"] = securePreferences.customDomain
        }
        if (securePreferences.resendApiKey.isNotBlank()) {
            settingsMap["resendApiKey"] = securePreferences.resendApiKey
        }

        if (settingsMap.isEmpty()) return Result.success(Unit)
        return updateSettings(settingsMap)
    }

    suspend fun saveKeysAndSettings(
        domain: String,
        workerUrl: String,
        adminPassword: String,
        resendApiKey: String = "",
        cloudflareApiToken: String = ""
    ): Result<Unit> {
        saveKeysLocally(domain, workerUrl, adminPassword, resendApiKey, cloudflareApiToken)
        return pushSettingsToWorker()
    }

    suspend fun testConnection(): Result<HealthResponse> {
        if (!securePreferences.isWorkerUrlValid()) {
            return Result.failure(Exception("Worker URL not set"))
        }

        val api = networkClient.createApi()
            ?: return Result.failure(Exception("Worker URL not set"))

        return try {
            val response = api.getHealth()
            if (response.isSuccessful && response.body()?.ok == true) {
                val health = response.body()!!
                // If admin password is provided, verify it too
                if (securePreferences.adminPassword.isNotBlank()) {
                    try {
                        val loginRes = api.login(LoginRequest(securePreferences.adminPassword))
                        if (loginRes.code() == 401 || loginRes.body()?.ok == false) {
                            return Result.failure(Exception("Wrong admin password (401)"))
                        }
                    } catch (e: Exception) {
                        val mapped = mapError(e)
                        if (mapped.contains("401")) {
                            return Result.failure(Exception("Wrong admin password (401)"))
                        }
                    }
                }
                Result.success(health)
            } else {
                val errBody = try { response.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
                val code = response.code()
                val errorMsg = when {
                    code == 401 -> "Wrong admin password (401)"
                    errBody.contains("1101") || (code == 500 && errBody.contains("error code: 1101")) ->
                        "Worker returned Error 1101 — this worker has no HTTP API, the backend is not deployed"
                    code == 500 -> "Worker returned HTTP 500: ${errBody.take(80).ifBlank { "Internal Server Error" }}"
                    code == 502 -> "Worker returned HTTP 502 (Bad Gateway) — worker crashed or upstream failed"
                    code == 503 -> "Worker returned HTTP 503 (Service Unavailable)"
                    code == 404 -> "Worker returned HTTP 404: /health not found on this worker"
                    else -> "Worker returned HTTP $code: ${errBody.take(80).ifBlank { response.message() }}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e)))
        }
    }

    suspend fun testInject(to: String): Result<Unit> {
        if (!securePreferences.isWorkerUrlValid()) {
            return Result.failure(Exception("Worker URL not set"))
        }

        val api = networkClient.createApi()
            ?: return Result.failure(Exception("Worker URL not set"))

        return try {
            val response = api.testInject(TestInjectRequest(to))
            if (response.isSuccessful && response.body()?.ok == true) {
                refreshMessages()
                Result.success(Unit)
            } else if (response.code() == 401) {
                Result.failure(Exception("Wrong admin password (401)"))
            } else {
                val errorMsg = response.body()?.error ?: "Failed to inject test mail"
                Result.failure(Exception(mapError(Exception(errorMsg))))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e)))
        }
    }

    suspend fun loadEverything(): InitialLoadResult {
        if (!securePreferences.isWorkerUrlValid()) {
            val cached = getCachedMessageCount()
            return InitialLoadResult(
                success = false,
                cachedMessageCount = cached,
                error = "Worker URL not set"
            )
        }

        return coroutineScope {
            val settingsDeferred = async { fetchSettings() }
            val messagesDeferred = async { refreshMessages() }
            val aliasesDeferred = async { refreshAliases() }

            val settingsRes = settingsDeferred.await()
            val messagesRes = messagesDeferred.await()
            val aliasesRes = aliasesDeferred.await()

            val cachedCount = getCachedMessageCount()

            if (messagesRes.isSuccess) {
                InitialLoadResult(
                    success = true,
                    cachedMessageCount = cachedCount
                )
            } else {
                val err = messagesRes.exceptionOrNull()?.message
                    ?: settingsRes.exceptionOrNull()?.message
                    ?: aliasesRes.exceptionOrNull()?.message
                    ?: "Network error"
                InitialLoadResult(
                    success = false,
                    cachedMessageCount = cachedCount,
                    error = mapError(Exception(err))
                )
            }
        }
    }
}
