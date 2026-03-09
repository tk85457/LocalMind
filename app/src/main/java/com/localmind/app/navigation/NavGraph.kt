package com.localmind.app.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.localmind.app.ui.screens.*

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val CHAT = "chat"
    const val CHAT_WITH_ID = "chat?conversationId={conversationId}"
    const val MODEL_MANAGER = "model_manager"
    const val MODEL_HUB = "model_hub"
    const val MODEL_DETAIL = "model_detail/{modelId}"
    const val ONLINE_MODEL_DETAIL = "online_model_detail/{repoId}"
    const val IMPORT_MODEL = "import_model"
    const val SETTINGS = "settings"
    const val BENCHMARK_HOME = "benchmark"
    const val BENCHMARK = "benchmark/{modelId}"
    const val PERSONA_MANAGEMENT = "persona_management"
    const val PROMPT_TEMPLATE_MANAGER = "prompt_template_manager"

    fun modelDetail(modelId: String): String = "model_detail/${Uri.encode(modelId)}"
    fun onlineModelDetail(repoId: String): String = "online_model_detail/${Uri.encode(repoId)}"
    fun benchmark(modelId: String): String = "benchmark/${Uri.encode(modelId)}"
    fun chat(conversationId: String): String = "chat?conversationId=${Uri.encode(conversationId)}"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    nextDestination: String,
    startDestination: String = Routes.SPLASH
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Splash Screen
        composable(Routes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(nextDestination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding Screen
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // Routes.CHAT alias — drawer se navigate karne pe crash fix
        composable(Routes.CHAT) {
            ChatScreen(
                conversationId = null,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = {
                    navController.navigate(Routes.MODEL_MANAGER) { launchSingleTop = true }
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
                onNavigateToPersonaManagement = {
                    navController.navigate(Routes.PERSONA_MANAGEMENT)
                },
                onNavigateToPromptTemplates = {
                    navController.navigate(Routes.PROMPT_TEMPLATE_MANAGER)
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        // Chat Screen (single destination, optional conversationId query argument)
        composable(
            route = Routes.CHAT_WITH_ID,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId")?.let(Uri::decode)
            ChatScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = {
                    runCatching {
                        navController.navigate(Routes.MODEL_MANAGER) {
                            launchSingleTop = true
                        }
                    }.onFailure {
                        Log.w("LocalMind-Nav", "Model navigation failed from chat", it)
                    }
                },
                onNavigateToSettings = {
                    runCatching {
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    }.onFailure {
                        Log.w("LocalMind-Nav", "Settings navigation failed from chat", it)
                    }
                },
                onNavigateToPersonaManagement = {
                    navController.navigate(Routes.PERSONA_MANAGEMENT)
                },
                onNavigateToPromptTemplates = {
                    navController.navigate(Routes.PROMPT_TEMPLATE_MANAGER)
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(Routes.MODEL_MANAGER) {
            ModelManagerScreen(
                onNavigateBack = { navController.popBackStack() },
                onImportModel = { navController.navigate(Routes.IMPORT_MODEL) },
                onNavigateToChat = {
                    val chatDestinationId = navController.graph.findNode(Routes.CHAT_WITH_ID)?.id
                    navController.navigate(Routes.CHAT) {
                        if (chatDestinationId != null) {
                            popUpTo(chatDestinationId) {
                                inclusive = true
                                saveState = false
                            }
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                onModelDetail = { modelId ->
                    navController.navigate(Routes.modelDetail(modelId))
                },
                onOnlineModelDetail = { repoId ->
                    navController.navigate(Routes.onlineModelDetail(repoId))
                },
                onNavigateToHub = {
                    navController.navigate(Routes.MODEL_HUB)
                }
            )
        }

        // Model Hub (The Hugging Face search screen)
        composable(Routes.MODEL_HUB) {
            ModelHubScreen(
                onNavigateBack = { navController.popBackStack() },
                onOnlineModelDetail = { repoId ->
                    navController.navigate(Routes.onlineModelDetail(repoId))
                }
            )
        }

        // Model Detail
        composable(
            route = Routes.MODEL_DETAIL,
            arguments = listOf(
                navArgument("modelId") { type = NavType.StringType }
            )
        ) {
            ModelDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onBenchmark = { modelId ->
                    navController.navigate(Routes.benchmark(modelId))
                }
            )
        }

        composable(
            route = Routes.ONLINE_MODEL_DETAIL,
            arguments = listOf(
                navArgument("repoId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val repoId = backStackEntry.arguments?.getString("repoId")?.let(Uri::decode).orEmpty()
            OnlineModelDetailScreen(
                repoId = repoId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Import Model
        composable(Routes.IMPORT_MODEL) {
            ImportModelScreen(
                onNavigateBack = { navController.popBackStack() },
                onImportSuccess = {
                    navController.popBackStack()
                }
            )
        }


        // Settings
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPersonas = { navController.navigate(Routes.PERSONA_MANAGEMENT) },
                onNavigateToPromptTemplates = { navController.navigate(Routes.PROMPT_TEMPLATE_MANAGER) }
            )
        }

        // Benchmark Home
        composable(Routes.BENCHMARK_HOME) {
            BenchmarkScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Persona Management
        composable(Routes.PERSONA_MANAGEMENT) {
            PersonaManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Prompt Template Manager
        composable(Routes.PROMPT_TEMPLATE_MANAGER) {
            PromptTemplateManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Benchmark
        composable(
            route = Routes.BENCHMARK,
            arguments = listOf(
                navArgument("modelId") { type = NavType.StringType }
            )
        ) {
            BenchmarkScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

    }
}
