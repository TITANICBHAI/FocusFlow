---
name: Shared preference compatibility
description: Compatibility rule for Android SharedPreferences values that outlive app code changes.
---

Read metadata preferences by checking the stored value type instead of assuming every historical value matches the current accessor.

When a one-time preference changes meaning, use a distinct versioned key; an old “shown” value must not suppress a newly actionable prompt.

**Why:** Android SharedPreferences throws `ClassCastException` when a key written as a Boolean is later read with `getString`; existing users can carry those values across APK updates.

**How to apply:** For non-enforcement metadata, treat an unexpected stored type as absent or migrate it explicitly before using a typed accessor. Avoid changing enforcement preference semantics without a deliberate migration.

**Why:** A previous flag may record that an old diagnostic warning appeared, not that the user saw or acted on a settings prompt.

**How to apply:** Keep separate keys for prompts whose purpose or action changes, unless a deliberate migration preserves the intended once-only experience.