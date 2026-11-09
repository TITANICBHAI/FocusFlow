package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.layout.Arrangement
import com.tbtechs.focusflow.ui.theme.scaledSp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.stats.StatsCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted

/** The scrolling list every Extra tab is built from (16 dp padding, 12 dp between cards). */
@Composable
internal fun ExtraTabList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A card with an upper-case title and a spaced column of content. */
@Composable
internal fun ExtraCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    StatsCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PhoneSectionTitle(title)
            content()
        }
    }
}

/** A large one-line figure, used as a card's hero. */
@Composable
internal fun PhoneHeroText(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 22.scaledSp, fontWeight = FontWeight.Bold, modifier = modifier)
}

/** Title with a muted second line, for lists that have no bar. */
@Composable
internal fun PhoneListRow(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 15.scaledSp, fontWeight = FontWeight.Medium)
        if (!subtitle.isNullOrEmpty()) Text(subtitle, fontSize = 12.scaledSp, color = DarkTextMuted)
    }
}
