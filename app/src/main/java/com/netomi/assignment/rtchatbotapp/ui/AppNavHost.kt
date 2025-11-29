package com.netomi.assignment.rtchatbotapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.netomi.assignment.rtchatbotapp.ui.viewmodel.ChatListViewModel
import com.netomi.assignment.rtchatbotapp.ui.viewmodel.ChatViewModel

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "chat_list") {

        composable("chat_list") {backStackEntry ->
            val chatVm: ChatViewModel = hiltViewModel(backStackEntry)
            val listVm: ChatListViewModel = hiltViewModel(backStackEntry)
            ChatListScreen(
                navController = navController,
                viewModel = listVm,
                chatViewModel = chatVm
            )
        }

        composable(
            "chat/{threadId}",
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId") ?: "default"

            val parentEntry = remember(navController) { navController.getBackStackEntry("chat_list") }
            val chatVm: ChatViewModel = hiltViewModel(parentEntry)

            LaunchedEffect(threadId) {
                chatVm.setCurrentThread(threadId)
            }
            ChatScreen(viewModel = chatVm, threadId = threadId)
        }
    }
}