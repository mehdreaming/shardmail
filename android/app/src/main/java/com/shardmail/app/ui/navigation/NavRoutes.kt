package com.shardmail.app.ui.navigation

sealed class Screen(val route: String) {
    object Greeting : Screen("greeting")
    object Setup : Screen("setup")
    object Loading : Screen("loading")
    object Inbox : Screen("inbox")
    object Accounts : Screen("accounts")
    object Keys : Screen("keys")
    object SetupTest : Screen("setup_test")
    object About : Screen("about")
    object MessageDetail : Screen("message_detail/{messageId}") {
        fun createRoute(messageId: String) = "message_detail/$messageId"
    }
}
