package com.shardmail.app

import com.shardmail.app.ui.components.BottomNavTab
import com.shardmail.app.ui.components.DrawerDestination
import com.shardmail.app.ui.navigation.Screen
import com.shardmail.app.ui.screens.validateWorkerUrlInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ShardMailUnitTest {

    private fun mapError(throwable: Throwable?): String {
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

    @Test
    fun testErrorMappingError1101() {
        val mapped = mapError(Exception("error code: 1101"))
        assertEquals("Worker returned Error 1101 — this worker has no HTTP API, the backend is not deployed", mapped)
    }

    @Test
    fun testErrorMappingWorkerUrlNotSet() {
        assertEquals("Worker URL not set", mapError(Exception("Worker URL not set")))
        assertEquals("Worker URL not set", mapError(Exception("Worker URL is not configured. Go to Keys tab.")))
    }

    @Test
    fun testErrorMappingUnauthorized() {
        assertEquals("Wrong admin password (401)", mapError(Exception("HTTP 401 Unauthorized")))
        assertEquals("Wrong admin password (401)", mapError(Exception("Wrong admin password (401)")))
        assertEquals("Wrong admin password (401)", mapError(Exception("Invalid admin password")))
    }

    @Test
    fun testErrorMappingUnreachableWorker() {
        assertEquals("Could not connect — check the URL", mapError(UnknownHostException("Unable to resolve host")))
        assertEquals("Could not connect — check the URL", mapError(ConnectException("Failed to connect to host")))
        assertEquals("Could not connect — connection timed out", mapError(SocketTimeoutException("Read timed out")))
        assertEquals("Worker returned HTTP 502 (Bad Gateway)", mapError(Exception("HTTP 502 Bad Gateway")))
        assertEquals("Worker returned HTTP 503 (Service Unavailable)", mapError(Exception("HTTP 503 Service Unavailable")))
    }

    @Test
    fun testWorkerUrlValidation() {
        assertNotNull(validateWorkerUrlInput(""))
        assertEquals("Worker URL cannot be empty", validateWorkerUrlInput(""))
        assertEquals("Worker URL has leading or trailing spaces — please trim", validateWorkerUrlInput("  https://example.com"))
        assertEquals("Worker URL contains placeholders (<subdomain> or ...)", validateWorkerUrlInput("https://shardmail-worker.<subdomain>.workers.dev"))
        assertEquals("Worker URL contains placeholders (<subdomain> or ...)", validateWorkerUrlInput("https://shardmail-worker...workers.dev"))
        assertEquals("Worker URL must start with https://", validateWorkerUrlInput("http://example.com"))
        assertEquals("Worker URL must start with https://", validateWorkerUrlInput("ftp://example.com"))
        assertNull(validateWorkerUrlInput("https://shardmail-worker.subdomain.workers.dev"))
    }

    @Test
    fun testScreenRoutes() {
        assertEquals("greeting", Screen.Greeting.route)
        assertEquals("setup", Screen.Setup.route)
        assertEquals("loading", Screen.Loading.route)
        assertEquals("inbox", Screen.Inbox.route)
        assertEquals("accounts", Screen.Accounts.route)
        assertEquals("keys", Screen.Keys.route)
        assertEquals("setup_test", Screen.SetupTest.route)
        assertEquals("about", Screen.About.route)
        assertEquals("message_detail/msg-123", Screen.MessageDetail.createRoute("msg-123"))
    }

    @Test
    fun testAboutRouteAndDrawerDestination() {
        assertEquals("about", Screen.About.route)
        val destinations = DrawerDestination.entries.map { it.screen }
        assertTrue(destinations.contains(Screen.About))
        assertEquals("About", DrawerDestination.ABOUT.label)
    }

    @Test
    fun testBottomNavSelectionState() {
        fun resolveTab(route: String?): BottomNavTab? = when (route) {
            Screen.Inbox.route -> BottomNavTab.INBOX
            Screen.Accounts.route -> BottomNavTab.ACCOUNTS
            Screen.Keys.route, Screen.SetupTest.route -> BottomNavTab.KEYS
            else -> null
        }

        assertEquals(BottomNavTab.INBOX, resolveTab(Screen.Inbox.route))
        assertEquals(BottomNavTab.ACCOUNTS, resolveTab(Screen.Accounts.route))
        assertEquals(BottomNavTab.KEYS, resolveTab(Screen.Keys.route))
        assertEquals(BottomNavTab.KEYS, resolveTab(Screen.SetupTest.route))
        assertNull(resolveTab(Screen.About.route))
        assertNull(resolveTab(Screen.Greeting.route))
        assertNull(resolveTab(Screen.Loading.route))
        assertNull(resolveTab("unknown_route"))
    }
}
