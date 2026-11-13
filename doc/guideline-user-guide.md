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

Several accounts can exist side by side. The switch on the card turns an account on or off without deleting it.

### Keeping the guide up to date

The program guide refreshes itself, you do not have to do anything:

- when the app opens, if the guide is older than 6 hours
- every 30 minutes while the app is open, if the guide is older than 6 hours or covers less than the next 12 hours (then at most once per hour)
- in the background about every 6 hours, even when the app is closed, as long as the device is online
- when you open a channel that has no guide data, the app asks the provider for the next programs of that channel directly (Xtream accounts)

The refresh only downloads again when the provider's guide has changed, and an update that fails never removes the guide you already have. The account card shows when the guide was last updated, and the button **Update guide** refreshes it at once.

## 4. Watch

- **Live TV:** the chips above the list are the groups of the provider. `Favorites` shows your own lists, `All` shows everything. With more than one account an account selector appears. The search field filters by name.
- **Movies and Series:** posters, press one to play it. For a series, choose the season and then the episode. Playback resumes where you stopped.
- Press the **star** next to a channel to add it to your favorites.

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

## 6. Favorites and manual order

1. Live TV, chip **Favorites**.
2. The chips on top are your lists. **New list** creates another one ("Sport", "Kids"). **Rename** and **Delete** act on the selected list.
3. Press **Sort**: every channel gets an arrow up and an arrow down. Move a channel with them (works with touch and with the remote control), then press **Done**.
4. Channels that the provider removed stay in the list, greyed out, so your order is not lost. They return when the provider adds them again.

With more than one list the star opens a dialog where you tick the lists of the channel. With one list the star toggles it directly.

Zapping with up and down while playing follows the order of the list you started from.

## 7. Backup and restore

Settings, section **Backup**.

- **Create backup:** choose a password or leave the field empty, then choose where to save the file (for example a USB stick). The file holds your accounts (server, user name, options), favorite lists with their order and the time shift settings. **Without a password the file contains no account passwords.** With a password the whole file is encrypted and the account passwords are included. The password cannot be recovered, keep it safe.
- **Restore backup:** choose the file, enter the password if the file is protected. Restoring **adds** what is missing, it never deletes. Accounts that already exist (same type, address and user name) are kept as they are, favorite lists with the same name are merged. Restored accounts start to load their channels at once. An account from a backup without password shows `Login failed` until you open **Edit** on the card and enter the password.

## 8. Language

Tab **Settings**, **Language**. `Automatic` follows the device. The change applies at once.

## 9. Limits of this version

- No multiview yet and no parental PIN yet. Planned for the next versions, see the roadmap.
- Pause, rewind and recording need TS streams, not HLS.
- Stalker portal, HDHomeRun, Enigma2 and TVHeadend sources are not implemented yet.
- Series in M3U playlists are listed as single movies.
- Favorites can be set for live channels only.
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
