# ProIPTV: Install, first start and daily use

- **Version:** 0.1 (draft)
- **Date:** November 13, 2026
- **Audience:** Users and testers who install the debug or release APK and add their provider account
- **Reference implementation:** `app/src/main/java/com/kutlusoy/proiptv/ui/`, `sync/SyncManager.kt`, `net/XtreamApi.kt`
- **See also:** [`guideline-feature-matrix.md`](./guideline-feature-matrix.md), [`guideline-release-signing.md`](./guideline-release-signing.md)

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Get the app

| Where | How |
|---|---|
| Debug build from GitHub | Open the repository, tab Actions, the latest run of the workflow "CI" on your branch, section Artifacts, download `proiptv-debug-apk`, unzip it. The file is `app-debug.apk`. |
| Release build | Open the repository, Releases, download `ProIPTV-<version>.apk`. Compare the certificate fingerprint on the release page with the one you were told (see the signing guideline). |

The debug app has its own ID (`com.kutlusoy.proiptv.debug`, name "ProIPTV Debug"), so it installs next to the release app and never replaces it.

## 2. Install

**Phone or tablet:** open the APK file, allow "install unknown apps" for the app you used to open it, confirm.

**Android TV or box:** the easiest way is `adb` from a computer in the same network.

1. On the TV: Settings, Device Preferences, About, press OK seven times on "Build" to enable developer options. Then Settings, Developer options, turn on "USB debugging" or "Network debugging". Note the IP address of the TV.
2. On the computer:

```bash
adb connect 192.168.1.50:5555
adb install -r app-debug.apk
```

Replace the address with the one of your TV. PowerShell and the Linux shell use the same commands.

Alternatively copy the APK to a USB stick and open it with a file manager app on the TV.

## 3. Add an account

Open the tab **Accounts**, button **Add account**.

### Xtream Codes account

| Field | What to enter | Example |
|---|---|---|
| Type | `Xtream Codes` | |
| Name | any name for yourself | `Home` |
| Server URL | the address your provider gave you, with port. The ending `/player_api.php`, `/get.php...` or `/c/` is removed automatically. You MAY paste a full `get.php?username=...&password=...` link, user name and password are then taken from it. | `http://example.com:8080` |
| Username | the user name of your provider | |
| Password | the password of your provider (stored encrypted on the device) | |
| Live stream format | `Automatic` is right for nearly all providers. Choose `TS` or `HLS` only if a provider needs it. | |
| Load movies, Load series | switch off what you do not use, it makes the sync faster | |
| Guide URL | optional, only if your provider gave a separate XMLTV address | |
| User agent | optional, only if your provider requires a specific one | |

### M3U playlist

| Field | What to enter |
|---|---|
| Type | `M3U playlist` |
| Name | any name |
| Playlist URL or file | the link of the playlist, or press **Choose file** to pick a file from the device |
| Guide URL | optional. If the playlist names a guide (`url-tvg`), it is used automatically. |

Press **Save**. The app connects, loads channels, movies, series and the guide in the background. The account card shows the progress, then the status of the account (state, expiry date, allowed connections) and the number of channels.

If **Error** is shown on the card: `Login failed` means user name, password or server are wrong. Other messages are shown as they come from the server.

Several accounts can exist side by side. Each account is a compact line with the name, a short status (sync progress, an error in red, or the expiry date) and a switch that turns it on or off without deleting it. **Tap the line** to open the details and the buttons (sync, update guide, edit, delete). Only one account is open at a time, a single account is always open.

### Keeping the guide up to date

The program guide refreshes itself, you do not have to do anything:

- when the app opens, if the guide is older than 6 hours
- every 30 minutes while the app is open, if the guide is older than 6 hours or covers less than the next 12 hours (then at most once per hour)
- in the background about every 6 hours, even when the app is closed, as long as the device is online
- when you open a channel that has no guide data, the app asks the provider for the next programs of that channel directly (Xtream accounts)

The refresh only downloads again when the provider's guide has changed, and an update that fails never removes the guide you already have. The account card shows when the guide was last updated, and the button **Update guide** refreshes it at once.

## 4. Watch

- **Menu:** the icon with three lines at the top left opens the menu with Live TV, Movies, Series, Recordings, Accounts and Settings (on a TV remote: move the focus to it and press OK, or swipe from the left edge on a touch screen).
- **Live TV:** the dropdown above the list chooses the group of the provider (`Favorites` for your own lists, `All` for everything). With more than one account a second dropdown selects the account. The magnifier icon opens the search field, the cross closes it again. In Live TV the search also finds what is running right now: type the name of a show and the channels that currently broadcast it are listed.
- **Country and year:** many providers put a country code in front of the title (`DE | Das Erste`, `(DE-) Film`) and a year behind it (`The Matrix (1999)`). The app reads both and shows them as small badges next to the clean title. Titles without such a marker simply have no badge. The original title stays unchanged for the search.
- **Movies and Series:** with **All** selected the page shows rows per category (like a streaming service), with a category chosen or a search entered it shows a poster grid. Press a poster to open its **info page** first.
  - **Movie info page:** big picture, title, rating, year, length, genre, story, director and cast (as far as the provider delivers them). The red button plays the movie, or resumes it where you stopped, with **From the start** next to it.
  - **Series info page:** the same header, a button **Play S1 E1** (or **Continue S2 E5** after you watched something), a **season dropdown** and the list of episodes with picture, length, short story and a progress bar for what you started.
  - Playback resumes where you stopped.
  - **Watched marks:** a finished episode or movie gets a check mark. In the episode list the check button at the right of an episode switches the mark by hand, the three dots menu next to the season dropdown marks a whole season as watched or unwatched. On the movie page there is a button **Mark as watched**. The series button then offers the next unwatched episode (**Continue S1 E3**).
  - **Favorites for movies and series:** the star in the top right corner of the info page, or a long press (touch) or a long press on OK (remote control) on a poster. In the Movies and Series tabs choose **Favorites** in the dropdown to see them. The order can be changed like with channels.- Press the **star** next to a channel to add it to your favorites.

### Full screen

The app runs in full screen mode, the buttons of Android are hidden. Swipe from the edge of the screen (bottom for the buttons, top for the status bar) to show them for a moment.

### Screen orientation while playing

On a phone the player switches to **landscape** by itself and hides the system bars (swipe from the edge to show them). The button **Rotate screen** in the information bar switches between landscape and portrait. When you leave the player the orientation is as before. TVs are not affected.

### Remote control keys while playing

| Key | Action |
|---|---|
| Up, Down | previous or next channel in the current list |
| Channel up and down | next or previous channel |
| Number keys | jump to a channel position in the list, confirmed after a short pause |
| OK | show or hide the information bar |
| Left, Right | for movies and episodes: jump back or forward when the bar is hidden |
| Play or pause | pause and resume |
| Back | leave the player |

In the information bar: favorite, **Guide** (program list of the channel, and watch past programs from the start if the provider keeps an archive), **Audio and subtitles**, **Picture size** (fit, zoom, stretch).

On a phone: tap the picture to show the bar.

If a stream fails, the app retries three times and then says so. A stream that stays stuck for about 25 seconds is restarted.

## 5. Pause, rewind and record live TV

This works for live channels in **TS** format (the `Automatic` setting uses TS where the provider allows it). Channels in HLS format (`.m3u8`) play directly, without pause and rewind.

While you watch, the app keeps the last minutes of the channel on the device. The information bar then shows:

| Control | Action |
|---|---|
| Pause button | stops the picture, the channel keeps being stored, resume continues where you stopped |
| `-30 s`, `+30 s` | jump back or forward. With the bar hidden, Left and Right on the remote jump 10 seconds. |
| `Live` | jump to the live picture. The label above shows `Live` or how far you are behind, for example `-02:15`. |
| `Record` | start a recording, see below |

**Buffer size:** Settings, "Pause and rewind live TV" (switch) and "Buffer size on this device". A bigger buffer allows rewinding further and uses more storage. As a guide, 1 GB holds about 17 minutes of an HD channel (8 Mbit/s) or more than an hour of an SD channel (2 Mbit/s). If the device has little free space, the app uses less than the chosen size.

**Connections:** watching, rewinding and recording the same channel use **one** connection of your provider. If the account has a limit (shown on the account card) and it is used up, the app says which channels use the connections instead of failing silently.

### Recording

1. Press **Record** in the information bar and choose how long: until the current program ends, 30, 60 or 120 minutes, or until you stop it.
2. On Android 13 and newer the app asks once for permission to show notifications, allow it. A notification "Recording: ..." stays visible while a recording runs.
3. The recording continues when you leave the player and when the screen turns off. Press **Stop recording** in the bar or in the tab **Recordings** to end it earlier.
4. Tab **Recordings** lists all recordings with date, length and size. **Play**, **Stop recording** and **Delete** are there. Playback offers audio and subtitle choice and resumes where you stopped.

The files are normal transport stream files (`.ts`) in the app's Movies folder (`Android/data/com.kutlusoy.proiptv/files/Movies`, for the debug app `...proiptv.debug...`). You can copy them with a file manager or over USB and play them with VLC or similar players. Deleting a recording in the app deletes the file.

### Record a program from the guide

1. While watching a channel open the information bar, button **Guide**.
2. Choose a program that is running now or comes later, confirm with **Record**.
3. A program that is running starts to record at once. A later program is **scheduled**: it starts 1 minute before and ends 5 minutes after the announced times, so the start and the end are not cut off.
4. Scheduled recordings appear in the tab **Recordings** with the status **Scheduled**. **Cancel** removes one. They survive a restart of the device.
5. If another scheduled recording at the same time may need the same connection of an account with a connection limit, the app warns you when you schedule.
6. If a recording cannot start (no free connection, little storage, channel removed, program already over), it is marked **Missed** with the reason and you get a notification.

Scheduled recordings use an alarm of the system. They do **not** work if you forced the app to stop in the Android settings (the system then runs nothing of the app until you open it once), and some TV boxes with aggressive power saving may delay alarms. After a restart of the device the app is started by the system and registers the alarms again.

If the app is closed by the system or the stream stays down for a long time, the recording is marked **Interrupted**, the part that was recorded is kept. For long recordings turn off battery optimization for the app (see the continuous operation notes of your device vendor). A recording does not start if less than 200 MB of storage is free.

### If a channel does not play

The app retries three times. If the channel still fails, it looks for the same channel (same guide ID or same name) in your enabled accounts, first in the same account, and switches to it with a short message "Switched to ...". This only happens while you zap in a list, not for movies, episodes or recordings.

## EPG: the program guide of all channels

The menu entry **EPG** lists shows of all channels of the selected group and account. Use the dropdowns on top for the group and the account, the field below for a search in show titles and channel names, and the third dropdown for the time range: **Now on air**, **Next 3 hours** or **Next 12 hours**. A tap on an entry starts the channel; up and down while playing then follows the order of the list you came from. Shows that run right now are bold and have a progress bar. The guide itself is loaded with the account sync, see section 4.

## Settings and updates

The settings are grouped: **General** (language, interface), **Playback and recording** (pause and rewind, buffer size), **Accounts and data** (refresh, backup and restore), **About**, and at the very bottom **Update**.

- **Check for updates:** the button asks GitHub for the newest release. If there is a newer version, **Download and install** appears. The app downloads the file, checks its SHA-256 checksum against `SHA256SUMS.txt` of the release, and only then hands it to Android. Android installs it only if it was signed with the same key, and asks you to confirm. The first time Android may ask to allow installing from this app.
- **Check every day:** the switch is on by default. A check runs in the background once a day, only with a network connection, and you get **one notification per day at most**, and only when a newer version exists. Turn the switch off to stop the background check completely.
- Test builds from the workflow `Test release` follow the newest test release (`test-<number>`), release builds follow the newest release.

## The app remembers where you were

When you close the app and open it again, it continues with the same area (Live TV, Movies, ...), the same group or favorites list and the same account selection.

## Downloads for offline use

1. Open a movie: the button **Download** under the title saves it on the device. For a series open the series, then use the download symbol next to an episode, or the three dots menu next to the season selector and **Download season**.
2. The menu entry **Downloads** has two tabs, **Movies** and **Series**. Series are grouped by series and season, tap a series and a season to open it. Each entry shows progress, **Play** and **Delete**. A failed download has **Try again**.
3. Downloads run one after another in the background with a notification and continue after an interrupted connection. Movies and episodes that are saved are played from the device automatically, also without internet. The place is the app folder on the device (`Android/data/com.kutlusoy.proiptv/files/downloads`), it is removed when the app is uninstalled.
4. Only movies and episodes can be saved, no live TV. The download uses one connection of the account while it runs.

## Gestures in the player

While watching live TV you can switch channels in the order of the list you started from: **up** and **down** on the remote control or the keyboard, the two arrow buttons at the right edge of the screen, or a swipe with the finger (swipe up for the next channel, swipe down for the previous one).

Double tap on the left half of the picture jumps 10 seconds back, double tap on the right half 10 seconds forward (movies and episodes). A single tap shows or hides the controls. Recording is the red **REC** button at the bottom, it turns into a red square while a recording runs.

## ProIPTV on Windows

Download `ProIPTV-<version>-windows-x64-setup.exe` from the release page (or from a test release) and start it. It installs for your Windows user, without administrator rights, with a start menu entry and an uninstaller. As long as the installer is not signed, Windows SmartScreen shows "Windows protected your PC": click **More info** and then **Run anyway**.

Add your accounts under **Accounts** (the help texts are the same as on Android), then open Live TV, Movies or Series. While watching: **Space** pause, **arrow left/right** 10 seconds (movies), **arrow up/down** previous and next channel (live), **F** or a double click full screen (without window border, the controls float over the picture and fade away when the mouse rests), **Esc** leaves full screen and then stops, mouse wheel changes the volume. Under **Settings** you find the language and the **Update** section; the app also checks once a day by itself and asks once per day when a newer version is there. The update closes the app, installs the new version and starts it again. Your accounts and favorites stay, they are in `%APPDATA%\ProIPTV`.

The Windows version does not have guide (EPG), recording, time shift, downloads and the track menu yet, see `doc/guideline-windows-version.md`.

## Set up an account with your phone (QR code)

Typing a long server address with a remote control is tedious. Under **Accounts** choose **Set up with your phone (QR code)**: the TV shows a QR code. Scan it with the camera of your phone (phone and TV in the same Wi-Fi), a page opens in the browser of the phone in your language. Enter the account there (Xtream Codes or M3U, name, address, user name, password) and send it. For Xtream accounts the TV logs in once first, so a wrong password is shown on the phone right away. The account appears on the TV and the first sync starts.

Safety: the page is served by the TV itself, only while the window with the QR code is open (closes by itself after 10 minutes), and every call needs the one-time code that is part of the QR code. Nothing goes over the internet, but inside your home network the data (also the password) travels unencrypted, as with any simple local web page. Do not use it in a network that you do not trust.

On a TV the dialog **Add account** also has its **Save** and **Cancel** buttons at the end of the form (down arrow after the last field), so the remote control reaches them.

## Android TV and TV boxes

When the app runs on Android TV or a TV box it switches to the TV layout by itself: the menu is a fixed column on the left (press left to reach it), texts and posters are larger, there is a safe margin around the picture, and the rotate button of the player is hidden. The menu has an **Exit** entry that closes the app, and the player has **Stop** and **Play/Pause** in the middle of the bottom row (Stop ends playback and returns to the list), a **menu button** with the channel list in the upper left corner, and a **star** (favorite) and a **More** button in the upper right corner for audio and subtitles, screen rotation and picture size. The menu button opens a list of the channels of the list you started from at the left edge, the channel keeps playing, a tap on another channel switches to it and closes the list. Whatever has the focus (buttons, list rows, menu entries, also in dialogs) is drawn with a bright frame, so you always see where the remote control is. The menu column scrolls, so all entries down to **Settings** and **Exit** can be reached with the down arrow. The Back key on the first level asks whether to close the app; an open search field is closed first. While watching live TV with the remote control: **up** and **down** always switch the channel, whether the controls are shown or not; **left** and **right** show the controls and then walk through all of them (menu, star, More, Program, Live, REC, Stop, Play/Pause); **OK** presses the highlighted control, or shows the controls when they are hidden; rewind and fast forward keys move through the time shift. Everything can be used with the remote control: arrow keys to move, OK to open, long OK on a poster for favorites, Back to go one step back. If your device is detected wrongly, choose **Settings, Interface** and select TV or Touch by hand.

## 6. Favorites and manual order

Live TV, Movies and Series each have their own favorites lists. A list that you create in Live TV does not show up in Movies or Series. Existing lists were split by area when the app was updated, so nothing is lost.

1. Live TV, in the dropdown choose **Favorites**.
2. The dropdown on top of the favorites chooses your list. The three dots menu next to it has **New list** ("Sport", "Kids"), **Rename** and **Delete** for the selected list.
3. **Change the order:** touch the favorite and hold, then drag it to the new place. With the remote control hold **OK** on the favorite until the row is highlighted, move it with **up** and **down**, and press **OK** again to fix the position. This works for channels, movies and series.
4. Channels that the provider removed stay in the list, greyed out, so your order is not lost. They return when the provider adds them again.

Removing a favorite with the star asks for confirmation first.

With more than one list the star opens a dialog where you tick the lists of the channel. With one list the star toggles it directly.

Zapping with up and down while playing follows the order of the list you started from.

## 7. Backup and restore

Settings, section **Backup**.

- **Create backup:** choose a password or leave the field empty, then choose where to save the file (for example a USB stick). The file holds your accounts (server, user name, options), favorite lists with their order and the time shift settings. **Without a password the file contains no account passwords.** With a password the whole file is encrypted and the account passwords are included. The password cannot be recovered, keep it safe.
- **Restore backup:** choose the file, enter the password if the file is protected. Restoring **adds** what is missing, it never deletes. Accounts that already exist (same type, address and user name) are kept as they are, favorite lists with the same name are merged. Restored accounts start to load their channels at once. An account from a backup without password shows `Login failed` until you open **Edit** on the card and enter the password.

## 8. Language and look

Tab **Settings**. The settings are grouped in sections with dropdown menus (language, buffer size, live stream format of an account, season of a series). In **Language**, `Automatic` follows the device, the change applies at once. The app uses a dark black and wine red design on every device.

## 9. Limits of this version

- No multiview yet and no parental PIN yet. Planned for the next versions, see the roadmap.
- Pause, rewind and recording need TS streams, not HLS.
- Stalker portal, HDHomeRun, Enigma2 and TVHeadend sources are not implemented yet.
- Series in M3U playlists are listed as single movies.
- The translations of Portuguese, Dutch, Polish, Russian and Arabic cover only the first screens, the rest shows English.
- The splash screen and icons are placeholders until the logo is delivered.

## 10. Troubleshooting

| Observation | What to check |
|---|---|
| `Login failed` on the card | Server address with port, user name, password. Open the address `<server>/player_api.php?username=..&password=..` in a browser, it must show account data. |
| Account loads but a channel does not play | Try another live format in the account (`TS` or `HLS`). Check that the provider allows another connection (card shows allowed and active connections). |
| Guide is empty | The channel has no guide id in the playlist, or the provider offers no XMLTV. Enter a guide URL in the account. |
| A scheduled recording was missed | The status line names the reason. Typical causes: no free provider connection at that time, the app was force stopped, or the device was off. |
| Recording does not start | Free storage under 200 MB, or all provider connections are in use (the message names them). |
| Very large lists load slowly | The first load of a big account takes a minute or more on weak devices. It runs in the background, the app stays usable. |
| Text in another language than expected | Settings, Language. |


## Movie information from TMDB (optional)

M3U lists carry only a title, a picture and a group. Xtream providers often have a plot, cast and more. For titles without them the app can ask themoviedb.org:

1. Create a free account at themoviedb.org and request an API key (Settings, API).
2. Enter the key in the settings of the app (Android, Android TV and Windows) under **Movie information (TMDB)**.
3. Open a movie or a series. Plot, cast, director, genre, running time, rating, trailer and a backdrop picture appear.

Only the cleaned title and the year of the title that you open are sent to TMDB, together with your key, nothing else. Without a key nothing is sent and the details show what the provider delivers. The answer is kept until the app is closed, so opening a title again does not ask again.
