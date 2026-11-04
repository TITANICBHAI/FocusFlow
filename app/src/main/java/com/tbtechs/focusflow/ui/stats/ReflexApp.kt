package com.tbtechs.focusflow.ui.stats

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import java.util.Locale

/** The app the person said they open without deciding to, resolved to a real package. */
data class ReflexApp(val packageName: String, val appName: String)

/**
 * Matches the app name typed in the cold-start sheet to an installed app.
 * Exact name (ignoring case, spaces and punctuation) wins. Otherwise a partial match is accepted
 * only when it points to exactly one app, so "insta" finds Instagram but "google" does not guess.
 */
internal object ReflexAppResolver {
    fun resolve(text: String, apps: List<Pair<String, String>>): String? {
        val query = normalize(text)
        if (query.isEmpty()) return null
        apps.firstOrNull { normalize(it.second) == query }?.let { return it.first }
        if (query.length < 3) return null
        val partial = apps
            .filter { (_, name) ->
                val normalized = normalize(name)
                normalized.length >= 3 && (normalized.contains(query) || query.contains(normalized))
            }
            .map { it.first }
            .distinct()
        return partial.singleOrNull()
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}

/**
 * Fills in the package of the "reflex_app" answer before it is saved. Without a package the
 * answer can never be used, which is how it used to end up stored and ignored.
 */
internal suspend fun withResolvedReflexApp(
    context: Context,
    answers: List<BehaviouralHypothesisEntity>,
): List<BehaviouralHypothesisEntity> {
    val target = answers.firstOrNull {
        it.questionId == "reflex_app" && it.answerPackage == null && it.answerText.isNotBlank()
    } ?: return answers
    val apps = runCatching { InstalledAppsRepository(context.applicationContext).getInstalledApps() }
        .getOrDefault(emptyList())
    val resolved = ReflexAppResolver.resolve(
        target.answerText,
        apps.map { it.packageName to it.appName },
    ) ?: return answers
    return answers.map { if (it === target) it.copy(answerPackage = resolved) else it }
}

@Composable
internal fun ReflexAppCard(
    app: ReflexApp,
    onBlock: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    StatsCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("YOU SAID YOU OPEN THIS WITHOUT DECIDING", style = MaterialTheme.typography.labelLarge)
            Text(app.appName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "If opening it is a reflex, a short block gives you a pause before you do.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onBlock(app.packageName) }, modifier = Modifier.weight(1f)) {
                    Text("Block ${app.appName} now", fontSize = 13.sp, maxLines = 1)
                }
                TextButton(onClick = onDismiss) { Text("Not now", fontSize = 13.sp) }
            }
        }
    }
}
