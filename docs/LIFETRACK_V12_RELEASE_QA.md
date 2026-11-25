# LifeTrack v1.2 — Reliability + Productivity

Base: `feature/lifetrack-notifications-v2` (`8d3a14fdcea61e42fa3fab30ba2b377eade6d143`)

Branch: `feature/lifetrack-v12-reliability-productivity`

**Important:** This branch is intended for verification and APK testing. It has not been merged into `main`.

## Improvements and acceptance

1. **Backup & data safety:** Full JSON exports now include calendar, current study, water, screen-time, notification history and voice history preference files that earlier backups missed. User-selected Android document folders, including Google Drive where the provider is installed, receive periodic verified copies; local backups are checked before recording success. Restore validates before writing and attempts rollback on write errors.
2. **Reminder reliability:** Users may opt into Android exact-alarm permissions for task reminders. `AlarmManager.setExactAndAllowWhileIdle` provides precise task alarm dispatch when the OS permits; WorkManager is used as fallback. Reconciliation runs on reboot, time/timezone changes, package replacement and exact-alarm permission changes. Doze, battery optimization and vendor-specific policies may still delay delivery. Other module reminders remain on WorkManager.
3. **Quick Add:** Existing radial actions remain; reminder creation is added and water now logs 250 ml in two taps. Tasks, expense and calendar entry points remain intact.
4. **Notification Center:** Shows derived upcoming/overdue task, event, habit and routine reminders plus an on-device bounded history of delivered, snoozed and completed actions. The center is local-only and does not claim that Android confirmed the user saw a push.
5. **Today Planner:** One chronological view across saved tasks, calendar events, habits, routines and study sessions, with date navigation, status filters and task completion.
6. **Widgets:** Home-screen Today Tasks, Water Progress and Habit Progress native Android widgets, auto-refreshed periodically and after key data actions.
7. **Automated UI tests:** New Android Compose UI tests for the new flows and an instrumentation test for backup content. Pure Kotlin tests cover recurrence, reminders, history serialization, weekly progress and follow-up rules.
8. **Smart reminders:** Optional high-priority-only follow-ups (default), adjustable delay 15/30/60 minutes and hard cap of one to three follow-ups per incomplete task; disabled unless the user opts in.
9. **Analytics:** Weekly progress computed from saved task/habit occurrences, focus-session minutes, overdue task count and history entries, without sample or invented data.
10. **Performance & accessibility:** Weather background refreshes use a 30-minute cache; new screens expose labeled actions, empty states and scalable Material typography. A full accessibility audit of all legacy screens remains future work.

## Data safety considerations

- **Local snapshot ≠ cloud sync.** The Google-account chooser is not a genuine cloud authentication or multi-device conflict resolution system. Selecting a Google Drive folder uses the system document picker and provider; no Google credentials enter LifeTrack.
- An off-device backup is configured only after the user selects a writable folder. Otherwise backups remain local and can be lost on uninstall or device loss.
- Exported JSON is not end-to-end encrypted by LifeTrack. Select a private Drive/Files location under your control and avoid sharing its link publicly.
- On restore, old entries/settings should remain on errors; rerun reminder reconciliation and refresh widgets. A power loss midway through SharedPreferences writes is not a database transaction; consider Room with migrations for a future release.
- Existing data, preference keys and application ID are preserved; `versionCode` increases 3→4 for upgrade installation.

## Automated checks

- `gradle testDebugUnitTest`
- `gradle lintDebug`
- `gradle assembleDebug`
- `gradle connectedDebugAndroidTest` on API 35 emulator

## Real-device acceptance checklist (not simulated by CI)

- [ ] Upgrade an installed 1.1 app and check all saved task, habit, expense, note, study and calendar records remain intact.
- [ ] Tap each existing quick action; check reminder draft and +250 ml water action.
- [ ] Create important task reminders. Toggle Android exact-alarm special access off/on and verify WorkManager fallback.
- [ ] Check a task reminder, snooze it, complete it from the notification; ensure stale notifications stop.
- [ ] Repeat after reboot, airplane mode, Doze/battery saver, timezone change and midnight.
- [ ] Verify quiet hours, sound and notification logo across locked/unlocked states.
- [ ] Choose a Drive/Files folder, run manual backup, open the resulting JSON from Drive on a second device, and restore safely. Test missing/revoked folder access and errors.
- [ ] Add all three widgets, check totals before/after marking a task, habit or logging water.
- [ ] Check small-screen (320 dp), large-font, dark/light and TalkBack UI accessibility.
- [ ] Inspect release signing/versioning separately; GitHub Actions currently creates a debug APK, not a Play Store signed release.
