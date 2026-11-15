# ProIPTV: review and plan for 0.0.3

A technical, performance and UI review of version 0.0.2 and what it led to. Status marks: done (in 0.0.3), open (planned, with the reason).

## Strengths to keep

- stream hub with ring buffer, one provider connection per channel, connection budget
- paging for large lists, Room with indices, streaming parsers
- accounts: passwords in the Android Keystore (Windows: DPAPI), no analytics, no own server
- background sync with WorkManager, TV focus handling, tests for the engine, the parsers and the backup

## Technical

| Item | Status | Notes |
|---|---|---|
| Adaptive size of the time shift buffer | done | a third of the free space at most, on Android and on Windows |
| Stall detection of the hub data source | done | 12 s while connected but silent (was 30 s), 30 s while the hub reconnects, an immediate error when the hub gave up |
| Provider errors to the UI | done | login (401, 403), connection limit (429, 509), blocked (451), not found (404, 410) are shown in words in the player and on the account card (`ProviderProblem`) |
| Warm hubs for neighbouring channels | open | speeds up zapping, but every warm hub holds a provider connection, so it needs the connection budget of the account and a setting that is off by default. Planned for 0.0.4 with a device test |
| Pre-connect for favorites | open | same reason as warm hubs |
| Debug logging | open | Windows writes `log.txt` and `vlc.log`. Android: a small local log file for debug builds is planned, no external crash reporter |
| Shared module for Android and Windows | open | the Windows module compiles the Android free sources of `app` (`syncShared`). A real Gradle module is the next step when the engine moves too |
| OkHttp debug interceptor, pool tuning | open | no measured problem yet |
| Cold start | open | measure first (target under 4 s), then move work out of `Application.onCreate` |

## Performance

| Item | Status |
|---|---|
| Lists: paging, now playing from the guide | done before |
| Pages of the last list stay loaded when the player closes (same list, same place) | done |
| Logos: Coil memory and disk cache, placeholder while loading, initials when there is no picture | done |
| EPG timeline loads the shows of a channel only when its row is visible | done (lazy rows) |
| Time shift disk I/O on weak devices | open, to be measured on the low end box |

Targets stay as in the product plan: cold start under 4 s, zapping under 1 s after warm up, controlled memory with 5,000 to 10,000 channels.

## UI and UX

| Item | Status |
|---|---|
| Start page with rows, one search over everything | done |
| EPG as a timeline on Windows, Android and Android TV | done |
| Empty state with a button (Add account), skeleton rows while loading | done |
| Logo fallback (initials) | done |
| Quick filters for the channel list: HD, catch-up, favorites (Windows: HD, favorites) | done |
| Player: now and next, reconnect notice, audio and subtitles, time shift, recording | done (Windows now and next and reconnect notice new) |
| Better error texts | done, see above |
| Accessibility: descriptions for the icon buttons without text | done for the buttons found in this review |
| Onboarding: QR setup as the first step | open: the empty state now points to the account screen, a first run flow with the QR code is planned |
| Larger touch targets, visual polish | open, ongoing |

## Roadmap

| Version | Focus |
|---|---|
| 0.0.3 | stabilise zapping and errors, EPG timeline, filters, empty and loading states (this document) |
| 0.0.4 | warm hubs with a setting and a device test, onboarding flow, cold start measurement |
| 0.0.5 | multiview, parental PIN |
| 0.0.6 and later | more sources (Stalker, HDHomeRun, Enigma2, TVHeadend), polish |
