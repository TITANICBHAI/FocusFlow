package com.tbtechs.focusflow.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

@Composable
internal fun FocusFlowFormatGuideHeader() {
    FormatGuideHero()
}

@Composable
internal fun FocusFlowFormatGuideBody() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        GuideArticleSection("1. What is a .focusflow file?") {
            Text(
                FOCUSFLOW_FORMAT_GUIDE_INTRO,
                color = DarkTextSecondary,
                fontSize = 14.scaledSp,
                lineHeight = 21.scaledSp,
            )
            GuideCallout("Important", FOCUSFLOW_RUNTIME_STATE_NOTE, highlighted = true)
            GuideFacts(FOCUSFLOW_FILE_FACTS)
            Text(
                FOCUSFLOW_VERSION_NOTE,
                color = DarkTextSecondary,
                fontSize = 13.scaledSp,
                lineHeight = 19.scaledSp,
            )
        }

        GuideArticleSection("2. The shape of the file") {
            Text(
                "A backup has one top-level object. settings and tasks are the main payload; " +
                    "the remaining fields are metadata or inventory.",
                color = DarkTextSecondary,
                fontSize = 14.scaledSp,
                lineHeight = 21.scaledSp,
            )
            Text(
                "Schematic only: the ellipses below stand for real data and are not valid JSON values.",
                color = DarkTextSecondary,
                fontSize = 12.scaledSp,
                lineHeight = 18.scaledSp,
            )
            GuideCodeBlock(FOCUSFLOW_ENVELOPE_EXAMPLE)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuideMiniCard(
                    title = "summary",
                    body = FOCUSFLOW_SUMMARY_NOTE,
                )
                GuideMiniCard(
                    title = "presetSections",
                    body = FOCUSFLOW_PRESET_SECTIONS_NOTE,
                )
            }
        }

        GuideArticleSection("3. Tasks") {
            Text(
                "Each task is an object in the tasks array. Include the core fields explicitly " +
                    "when creating a file.",
                color = DarkTextSecondary,
                fontSize = 14.scaledSp,
                lineHeight = 21.scaledSp,
            )
            GuideFacts(FOCUSFLOW_TASK_FIELDS)
            GuideCallout(
                title = "Task-specific app access",
                body = FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE,
                highlighted = true,
            )
        }

        GuideArticleSection("4. Reminders") {
            Text(
                "A reminder is an object attached to a task, not a bare number.",
                color = DarkTextSecondary,
                fontSize = 14.scaledSp,
                lineHeight = 21.scaledSp,
            )
            GuideCodeBlock(FOCUSFLOW_REMINDER_EXAMPLE)
            GuideCallout("How scheduling works", FOCUSFLOW_REMINDERS_NOTE)
        }

        GuideArticleSection("5. Portable settings") {
            Text(
                "The settings object can hold portable preferences, app lists, presets, and " +
                    "schedules. FocusFlow applies only supported fields; omitted fields leave " +
                    "this device's existing values unchanged.",
                color = DarkTextSecondary,
                fontSize = 14.scaledSp,
                lineHeight = 21.scaledSp,
            )
            GuideFacts(FOCUSFLOW_PORTABLE_SETTING_GROUPS)
            GuideCallout("What stays on the device", FOCUSFLOW_SETTINGS_BOUNDARY_NOTE)
        }

        GuideArticleSection("6. Rules that matter most") {
            GuideFacts(FOCUSFLOW_FORMAT_RULES)
        }

        GuideArticleSection("7. Import in plain English") {
            GuideFacts(FOCUSFLOW_IMPORT_GUIDANCE)
        }

        GuideArticleSection("8. Small valid example") {
            Text(
                "This compact example uses a completed task so it does not imply a future alarm. " +
                    "A real export can include more portable settings and metadata.",
                color = DarkTextSecondary,
                fontSize = 14.scaledSp,
                lineHeight = 21.scaledSp,
            )
            GuideCodeBlock(FOCUSFLOW_VALID_BACKUP_EXAMPLE)
        }

        GuideArticleSection("9. Create a task backup from your plan") {
            GuideCallout(
                "Use the AI task-backup prompt",
                FOCUSFLOW_GENERATION_NOTE,
                highlighted = true,
            )
        }
    }
}

@Composable
private fun FormatGuideHero() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "PRACTICAL FORMAT GUIDE",
            color = BrandPrimary,
            fontSize = 11.scaledSp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.scaledSp,
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = BrandPrimary)) { append(".focusflow") }
                append(", explained without the manual.")
            },
            color = DarkTextPrimary,
            fontSize = 28.scaledSp,
            lineHeight = 31.scaledSp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "A human-readable guide for people who need to understand or create a FocusFlow backup. " +
                "The full schema does not need to be memorized.",
            color = DarkTextSecondary,
            fontSize = 15.scaledSp,
            lineHeight = 22.scaledSp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GuideBadge("V1")
            GuideBadge("UTF-8 JSON")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GuideBadge(".focusflow")
            GuideBadge("Not a ZIP")
        }
    }
}

@Composable
private fun GuideBadge(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = DarkTextSecondary,
        fontSize = 12.scaledSp,
    )
}

@Composable
private fun GuideArticleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = {
        Text(
            title,
            color = DarkTextPrimary,
            fontSize = 19.scaledSp,
            fontWeight = FontWeight.Bold,
        )
        content()
    })
}

@Composable
private fun GuideFacts(items: List<FocusFlowGuideItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    item.title,
                    color = BrandPrimary,
                    fontSize = 12.scaledSp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    item.body,
                    color = DarkTextSecondary,
                    fontSize = 13.scaledSp,
                    lineHeight = 19.scaledSp,
                )
            }
            if (index < items.lastIndex) {
                HorizontalDivider(color = DarkBorder, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun GuideMiniCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = DarkTextPrimary, fontSize = 13.scaledSp, fontWeight = FontWeight.Bold)
        Text(body, color = DarkTextSecondary, fontSize = 12.scaledSp, lineHeight = 17.scaledSp)
    }
}

@Composable
private fun GuideCallout(
    title: String,
    body: String,
    highlighted: Boolean = false,
) {
    val borderColor = if (highlighted) BrandPrimary.copy(alpha = 0.6f) else DarkBorder
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(title, color = DarkTextPrimary, fontSize = 13.scaledSp, fontWeight = FontWeight.Bold)
        Text(body, color = DarkTextSecondary, fontSize = 13.scaledSp, lineHeight = 19.scaledSp)
    }
}

@Composable
private fun GuideCodeBlock(code: String) {
    Text(
        text = code.trimIndent(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .horizontalScroll(rememberScrollState())
            .padding(14.dp),
        color = DarkTextSecondary,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.scaledSp,
        lineHeight = 16.scaledSp,
        softWrap = false,
    )
}
