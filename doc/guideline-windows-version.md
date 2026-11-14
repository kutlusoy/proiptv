# ProIPTV: Windows version

## Current state (first Windows version)

Every build of the repository now ships a Windows installer: CI (artifact), **Test release** (pre-release `test-<number>`) and **Release** (same GitHub release as the Android files). The setup is a normal installer (Inno Setup, no portable version) with start menu entry, optional desktop icon, uninstaller and update. It is **not signed yet**, so Windows SmartScreen may warn ("More info", "Run anyway"). Signing follows later (see section 4).

What the app can do on Windows today:

- several Xtream Codes and M3U accounts (passwords protected with Windows DPAPI, config in `%APPDATA%\ProIPTV\config.json`)
- live channels, movies and series with groups, search, favorites (one list per area), country and year badges
- series with seasons and episodes
- playback with libVLC (LGPL, bundled as separate, unmodified DLLs). The video is drawn by libVLC into memory (video callbacks) and shown as an image inside the app window, so it never opens a window of its own and needs no window handle (also the base for macOS and Linux). A log file with start and player messages is `%APPDATA%\\ProIPTV\\log.txt`: stop, pause, seek, volume, full screen (F or double click), channel up/down while watching live, keyboard shortcuts (space, arrows, Esc, F, M)
- a resizable window (starts at 1280 x 800 or at 90 percent of a smaller screen, smallest size 640 x 420, so it also fits a 13 inch laptop screen) with a wide layout: menu column on the left, content on the right; the group box, search field and dialogs follow the width
- 11 languages, taken from the same `strings.xml` files as the Android app
- update check by hand and once a day in the background (one notice per day at most), downloads the newest setup, checks the SHA-256 against `SHA256SUMS.txt` and starts the installer silently, which closes the app, installs and starts the new version. Test builds follow the newest test release, normal builds the newest release.

- program guide: the XMLTV guide of each account (Xtream, M3U header or the entered guide address) is loaded after the lists and every 6 hours, shows what runs now in the channel list, the live search also finds running shows, and the **EPG** screen lists the shows by group, search and time range; a click starts the channel
- player: a menu button with the list of channels that the player was started from (the channel keeps playing), channel up and down
- accounts with the same information as on Android (status, expiry date and days left, connections, counts, last sync, guide update, errors) in collapsible cards, and buttons to refresh the lists and the guide by hand (also in the settings)

Not in the Windows version yet (the Android app has them): recording, time shift and the stream hub, downloads, audio and subtitle track menu, several favorites lists, watched marks and resume, TV style navigation. They need the shared engine (section 5) and come step by step.

How it is built (differs from the plan below): the desktop app is the Gradle module `desktop`. Instead of a full Kotlin Multiplatform refactor the module compiles the Android-free source files of the app directly (`parse`, Xtream client, stream URLs, update logic, see `syncShared` in `desktop/build.gradle`), so there is one copy of them. The installer is built with Inno Setup instead of an MSI, because it can update silently and runs without administrator rights (per user installation). libVLC 3.0.21 is downloaded in the build, checked against a pinned SHA-256 and reduced to the plugins in `desktop/vlc/plugins.txt` (GPL only plugins are left out, the build fails if a listed plugin is missing).

Not verified yet: I could build and test the app only on Linux. The Windows installer, the libVLC start and the video output have to be tried on a real Windows machine with the first test release.

- **Version:** 0.3 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers who decide on and build a Windows release
- **Reference implementation:** [`guideline-product-plan.md`](./guideline-product-plan.md) sections 5, 6 and 11, `.github/workflows/release.yml`
- **See also:** [`guideline-release-signing.md`](./guideline-release-signing.md), [`guideline-windows-signing-signpath.md`](./guideline-windows-signing-signpath.md), [`guideline-windows-signing-azure.md`](./guideline-windows-signing-azure.md)

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Short answer

Yes, a Windows version from the same code base is possible, with limits:

- The **Android APK does not run on Windows**. Microsoft ended the Windows Subsystem for Android in March 2025, so there is no way to ship the same binary.
- What can be shared is the **Kotlin source code**: data model, database, Xtream and M3U clients, parsers, guide sync, accounts, favorites, and most of the user interface, through Kotlin Multiplatform and Compose Multiplatform for Desktop.
- What differs per platform is the **video engine**, the secret storage, background work and the installer. Media3 (the Android player) does not exist on Windows.
- One tag (`v0.3.0`) can build **both** the Android files and the Windows installer in the same GitHub release, with one version number from `app/version.txt`.

It is a real project, not a switch. It SHOULD start after the Android engine (stream hub, time shift, recording) is stable, because the engine is designed to be shared.

## 2. What is shared and what is not

| Part | Shared | Windows solution |
|---|---|---|
| Data classes, favorites logic, sort keys, guide policy | yes | none needed |
| Database (Room) | yes, Room supports desktop with a bundled SQLite driver | add the driver |
| Network (OkHttp), JSON (kotlinx.serialization) | yes, both run on the JVM | none |
| M3U parser | yes | none |
| XMLTV parser | mostly | Android's XML parser does not exist on the desktop JVM, use an included one (kxml2 or xmlutil) |
| Sync, accounts, guide freshness | yes | replace WorkManager by an in-process timer, optional tray icon |
| Passwords | no | Android Keystore is replaced by Windows DPAPI or Credential Manager through JNA |
| Screens (Compose) | mostly | adapt for mouse and keyboard, window resizing, keyboard shortcuts |
| Texts in 11 languages | yes | move `strings.xml` to Compose Multiplatform resources |
| Image loading (Coil 3), paging | yes, both are multiplatform | none |
| Player | no | see section 3 |
| Per-app language | no | own locale handling in the desktop app |
| Installer and updater | no | see section 4 |

## 3. Player on Windows

Media3 is Android only. The desktop needs another engine behind the same `PlaybackEngine` interface.

| Option | License note | Remarks |
|---|---|---|
| libVLC | LGPL 2.1 or later | many formats, good with IPTV. The popular Java binding `vlcj` is GPL 3, which does **not** fit an MIT project. A thin own binding (JNA) to libVLC is needed. |
| libmpv | LGPL when built that way (many builds are GPL) | very good quality and low latency, a suitable LGPL build MUST be verified. |
| JavaFX Media | GPL with classpath exception | too few codecs, weak for transport streams and HLS. Not recommended. |

**Decision: libVLC.** It is bundled with the installer, and the Windows player talks to it through a thin binding of our own (JNA calls to `libvlc`: create instance, open a location, play, pause, seek, audio and subtitle tracks, video output into a window handle).

License rules for libVLC (they MUST be followed and checked again for every VLC version that is bundled):

- `libvlc` and `libvlccore` are LGPL 2.1 or later, so they fit an MIT project when they are shipped as separate, unmodified DLL files that the user can replace. They MUST NOT be linked into the application executable.
- The license text and a source offer or link of the VLC version used MUST be part of the installer and of the repository notices.
- VLC's plugins have different licenses, a few are GPL. Only the plugins that are needed (demuxers for MPEG-TS, HLS and MP4, decoders, audio and video output) are shipped, and the license of each one is checked. GPL only plugins are left out.
- The binding MUST NOT use `vlcj` (GPL 3).

**One engine for both platforms.** The stream hub from the product plan (one provider connection per channel, ring buffer for time shift, recorder) is plain Kotlin and runs on Android and on the desktop. The hub serves each channel on a local address (`http://127.0.0.1:<port>/...`). The Windows player opens that address like any other stream, so time shift, recording and the connection budget work the same on both platforms without special code in the player.

## 4. Build, signing and release on GitHub

The release workflow gets a second job on `windows-latest`:

1. Build the desktop app with the Gradle task of Compose Multiplatform (`packageMsi`, which uses `jpackage`) with a bundled runtime, so users need no Java.
2. Name the file `ProIPTV-<version>-windows-x64.msi` and add it to the same `SHA256SUMS.txt`.
3. Sign the installer and the executable.
4. Publish both Android and Windows files in the same GitHub release. The release page lists the certificate fingerprint of the Windows signature next to the Android one.

**Code signing.** Without a signature Windows SmartScreen warns every user. Options, from cheapest:

| Route | Cost | Remark |
|---|---|---|
| No signature | free | SmartScreen warning "unknown publisher", acceptable only for tests |
| **SignPath Foundation (chosen)** | free for open source projects | no key file, signing runs at SignPath and is started by the GitHub workflow. The publisher shown by Windows is "SignPath Foundation". Needs a public repository, an earlier release and a manual approval per release. Step by step: [`guideline-windows-signing-signpath.md`](./guideline-windows-signing-signpath.md) |
| Azure Artifact Signing (alternative) | about 10 USD per month, needs a paid Azure subscription and a **registered organization** in a supported country (individuals only in the USA and Canada) | the publisher is your organization. Step by step: [`guideline-windows-signing-azure.md`](./guideline-windows-signing-azure.md) |
| Authenticode certificate (OV or EV) | yearly fee | EV removes the warning fastest |

With SignPath the signing happens at SignPath, so no certificate file is stored on GitHub. The names of the secret and the variables to enter are listed in [`guideline-windows-signing-signpath.md`](./guideline-windows-signing-signpath.md) section 6 (Azure: [`guideline-windows-signing-azure.md`](./guideline-windows-signing-azure.md) section 4). The secrets `PROIPTV_WIN_CERT_*` are only needed if a certificate file from another route is used instead.

The certificate is renewed by the signing service, so the release check compares the signer name ("SignPath Foundation") and shows the SHA-256 of the signed installer, not a fixed certificate fingerprint.

**Order of events with SignPath:** the first Windows installer is published unsigned (SmartScreen warns), then the project applies to SignPath Foundation, and later releases are signed. The application needs an earlier release.

The Windows signing identity MUST NOT be mixed with the Android release key.

**Updates.** The same method as the Android direct build: the app asks the GitHub API for the newest release, downloads the MSI, checks the SHA-256 against `SHA256SUMS.txt`, and starts the installer after a button press. Later channels: winget (a manifest that points to the GitHub release) and the Microsoft Store (needs its own packaging as MSIX and a store account).

## 5. Changes needed in the code

1. Split the Gradle project into `shared` (Kotlin Multiplatform with an Android and a desktop target), `androidApp` and `desktopApp`. The Android app MUST behave exactly as before.
2. Move Room to the multiplatform setup with the bundled SQLite driver. The migrations stay.
3. Remove Android classes from shared code: `android.util.Base64` in the guide parser, `SecretBox`, `DateUtils`, `AppCompatDelegate`. Each gets an interface with an Android and a desktop implementation.
4. Introduce `PlaybackEngine` and `SecretStore` interfaces. Today the player screen talks to Media3 directly, this moves behind the interface.
5. Move strings to Compose Multiplatform resources. The unit test for placeholders keeps working on the new location.
6. Add keyboard and mouse handling (arrow keys, Enter, Escape, Page Up and Down, number keys, mouse wheel, double click for full screen).
7. Tests: shared logic runs as plain JVM tests (faster than Robolectric), the Android specific tests stay.

## 6. Effort and order

| Step | Content | Result |
|---|---|---|
| 1 | Finish the Android engine (0.0.5 to 0.0.7) | stable hub, time shift, recording |
| 2 | Multiplatform refactor (steps 1 to 5 of section 5), Android unchanged | same Android release, shared module |
| 3 | Desktop shell, player behind the interface, keyboard and mouse | runnable Windows app from the IDE |
| 4 | Installer, signing, release job, updater | first Windows pre-release (tag with `-beta1`) |
| 5 | Polish, winget, optional Store | Windows part of 1.0 |

The same desktop target also builds for Linux and macOS with little extra work (`.deb`, `.dmg`), they MAY be added after Windows is stable. ARM64 for Windows is possible once the player library offers it.

## 7. Risks

| Risk | Mitigation |
|---|---|
| License conflict through a GPL binding | own LGPL based binding, license check before the first release |
| Large installer (runtime and player library) | accept about 100 to 200 MB, or ship only the needed libraries |
| SmartScreen warnings without signature | signing route chosen before the first public beta |
| Two platforms double the test effort | shared logic tested once on the JVM, small manual test list per release |
| Refactor breaks the Android app | step 2 changes no behavior, all existing tests MUST stay green |

## 8. Checklist

- [ ] Android engine finished (0.0.7)
- [ ] libVLC version chosen, license of every bundled plugin checked, notices added
- [x] Windows installer is built and attached to every CI run, test release and release (unsigned)
- [ ] First unsigned Windows release published, then SignPath Foundation set up (see the SignPath guide checklist)
- [ ] Multiplatform refactor merged, Android release unchanged
- [x] Windows installer builds in the release workflow
- [ ] First Windows pre-release tested on a clean Windows machine
- [ ] In-app update tested from one Windows release to the next

## 9. Decisions and open questions

Decided: the player library is libVLC, the signing route is the free SignPath Foundation certificate (Azure only as an alternative if a registered company exists).

Open:

1. Does SignPath Foundation accept the project (one maintainer, application after the first unsigned release)?
2. Should Linux and macOS be built from the same desktop target after Windows?

## 10. macOS and Linux

The Windows app is Compose for Desktop on the JVM, so the same module can run on macOS and Linux. What is missing is not the app but the platform parts:

| Part | Linux | macOS |
|---|---|---|
| App code (accounts, lists, sync, screens, update logic) | runs as is | runs as is |
| Video | libVLC with the X11 window handle works with the current code (`set_xwindow`). Wayland needs the XWayland path or an own render path. The `libvlc` package of the distribution or a bundled copy | libVLC needs the native view handle of a window (`set_nsobject`). A Swing canvas does not give it easily, so the video would be rendered into a Compose surface through libVLC video callbacks. More work and slower |
| Password storage | replace Windows DPAPI by the secret service (libsecret through JNA) | replace by the Keychain through JNA |
| Installer | `.deb` and AppImage with `jpackage`, built on `ubuntu-latest` | `.dmg` with `jpackage`, built on `macos-latest` |
| Signature | none needed for `.deb` and AppImage | **Apple Developer account (99 USD a year)** for signing and notarization, otherwise Gatekeeper blocks the app (workaround: right click, Open) |
| Update | the same download and checksum logic, then package manager or AppImage replacement | the same, then replace the app in `/Applications` |

Order: Linux first (small steps: file naming, secret service, `.deb` and AppImage jobs, libVLC from the system), then macOS when a Mac is available for tests, because the video path and the notarization cannot be checked on a build server alone. Until then macOS and Linux are not supported, even though `./gradlew :desktop:run` may already start on them for development.
