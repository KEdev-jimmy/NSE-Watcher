# NSE Watcher permanent APK releases

The rolling Android release uses one permanent signing identity, independent of PesaTrack. Never regenerate the key for an ordinary build, a failed workflow or a new development chat.

## Two downloads

After validation succeeds on a push to `main`, Android CI signs the APK from that same tested source commit and publishes a prerelease at tag `ci-apk` with exactly:

- `NSE-Watcher-Full.apk`: complete app for a first installation.
- `NSE-Watcher-Update.apk`: identical complete APK bytes for a compatible in-place update.

Both filenames remain fixed and both assets are replaced each time. Debug APKs remain internal workflow artifacts with 14-day retention. PR builds and manual validation runs do not replace rolling downloads. Source archives follow the original `ci-apk` tag; the release notes identify the exact source commit used for the current APK.

The workflow preserves the existing backend tests and Android `gradle build` validation. Only the publish job receives repository write permission. Publishers are serialized, and a publisher refuses a source commit that is already behind current `main`. Android instrumentation and physical-device acceptance are separate from CI.

## Permanent signing inputs

All four repository secrets must be present:

- `NSEWATCHER_RELEASE_KEYSTORE_BASE64`
- `NSEWATCHER_RELEASE_STORE_PASSWORD`
- `NSEWATCHER_RELEASE_KEY_ALIAS`
- `NSEWATCHER_RELEASE_KEY_PASSWORD`

The repository variable `NSEWATCHER_RELEASE_CERT_SHA256` holds the permanent certificate's public SHA-256 fingerprint. The publisher checks the signed APK against this pin before any release mutation. This value is public certificate metadata, not a password.

If any signing input or the pin is absent, publication stops. There is no unsigned/debug fallback and no automatic key generation. Partial Gradle signing configuration also fails. Existing application/provider credentials such as `NSE_SECRET` are unrelated and must be preserved.

Gradle consumes the temporary keystore path through `NSEWATCHER_RELEASE_KEYSTORE` and its three credential environment variables. Passwords must never be in source files, Gradle properties, shell arguments, chat, logs or APK resources. The runner materializes a protected temporary PKCS12 keystore and removes it after the publish job. Do not upload signing material as an artifact.

## Version and identity

- Application ID remains `ke.co.nsewatcher`.
- Android min SDK remains 26 and target/compile SDK remains 35.
- Rolling versionCode is `200000 + GITHUB_RUN_NUMBER`.
- Rolling versionName is `0.1.<GITHUB_RUN_NUMBER>`.
- Normal local builds retain versionCode 1/versionName 0.1.0 when overrides are absent.
- Explicit builds can supply `NSEWATCHER_VERSION_CODE` and `NSEWATCHER_VERSION_NAME` through Gradle properties or environment variables.

The versionCode must be positive and within Android's supported bound, and every new APK binary must increase it. The publisher downloads existing Full/Update assets and verifies their certificates, versions and bytes before replacing either asset. Downgrades and different bytes under an equal code are refused. An equal-code asset is accepted only when it is byte-identical to the candidate; it is preserved while missing/older partners or metadata are repaired after a partial publication. A future manually distributed APK with a higher version code must be considered before using the rolling channel; an installed device must never be downgraded.

## Local key and recovery copy

Provisioning uses a password-encrypted RSA-4096 PKCS12 key with approximately 30-year certificate validity. This Windows setup keeps signing material outside Git:

- Primary: `%LOCALAPPDATA%\NSEWatcherRelease\keys\nse-watcher-release.p12`.
- Recovery copy: `%LOCALAPPDATA%\NSEWatcherRelease\recovery\nse-watcher-release.p12`.
- Local recovery instructions: `RECOVERY.txt` beside the recovery copy.
- Alias: `nse-watcher-release`.

The local setup helper must verify matching encrypted bytes, password access to both copies, and private-key use from the backup before configuring the GitHub secrets. The two directories restrict access to the owning Windows account and SYSTEM. Password entry is masked locally, and the owner must separately save the password recovery information. The store and private-key passwords use the same setup password; it is not stored in recovery instructions.

The owner authorized C: recovery storage. Both copies on C: are vulnerable to loss of that drive. Keep a separate offline copy when media is available:

1. Copy the entire recovery folder to private offline media. Never commit, email, attach or upload its keystore into chat or release assets.
2. Compare the original and offline encrypted keystore checksums locally.
3. Open the offline copy on a trusted machine using interactive password entry. Confirm alias `nse-watcher-release` and that its public SHA-256 certificate fingerprint matches `NSEWATCHER_RELEASE_CERT_SHA256`.
4. Keep the password record separately, disconnect the backup media, and preserve both identity and recovery information when changing computers.

If a key or secret already exists, inspect its identity and recovery state before changing anything. Do not replace it with a new key or reuse PesaTrack's key. A runner's transient debug identity is not the permanent release identity.

## Safe retry and verification

For a job blocked before publication, restore the same signing inputs and retry the failed publish job from its original main-push run. GitHub asset replacement is not atomic: a network failure can leave a missing or mixed pair. On retry, every remaining signed asset is verified. An equal-version asset must exactly match the candidate bytes and is not uploaded again; only absent/older partners and final cleanup/notes are repaired. This does not permit a different APK under the same code. Use a newer successful main push for any new binary. An inconsistent old pair, certificate mismatch, corrupt archive, invalid package/version, stale source or failed GitHub operation stops publication for investigation.

Successful publication verifies APK ZIP integrity, alignment, signature, public certificate pin, application ID, SDK metadata and version progression. It uploads the two copies before removing old attachments, then verifies the final asset names. Synthetic publisher tests use invented APK archives and doubles for external Android/GitHub commands; they do not prove an Android installation works.

For release acceptance, download both assets and independently verify their bytes, signatures, fingerprint, application ID, versionCode/versionName and archive/alignment integrity. Report the checked source SHA and workflow run. A green CI result does not establish on-phone appearance, installed-app compatibility or data retention.

## Existing phone installations

An in-place Android update requires the same application ID, compatible signing identity and a non-downgrade versionCode. Filenames cannot make an incompatible APK update an existing app. A previous debug-signed install may have a different certificate.

Phone testing is manual unless separately authorized. Do not install, uninstall, reset, clear storage or discard watchlists, practice portfolios or settings as part of signing setup. If Android refuses the update because of signature/package incompatibility, stop and verify private backup/restore before any explicitly approved one-time migration. The release process does not provide silent background installation.
