# ProIPTV: to do list

Status of the work that came out of the reviews and the tests on real devices. `[x]` done, `[ ]` open, `[-]` dropped (with the reason).

## Done in 0.0.4

- [x] Main menu on a TV folds away for live TV, movies and series (Back brings it back)
- [x] Player controls: pause, stop, record in the middle, guide and gear at the right, same symbols on Android and Windows (one shared file)
- [x] Connections cut for real: closing a stream cancels the call, a channel switch closes the old connection at once
- [x] First start on a TV offers the QR code first
- [x] Debug log for Android debug builds (local file, passwords and addresses blacked out)
- [x] Windows: the intro can be switched off in the settings
- [x] Shared Gradle module `core` for Android and Windows (parsers, Xtream client, stream hub, update logic)
- [x] Safe margin at the left of the channel list of the TV browser

## Open

### Shared code
- [ ] Move the picture code that is still copied (`ui/splash`, `ui/PlayerControls.kt`) into a Compose module that both apps use (needs the Compose Multiplatform plugin in a library module and a check that the Android app size does not grow)
- [ ] Move the data model (Room entities) and the repository logic that does not need Android into `core`
- [ ] Run the tests of `core` on its own in CI (`./gradlew :core:test`), today they run with the app tests

### Player and playback
- [ ] Multiview (several streams at once). Needs the connection budget per account in the UI
- [ ] Parental PIN
- [ ] Time shift on Windows: let the buffer survive a change to full screen

### Sources
- [ ] Stalker portal, HDHomeRun, Enigma2, TVHeadend

### Quality
- [ ] Measure the cold start on a low end box (target under 4 s) and move work out of `Application.onCreate` where needed
- [ ] Larger touch targets and visual polish, ongoing
- [ ] Native speaker review of the translations (the texts of the last versions are machine translated)
- [ ] Windows: sign the installer (SignPath Foundation)
- [ ] macOS and Linux ports

## Dropped

- [-] Warm hubs (open the neighbouring channels in advance to zap faster) and pre-connect for favorites: every warm hub is a connection to the provider, and a provider may count it as a second stream and refuse the real one. Decided against it.
- [-] "Recently added" list and sort by date: Xtream gives the date, M3U lists do not, and it needs a database change. Left out on purpose; sorting by name is there.
