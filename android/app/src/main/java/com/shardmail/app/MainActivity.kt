package com.shardmail.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shardmail.app.theme.M3Easing
import com.shardmail.app.theme.MotionDetailDuration
import com.shardmail.app.theme.ShardMailTheme
import com.shardmail.app.ui.components.BottomNavTab
import com.shardmail.app.ui.components.CreateAccountBottomSheet
import com.shardmail.app.ui.components.ShardBottomNavBar
import com.shardmail.app.ui.components.ShardDrawerContent
import com.shardmail.app.ui.components.ShardExtendedFab
import com.shardmail.app.ui.navigation.Screen
import com.shardmail.app.ui.screens.AboutScreen
import com.shardmail.app.ui.screens.AccountsScreen
import com.shardmail.app.ui.screens.GreetingScreen
import com.shardmail.app.ui.screens.InboxScreen
import com.shardmail.app.ui.screens.KeysScreen
import com.shardmail.app.ui.screens.LoadingScreen
import com.shardmail.app.ui.screens.MessageDetailScreen
import com.shardmail.app.ui.screens.SetupScreen
import com.shardmail.app.ui.screens.SetupTestScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ShardMailApplication
        val repository = app.repository
        val securePreferences = app.securePreferences

        setContent {
            ShardMailTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    ShardMailMain(
                        repository = repository,
                        securePreferences = securePreferences
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShardMailMain(
    repository: com.shardmail.app.data.repository.MailRepository,
    securePreferences: com.shardmail.app.data.local.SecurePreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCreateSheet by remember { mutableStateOf(false) }
    var isCreatingAccount by remember { mutableStateOf(false) }
    var selectedAccountFilter by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshError by remember { mutableStateOf<String?>(null) }
    var isInboxSelectionMode by remember { mutableStateOf(false) }
    var currentDomain by remember { mutableStateOf(securePreferences.customDomain) }
    var lastTabSwitchTime by remember { mutableStateOf(0L) }

    androidx.compose.runtime.LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            currentDomain = securePreferences.customDomain
        }
    }

    val unreadCount by repository.getUnreadCount().collectAsState(initial = 0)
    val aliasesFlow = remember { repository.getAllAliases() }
    val allAliases by aliasesFlow.collectAsState(initial = emptyList())

    val isMainScreen = currentRoute == Screen.Inbox.route ||
            currentRoute == Screen.Accounts.route ||
            currentRoute == Screen.Keys.route ||
            currentRoute == Screen.SetupTest.route ||
            currentRoute == Screen.About.route

    val currentTab = when (currentRoute) {
        Screen.Inbox.route -> BottomNavTab.INBOX
        Screen.Accounts.route -> BottomNavTab.ACCOUNTS
        Screen.Keys.route, Screen.SetupTest.route -> BottomNavTab.KEYS
        else -> null
    }

    val isMessageDetailOpen = currentRoute?.startsWith("message_detail") == true
    val isFabVisible = !isMessageDetailOpen && !showCreateSheet && !isInboxSelectionMode &&
            (currentRoute == Screen.Inbox.route || currentRoute == Screen.Accounts.route)

    // Back handler closes drawer if open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    fun triggerRefresh() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            val mailResult = repository.refreshMessages(selectedAccountFilter)
            val aliasResult = repository.refreshAliases()
            isRefreshing = false

            if (mailResult.isFailure && aliasResult.isFailure) {
                val cachedCount = repository.getCachedMessageCount()
                if (cachedCount > 0) {
                    lastRefreshError = null
                    snackbarHostState.showSnackbar(
                        message = "Couldn't refresh — showing saved mail",
                        duration = SnackbarDuration.Short
                    )
                } else {
                    val rawErr = mailResult.exceptionOrNull()
                    val err = repository.mapError(rawErr)
                    lastRefreshError = err
                    snackbarHostState.showSnackbar(
                        message = err,
                        duration = SnackbarDuration.Short
                    )
                }
            } else {
                lastRefreshError = null
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isMainScreen,
        drawerContent = {
            ShardDrawerContent(
                currentRoute = currentRoute,
                unreadCount = unreadCount,
                accountCount = allAliases.size,
                domain = currentDomain,
                onNavigate = { screen ->
                    scope.launch {
                        drawerState.close()
                        if (currentRoute == screen.route) {
                            if (screen == Screen.Inbox && selectedAccountFilter != null) {
                                selectedAccountFilter = null
                            }
                            return@launch
                        }
                        if (screen == Screen.Inbox) {
                            selectedAccountFilter = null
                        }
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Inbox.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    if (isMainScreen && !isMessageDetailOpen) {
                        ShardBottomNavBar(
                            selectedTab = currentTab,
                            hasUnread = unreadCount > 0,
                            onTabSelected = { tab ->
                                val targetRoute = when (tab) {
                                    BottomNavTab.INBOX -> Screen.Inbox.route
                                    BottomNavTab.ACCOUNTS -> Screen.Accounts.route
                                    BottomNavTab.KEYS -> Screen.Keys.route
                                }
                                val now = System.currentTimeMillis()
                                if (currentRoute == targetRoute) {
                                    if (tab == BottomNavTab.INBOX && selectedAccountFilter != null) {
                                        selectedAccountFilter = null
                                    }
                                    return@ShardBottomNavBar
                                }
                                if (now - lastTabSwitchTime < 250L) {
                                    return@ShardBottomNavBar
                                }
                                lastTabSwitchTime = now

                                if (tab == BottomNavTab.INBOX) {
                                    selectedAccountFilter = null
                                }

                                navController.navigate(targetRoute) {
                                    popUpTo(Screen.Inbox.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                },
                snackbarHost = {
                    val bottomPadding = if (isMainScreen) 84.dp else 24.dp
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .padding(bottom = bottomPadding, start = 16.dp, end = 16.dp)
                    ) { data ->
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.inverseSurface)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = data.visuals.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            ) { paddingValues ->
                val startDestination = if (!securePreferences.onboardingDone) {
                    Screen.Greeting.route
                } else {
                    Screen.Loading.route
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(paddingValues)
                ) {
                    composable(Screen.Greeting.route) {
                        GreetingScreen(
                            onGetStarted = {
                                navController.navigate(Screen.Setup.route)
                            }
                        )
                    }

                    composable(Screen.Setup.route) {
                        SetupScreen(
                            securePreferences = securePreferences,
                            repository = repository,
                            onContinue = {
                                navController.navigate(Screen.Loading.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onShowSnackbar = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                                }
                            }
                        )
                    }

                    composable(Screen.Loading.route) {
                        LoadingScreen(
                            repository = repository,
                            onSuccess = {
                                navController.navigate(Screen.Inbox.route) {
                                    popUpTo(Screen.Loading.route) { inclusive = true }
                                }
                            },
                            onFallbackCached = {
                                navController.navigate(Screen.Inbox.route) {
                                    popUpTo(Screen.Loading.route) { inclusive = true }
                                }
                                scope.launch {
                                    snackbarHostState.showSnackbar("Couldn't refresh — showing saved mail")
                                }
                            },
                            onCheckKeys = {
                                navController.navigate(Screen.Keys.route)
                            }
                        )
                    }

                    composable(Screen.Inbox.route) {
                        InboxScreen(
                            repository = repository,
                            selectedAccountFilter = selectedAccountFilter,
                            onClearAccountFilter = { selectedAccountFilter = null },
                            onOpenMessage = { id ->
                                navController.navigate(Screen.MessageDetail.createRoute(id))
                            },
                            onShowSnackbar = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                                }
                            },
                            isRefreshing = isRefreshing,
                            onRefresh = { triggerRefresh() },
                            onInjectTestMail = { to ->
                                scope.launch {
                                    val res = repository.testInject(to)
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("Test email injected!")
                                    } else {
                                        val err = repository.mapError(res.exceptionOrNull())
                                        snackbarHostState.showSnackbar("Test inject failed: $err")
                                    }
                                }
                            },
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            },
                            onCheckKeys = {
                                navController.navigate(Screen.Keys.route) {
                                    launchSingleTop = true
                                }
                            },
                            customDomain = securePreferences.customDomain,
                            lastErrorMessage = lastRefreshError,
                            onSelectionModeChange = { isInboxSelectionMode = it },
                            onBackFromAccountInbox = {
                                selectedAccountFilter = null
                                navController.navigate(Screen.Accounts.route) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(Screen.Accounts.route) {
                        AccountsScreen(
                            repository = repository,
                            onOpenAccountInbox = { address ->
                                selectedAccountFilter = address
                                navController.navigate(Screen.Inbox.route) {
                                    popUpTo(Screen.Inbox.route) { inclusive = true }
                                }
                            },
                            onOpenCreateSheet = { showCreateSheet = true },
                            onShowSnackbar = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                                }
                            },
                            isRefreshing = isRefreshing,
                            onRefresh = { triggerRefresh() },
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            }
                        )
                    }

                    composable(Screen.Keys.route) {
                        KeysScreen(
                            securePreferences = securePreferences,
                            repository = repository,
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            },
                            onShowSnackbar = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                                }
                            }
                        )
                    }

                    composable(Screen.SetupTest.route) {
                        SetupTestScreen(
                            securePreferences = securePreferences,
                            repository = repository,
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            },
                            onShowSnackbar = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                                }
                            }
                        )
                    }

                    composable(Screen.About.route) {
                        AboutScreen(
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            },
                            onShowSnackbar = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                                }
                            }
                        )
                    }

                    composable(
                        route = Screen.MessageDetail.route,
                        arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(MotionDetailDuration, easing = M3Easing)
                            )
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(MotionDetailDuration, easing = M3Easing)
                            )
                        }
                    ) { backStackEntry ->
                        val messageId = backStackEntry.arguments?.getString("messageId") ?: ""
                        MessageDetailScreen(
                            messageId = messageId,
                            repository = repository,
                            onBack = { navController.popBackStack() },
                            onShowSnackbar = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                                }
                            }
                        )
                    }
                }
            }

            // Extended FAB ("+ New"): Visible only on Inbox and Accounts
            AnimatedVisibility(
                visible = isFabVisible,
                enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { 50 }, animationSpec = tween(180)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 84.dp)
            ) {
                ShardExtendedFab(
                    onClick = { showCreateSheet = true }
                )
            }

            // Bottom sheet for create account (280ms slide-up)
            if (showCreateSheet) {
                CreateAccountBottomSheet(
                    sheetState = sheetState,
                    domain = if (securePreferences.customDomain.isNotBlank()) securePreferences.customDomain else "example.com",
                    onDismiss = { showCreateSheet = false },
                    isCreating = isCreatingAccount,
                    onCreateAccount = { prefix ->
                        isCreatingAccount = true
                        scope.launch {
                            val result = repository.createAlias(prefix)
                            isCreatingAccount = false
                            if (result.isSuccess) {
                                val alias = result.getOrNull()!!
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ShardMail Address", alias.address))
                                showCreateSheet = false
                                snackbarHostState.showSnackbar("Created & copied ${alias.address}")
                            } else {
                                val err = repository.mapError(result.exceptionOrNull())
                                snackbarHostState.showSnackbar(err)
                            }
                        }
                    }
                )
            }
        }
    }
}
