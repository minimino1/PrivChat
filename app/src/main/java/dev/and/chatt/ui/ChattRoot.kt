package dev.and.chatt.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.and.chatt.ui.auth.AuthScreen
import dev.and.chatt.ui.chat.ChatScreen
import dev.and.chatt.ui.chats.ChatListScreen
import dev.and.chatt.ui.profile.ProfileScreen
import dev.and.chatt.ui.theme.ChattTheme

private object Routes {
    const val AUTH = "auth"
    const val CHATS = "chats"
    const val PROFILE = "profile"
    const val CHAT = "chat/{conversationId}"
    fun chat(conversationId: String) = "chat/$conversationId"
}

@Composable
fun ChattRoot(deepLinkConversationId: String? = null) {
    ChattTheme {
        val sessionViewModel: SessionViewModel = viewModel()
        val session by sessionViewModel.state.collectAsState()

        Surface(modifier = Modifier.fillMaxSize()) {
            if (session is SessionState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Surface
            }

            val signedIn = session is SessionState.SignedIn
            val navController = rememberNavController()

            // Abmelden / Token abgelaufen: zurueck zum Login, Verlauf leeren.
            LaunchedEffect(signedIn) {
                if (!signedIn) {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.CHATS) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            // Deep-Link aus einer Push-Benachrichtigung.
            LaunchedEffect(deepLinkConversationId, signedIn) {
                if (signedIn && !deepLinkConversationId.isNullOrBlank()) {
                    navController.navigate(Routes.chat(deepLinkConversationId)) { launchSingleTop = true }
                }
            }

            NavHost(
                navController = navController,
                startDestination = if (signedIn) Routes.CHATS else Routes.AUTH,
            ) {
                composable(Routes.AUTH) {
                    AuthScreen(
                        onAuthenticated = {
                            navController.navigate(Routes.CHATS) {
                                popUpTo(Routes.AUTH) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(Routes.CHATS) {
                    ChatListScreen(
                        onOpenConversation = { id -> navController.navigate(Routes.chat(id)) },
                        onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Routes.CHAT,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
                ) { entry ->
                    ChatScreen(
                        conversationId = entry.arguments?.getString("conversationId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}