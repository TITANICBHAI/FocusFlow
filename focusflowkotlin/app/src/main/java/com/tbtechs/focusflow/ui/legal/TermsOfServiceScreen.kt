package com.tbtechs.focusflow.ui.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val TERMS_URL = "https://focusflowapp.pages.dev/terms-of-service/"

@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sections = listOf(
        "1. Acceptance" to "By downloading, installing, or using FocusFlow, you agree to these Terms of Service. If you do not agree, do not use the app.",
        "2. What FocusFlow Does" to "FocusFlow provides configurable focus sessions, app blocking, network protection, schedules, allowances, and related productivity tools.",
        "3. Your Sole Responsibility" to "You are responsible for configuring FocusFlow correctly, keeping emergency access available, and deciding which applications or network destinations to block.",
        "4. Emergency Access Disclaimer" to "FocusFlow is not an emergency, medical, security, or parental-control guarantee. Never configure it in a way that prevents access to emergency services.",
        "5. No Data Collection" to "FocusFlow is designed to keep tasks, settings, and analytics on the device. Technical diagnostics leave the device only when you intentionally submit a report.",
        "6. Accessibility Service Disclosure" to "The Accessibility Service is used to detect the foreground package and enforce configured blocking. It does not read messages, passwords, form contents, or screen recordings.",
        "7. No Warranty" to "The app is provided as-is and as-available. Android, OEM, permission, battery, and system behavior can affect enforcement.",
        "8. Limitation of Liability" to "To the fullest extent permitted by law, TBTechs' liability for any claims shall not exceed zero (USD $0).",
        "9. Changes to These Terms" to "These Terms may be updated, replaced, or removed at any time without prior notice. Continued use after publication constitutes acceptance.",
        "10. Contact" to "For questions, email tbtechsdev@gmail.com or visit focusflowapp.pages.dev. Questions do not alter or waive any provision.",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Terms of Service", style = MaterialTheme.typography.headlineMedium)
                Text("Last updated: April 2026", style = MaterialTheme.typography.bodySmall)
            }
            sections.forEach { (title, body) ->
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                Text("Read the full Terms of Service online")
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                Text("Back to Settings")
            }
        }
    }
}