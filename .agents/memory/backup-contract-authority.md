---
name: Portable backup authorization and legacy migration
description: Keep authorized FocusFlow file backups separate from the historical one-time database migration.
---

The user explicitly reauthorized the FocusFlow `.focusflow` import/export implementation in native Kotlin after the earlier removal request. Do not treat that earlier removal instruction as active for this feature while the current authorization and implementation remain in force.

Keep backup portability filtering and restore mappings separate from the historical one-time `app_settings` database migration. Do not broaden that migration adapter to support backups.

After task replacement, run one alarm reconciliation even if the backup has no future scheduled tasks, so alarms for deleted local tasks are cleared. In merge mode, reconcile when the backup adds scheduled tasks; never reconcile once per inserted task.

**Why:** The earlier removal instruction became stale after the user asked to compare and implement TypeScript import/export in Kotlin and then tested an actual `.focusflow` file.

**How to apply:** Follow the current backup plan and tracker for `.focusflow` changes; preserve explicit confirmation, the replacement-off default, active-session/data guards, and replacement reconciliation. Keep the legacy settings migration contract unchanged.