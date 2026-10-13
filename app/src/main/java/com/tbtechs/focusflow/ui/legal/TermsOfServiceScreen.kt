package com.tbtechs.focusflow.ui.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp

private const val TERMS_URL = "https://focusflowapp.pages.dev/terms-of-service/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    val dimensions = LocalFocusFlowDimensions.current
    val context = LocalContext.current
    val sections = listOf(
        "1. Acceptance" to "By installing or using FocusFlow you unconditionally agree to these Terms. If you do not agree, uninstall the app immediately. These Terms may be changed, amended, or replaced at any time without prior notice or obligation. Continued use after any change constitutes your immediate, irrevocable acceptance of the updated Terms. You are solely responsible for reviewing these Terms periodically.",
        "2. What FocusFlow Does" to "FocusFlow is a personal productivity tool that uses Android Accessibility Services, Usage Stats, and System Overlay permissions to enforce self-imposed focus sessions and app restrictions. All enforcement is performed locally on your device by the Android OS and is subject to hardware, software, and OS limitations beyond FocusFlow's control.",
        "3. Your Sole Responsibility" to "You are solely and exclusively responsible for all consequences of configuring, enabling, or disabling any blocking session. FocusFlow and TBTechs bear absolutely no responsibility for missed communications, missed alarms, missed appointments, accidents, injuries, financial losses, or any other outcome — foreseeable or unforeseeable — arising while any blocking feature is active or inactive. Use of FocusFlow is entirely at your own risk.",
        "4. Emergency Access Disclaimer" to "FocusFlow attempts to allow emergency dialers and calls but makes NO guarantee whatsoever that emergency services (112, 911, 999, or any equivalent) will be reachable during an active session. Android OS, OEM skins (One UI, MIUI, ColorOS, etc.), carrier restrictions, or device state may prevent or delay emergency access in ways FocusFlow cannot detect or control. TBTechs is not liable — under any legal theory, in any jurisdiction — for any failure, delay, or inability to access emergency services while FocusFlow is installed, running, or active. DO NOT rely on FocusFlow in any safety-critical or emergency situation.",
        "5. No Data Collection" to "FocusFlow does not collect, transmit, or share any personal data, usage statistics, or behavioral information. All data is stored locally on your device and never leaves it.",
        "6. Accessibility Service Disclosure" to "FocusFlow uses Android's Accessibility Service solely to detect which app is in the foreground and enforce the blocking rules you configure. It does not read passwords, private messages, or any user input beyond keyword patterns you explicitly configure. This disclosure is required by Google Play policy.",
        "7. No Warranty" to "TO THE FULLEST EXTENT PERMITTED BY LAW: FocusFlow is provided \"AS IS\" and \"AS AVAILABLE\" without any warranty of any kind, whether express, implied, or statutory, including but not limited to implied warranties of merchantability, fitness for a particular purpose, accuracy, or reliability. TBTechs explicitly disclaims any warranty that the app will function correctly on your device, Android version, or OEM configuration. Blocking may fail at any time for any reason, including but not limited to OS updates, permission revocation, battery optimization, Doze mode, or OEM-specific process killing.",
        "8. Limitation of Liability" to "TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW: TBTechs, its affiliates, officers, developers, and contributors shall not be liable for any damages of any kind — including direct, indirect, incidental, special, punitive, or consequential damages, personal injury, death, property damage, financial loss, or harm resulting from failure to access emergency services — arising out of or related to your use of or inability to use FocusFlow, regardless of the legal theory (contract, tort, strict liability, or otherwise), even if advised of the possibility of such damages. The aggregate total liability of TBTechs for any and all claims shall not exceed zero (USD $0). No legal action, arbitration, or proceeding against TBTechs may exceed this cap under any circumstances or jurisdiction.",
        "9. Changes to These Terms" to "These Terms may be updated, replaced, or removed at any time without prior notice. The updated Terms are effective immediately upon publication. Your continued use of FocusFlow after any change — regardless of whether you have read the updated Terms — constitutes your unconditional acceptance.",
        "10. Contact" to "For questions, email tbtechsdev@gmail.com or visit focusflowapp.pages.dev. Questions do not alter or waive any provision.",
    )

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = DarkTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dimensions.screenPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BrandPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Gavel,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = "Terms of Service",
                    fontSize = 24.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
                Text(
                    text = "Last updated: April 2026",
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            sections.forEach { (title, body) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = title,
                            fontSize = 15.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                        Text(
                            text = body,
                            fontSize = 13.scaledSp,
                            lineHeight = 19.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))) }
                    }
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Read the full Terms of Service online",
                        fontSize = 14.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandPrimary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Back to Settings", color = DarkTextSecondary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
