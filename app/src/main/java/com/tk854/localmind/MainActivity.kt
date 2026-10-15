package com.tk854.localmind

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import androidx.core.view.WindowCompat
import com.tk854.localmind.navigation.NavGraph
import com.tk854.localmind.navigation.Routes
import com.tk854.localmind.ui.theme.LocalMindTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tk854.localmind.core.utils.BiometricHelper

@AndroidEntryPoint
class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    private val navigationChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // UI FIX: Edge-to-edge enable karo  —  Compose WindowInsets se padding handle karega.
        // Ye line honi CHAHIYE, warna status bar ke peeche content chhup jata hai.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        handleNavigationIntent(intent)

        setContent {
            val settingsViewModel: com.tk854.localmind.ui.viewmodel.SettingsViewModel = hiltViewModel()
            val conversationHistoryViewModel: com.tk854.localmind.ui.viewmodel.ConversationHistoryViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState()
            val onboardingCompleted by settingsViewModel.onboardingCompletedStartup.collectAsState()
            val recentConversations by conversationHistoryViewModel.conversations.collectAsState()

            var isAppUnlocked by remember { mutableStateOf(false) }
            val biometricHelper = remember { BiometricHelper(this@MainActivity) }

            // LIFECYCLE LOCK: App background mein jaye toh wapas lock hona chahiye.
            DisposableEffect(settings.biometricLock) {
                val observer = object : DefaultLifecycleObserver {
                    override fun onStop(owner: LifecycleOwner) {
                        // App backgrounded -> Secure it
                        if (settings.biometricLock) {
                            isAppUnlocked = false
                        }
                    }
                }
                val lifecycle = ProcessLifecycleOwner.get().lifecycle
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(settings.biometricLock) {
                if (settings.biometricLock && !isAppUnlocked && biometricHelper.isBiometricAvailable()) {
                    biometricHelper.showBiometricPrompt(
                        onSuccess = { isAppUnlocked = true },
                        onError = { error ->
                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                        }
                    )
                } else if (!settings.biometricLock) {
                    isAppUnlocked = true
                }
            }



            // Observe language change for real-time locale switching
            LaunchedEffect(settings.language) {
                val localeTag = when (settings.language) {
                    "Hindi (HI)" -> "hi"
                    "Spanish (ES)" -> "es"
                    "French (FR)" -> "fr"
                    "German (DE)" -> "de"
                    else -> "en"
                }
                val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(localeTag)
                if (androidx.appcompat.app.AppCompatDelegate.getApplicationLocales() != appLocale) {
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
                }
            }

            LocalMindTheme(
                darkMode = settings.darkMode,
                fontScale = settings.fontScale,
                themeColor = settings.themeColor,
                fontFamily = settings.fontFamily
            ) {
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val screenWidthDp = configuration.screenWidthDp.dp

                val dimens = when {
                    screenWidthDp < 600.dp -> com.tk854.localmind.ui.theme.CompactDimens
                    screenWidthDp < 840.dp -> com.tk854.localmind.ui.theme.MediumDimens
                    else -> com.tk854.localmind.ui.theme.ExpandedDimens
                }

                CompositionLocalProvider(com.tk854.localmind.ui.theme.LocalDimens provides dimens) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                    val navController = rememberNavController()
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    val localContext = androidx.compose.ui.platform.LocalContext.current
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    var navigationInFlight by remember { mutableStateOf(false) }
                    var showExitDialog by remember { mutableStateOf(false) }
                    val shouldConfirmExit = drawerState.isClosed &&
                        !showExitDialog &&
                        onboardingCompleted == true &&
                        (currentRoute?.startsWith(Routes.CHAT) == true) &&
                        navController.previousBackStackEntry == null

                    // Handle external navigation intents
                    LaunchedEffect(Unit) {
                        for (route in navigationChannel) {
                            try {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) {
                                Log.e("LocalMind-Nav", "Failed to handle external navigation to $route", e)
                            }
                        }
                    }

                    fun safeNavigate(route: String) {
                        scope.launch {
                            if (navigationInFlight) return@launch
                            // FIX: Agar already usi route pe hain toh sirf drawer band karo
                            // Routes.CHAT navigate karne se popUpTo chat clear kar deta tha
                            val alreadyOnChat = currentRoute?.startsWith(Routes.CHAT) == true
                            if (route == Routes.CHAT && alreadyOnChat) {
                                drawerState.close()
                                return@launch
                            }
                            navigationInFlight = true
                            try {
                                drawerState.close()
                                val chatDestinationId = navController.graph.findNode(Routes.CHAT_WITH_ID)?.id
                                if (route == Routes.CHAT && chatDestinationId != null) {
                                    navController.navigate(Routes.CHAT) {
                                        popUpTo(chatDestinationId) {
                                            inclusive = true
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                } else {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (t: Throwable) {
                                Log.w("LocalMind-Nav", "Navigation failed for route=$route", t)
                                Toast.makeText(
                                    localContext,
                                    localContext.getString(R.string.navigation_failed_try_again),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                navigationInFlight = false
                            }
                        }
                    }

                    BackHandler(enabled = shouldConfirmExit) {
                        showExitDialog = true
                    }

                    // Active conversation ID  —  route se extract karo
                    val activeConvId = remember(currentRoute) {
                        navBackStackEntry?.arguments?.getString("conversationId")
                    }

                    AdaptiveAppContent(
                        isExpanded = screenWidthDp >= 600.dp,
                        drawerState = drawerState,
                        currentRoute = currentRoute,
                        recentConversations = recentConversations,
                        activeConversationId = activeConvId,
                        onNavigate = ::safeNavigate,
                        isDarkMode = settings.darkMode,
                        onToggleDarkMode = { settingsViewModel.updateDarkMode(!settings.darkMode) },
                        onConversationClick = { conversationId ->
                            scope.launch {
                                if (navigationInFlight) return@launch
                                // FIX: Same conversation already open hai toh sirf drawer band karo
                                // Dobara navigate karne se popUpTo inclusive=true chat clear kar deta tha
                                if (activeConvId == conversationId) {
                                    drawerState.close()
                                    return@launch
                                }
                                navigationInFlight = true
                                try {
                                    drawerState.close()
                                    val chatDestinationId = navController.graph.findNode(Routes.CHAT_WITH_ID)?.id
                                    navController.navigate(Routes.chat(conversationId)) {
                                        if (chatDestinationId != null) {
                                            popUpTo(chatDestinationId) {
                                                inclusive = true
                                                saveState = false
                                            }
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (t: Throwable) {
                                    Log.w("LocalMind-Nav", "Conv navigation failed", t)
                                } finally {
                                    navigationInFlight = false
                                }
                            }
                        },
                        onDeleteConversation = { conversationHistoryViewModel.deleteConversation(it) },
                        onRenameConversation = { id, title -> conversationHistoryViewModel.renameConversation(id, title) },
                        content = {
                            if (!isAppUnlocked && settings.biometricLock) {
                                BiometricLockScreen(onUnlock = {
                                    biometricHelper.showBiometricPrompt(
                                        onSuccess = { isAppUnlocked = true },
                                        onError = { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
                                    )
                                })
                            } else {
                                val startDest = if (onboardingCompleted == true) Routes.CHAT else Routes.ONBOARDING
                                NavGraph(
                                    navController = navController,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    startDestination = startDest
                                )
                            }
                        }
                    )

                    // Permission Flow
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val permissionsToRequest = remember {
                        val list = mutableListOf<String>()
                        // Notification Permission for Android 13+
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            list.add(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        // Storage Permission for older Android versions is not needed due to SAF
                        // Microphone for STT
                        list.add(android.Manifest.permission.RECORD_AUDIO)
                        list
                    }

                    var currentIndex by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }

                    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { _ ->
                        // Proceed to next permission regardless of result
                        currentIndex++
                    }

                    LaunchedEffect(onboardingCompleted, settings.permissionsRequested, currentIndex) {
                        if (onboardingCompleted == true && !settings.permissionsRequested) {
                            if (currentIndex < permissionsToRequest.size) {
                                // Check if already granted to avoid unnecessary dialogs
                                val permission = permissionsToRequest[currentIndex]
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        permission
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    currentIndex++
                                } else {
                                    launcher.launch(permission)
                                }
                            } else {
                                // All permissions handled
                                settingsViewModel.setPermissionsRequested(true)
                            }
                        }
                    }



                    if (showExitDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitDialog = false },
                            title = { Text(stringResource(R.string.exit_dialog_title)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showExitDialog = false
                                        finish()
                                    }
                                ) {
                                    Text(stringResource(R.string.exit_dialog_exit))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExitDialog = false }) {
                                    Text(stringResource(R.string.exit_dialog_cancel))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: android.content.Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo == "model_manager") {
            // Using Routes.MODEL_MANAGER would be better if accessible, defaulting to string literal for safety
            // assuming Routes.MODEL_MANAGER const value is "model_manager"
            navigationChannel.trySend(Routes.MODEL_MANAGER)
            intent.removeExtra("navigate_to")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveAppContent(
    isExpanded: Boolean,
    drawerState: DrawerState,
    currentRoute: String?,
    recentConversations: List<com.tk854.localmind.domain.model.Conversation>,
    activeConversationId: String? = null,
    onNavigate: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit = { _, _ -> },
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    content: @Composable () -> Unit
) {
    if (isExpanded) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(320.dp),
                    drawerContainerColor = com.tk854.localmind.ui.theme.NeonSurface,
                    drawerContentColor = com.tk854.localmind.ui.theme.NeonText
                ) {
                    com.tk854.localmind.ui.components.NavigationDrawerContent(
                        currentRoute = currentRoute,
                        recentConversations = recentConversations,
                        activeConversationId = activeConversationId,
                        onNavigate = onNavigate,
                        onConversationClick = onConversationClick,
                        onDeleteConversation = onDeleteConversation,
                        onRenameConversation = onRenameConversation,
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = onToggleDarkMode
                    )
                }
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = com.tk854.localmind.ui.theme.NeonSurface,
                    drawerContentColor = com.tk854.localmind.ui.theme.NeonText
                ) {
                    com.tk854.localmind.ui.components.NavigationDrawerContent(
                        currentRoute = currentRoute,
                        recentConversations = recentConversations,
                        activeConversationId = activeConversationId,
                        onNavigate = onNavigate,
                        onConversationClick = onConversationClick,
                        onDeleteConversation = onDeleteConversation,
                        onRenameConversation = onRenameConversation,
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = onToggleDarkMode
                    )
                }
            }
        ) {
            content()
        }
    }
}

@Composable
fun BiometricLockScreen(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = com.tk854.localmind.ui.theme.NeonPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "App Locked",
                style = MaterialTheme.typography.headlineMedium,
                color = com.tk854.localmind.ui.theme.NeonText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = com.tk854.localmind.ui.theme.NeonPrimary)
            ) {
                Text("Unlock", color = androidx.compose.ui.graphics.Color.Black)
            }
        }
    }
}
