package com.tbtechs.focusflow.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val changelog = remember(context) { loadChangelog(context) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "What's New",
                        color = DarkTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.scaledSp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = DarkTextPrimary)
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(BrandPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Text("Changelog", fontSize = 28.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                Text(
                    "Every improvement, fix, and new feature across all versions.",
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            changelog.forEach { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder.copy(alpha = 0.9f)),
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BrandPrimary)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (entry.version.startsWith("c")) entry.version else "v${entry.version}",
                                    fontSize = 14.scaledSp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Text(entry.date, fontSize = 13.scaledSp, color = DarkTextMuted)
                        }
                        entry.sections.forEach { section ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        sectionIcon(section.heading),
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        section.heading,
                                        fontSize = 16.scaledSp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkTextPrimary,
                                    )
                                }
                                section.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .size(4.dp)
                                                .background(BrandPrimary, CircleShape),
                                        )
                                        Text(
                                            item,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 14.scaledSp,
                                            color = DarkTextSecondary,
                                            lineHeight = 20.scaledSp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Privacy Policy: titanicbhai.github.io/FocusFlow",
                fontSize = 12.scaledSp,
                color = DarkTextMuted,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

private fun sectionIcon(heading: String): ImageVector = when {
    heading.contains("fix", ignoreCase = true) ||
        heading.contains("reliab", ignoreCase = true) -> Icons.Outlined.CheckCircle
    heading.contains("permission", ignoreCase = true) ||
        heading.contains("protect", ignoreCase = true) -> Icons.Outlined.Security
    heading.contains("block", ignoreCase = true) ||
        heading.contains("focus", ignoreCase = true) -> Icons.Outlined.Tune
    heading.contains("database", ignoreCase = true) ||
        heading.contains("stability", ignoreCase = true) -> Icons.Outlined.BugReport
    else -> Icons.Outlined.AutoAwesome
}