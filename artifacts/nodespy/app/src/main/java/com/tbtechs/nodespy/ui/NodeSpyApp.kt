package com.tbtechs.nodespy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tbtechs.nodespy.ui.screens.CaptureListScreen
import com.tbtechs.nodespy.ui.screens.InspectorScreen
import com.tbtechs.nodespy.ui.screens.PermissionsScreen

@Composable
fun NodeSpyApp(
    initialCaptureId: String? = null,
    onLaunchBubble: () -> Unit = {}
) {
    val nav = rememberNavController()

    LaunchedEffect(initialCaptureId) {
        if (initialCaptureId != null) {
            nav.navigate("inspector/$initialCaptureId")
        }
    }

    NavHost(navController = nav, startDestination = "captures") {
        composable("captures") {
            CaptureListScreen(
                onOpenCapture = { id -> nav.navigate("inspector/$id") },
                onLaunchBubble = onLaunchBubble,
                onOpenPermissions = { nav.navigate("setup") }
            )
        }
        composable(
            "inspector/{captureId}",
            arguments = listOf(navArgument("captureId") { type = NavType.StringType })
        ) { back ->
            val id = back.arguments?.getString("captureId") ?: return@composable
            InspectorScreen(captureId = id, onBack = { nav.popBackStack() })
        }
        composable("setup") {
            PermissionsScreen(onBack = { nav.popBackStack() })
        }
    }
}
