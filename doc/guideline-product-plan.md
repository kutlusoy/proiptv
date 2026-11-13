# ProIPTV: Product and Architecture Plan

- **Version:** 0.3 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers and contributors who build and release ProIPTV
- **Reference implementation:** [`tvsig`](https://github.com/kutlusoy/tvsig) (`.github/workflows/`, `app/build.gradle`, `tools/`, `doc/guideline-*.md`), read-only reference for release engineering, signing, update, splash and continuous operation
- **See also:** `guideline-release-signing.md` and `guideline-android-developer-verification.md` of TVSig, which ProIPTV mirrors

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal and decisions

ProIPTV is a lean, fast, modern Android IPTV player for Android TV, TV boxes, tablets and phones. It plays the streams of the user's own IPTV providers. It ships, hosts, sells and recommends no content.

Decisions taken:

| Topic | Decision |
|---|---|
| License | MIT for everything, no closed parts |
| Accounting | several IPTV accounts can be created and used side by side. No subscriptions, no own accounts, no server, no billing |
| Sources | Xtream Codes first, then as many open source types as sensible through small source adapters (Stalker portal, HDHomeRun, Enigma2, TVHeadend, Jellyfin and more), see the feature matrix section 8. Nothing illegal, no content, no playlist discovery |
| Languages | English, German, Turkish, French, Italian, Spanish, Portuguese, Dutch, Polish, Russian, Arabic from the start, more later (feature matrix section 9) |
| Favorites | several favorite lists per account, manual sort by touch and remote control (feature matrix section 10) |
| Engine | Media3 based engine with a custom stream hub for parallel streams, recording and time shift (section 6) |
| UI | very modern, Compose, TV first, inspired by the best players on the market |
| Signing | one release key for GitHub and Google Play, one application ID `com.kutlusoy.proiptv` |
| Distribution | GitHub releases first, Google Play later with the same ID and the same key |
| Splash | animated start screen built like in TVSig, logo will be delivered by the maintainer |
| Size | lean: release APK under 20 MB, few dependencies, no analytics, no ads |
| Windows | planned after the engine is stable, same Kotlin code base with a second build target and its own player engine, the Android APK itself cannot run on Windows, see [`guideline-windows-version.md`](./guideline-windows-version.md) |

## 2. What TVSig gives us

ProIPTV follows the same conventions so one maintainer runs both projects the same way. Nothing in TVSig is changed.

| Topic | TVSig solution | ProIPTV |
|---|---|---|
| Application ID | `com.kutlusoy.tvsig`, debug `.debug` | `com.kutlusoy.proiptv`, debug `com.kutlusoy.proiptv.debug` |
| Version source | `app/version.txt`, `tools/version.py` derives the version code | same, `major * 1,000,000 + minor * 1,000 + patch` |
| Branches | one branch per version (`0.0.1` to `0.0.4`) | same, work starts on `0.0.1` |
| CI | `ci.yml`: version check, tests, lint, debug APK, release build, keep rules, version code check | same, plus detekt and benchmarks |
| Release | `release.yml` on tag `v*`: tag against `version.txt`, tests, signed APK and AAB, certificate check, APK facts, checksums, GitHub release | same |
| Secrets | `TVSIG_KEYSTORE_BASE64`, `_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`, `_RELEASE_CERT_SHA256` | `PROIPTV_*`, same names and meaning |
| Debug signing | fixed `app/debug.keystore` in the repository | same |
| Fingerprint | release page lists SHA-256 and SHA-1, `tools/release-notes.sh` | same script, adapted names |
| Developer verification | `adi-registration.properties` in assets, checked in the release | same mechanism with the ProIPTV key |
| In-app update | GitHub releases, SHA-256 against `SHA256SUMS.txt`, system installer, button only | same, see section 11.4 |
| Continuous operation | foreground service, watchdog, crash backoff, boot start | reused for TV mode, see section 6.8 |
| Splash | `SplashAnimationView`, `SplashTimeline`, `SplashParticles`, generated layers from an SVG (`design/`) | same technique, new logo |
| Languages | English, German, Turkish, unit test for all strings | same |
| Docs | `doc/guideline-*.md` with this header block | same |

ProIPTV MUST NOT reuse the TVSig key, keystore or application ID.

## 3. Market review and what we take from it

The sources are mostly comparison blogs with affiliate interests, so this is a summary of what they agree on, not a ranking. Claims MUST be verified before they are used in marketing text.

| App | Strength worth learning from | Weakness we address |
|---|---|---|
| TiviMate | best TV experience: fast EPG grid, channel groups, quick channel list while playing, catch-up, recording and multiview as premium features | TV only, closed source, key features paid |
| IPTV Smarters Pro | Xtream login flow, several users, VOD and series, runs on many platforms | dated UI, clone and trust problems |
| Kodi with PVR IPTV Simple Client | very powerful and extensible | heavy, technical setup, not focused |
| Televizo | light and stable on weak boxes | smaller feature set |
| Open source players (OwnTV, StreamVault and others) | streaming parsers, Media3, merged XMLTV | young, partial TV UX, few releases |

What we take: TiviMate level zapping and EPG, Smarters level Xtream login and VOD, Televizo level lightness, and what none of them gives free and open: parallel streams, recording and time shift in one lean engine, with several accounts.

References to read before building the parsers and the TV UI: [OwnTV](https://github.com/oerd/OwnTV) and [iptv-app](https://github.com/SERFF/iptv-app).

## 4. Scope

### 4.1 Must have for 1.0

- **Accounts:** any number of Xtream and M3U accounts, each with its own sync, status and connection limit (section 7)
- **Xtream Codes API:** `player_api.php` for login, live, VOD and series categories and streams, EPG short and full, catch-up (timeshift URLs), `get.php` M3U as fallback
- **Live TV:** groups, favorites, hidden channels, instant search, last channel, number zapping, now/next, full EPG grid
- **Engine features:** parallel streams, recording with scheduler, time shift (pause and rewind live), see section 6
- **VOD and series:** posters, details, seasons, resume position
- **Catch-up** from the EPG
- **Parental control:** PIN, locked groups
- **Offline cache** of lists and EPG, crash recovery, watchdog
- **Backup and restore** of accounts (credentials only with a password) and settings
- **Sources:** Xtream Codes, M3U, XMLTV and the further source types of the feature matrix section 8
- **Favorites:** several lists, manual sort (section 10 of the feature matrix)
- **Languages:** tier 1 of the feature matrix section 9 (11 languages, Arabic with right to left layout)

### 4.2 Later (1.x)

- Picture in picture, Chromecast, external player hand-off
- Phone setup with QR code (as in TVSig)
- Reminders, sleep timer
- Optional FFmpeg audio decoder extension (AC3, EAC3, DTS) where the device lacks them
- Tier 2 sources (Jellyfin and Emby, NextPVR, SAT>IP), see the feature matrix

### 4.3 Not planned

Any own server or account system, billing, ads, analytics, playlist discovery, bundled content, iOS and web.

## 5. Architecture

### 5.1 Stack (lean)

| Layer | Choice | Reason |
|---|---|---|
| Language | Kotlin, coroutines, Flow | as TVSig |
| UI | Jetpack Compose with Compose for TV on TV, Material 3 on phones, one adaptive code base | modern, focus handling built in |
| Engine | Media3 ExoPlayer plus own `engine` module | see section 6 |
| Database | Room with FTS | instant search, big lists |
| Network | OkHttp, kotlinx.serialization | no reflection, no keep-rule problems |
| DI | Koin or hand written container (no Hilt, no kapt) | fast builds, small APK |
| Background | WorkManager (sync), foreground service (playback, recording) | survives restarts |
| Settings | DataStore | typed |
| Secrets | Android Keystore, AES-GCM | account passwords |
| Build | Gradle, version catalog, R8 full mode, baseline profile | small and fast |
| SDK | min 24 (as TVSig), compile and target at the level Google Play requires at release time (check the current rule, plan 36) | store policy |

### 5.2 Modules

```text
app                  navigation, service, boot receiver, splash
core/model           data classes
core/database        Room entities, DAOs, migrations, FTS
core/network         OkHttp client, M3U and XMLTV streaming parsers
source/*             one small module per source type behind SourceProvider (xtream, m3u, stalker, hdhomerun, enigma2, tvheadend, ...)
core/account         accounts, keystore, connection budget
core/ui              design system, TV and phone components, focus helpers
engine               stream hub, ring buffer, recorder, player pool (section 6)
feature/live         channel list, overlay, zapping
feature/epg          grid, now/next
feature/vod          movies, series, resume
feature/recordings   schedule, library, timeshift controls
feature/settings     settings, accounts, backup, updates
benchmark            Macrobenchmark, baseline profile
```

Dependencies run one way, feature to core and engine, never feature to feature. The `engine` module has no Compose dependency and is tested with plain JVM tests and instrumented tests.

### 5.3 Data flow

1. A WorkManager job syncs an account: Xtream categories and streams or an M3U file, plus XMLTV or the Xtream EPG.
2. Streaming parsers write to Room in batches. They never hold a full list in memory.
3. The UI reads only from Room. The network is never in the path of drawing a screen.
4. A sync runs in one transaction. A failed sync leaves the old data in place. Removed channels stay for a grace period before they are deleted.

## 6. Engine: parallel streams, recording, time shift

This is the core of the product. The decisions below are the plan, the exact design is reviewed in `0.0.5` before code is written.

### 6.1 Why Media3 as base

Media3 ExoPlayer is the best fit on Android: hardware decoding and tunneling on TV, HLS, MPEG-TS and DASH, adaptive buffering, track selection, DRM, an actively maintained code base and a small footprint. libmpv, VLC or a pure FFmpeg player add 15 to 30 MB of native code, JNI risk and weaker TV integration, so they are not used. An optional FFmpeg decoder extension for audio codecs MAY be added later. The engine hides Media3 behind an interface.

### 6.2 One upstream connection per channel (stream hub)

Most providers allow only a few simultaneous connections (Xtream `max_connections`, often 1 to 3). The engine therefore fetches **each channel once** and fans the data out locally:

```text
provider --(1 connection)--> StreamHub(channel) --> ring buffer on disk
                                                     |-- viewer 1 (live edge)
                                                     |-- viewer 2 (time shifted)
                                                     '-- recorder (file)
```

Watching a channel while recording it, or watching it with time shift, costs one provider connection, not two or three.

### 6.3 Connection budget

The account module knows the connection limit of each account. A `ConnectionBudget` counts live hubs per account. Before a new hub opens, the budget decides: allowed, or blocked with a clear message that says which channel could be closed. Recordings have a priority over background previews. Hubs of different accounts do not share a budget.

### 6.4 Time shift

- Every live hub writes a ring buffer of the transport stream into app storage, size by minutes or megabytes (user setting, default 30 min, hard cap by free space)
- The player reads through a custom `DataSource` with an index of keyframes, so pause, rewind and jump to live work without a second provider connection
- The buffer is created when a channel is opened and removed when the last consumer leaves, unless a recording runs
- Disk writes go through a bounded queue, on a slow disk the buffer shrinks instead of the app stalling

### 6.5 Recording

- A recording is a hub consumer that copies the stream to a file. The file is the TS data, container remux (MP4 or MKV) is optional and runs after the recording
- Storage: app folder or a folder picked with the Storage Access Framework (USB disk on a box)
- Scheduler: exact alarms plus a foreground service. Start and end padding per recording, series rules in 1.x
- Recordings continue with the screen off and survive an app restart (the service reopens the hub and appends)
- Recording a channel never needs a decoder, so it is cheap on weak devices

### 6.6 Parallel playback (multiview)

- A `PlayerPool` holds a small number of players. The size comes from the device: `MediaCodecInfo` maximum instances for the codec, RAM class and a quick self test
- Only one tile has audio and the large decoder. Further tiles use the lowest quality variant where the provider offers one (HLS), otherwise they are limited by the decoder count
- On weak boxes the pool is one player, multiview is hidden instead of failing
- Layouts: 2, 3 and 4 tiles, focus moves between tiles with the D-pad

### 6.7 Fast zapping and failover

- One reused player, no recreation on a channel change, the hub of the previous channel stays open for a short time to make a quick back-and-forth instant
- Pre-connecting to a neighboring channel happens only when the connection budget has a free slot
- Stall detection (no progress), then reconnect, then failover to a channel with the same normalized name or `tvg-id`, then a message
- Buffer profiles: low latency, balanced, stable
- Per-account headers and user agent

### 6.8 Continuous operation on TV

As in TVSig: foreground service while playing or recording (`mediaPlayback` for playback, `specialUse` for recording because `dataSync` has runtime limits on current Android), start after boot and after an update (off by default on phones), crash recovery with doubling delay (3 s up to 2 min inside ten minutes), watchdog back to the player in launcher mode, memory trimming on request. The same device guidance (battery optimization, auto start, home app) is written for ProIPTV.

### 6.9 Performance targets

Measured on a reference low-end box (1 GB RAM, Android 9) and a mid-range device, enforced by Macrobenchmark where possible.

| Metric | Target |
|---|---|
| Cold start to the last channel playing | under 4 s on the low-end box |
| Channel switch | under 1 s typical, under 2 s worst case |
| Import of 100,000 channels | under 60 s, memory growth under 80 MB |
| Import of a 200 MB XMLTV | in the background, UI responsive, no out of memory |
| EPG grid | 60 fps target while scrolling |
| Watch, record and time shift the same channel | one provider connection |
| 72 hours continuous playback and recording | no crash, memory growth under 10% |
| Release APK | under 20 MB |

## 7. Accounts

Accounting means several IPTV accounts. There are no subscriptions of our own.

**Account record:** name, type (`xtream` or `m3u`), server URL, username, password (Keystore encrypted), optional user agent and headers, optional extra EPG URL, enabled flag, order, last sync, last error.

**Xtream status** (from `user_info` and `server_info`), shown on the account screen and refreshed on every sync: status, expiry date and days left (warning at 30, 7 and 1 days), trial flag, `max_connections`, `active_cons`, server time zone.

**Using several accounts:**

- Switch the active account with one key, or show all accounts merged in one channel list with the account as a tag
- Each account syncs on its own schedule and fails on its own, one broken account never blocks the others
- Each account has its own connection budget (section 6.3)
- Favorites and hidden channels belong to the account and survive a resync (matched by stable stream id)
- Duplicate channels across accounts are grouped for failover
- Export and import of accounts to a file, credentials only inside a password protected file

Passwords MUST NOT appear in logs, plain text backups, QR codes or crash reports.

## 8. UI and UX

- Modern, calm and dense: dark theme default on TV, large artwork, smooth focus animation, a clear channel overlay on top of the video (list left, info bottom), a fast EPG grid
- TV first with a fixed design size and safe margins (as TVSig), adaptive layouts for tablets and phones
- Everything works with D-pad, OK, back and colored keys: up and down zap, number entry, last channel, long OK for the list, channel info on OK
- First start in three steps: language, add an account (typing or QR from the phone), pick groups. Playing within one minute
- Recording and time shift controls are part of the player bar, not buried in menus
- Clear errors in plain words with the next step
- Accessibility: large text, high contrast, TalkBack labels
- Design inspiration: TiviMate (grid and overlay), Plex and Netflix style rows for VOD, Material You for phones. A design review with screenshots happens in `0.0.3` before the screens are built

### 8.1 Splash screen

Built like TVSig: a short intro drawn by the app itself with a timeline and particle layer, finished layers prepared at build time from an SVG by a script, still image when system animations are off, any key skips it. The logo is delivered by the maintainer. Until then `0.0.1` ships a neutral placeholder. Launcher icons, TV banner and store icon are generated from the same SVG (`design/` scripts as in TVSig).

## 9. Security, privacy, legal

- **No content, no recommendations.** README and store text say that the user supplies the source. No default playlists.
- **Store policy.** Google Play removes IPTV apps that ship or promote infringing content. Listing, screenshots and demo material MUST use only legal content. A contact for takedown requests and a terms page are required.
- **Privacy.** No analytics, no ads. Crash reports are a local file the user may export. A privacy policy page is required for Play (Data safety form).
- **Network.** HTTPS preferred. Many providers use HTTP, so cleartext is allowed per account after a visible warning. Certificate errors are never ignored silently.
- **Secrets.** Keystore backed encryption, backups exclude credentials unless protected by a password.
- **Supply chain.** Pinned dependency versions, Dependabot, pinned GitHub Actions, checksum and certificate verification in the release.
- **License.** MIT, third party notices generated in the build.

## 10. Testing and quality gates

| Gate | Where |
|---|---|
| Parser tests: M3U edge cases, XMLTV with broken encodings, Xtream payloads, catch-up URL builders | every push |
| Engine tests: hub fan-out, ring buffer, seek index, connection budget, reconnect and failover with a fake provider server | every push |
| String check: every text in en, de, tr with equal placeholders | every push |
| Lint, detekt, migration tests | every push |
| Synthetic fixtures: 100,000 channel list, 200 MB EPG | benchmark module |
| Macrobenchmark and baseline profile | before each minor release |
| Instrumented tests on TV and phone emulator for navigation and focus | nightly |
| 72 hour soak test with record and time shift on a real box | before each minor release |

A release MUST NOT be tagged while CI is red.

## 11. Build, versioning, signing, release

### 11.1 Version and branches

`app/version.txt` holds the version (`0.0.1`, or `0.1.0-rc1` for a pre-release). `tools/version.py` and `app/build.gradle` derive the version code as in TVSig and the code MUST grow with every release. One branch per version, named exactly like the version. A release is the tag `v<version>`. Tags with a suffix are pre-releases and ignored by the in-app update check.

### 11.2 Workflows

| Workflow | Trigger | What it does |
|---|---|---|
| `CI` | every push, pull requests, manual | version check, unit tests, lint, detekt, debug APK as artifact, release build and keep rule check, version code check |
| `Release` | tag `v*` or manual with the version | tag against `app/version.txt`, tests, signed APK and AAB, certificate check against the expected fingerprint, APK facts (application ID, version name and code), `SHA256SUMS.txt`, R8 mapping, GitHub release with notes |

Build scripts are copied and adapted from TVSig: `tools/version.py`, `tools/release-notes.sh`, `tools/check-keep-rules.py` (only if reflection remains).

### 11.3 One signing key for GitHub and Google Play

The same private key signs the GitHub APK and, through Play App Signing, the Play version. Android then accepts an update from either channel over the other and users can switch.

Steps (the maintainer does the key work offline, the workflows only use the secrets):

1. Create the release keystore offline: `keytool -genkeypair -v -keystore proiptv-release.jks -alias proiptv-release -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 50040`. Two offline backups. If the key is lost, no update can be signed any more.
2. Print the certificate fingerprint (`keytool -list -v ...`), note the SHA-256 and set it as `PROIPTV_RELEASE_CERT_SHA256`.
3. Set the repository secrets:

| Secret | Required | Content |
|---|---|---|
| `PROIPTV_KEYSTORE_BASE64` | yes | keystore as Base64 text |
| `PROIPTV_KEYSTORE_PASSWORD` | yes | keystore password |
| `PROIPTV_KEY_ALIAS` | yes | `proiptv-release` |
| `PROIPTV_KEY_PASSWORD` | no | defaults to the keystore password |
| `PROIPTV_RELEASE_CERT_SHA256` | no, recommended | expected fingerprint |

4. Publish the first release on GitHub (`v0.0.1-rc1`). The release page shows the fingerprint, which MUST match the noted one.
5. Register the package name and the key with Google (section 11.5).
6. When Play is opened: in the Play Console, enrol the app in Play App Signing with **this key** (export it with Google's PEPK tool and upload it when the first release is created). Do not let Play generate a new key, that would give the two channels different certificates.

Debug builds use the committed `app/debug.keystore` and the ID `com.kutlusoy.proiptv.debug`, never the release key.

Keystore, passwords and the exported key MUST never be committed or pasted anywhere except the offline backups, the secrets and the Play Console upload.

### 11.4 In-app update and the Play channel

The in-app update (GitHub API, SHA-256 check, system installer, button only) is the same as in TVSig. Google Play does not allow an app to install updates from outside the store. Therefore:

- Until Play is opened there is one build with the update.
- When Play is opened, the update code and the permission `REQUEST_INSTALL_PACKAGES` move into a `direct` flavor. A `play` flavor without them is uploaded to Play. Both flavors have the same application ID and the same key.

### 11.5 Application ID and Google registration

| Item | Plan |
|---|---|
| Application ID | `com.kutlusoy.proiptv`, permanent once published to Play |
| Android developer verification | register the package name `com.kutlusoy.proiptv` with the SHA-256 fingerprint of the release certificate in the Play Console (a personal account is enough, Play distribution is not required), put the console's snippet in `app/src/main/assets/adi-registration.properties`, the release workflow checks it as in TVSig, upload the APK of a release that says "included" as proof |
| Play Console app | create the app with the package name, enrol in Play App Signing with the existing key (11.3), internal testing track first |
| Policy forms | Data safety, content rating, target audience, ads declaration (none), privacy policy URL |
| TV listing | Leanback launcher, `leanback` and `touchscreen` declared as not required, banner 320 x 180, TV screenshots, generated from the logo SVG |
| Listing text | English, German, Turkish, states clearly that no content is provided |

### 11.6 Release procedure

1. Work on the version branch, CI green.
2. Set `app/version.txt`, merge to `main`.
3. Push the tag `v<version>` (or run the workflow manually).
4. Compare the fingerprint on the release page with the noted one.
5. Test the update from the previous release on a real device.

## 12. Roadmap

**Status:** version 0.0.1 as built (including the self-refreshing guide) contains the scope of the rows 0.0.1 to 0.0.4 (accounts, Xtream and M3U sync, live list, favorites with manual sort, zapping, EPG now and next and program list) and the parts of 0.0.8 for VOD, series, resume and catch-up. The rows for the engine (0.0.5 to 0.0.7: stream hub, time shift, recording, multiview), the further sources (0.0.9) and the remaining parts of 0.0.8 (PIN, backup) are still open. The EPG grid view is not built yet, the guide is a program list per channel. The app is a single Gradle module with packages for data, net, parse, sync and ui, the split into modules from section 5.2 follows when the engine is added.

| Version | Content | Exit criterion |
|---|---|---|
| **0.0.1** | Skeleton: Gradle project, `version.txt`, `tools/`, CI and Release workflows, debug keystore, string scaffold in three languages, placeholder splash, empty adaptive Compose shell | CI green, `v0.0.1-rc1` published with fingerprint on the page |
| 0.0.2 | Room schema, accounts (add, edit, delete, several), Keystore, Xtream login with status screen, streaming Xtream and M3U sync | two accounts sync, expiry and limits shown, no secret in logs |
| 0.0.3 | UI design review, live channel list, groups, favorites, search, Media3 playback, fast zapping | channel switch target met on the low-end box |
| 0.0.4 | EPG import and grid, now/next, mapping rules, timezone tests | 200 MB EPG in the background, scroll target met |
| 0.0.5 | Engine: stream hub, ring buffer, time shift, connection budget | watch plus time shift uses one provider connection (fake server test) |
| 0.0.6 | Recording, scheduler, foreground service, SAF storage | scheduled recording survives an app restart |
| 0.0.7 | Player pool and multiview, failover, stall detection, continuous operation | 72 hour soak test green |
| 0.0.8 | VOD, series, catch-up, resume, parental PIN, backup and restore | catch-up URL tests for common Xtream setups |
| 0.0.9 | More sources: Stalker portal, HDHomeRun, Enigma2, TVHeadend | each adapter passes its fixture tests, a stalled adapter never blocks the others |
| 0.1.0-rc1 | Feature freeze, in-app update, developer verification registered, real splash with the final logo | update from the previous release verified on a device |
| 0.1.x | Play channel (flavors, Play App Signing, internal track), PiP, polish | internal track install and update tested |
| 1.0.0 | Stability, docs, store listing | all gates of section 10 green, 30 days without a blocking issue in beta |

`0.0.1` stays first so every later step ships through the real release path.

## 13. Risks

| Risk | Mitigation |
|---|---|
| Store removal because of IPTV misuse | no content, clean listing, takedown contact, legal test streams only |
| Xtream servers differ in behavior and formats | tolerant parsing, recorded fixtures, per-account quirks |
| Weak boxes: out of memory or too few decoders | streaming parsers, pool sized by device, features hidden instead of failing |
| Providers with one connection | hub fan-out and the budget make the limit visible and prevent surprises |
| Disk wear and slow USB disks (time shift and recording) | bounded queue, shrinking buffer, size caps, warning on slow storage |
| Foreground service limits on new Android versions | service types as in 6.8, tested on the newest API level |
| Key loss | two offline backups, fingerprint recorded |
| Wrong EPG mapping | layered matching, manual override |

## 14. Open questions

1. Logo and brand colors for the splash and icons (delivered by the maintainer).
2. Koin or a hand written container for dependency injection (default: Koin).
3. Is a time shift buffer on internal storage acceptable on boxes with little space, or is a USB disk required above a size?
4. Documentation language: this plan is English like TVSig, confirm English for the whole repository.

## 15. Checklist

- [ ] Open questions answered
- [ ] Branch `0.0.1` skeleton built (Gradle, `version.txt`, `tools/`, workflows)
- [ ] Release keystore created offline, two backups, fingerprint noted
- [ ] Repository secrets `PROIPTV_*` set
- [ ] `v0.0.1-rc1` published, fingerprint compared
- [ ] Package name `com.kutlusoy.proiptv` registered with the fingerprint
- [ ] Logo delivered, splash and icons generated
- [ ] Privacy policy and terms pages online before the Play listing
- [ ] Legal test streams prepared for fixtures and screenshots
