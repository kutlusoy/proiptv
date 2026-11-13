# ProIPTV: Product and Architecture Plan

- **Version:** 0.1 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers and contributors who build and release ProIPTV
- **Reference implementation:** [`tvsig`](https://github.com/kutlusoy/tvsig) (`.github/workflows/`, `app/build.gradle`, `tools/`, `doc/guideline-*.md`), treated as read-only reference for release engineering, signing, update and continuous operation
- **See also:** `guideline-release-signing.md` and `guideline-android-developer-verification.md` of TVSig, which this plan mirrors for ProIPTV

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal and scope

ProIPTV is an Android IPTV player for Android TV, TV boxes, tablets and phones. It plays the streams of the user's own IPTV provider. It does not ship, host, sell or recommend any content.

The product promise is: **fast, stable, private, and clear about the state of the user's account.**

| In scope | Out of scope |
|---|---|
| M3U/M3U8, Xtream Codes API, XMLTV EPG | Selling or bundling channels, playlists or subscriptions |
| Live TV, catch-up, VOD, series | A built-in content store or playlist discovery |
| Account management of provider logins and local profiles | Circumventing DRM, geo-blocks or provider limits |
| Recording and time shift where the provider allows it | iOS, tvOS, web (not before 1.0, see section 15) |

## 2. What TVSig gives us (reference review)

TVSig is the proven blueprint for everything around the code. ProIPTV MUST follow the same conventions so one maintainer can run both projects the same way. Nothing in TVSig is changed by this plan.

| Topic | TVSig solution | ProIPTV decision |
|---|---|---|
| Application ID | `com.kutlusoy.tvsig`, debug `com.kutlusoy.tvsig.debug` | `com.kutlusoy.proiptv`, debug `com.kutlusoy.proiptv.debug` |
| Version source | one file `app/version.txt`, `tools/version.py` derives the version code | same, `major * 1,000,000 + minor * 1,000 + patch` |
| Branches | one branch per version (`0.0.1` to `0.0.4`) | same, this plan starts on branch `0.0.1` |
| CI | `ci.yml`: version check, unit tests, lint, debug APK, release build, keep rules, version code check | same, plus detekt/ktlint and parser benchmarks |
| Release | `release.yml` on tag `v*`: tag against `version.txt`, tests, signed APK and AAB, certificate check, checksums, GitHub release | same, plus optional Play upload job |
| Signing secrets | `TVSIG_KEYSTORE_BASE64`, `..._PASSWORD`, `..._KEY_ALIAS`, `..._KEY_PASSWORD`, `..._RELEASE_CERT_SHA256` | `PROIPTV_*` with the same names and semantics |
| Debug signing | fixed `app/debug.keystore` in the repository, debug ID suffix | same, so CI, local and Studio builds update each other |
| Fingerprint | release page lists SHA-256 and SHA-1 of the certificate, `tools/release-notes.sh` | same script, adapted names |
| Developer verification | `adi-registration.properties` in assets, checked by the release workflow | register `com.kutlusoy.proiptv` with its own release key, same proof file mechanism |
| In-app update | GitHub releases, SHA-256 check against `SHA256SUMS.txt`, system installer | only in the `direct` flavor, see section 12.4 |
| Continuous operation | foreground service, watchdog, crash backoff, boot start | reuse the ideas for the TV launcher mode, see section 6.6 |
| Gson keep rules | `proguard-rules.pro` plus `tools/check-keep-rules.py` | avoid by using kotlinx.serialization, keep the script idea for any reflection left |
| Languages | English, German, Turkish, unit test checks all strings and placeholders | same three languages from day one |
| Docs | `doc/guideline-*.md` with the header block above | same |

ProIPTV MUST NOT reuse the TVSig key, keystore or application ID. It needs its own release key and its own registration.

## 3. Market review

Sources are mostly comparison blogs with affiliate interests, so the table lists what they agree on and is not a ranking. Details MUST be verified against each app's store page before they are used in marketing text.

| App | Strength | Weakness we can address |
|---|---|---|
| TiviMate | Best TV experience on Android TV and Fire TV, fast EPG grid, multiple playlists, favorites groups, catch-up, recording and multiview in Premium | TV only (poor on touch), closed source, key features behind subscription, no real account overview |
| IPTV Smarters Pro | One login on many platforms, Xtream login, VOD and series, parental control, external players | Dated UI, ad-like branding for white-label copies, mixed trust in clones |
| Kodi with PVR IPTV Simple Client | Very powerful, add-on ecosystem, M3U plus XMLTV | A media center, not a focused IPTV app, heavy, setup is technical |
| Televizo | Lightweight, solid on low-end boxes | Smaller feature set, varying pricing between sources |
| Jellyfin, Plex | Self-hosted live TV and DVR, central server | Needs a server, EPG depends on plugins, not for direct provider logins |
| IPTVnator, OwnTV, StreamVault and similar open source players | Open code, Media3 or libmpv engines, XMLTV merge, streaming parsers | Mostly young, partial TV UX, no release discipline |

Useful open source references to read before designing the parsers and the TV UI: [OwnTV](https://github.com/oerd/OwnTV) (dual engine, streaming M3U and Xtream JSON parsing, merged XMLTV feeds) and [iptv-app](https://github.com/SERFF/iptv-app).

## 4. What makes an IPTV player valuable

Distilled from the review, in order of user impact:

1. **Zapping speed.** A channel switch under one second on a weak box. This is the first thing users judge.
2. **A fast, correct EPG.** Grid scrolls without jank with 50,000 channels, timezone and daylight saving are right, now/next is always visible.
3. **Stability around the clock.** No crash after days of running, recovery without user action, no memory growth.
4. **Big playlists without pain.** 100,000+ entries load in the background, search is instant, the UI never blocks on a sync.
5. **Great remote control UX.** Everything reachable with D-pad, number zapping, last channel, channel list overlay while playing.
6. **Provider independence.** Several providers, several logins, failover between duplicate channels.
7. **Honest account information.** Expiry date, connection limit, active connections and status are always visible (the gap in most apps, see section 7).
8. **Privacy and trust.** No account with us, no tracking, no ads, readable permissions, signed and verifiable releases.
9. **Same app everywhere.** TV, tablet and phone share one code base with an adaptive UI.

## 5. Feature scope

### 5.1 Must have for 1.0

- Sources: M3U/M3U8 by URL and local file, Xtream Codes login, XMLTV by URL (plain and gzip), several sources merged
- Live TV: channel list with groups, favorites, hidden channels, search, last channel, number zapping, now/next banner, full EPG grid
- Playback: HLS, MPEG-TS, DASH, RTSP via Media3, audio and subtitle track choice, aspect modes, HEVC, hardware decoding with automatic fallback, buffer profiles
- Catch-up: Xtream timeshift URLs and M3U `catchup`, `catchup-days`, `catchup-source` attributes
- VOD and series: posters, details, seasons, resume position, watched marks
- Accounts and profiles: section 7
- Parental control: PIN per profile, locked groups and categories
- Stability: crash recovery, watchdog, stall detection, offline cache of lists and EPG
- Backup and restore of settings, favorites and sources into a file the user controls
- Languages: English, German, Turkish
- Releases: GitHub releases and Google Play, section 12

### 5.2 Should have (1.x)

- Recording to local or SAF storage (respects the provider's connection limit)
- Time shift (pause live) with a disk buffer
- Multiview (up to four streams, only when the provider limit allows it)
- Picture in picture, Chromecast, external player hand-off
- Reminders for programs, sleep timer, scheduled start on a channel
- Stalker portal and Jellyfin as additional sources
- Phone remote and setup through QR code (idea from TVSig, one-time code, served only while the dialog is open)
- Trailers and artwork from TMDB (user's own API key, optional)

### 5.3 Could have

- Second engine (libmpv or FFmpeg extension) as opt-in for streams Media3 cannot play
- Channel logo cache and fuzzy channel matching for EPG
- Android Auto and Wear remote
- Optional self-hosted sync of profiles and favorites

## 6. Architecture

### 6.1 Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | Kotlin, coroutines, Flow | same as TVSig |
| UI | Jetpack Compose, Compose for TV (TV Material) on TV, Material 3 on phones, one adaptive module | one code base, focus handling built in |
| Player | Media3 ExoPlayer, current stable release | already used in TVSig, best Android integration |
| Database | Room with FTS for search, paging | millions of rows, instant search |
| Network | OkHttp, kotlinx.serialization | no reflection, no Gson keep rules |
| DI | Hilt | standard, testable |
| Background | WorkManager for sync, foreground service for recording | survives restarts |
| Settings | DataStore | typed, async |
| Secrets | Android Keystore with Tink, never plain text | provider passwords |
| Build | Gradle with version catalog, Kotlin DSL for new modules (Groovy accepted only for parity if preferred) | maintainable |
| SDK | min 24 (Android 7.0, as TVSig), compile and target at the level Google Play requires at release time (check the current requirement, plan 36) | store policy |

### 6.2 Modules

```text
app                  application, navigation, flavors, service, boot receiver
core/model           plain data classes
core/database        Room entities, DAOs, migrations, FTS
core/network         OkHttp client, Xtream API, retry and cache policy
core/parser          streaming M3U parser, XMLTV parser, Xtream mapper
core/account         accounts, profiles, keystore, usage records
core/ui              design system, TV and phone components, focus helpers
feature/live         channel list, overlay, zapping
feature/epg          grid, now/next, reminders
feature/vod          movies, series, details, resume
feature/player       Media3 wrapper, track selection, failover, buffer profiles
feature/recording    recording and time shift
feature/settings     settings, setup, backup, updates (direct flavor)
benchmark            Macrobenchmark and baseline profile generation
```

Modules MUST have one direction of dependencies (feature to core, never feature to feature). The player module MUST hide Media3 behind an interface so a second engine can be added later.

### 6.3 Data flow

1. A **source** (M3U, Xtream, XMLTV) is fetched by a WorkManager job.
2. The **streaming parser** reads line by line or element by element and writes to Room in batches of about 1,000 rows. It never holds a full playlist or a full EPG in memory.
3. The UI reads only from Room through Paging. The network is never in the path of drawing a screen.
4. A sync replaces data in a transaction. A failed sync leaves the previous data untouched (same principle as TVSig, a broken download never destroys what is playing).
5. Removed channels are kept for a grace period before deletion so one incomplete provider answer cannot empty the list.

### 6.4 EPG

- Parse XMLTV with a pull parser, handle `.gz` and `.xz`, map channels by `tvg-id`, then by normalized name, then by user override
- Store times as UTC with the provider offset applied once during import
- Keep a rolling window (for example 2 days back and 7 days ahead), prune on every sync
- Several EPG sources merge per channel with a priority order
- The grid uses a lazy layout with a fixed time scale and prefetch, target 60 fps on a mid-range box

### 6.5 Playback

- One shared player instance, reused between channels so a zap does not recreate it
- Prepare the next and previous channel (small look-ahead) for faster zapping where memory allows
- Buffer profiles: low latency, balanced, stable
- Per-source headers and user agent, since many providers require them
- **Failover:** channels with the same normalized name or `tvg-id` form a group, on a hard error the player moves to the next entry and tells the user
- Stall detection: no progress for a set time triggers a restart of the stream, then failover, then a message
- Catch-up and timeshift URL builders live in the parser module and are unit tested against the formats of the common providers

### 6.6 Continuous operation on TV

Taken from TVSig and adapted: foreground service while playing or recording, start after boot and after an update (optional, off by default on phones), crash recovery with doubling delay (3 s up to 2 min inside ten minutes), a watchdog that returns to the player in launcher mode, memory trimming on request, a periodic full reload only when the guide is stale, not on a fixed timer. Users get the same guidance as in TVSig for battery optimization and auto start.

## 7. Accounting

The term is split into three parts because they have different cost and value. Part A and B are part of 1.0. Part C needs a decision (section 16).

### 7.1 Part A: provider account management (1.0)

For every source the app keeps an **account record** and shows it on one screen:

| Field | Source |
|---|---|
| Status (active, expired, banned, disabled) | Xtream `user_info.status` |
| Expiry date and days left | `exp_date`, with a warning at 30, 7 and 1 days |
| Connection limit and active connections | `max_connections`, `active_cons` |
| Trial flag, creation date | `is_trial`, `created_at` |
| Server info (URL, port, time zone) | `server_info` |
| Last successful sync, last error | local |

For M3U sources without an API the record shows what can be known (last sync, channel counts, reachability). Credentials are encrypted with a Keystore backed key. Passwords MUST NOT appear in logs, backups in plain text, QR codes or crash reports.

### 7.2 Part B: local profiles and usage records (1.0)

- Profiles (for example family members), each with its own favorites, hidden groups, history, resume points, PIN and language
- A profile can be tied to allowed sources and rating limits
- **Usage records** stay on the device: watch time per channel and per day, per profile, data volume estimate, recording count. The user can view and delete them. They are never sent anywhere.
- A simple report screen (week and month) and an export to CSV

### 7.3 Part C: optional own accounts and licensing (decision needed)

Only if ProIPTV becomes a paid or synced product:

- An account service (own backend) for sync of profiles and favorites across devices and for entitlements
- Billing through Google Play Billing for the Play flavor, a signed license token for the direct flavor
- This brings a server, privacy law duties (GDPR), support and operations, so it is **not** part of 0.x. If chosen, it gets its own guideline and its own repository (`proiptv-server`).

The app MUST work fully without an account. Part C features MAY be added as optional layers.

## 8. UX principles

- TV first, then adapt up to tablets and phones. The TV layout is a fixed design size with safe margins, as in TVSig, so 720p, 1080p and 4K look the same.
- Every function is reachable with D-pad, OK, back and the colored keys, no touch needed
- Channel zapping: up and down, number entry, last channel key, long OK for the channel list overlay
- First start in three steps: choose language, add a source (QR from phone or typing), pick groups. Playing within one minute.
- Clear errors in plain words (what failed, what to do), never raw exception text
- Accessibility: large text option, high contrast theme, TalkBack labels
- Dark theme default on TV, light and dark on phones

## 9. Performance targets

Targets are measured on a reference low-end box (1 GB RAM, Android 9) and a mid-range device, and enforced by Macrobenchmark in CI where possible.

| Metric | Target |
|---|---|
| Cold start to the last channel playing | under 4 s on the low-end box |
| Channel switch (cached list) | under 1 s typical, under 2 s worst case |
| Import of 100,000 channels | under 60 s, memory growth under 80 MB |
| Import of a 200 MB XMLTV | in the background, UI stays responsive, no out of memory |
| EPG grid scroll | no dropped frame bursts, 60 fps target |
| 72 hours continuous playback | no crash, no memory growth over 10% |
| APK size (release) | under 25 MB without a second engine |

## 10. Security, privacy, legal

- **No content, no recommendations.** The app is a player. Store text and README MUST say that the user supplies the source. No default playlists, no playlist search.
- **Store policy.** Google Play removes IPTV apps that ship or promote infringing content. The listing, screenshots and demo material MUST use only legal content (own test streams or public free streams). A takedown contact and a clear terms page are required.
- **Privacy.** No analytics and no advertising SDK. Crash reports are opt-in and local first (export a report file), an optional ACRA style sender MAY come later. A privacy policy page is required for Play (Data safety form).
- **Network.** HTTPS preferred, cleartext allowed per source only after a visible warning (many providers use HTTP). Certificate errors are never silently ignored.
- **Secrets.** Keystore backed encryption, backups exclude credentials unless the user sets a backup password.
- **Supply chain.** Dependencies pinned in the version catalog, Dependabot enabled, only GitHub Actions pinned to a version or commit, release workflow verifies checksums and the certificate.
- **Open source license.** MIT as in TVSig (the repository already carries the same LICENSE). Third party notices are generated in the build.

## 11. Testing and quality gates

| Gate | Where |
|---|---|
| Unit tests for parsers (M3U edge cases, XMLTV with broken encodings, Xtream payloads, catch-up URL builders) | CI on every push |
| String check: every text in en, de, tr with identical placeholders | CI, unit test as in TVSig |
| Lint, detekt, ktlint | CI |
| Database migration tests | CI |
| Fixtures: synthetic 100,000 channel playlist and a 200 MB EPG for benchmark runs | benchmark module |
| Macrobenchmark and baseline profile | before each minor release |
| Instrumented tests on an emulator (Android TV and phone image) for navigation and focus | CI, nightly |
| Soak test on a real box, 72 h | before each minor release |
| Debug builds with StrictMode and leak detection | debug flavor |

A release MUST NOT be tagged while CI is red.

## 12. Build, versioning, signing, release

This section mirrors TVSig on purpose.

### 12.1 Version and branches

- The version lives in `app/version.txt` (`0.0.1`, or `0.1.0-rc1` for a pre-release). `tools/version.py` and `app/build.gradle` derive the version code the same way as in TVSig. The version code MUST grow with every release.
- One branch per version, named exactly like the version (`0.0.1`, `0.0.2`, ...). A release is the tag `v<version>` on the merged commit.
- Tags with a suffix (`-rc1`, `-beta1`) are published as pre-releases and are ignored by the in-app update check.

### 12.2 Workflows

| Workflow | Trigger | What it does |
|---|---|---|
| `CI` | every push, pull requests, manual | version file check, unit tests, lint, detekt, debug APK as artifact, release build and keep rule check, version code check |
| `Release` | tag `v*` or manual with the version | tag against `app/version.txt`, tests, signed APK and AAB for both flavors, certificate check against the expected fingerprint, APK facts (application ID, version name and code), `SHA256SUMS.txt`, R8 mapping, GitHub release with notes |
| `Play` (later, 0.1.x) | after a successful `Release`, manual approval | uploads the AAB of the `play` flavor to the internal testing track |

Build scripts are copied and adapted from TVSig: `tools/version.py`, `tools/release-notes.sh`, `tools/check-keep-rules.py` (only if reflection remains).

### 12.3 Signing, fingerprint, secrets

1. Create a new release keystore offline (`keytool`, RSA 4096, alias `proiptv-release`, long validity), two offline backups. If the key is lost, no update can be signed any more.
2. Note the SHA-256 fingerprint of the certificate. It goes into the secret `PROIPTV_RELEASE_CERT_SHA256` so every release run fails on a different key, and into the Android developer verification and the Play Console.
3. Repository secrets (same semantics as in TVSig):

| Secret | Required | Content |
|---|---|---|
| `PROIPTV_KEYSTORE_BASE64` | yes | keystore as Base64 text |
| `PROIPTV_KEYSTORE_PASSWORD` | yes | keystore password |
| `PROIPTV_KEY_ALIAS` | yes | key alias |
| `PROIPTV_KEY_PASSWORD` | no | defaults to the keystore password |
| `PROIPTV_RELEASE_CERT_SHA256` | no, recommended | expected fingerprint |
| `PROIPTV_PLAY_SERVICE_ACCOUNT_JSON` | only for the `Play` workflow | service account of the Play Console API |

4. Debug builds use the committed `app/debug.keystore` and the ID suffix `.debug`, so they never replace or conflict with the release app.
5. Keystore, passwords and the service account JSON MUST never be committed or pasted anywhere else.
6. A guideline `guideline-release-signing.md` for ProIPTV is written when the first release is prepared (adapted from TVSig).

### 12.4 Distribution channels and flavors

| Flavor | Channel | In-app update | `REQUEST_INSTALL_PACKAGES` |
|---|---|---|---|
| `play` | Google Play (AAB) | no, Play updates the app | not declared |
| `direct` | GitHub releases (APK) | yes, as in TVSig (button only, SHA-256 against `SHA256SUMS.txt`) | declared |

Google Play does not allow an app to install updates from outside the store, so the update code and the permission exist only in the `direct` flavor.

**Signing across channels (decision needed):** an app installed from Play is signed by Play App Signing, an APK from GitHub by our release key, and Android refuses an update across different keys. Options:

- **A (recommended):** enrol the same release key in Play App Signing (upload of the private key at enrolment), so both channels carry the same certificate and users can switch
- **B:** use another application ID for the direct flavor (`com.kutlusoy.proiptv.direct`), simple but two separate installs and two registrations

### 12.5 Play Store identity and listing

| Item | Plan |
|---|---|
| Application ID | `com.kutlusoy.proiptv`, permanent once published |
| Play Console | personal or organization developer account, identity verification done before the first upload |
| Android developer verification | register the package name with the release certificate SHA-256, ship `adi-registration.properties` in the assets, the release workflow checks it as in TVSig (the file holds the developer account ID, not a secret) |
| App signing | Play App Signing enabled, see 12.4 |
| Tracks | internal testing, closed testing (20 testers for 14 days if a new personal account requires it, check current rules), open testing, production |
| TV listing | Leanback launcher intent, `android.software.leanback` and `touchscreen` declared as not required, banner 320 x 180, TV screenshots, banner generated from an SVG like in TVSig (`design/` scripts) |
| Listing text | English, German, Turkish, states clearly that no content is provided |
| Policy forms | Data safety, content rating, target audience, ads declaration (no ads), privacy policy URL |
| Amazon Appstore | optional later channel for Fire TV, same `play`-like flavor without the installer permission |

### 12.6 Release procedure

1. Work on the version branch, CI green.
2. Set `app/version.txt`, merge to `main`.
3. Push the tag `v<version>` (or run the workflow manually). The workflow builds, verifies and publishes.
4. Compare the fingerprint on the release page with the noted one.
5. Test the update from the previous release on a real device (direct flavor) and install from the internal track (play flavor).

## 13. Roadmap by version

| Version | Content | Exit criterion |
|---|---|---|
| **0.0.1** | Repository skeleton: Gradle project, `app/version.txt`, `tools/`, CI and Release workflows, debug keystore, three languages scaffold, empty Compose TV and phone shell, this plan | CI green, first signed pre-release `v0.0.1-rc1` published with fingerprint on the page |
| 0.0.2 | Core: Room schema, streaming M3U parser, source management, basic channel list, Media3 playback of one channel | 100,000 channel import test passes, live channel plays on TV and phone |
| 0.0.3 | EPG: XMLTV import, now/next, grid, mapping rules, timezone tests | 200 MB EPG imports in the background, grid scroll benchmark meets target |
| 0.0.4 | Xtream login, account screen (section 7.1), expiry warnings, encrypted credentials | account data shown for a test provider, no secret in logs |
| 0.0.5 | Profiles, favorites, groups, parental PIN, backup and restore | profile switch under 1 s, restore test green |
| 0.0.6 | Zapping UX, failover, stall detection, buffer profiles, continuous operation service | 72 h soak test green |
| 0.0.7 | Catch-up, VOD, series, resume | catch-up URL tests for common provider formats |
| 0.0.8 | Usage records and reports (section 7.2), reminders, sleep timer | reports verified, export works |
| 0.1.0-rc1 | Feature freeze for the first public beta, `direct` flavor in-app update, Play internal track, developer verification registration | update from previous release verified on device |
| 0.1.x | Recording, time shift, PiP, Chromecast, multiview | provider limit respected in tests |
| 1.0.0 | Stability and polish, Play production, full docs | all gates of section 11 green, 30 days without a blocking issue in beta |

The order MAY change when testing shows risk earlier, but 0.0.1 (pipeline first) MUST stay first, so every later step is shipped through the real release path.

## 14. Risks

| Risk | Mitigation |
|---|---|
| Store removal because of IPTV misuse | no content, clean listing, takedown contact, legal test streams only |
| Provider APIs differ and change | adapter per provider type, recorded fixtures, tolerant parsing |
| Huge lists cause out of memory on weak boxes | streaming parsers, Room and Paging, benchmark in CI |
| Wrong EPG mapping | layered matching, manual override, visible mapping screen |
| Key loss | two offline backups, fingerprint recorded, Play App Signing |
| Update signature mismatch between channels | decision in 12.4 before the first public release |
| Connection limits of providers break multiview and recording | read `max_connections`, block features that would exceed it |
| Scope growth | the roadmap order and the "should have" list are the buffer, nothing from 5.2 moves into 1.0 without removing something |

## 15. Platform notes

iOS, tvOS and web are not part of 1.0. If they are wanted later, the parser, account and data modules SHOULD be moved to Kotlin Multiplatform at that time, which is why they are kept free of Android classes from the start.

## 16. Open questions

1. What exactly is meant by "Accounting"? This plan covers provider account management and local usage records (sections 7.1 and 7.2). Own user accounts, sync or paid licensing (7.3) need a separate decision.
2. Which channel comes first: GitHub releases only for 0.x, or Play internal track from the start?
3. Signing across channels: option A or B in section 12.4?
4. UI toolkit: Compose as proposed, or Views as in TVSig for lower risk on very old boxes?
5. Second engine (libmpv or FFmpeg) as a goal for 1.x, or Media3 only?
6. Documentation language: this plan is English like TVSig. Confirm English for the whole repository.
7. Is the project fully open source under MIT, or are parts (for example a licensing module) closed?

## 17. Checklist

- [ ] Plan reviewed, open questions in section 16 answered
- [ ] Branch `0.0.1` skeleton built (Gradle, `version.txt`, `tools/`, workflows)
- [ ] Release keystore created offline, two backups, fingerprint noted
- [ ] Repository secrets `PROIPTV_*` set
- [ ] First pre-release `v0.0.1-rc1` published and fingerprint compared
- [ ] Play Console account verified, package name `com.kutlusoy.proiptv` reserved
- [ ] Android developer verification registered with the release certificate
- [ ] Privacy policy and terms pages online
- [ ] Test sources (legal streams) prepared for CI fixtures and store screenshots
