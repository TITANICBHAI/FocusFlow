package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.SunAmber

private val BarShape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
private val HOUR_AXIS_LABELS = listOf("12a", "6a", "12p", "6p")

/** 24 bars for screen-on (or unlocks) by hour. Every value 0 shows a muted note instead of bars. */
@Composable
internal fun HourBars(
    values: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    height: Dp = 72.dp,
) {
    val fractions = remember(values) { barFractions(values) }
    if (fractions.none { it > 0f }) {
        Text(
            "No data yet",
            fontSize = 12.sp,
            color = DarkTextMuted,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .semantics { this.contentDescription = contentDescription },
        )
        return
    }
    Column(modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription }) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            fractions.forEachIndexed { i, f ->
                if (f <= 0f) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(f)
                            .clip(BarShape)
                            .background(if (i == highlightIndex) SunAmber else BrandPrimary.copy(alpha = 0.85f)),
                    )
                }
            }
        }
        // Each label sits at the start of its own quarter of the day: bars 0, 6, 12 and 18.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            HOUR_AXIS_LABELS.forEach { label ->
                Text(label, fontSize = 10.sp, color = DarkTextMuted, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

/** One bar per day. Dimmed days (partial / missing data) are drawn muted, the highlighted day in amber. */
@Composable
internal fun DayBars(
    points: List<DayBarPoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
) {
    val fractions = remember(points) { barFractions(points.map { it.value }) }
    val dimColor = DarkTextMuted.copy(alpha = 0.35f)
    Column(modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription }) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            fractions.forEachIndexed { i, f ->
                if (f <= 0f) {
                    Spacer(Modifier.weight(1f))
                } else {
                    val point = points[i]
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(f)
                            .clip(BarShape)
                            .background(
                                when {
                                    point.highlighted -> SunAmber
                                    point.dimmed -> dimColor
                                    else -> BrandPrimary.copy(alpha = 0.85f)
                                },
                            ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            points.forEach { point ->
                Text(
                    point.label,
                    fontSize = 10.sp,
                    color = DarkTextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Bars for a handful of buckets: the count above each bar (skipped when 0) and its label below. */
@Composable
internal fun BucketBars(
    counts: List<Int>,
    labels: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    val fractions = remember(counts) { barFractions(counts) }
    val barArea = (height - 14.dp).coerceAtLeast(8.dp)
    // Room for the count text above the tallest bar. In sp so it grows with the user's font size
    // instead of clipping; at the default scale this is the same 14 dp as before.
    val countRoom = with(LocalDensity.current) { 14.sp.toDp() }
    Column(modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription }) {
        Row(
            Modifier.fillMaxWidth().height(barArea + countRoom),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            fractions.forEachIndexed { i, f ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val count = counts.getOrElse(i) { 0 }
                    if (count > 0) Text(count.toString(), fontSize = 10.sp, color = DarkTextMuted, maxLines = 1)
                    if (f > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(barArea * f)
                                .clip(BarShape)
                                .background(BrandPrimary.copy(alpha = 0.85f)),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEach { label ->
                Text(
                    label,
                    fontSize = 10.sp,
                    color = DarkTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Title (and optional subtitle) on the left, a thin share bar under them, `trailing` text on the right. */
@Composable
internal fun ShareRow(
    title: String,
    subtitle: String?,
    fraction: Float,
    trailing: String,
    modifier: Modifier = Modifier,
) {
    val share = shareBarFraction(fraction)
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = DarkTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            ) {
                if (share > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(share)
                            .fillMaxHeight()
                            .background(BrandPrimary.copy(alpha = 0.85f)),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(trailing, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, maxLines = 1)
    }
}
