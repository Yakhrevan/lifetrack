# LifeTrack Phase 4A — Reliability Hardening and Emulator Acceptance

Parent checkpoint: `feature/lifetrack-v12-reliability-productivity` at
`719db60292f10800fb3babb71de8cc9a4f74b8e9` (successful CI).

Phase 4A branch: `feature/lifetrack-v12-phase4a-qa`. Do not merge into `main`.

## Reliability corrections

- System boot/date/timezone/exact-alarm grant events now **replace** potentially stale
  pending one-time reconciliation work, while retaining periodic reconciliation.
- Backup preferences use synchronous checked writes during restore. If a write
  reports failure, `restoreSafely` attempts rollback and does not report success.
- Monthly task recurrence no longer stops after 120 months; it keeps the
  original end-of-month anchor (31 January -> 28 February -> 31 March).
- Snooze interval inputs are bounded to 5–120 minutes for WorkManager and
  history timestamps even if a malformed caller passes a different value.
- The delete-confirmation preference now saves under the key its reader expects.
  The reader still accepts the previous camelCase key for older installs.

## Automated verification

| Test layer | Coverage |
| --- | --- |
| JVM `testDebugUnitTest` | Existing notification settings, quiet hours, recurring task behavior, backup JSON validation, reminder follow-up policy, planner and analytics; new monthly >10-year regression. |
| Android emulator API 35 | Full-backup module inclusion and restore round-trip to persisted preferences. |
| Android emulator API 35 | MainActivity notification intent routing for task/event IDs, widget route and invalid destinations. |
| Android emulator API 35 | Master/task switch persistence and notification suppression; corrected confirmation setting. |
| Android emulator API 35 | Timezone broadcast replaces a stale delayed reconciliation job. |
| Compose UI emulator | Today Planner navigation, Notification Center refresh, snoozed notification opening its task, and analytics-to-planner navigation. |
| CI | Android lint, debug APK build, APK artifact, and archived device-test results. |

## Remains outside emulator acceptance

- Upgrade the app over a **real existing 1.1 installation** after saving a separate backup,
  then compare task, expense, habit, study, note and calendar records.
- Test Google Drive provider selection, revoked access, offline errors,
  exported JSON inspection and restore on a second phone.
- Verify exact-alarm permission loss/regrant, Doze, vendor battery saver,
  reboot, time zone changes, clock drift and notification tap/sound/logo on
  locked and unlocked physical devices.
- Check all three home widgets, large fonts, 320 dp layouts and TalkBack.
- Exported JSON is not encrypted by LifeTrack. Debug APK is not a signed
  Play Store release. Avoid treating a green emulator run as physical-device proof.

## Safety

All source changes are additive or backward-compatible. Existing preference
files, application ID and saved records are not intentionally cleared.
Instrumented tests run on a disposable CI emulator, not on a user's phone.
