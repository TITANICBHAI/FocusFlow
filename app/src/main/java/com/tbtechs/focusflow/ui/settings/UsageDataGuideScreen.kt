package com.tbtechs.focusflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp

/**
 * Settings → About → "How usage data is calculated". This is the only place the Stats and Extra numbers are
 * explained; it is separate from "How to Use", which covers blocking and protections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsageDataGuideScreen(onBack: () -> Unit) {
    val dimensions = LocalFocusFlowDimensions.current
    val sections = remember { usageDataGuide() }
    var expanded by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {},
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(USAGE_GUIDE_TITLE, fontSize = 24.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
            Text(USAGE_GUIDE_SUBTITLE, fontSize = 14.scaledSp, color = DarkTextSecondary)
            sections.forEachIndexed { index, section ->
                GuideSectionCard(
                    section = section,
                    expanded = expanded == index,
                    onToggle = { expanded = if (expanded == index) -1 else index },
                )
            }
            Text(
                "Everything here is worked out on this phone.",
                fontSize = 12.scaledSp,
                color = DarkTextMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun GuideSectionCard(section: UsageGuideSection, expanded: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DarkCard)
            .border(1.dp, DarkBorder, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                section.title,
                modifier = Modifier.weight(1f),
                fontSize = 16.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary,
            )
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = DarkTextMuted,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                section.entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.question, fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                        Text(entry.answer, fontSize = 13.scaledSp, lineHeight = 19.scaledSp, color = DarkTextSecondary)
                    }
                }
            }
        }
    }
}
