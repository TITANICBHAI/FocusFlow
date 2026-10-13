package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.SunAmber

private val MANIPULATION_TYPES = setOf(
    "VARIABLE_REWARD_LOOP",
    "INFINITE_SESSION_DESIGN",
    "MORNING_HIJACK",
    "ESCALATING_CAPTURE",
    "STREAK_LOCK_IN",
    "NOTIFICATION_CONDITIONING",
)
private val NO_RESPONSE_TYPES = setOf("ALLOWANCE_SUGGESTION")

@Composable
fun FindingCardView(
    finding: FindingEntity,
    onMarkSeen: (String) -> Unit,
    onIntentional: (String, String) -> Unit,
    onAware: (String, String) -> Unit,
) {
    val isNew = finding.state == "detected"
    val showReply =
        finding.state == "seen" && finding.detectionType !in NO_RESPONSE_TYPES
    val isAware = finding.state == "aware"
    val accentColor = when {
        isAware -> SunAmber
        isNew -> BrandPrimary
        else -> BrandPrimary.copy(alpha = 0.6f)
    }

    StatsCard(
        modifier = if (isNew) {
            Modifier.clickable { onMarkSeen(finding.id) }
        } else {
            Modifier
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    findingCategoryLabel(finding),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(finding.headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                finding.body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(finding.evidenceLine, fontSize = 11.sp, color = DarkTextSecondary)

            if (showReply) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onIntentional(finding.id, finding.evidenceFingerprint) },
                    ) {
                        Text("I chose this", fontSize = 13.sp, color = DarkTextSecondary)
                    }
                    TextButton(
                        onClick = { onAware(finding.id, finding.evidenceFingerprint) },
                    ) {
                        Text("I didn't know", fontSize = 13.sp, color = BrandPrimary)
                    }
                }
            }
        }
    }
}

private fun findingCategoryLabel(finding: FindingEntity): String = when {
    finding.detectionType == "ALLOWANCE_SUGGESTION" -> "BASED ON YOUR DATA"
    finding.detectionType in MANIPULATION_TYPES && finding.subjectAppName != null ->
        "WHAT ${finding.subjectAppName.uppercase()} IS DOING"
    else -> "pattern"
}