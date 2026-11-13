# ProIPTV

Android IPTV player for phones, tablets and Android TV. It plays the streams of your own provider: Xtream Codes accounts, M3U playlists and XMLTV guides, with several accounts side by side, many source types (more are planned) (Xtream Codes, M3U, Stalker portal, HDHomeRun, Enigma2, TVHeadend and more), live TV, guide, movies, series, catch-up, favorites with manual sorting, recording and time shift. No content is included, no account with us, no ads, no analytics.

- **Application ID:** `com.kutlusoy.proiptv`
- **Version:** 0.0.1 (project skeleton, see below)
- **Min SDK:** 24 (Android 7.0)
- **License:** MIT

## State

Version 0.0.1 is the first runnable version. It works end to end and is tested with a fake provider server:

- several **Xtream Codes** and **M3U** accounts, passwords encrypted with the Android Keystore, account status (state, expiry, connections)
- streaming sync of channels, movies, series and the **XMLTV guide** (very large lists, no out of memory)
- **Program guide that keeps itself current**: refresh when the app opens, while it is open and in the background (WorkManager), only downloads when the guide changed, keeps the old guide if an update fails, and asks the provider for single channels without data
- **Live TV** with groups, search, now and next, zapping with the remote control, number keys, guide per channel and **catch-up**
- **Movies and series** with seasons, episodes and resume
- **Favorites** with several lists and **manual sorting**, kept after a provider refresh
- **stream hub**: one provider connection per channel for watching, **pause and rewind live TV** (disk ring buffer) and **recording** (foreground service, recordings list), connection limit of the account respected
- player on Media3 with audio and subtitle choice, picture size, automatic retry
- eleven languages (six complete, five partly translated, see Languages)

Not yet: scheduled recording for a later time, multiview, Stalker portal, HDHomeRun, Enigma2, TVHeadend, Jellyfin and the other sources. See the roadmap in [doc/guideline-product-plan.md](doc/guideline-product-plan.md) and the scope in [doc/guideline-feature-matrix.md](doc/guideline-feature-matrix.md). How to install and use it: [doc/guideline-user-guide.md](doc/guideline-user-guide.md).

## Build

Requirements: Android Studio (or just JDK 17 and the Android SDK 36). The Gradle wrapper is included.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

Debug builds have their own application ID, `com.kutlusoy.proiptv.debug`, and are installed next to the release app without touching it.

Debug builds are always signed with the same key, `app/debug.keystore`, which is part of the repository on purpose (password `android`, only for debug builds, it protects nothing). So a debug APK from GitHub Actions, one built on another computer and one run from Android Studio can update each other.

For a signed local release build, put a `signing/release.properties` file into `app/` with the keys `storeFile` (file name of the keystore inside `app/signing/`), `storePassword`, `keyAlias` and `keyPassword`. The `signing/` folder is ignored by git.

## GitHub Actions

| Workflow | Trigger | What it does |
|---|---|---|
| `CI` | every push, pull requests, manual | version file check, unit tests (parsers, fake provider server, app start), lint, debug APK as build artifact (`proiptv-debug-apk`), release build, version code check |
| `Release` | tag `v*` (for example `v0.0.1`) | checks the tag against `app/version.txt`, runs the tests, builds the signed APK and AAB, verifies the APK (application ID, version, certificate), publishes a GitHub release with the signing certificate (SHA-256 and SHA-1), the checksums, the Android versions and the source commit (tags with a suffix such as `-rc1` become pre-releases) |

## What you have to enter where

This is the short list. The full step by step guides are linked in each row.

| What | Where | Value | Guide |
|---|---|---|---|
| Release keystore | your computer, once, offline | `proiptv-release.jks` with alias `proiptv-release`, two backups | [release signing](doc/guideline-release-signing.md) §2 |
| Secret `PROIPTV_KEYSTORE_BASE64` | GitHub, repository Settings, Secrets and variables, Actions, "New repository secret" | the keystore file as one line of Base64 text | [release signing](doc/guideline-release-signing.md) §3 |
| Secret `PROIPTV_KEYSTORE_PASSWORD` | same place | the keystore password | same |
| Secret `PROIPTV_KEY_ALIAS` | same place | `proiptv-release` | same |
| Secret `PROIPTV_KEY_PASSWORD` | same place, optional | the key password, defaults to the keystore password | same |
| Secret `PROIPTV_RELEASE_CERT_SHA256` | same place, optional but recommended | the SHA-256 fingerprint printed by `keytool`, a release with another key then fails | same |
| Version of the app | `app/version.txt` | for example `0.0.1`, or `0.1.0-rc1` for a pre-release | [release signing](doc/guideline-release-signing.md) §4 |
| Package name and fingerprint at Google | Play Console, "Identity verification for Android developers" | `com.kutlusoy.proiptv` and the SHA-256 fingerprint | [developer verification](doc/guideline-android-developer-verification.md) |
| Proof snippet of that registration | `app/src/main/assets/adi-registration.properties` | the snippet of the console, nothing else | same |
| Google Play app and signing key | Play Console, create app, App signing | the same release key, exported with the PEPK tool | [Google Play](doc/guideline-google-play.md) |
| Logo and brand colors | `design/ProIPTV_icon.svg` | square SVG 1024 x 1024 | [logo and splash](doc/guideline-splash-and-icons.md) |

To release: change `app/version.txt`, commit, then push the matching tag.

```bash
git tag v0.0.1
git push origin v0.0.1
```

Or open Actions, workflow "Release", "Run workflow" and enter the version without the `v`.

The version of the app is in one file, **`app/version.txt`**. The Android version name is that text, the version code is derived from it (`major * 1,000,000 + minor * 1,000 + patch`, so `0.0.1` is 1, `0.1.0` is 1000 and `1.0.0` is 1000000). `tools/version.py` checks the file and the release workflow compares it with the tag.

## Languages

English (default), German, Turkish, French, Italian, Spanish, Portuguese, Dutch, Polish, Russian and Arabic (right to left). More languages follow, see [doc/guideline-feature-matrix.md](doc/guideline-feature-matrix.md) section 9. The texts are in `app/src/main/res/values/` (English) and `values-xx/`. A unit test checks that German, Turkish, French, Italian and Spanish have all texts and that every language keeps the same placeholders. Portuguese, Dutch, Polish, Russian and Arabic cover only the first screens so far (the rest falls back to English). All translations are drafts and are reviewed by native speakers before the first public beta.

To add a language: create `app/src/main/res/values-xx/strings.xml` with all keys of the English file and add the code to the language list of the settings screen.

## Documentation

| Document | Content |
|---|---|
| [guideline-product-plan.md](doc/guideline-product-plan.md) | product, architecture, engine, release plan, roadmap |
| [guideline-feature-matrix.md](doc/guideline-feature-matrix.md) | content types and features per version, market comparison |
| [guideline-user-guide.md](doc/guideline-user-guide.md) | install, add an account, remote control keys, favorites |
| [guideline-windows-version.md](doc/guideline-windows-version.md) | plan for a Windows version from the same code (libVLC) |
| [guideline-windows-signing-signpath.md](doc/guideline-windows-signing-signpath.md) | step by step: free SignPath Foundation certificate for the Windows installer |
| [guideline-windows-signing-azure.md](doc/guideline-windows-signing-azure.md) | alternative: Azure Artifact Signing (needs a registered company) |
| [PRIVACY.md](PRIVACY.md) | privacy statement |
| [guideline-release-signing.md](doc/guideline-release-signing.md) | keystore, GitHub secrets, running a release |
| [guideline-android-developer-verification.md](doc/guideline-android-developer-verification.md) | register package name and key with Google |
| [guideline-google-play.md](doc/guideline-google-play.md) | application ID, same key for GitHub and Play, store listing |
| [guideline-splash-and-icons.md](doc/guideline-splash-and-icons.md) | logo, splash and icons |

## License

MIT, see [LICENSE](LICENSE).
