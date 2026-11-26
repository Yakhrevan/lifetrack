# LifeTrack Phase 4B — Encrypted Backup and Upgrade-Safe Release

Parent: `feature/lifetrack-v12-phase4a-qa` (`576cb6415c64923ef3e0db7dd782c90034c1caee`).

Branch: `feature/lifetrack-v12-phase4b-release`. Do **not** merge into `main`.

## Portable encryption

- Manual full-backup export now prompts for a **12+ character passphrase**,
  encrypts the versioned JSON with a fresh random salt and nonce, and verifies
  the output by decrypting the saved file. File extension: `.ltb.json`.
- Format: AES-256-GCM with a 128-bit authentication tag; PBKDF2-HMAC-SHA256,
  210,000 iterations, random 16-byte salt and 12-byte GCM nonce per export.
  Header and KDF format are versioned; malformed or tampered files fail closed.
- Existing plaintext JSON backups are still accepted on import and validated
  before confirmation. This is **read compatibility**, not continued plaintext
  export. Previously exported plaintext files remain unencrypted until replaced.
- The Backup & Sync screen can remember a passphrase wrapped by an
  Android Keystore AES-GCM key **only on this device**. This enables unattended
  encrypted exports to a user-selected Google Drive/Files folder. It is not
  included in portable backups or sent to Google by LifeTrack. After uninstall,
  device replacement or loss of Keystore access, re-enter the original
  passphrase to restore; LifeTrack cannot recover it.
- On-device automatic snapshots remain local private app files and are not
  portable encrypted exports. `android:allowBackup=false` is preserved.
- If a Drive/Files folder was selected before Phase 4B, **automatic off-device
  plaintext export is now refused** until an encryption passphrase is set.

## Upgrade safety

- Application ID stays `com.yakhrevan.lifetrack`; `versionCode` increases
  4 -> 5 (`versionName` 1.2.1). Data preference file names and existing backups
  retain backward read compatibility.
- Android requires the **same signing identity** for a direct APK upgrade.
  CI debug APKs may have a different signing key from an existing installation:
  an update may be rejected. Never uninstall the old app to work around a
  signing error without a verified, off-device restorable backup.
- The optional signed-release workflow compares the output APK's certificate
  SHA-256 with the **expected existing APK fingerprint** before any release
  artifact is published. It does not generate, reveal, rotate or overwrite a key.

## Optional GitHub-signed release setup (no secrets in source)

Configure repository Actions secrets privately:

1. `LIFETRACK_UPLOAD_KEYSTORE_B64` — Base64 of your existing release/upload
   `.jks` keystore; do not paste it into chat or commit it.
2. `LIFETRACK_KEYSTORE_PASSWORD`
3. `LIFETRACK_KEY_ALIAS`
4. `LIFETRACK_KEY_PASSWORD`

Configure Actions variable `LIFETRACK_EXPECTED_SIGNER_SHA256` with the
64-character hex fingerprint from the **old installable APK**, obtained via
`apksigner verify --print-certs OLD.apk`. The workflow verifies the signer
before uploading APK/AAB. For Play App Signing, the Play signing key can differ
from the upload key; review the Play Console signing identities separately.

`.github/workflows/android-release.yml` is wired to this feature branch's
`push` trigger. With missing credentials/fingerprint it exits successfully
**without creating a signed artifact**. After configuring inputs, rerun that
workflow on the feature branch. `workflow_dispatch` is also declared but GitHub
only exposes manual dispatch after that workflow exists on the default branch;
**no default-branch change is authorized in this phase**.

## CI verification

- `testDebugUnitTest`: legacy backup parsing, encrypted round-trip, wrong
  passphrase, tampering, random nonces, upgrade and reminder regressions.
- API 35 instrumented tests: Android Keystore protection, app ID/versionCode,
  actual backup restore, notification intent targets, settings, widgets and UI.
- `lintDebug`, `assembleDebug`, APK artifact and device test reports.
- Optional release pipeline validates signing inputs and refuses unsigned
  publication; an actual signed build needs the owner's existing keystore.

## Physical Android phone tests — still pending

- [ ] Export a full backup, verify it can be decrypted/restored on a **second
      phone** with the passphrase; test incorrect password, file corruption,
      absent Drive provider and revoked folder access.
- [ ] Check a real old app's certificate identity and versionCode. Make an
      off-device recovery copy. Attempt in-place upgrade without uninstalling;
      compare tasks, expenses, notes, habits, study, calendar and history.
- [ ] Reboot, Doze, airplane mode, OEM battery saver and timezone-change
      notification delivery, snooze, quiet hours, lock screen sound/logo.
- [ ] Widget refresh, large font, dark mode, narrow screens and TalkBack.
- [ ] Release signing under the **real** owner's existing key and installation
      compatibility. CI debug APK is **not** a production-signed APK.

## Boundaries

- No Google OAuth, genuine bidirectional sync or Drive conflict resolution.
- No claim that an emulator simulates vendor battery optimizations, actual
  cloud restoration, or a release signed with the owner's key.
- No passphrase or signing key is committed, printed or sent to chat.
