package com.tbtechs.focusflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Native Activity host for the pure-Kotlin migration.
 *
 * The screen families and NavHost are the next UI migration layer. Keeping a
 * real Compose host here makes the Android application entry point resolvable
 * now while preserving the existing enforcement components' MainActivity
 * references.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme {
                FocusFlowScaffold(route = routeFromIntent(intent))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun routeFromIntent(intent: Intent?): String =
        intent?.data?.path?.removePrefix("/")?.ifBlank { null } ?: "home"
}

@Composable
private fun FocusFlowScaffold(route: String) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "FocusFlow",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Native Android host ready",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Route: $route",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}