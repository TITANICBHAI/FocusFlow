---
name: Rolling Stats dates
description: Date-series requirements for rolling and fixed weekly Stats views.
---

Weekly charts that can cross calendar-week boundaries must keep task, focus, and observed-device values keyed by ISO calendar date. Weekday-only aggregates are still suitable for legacy summaries, but they cannot render a truthful rolling seven-day sequence.

**Why:** A rolling range may contain two occurrences of the same weekday, and collapsing both into one weekday bucket loses chronology and can display the wrong day.

**How to apply:** Build the seven chart slots from the analytics range start date, use the date-keyed maps for each slot, and reserve weekday aggregates for summaries that do not require calendar ordering.