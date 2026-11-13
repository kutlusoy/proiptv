# ProIPTV privacy statement

ProIPTV is an open source IPTV player (MIT license). It is a player: it contains no channels and no content, you add the accounts of your own provider.

## What the app does with data

- **No account with us.** The app has no user accounts, no registration and no server of the developers.
- **No tracking.** There is no analytics, no advertising, no crash reporting service. Nothing about you or your use is sent to the developers.
- **Your provider accounts.** Server address, user name and password of the accounts you add are stored on your device only. Passwords are encrypted with the Android Keystore. The app sends them only to the server address you entered, to log in and to load channels, programs, movies and series.
- **Servers you configure.** The app connects to the addresses you enter or that your provider's playlists contain: the provider's server, guide (XMLTV) address, stream addresses and the addresses of channel logos and posters. These servers see your IP address and the requests, as with any other player. Their handling of data is governed by their own privacy policy.
- **Local data.** Channels, the program guide, favorites, watch positions, settings and recordings are stored on the device. Recordings are files in the app's storage folder. Deleting an account or a recording in the app deletes its data. Uninstalling the app removes all of it.
- **Time shift buffer.** While you watch live TV, the last minutes of the channel are kept in a temporary file in the app's cache and removed when you stop.
- **Updates.** This version has no update check. If a later version offers one, it will contact the GitHub API only when you press a button, and this statement will be updated.
- **Permissions.** Network access to load streams, notifications and a foreground service while a recording runs, and a wake lock to keep a recording alive with the screen off.

## Your control

You can delete accounts, favorites and recordings in the app at any time, and clear all data in the Android settings for the app.

## Contact

Questions or requests: open an issue at https://github.com/kutlusoy/proiptv/issues.

Last update: November 13, 2026
