# LifeTrack Phase 4C — Data Preservation & Crash-Safe Restore

Parent: `feature/lifetrack-v12-phase4b-release`. Branch:
`feature/lifetrack-v12-phase4c-data-safety`. **Do not merge `main`.**

## Critical preservation correction

Earlier app startup could automatically delete saved tracker entries, water logs,
tasks, notes, study sessions, habits or routines based on text like `DEMO DATA`
or generic sample names. User content is not reliably distinguishable from
older seeded examples using those fields. **All automatic startup deletion is
disabled.** Existing user and legacy data remain on disk. The explicitly
user-invoked 70-day sample removal control remains available.

## Durable restore recovery

- Before importing a full backup, LifeTrack writes a validated snapshot of the
  current entries and supported settings to an `AtomicFile` in the app-private
  no-backup directory. This must finish successfully before any changes begin.
- Restored entries use synchronous disk commits, as do supported settings.
- If restore fails, LifeTrack replays the journal, restoring previous entries
  and supported settings. If the process dies between writes, the next launch
  replays the journal **before rendering or loading user repositories**.
- The rollback file is removed only after restoration has completed; successful
  restore does not get undone on subsequent launches.
- If recovery cannot complete safely, the app blocks normal startup with an
  explanatory dialog rather than allowing further edits to a half-restored state.
  **Do not uninstall or clear storage if this occurs.**
- Application package name and preference file names are unchanged; versionCode
  6, versionName `1.2.2-rc`. Prior encrypted and plaintext backup import remains
  compatible.

## Automated tests

- Android emulator scenario: simulate partial restore by mutating entries and
  settings after journal staging, then verify complete rollback and idempotency.
- Verify successful restore removes recovery journal.
- Launch a real `MainActivity` with user task, note and tracker entry containing
  `DEMO DATA` and make sure none are silently deleted.
- Keep unit tests, lint, API 35 instrumentation, APK build and gated signing
  workflow on the Phase 4C branch.

## Still needs physical-device validation

- [ ] Restore on two real devices, including wrong passphrase, corrupt file,
      locked/offline/revoked Drive provider, storage low/full and crash/force-stop
      during a large full restore.
- [ ] Existing user's old installed APK -> `1.2.2-rc` **in-place upgrade** using
      the **same signing certificate**, with before/after record counts and data
      spot-checks across every module. Do not uninstall to bypass signing errors.
- [ ] OEM Doze/battery saver, reboot, DST/timezone transitions, precise alarms,
      snooze, quiet hours, notification icon and audio.
- [ ] Home widgets, TalkBack, large font, narrow-screen and dark-mode checks.
- [ ] Real owner-key signing and installation compatibility. CI debug APK is
      not a substitute for a release-signed build.

The app's internal local snapshots and short-lived rollback journal are still
app-private **plaintext**, not portable encrypted exports. They are removed
on uninstall, and existing Android `android:allowBackup=false` remains intact.
