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

## 5. Favorites and manual order

1. Live TV, chip **Favorites**.
2. The chips on top are your lists. **New list** creates another one ("Sport", "Kids"). **Rename** and **Delete** act on the selected list.
3. Press **Sort**: every channel gets an arrow up and an arrow down. Move a channel with them (works with touch and with the remote control), then press **Done**.
4. Channels that the provider removed stay in the list, greyed out, so your order is not lost. They return when the provider adds them again.

With more than one list the star opens a dialog where you tick the lists of the channel. With one list the star toggles it directly.

Zapping with up and down while playing follows the order of the list you started from.

## 6. Language

Tab **Settings**, **Language**. `Automatic` follows the device. The change applies at once.

## 7. Limits of this version

- No time shift, no recording and no multiview yet (planned for the versions after 0.0.1, see the roadmap).
- Stalker portal, HDHomeRun, Enigma2 and TVHeadend sources are not implemented yet.
- Series in M3U playlists are listed as single movies.
- Favorites can be set for live channels only.
- The translations of Portuguese, Dutch, Polish, Russian and Arabic cover only the first screens, the rest shows English.
- The splash screen and icons are placeholders until the logo is delivered.

## 8. Troubleshooting

| Observation | What to check |
|---|---|
| `Login failed` on the card | Server address with port, user name, password. Open the address `<server>/player_api.php?username=..&password=..` in a browser, it must show account data. |
| Account loads but a channel does not play | Try another live format in the account (`TS` or `HLS`). Check that the provider allows another connection (card shows allowed and active connections). |
| Guide is empty | The channel has no guide id in the playlist, or the provider offers no XMLTV. Enter a guide URL in the account. |
| Very large lists load slowly | The first load of a big account takes a minute or more on weak devices. It runs in the background, the app stays usable. |
| Text in another language than expected | Settings, Language. |
