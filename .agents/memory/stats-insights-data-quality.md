---
name: Stats insights data quality
description: Stable trend-chart and partial-source rules for FocusFlow analytics.
---

Zero-fill missing weekly trend periods for a stable chart, but track weeks with real records separately and use that count for confidence thresholds.

**Why:** A fixed 12-bar chart should show the current period even when no completion row exists, while treating an empty week as evidence of insufficient history would overstate the available data.

**How to apply:** Anchor the series to the current calendar week using the database's Sunday convention. Keep source failures distinct from valid empty results so the UI can explain partial analytics instead of silently presenting zeros as facts.