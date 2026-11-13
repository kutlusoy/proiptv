# ProIPTV: Feature Matrix and Content Types

- **Version:** 0.2 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers and contributors who decide what ships in which version
- **Reference implementation:** [`guideline-product-plan.md`](./guideline-product-plan.md) (sections 4, 5, 6, 7 and 12), this document refines its scope
- **See also:** the XCIPTV Player by OTTRun as a market reference (listing and independent guides, see section 7)

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Content types

ProIPTV MUST handle every kind of content a provider can offer, from both source types.

| Content | Xtream account | M3U account | Version |
|---|---|---|---|
| Live channels with groups | `get_live_categories`, `get_live_streams` | `#EXTINF` entries with `group-title`, `tvg-id`, `tvg-logo`, `tvg-name` | 0.0.2 |
| Playlist import | `get.php` M3U as fallback | URL, local file, gzip, very large lists (100,000+ entries, streamed) | 0.0.2 |
| EPG | `xmltv.php`, `get_short_epg`, `get_simple_data_table` | XMLTV by URL (plain, gzip), several EPG sources merged per channel | 0.0.4 |
| Catch-up | timeshift URL from `tv_archive` and `tv_archive_duration` | `catchup`, `catchup-days`, `catchup-source` attributes | 0.0.8 |
| Movies (VOD) | `get_vod_categories`, `get_vod_streams`, `get_vod_info` | entries recognized by URL path (`/movie/`), group names or file extension | 0.0.8 |
| Series | `get_series_categories`, `get_series`, `get_series_info` with seasons and episodes | entries grouped by the `SxxExx` pattern in the title, shown as seasons | 0.0.8 |
| Posters, plot, cast, rating, trailer | from the Xtream info calls | from `tvg-logo` only | 0.0.8 |
| Resume and watched marks | local, per account | local, per account | 0.0.8 |
| Favorites for channels, movies and series | local, per account | local, per account | 0.0.3 |

Notes:

- Content type detection for M3U MUST be tolerant and overridable per group by the user (live, movie, series).
- The EPG channel mapping runs by `tvg-id`, then by normalized name, then by a manual override.
- Large lists are always streamed into Room and read through paging, see the product plan section 5.3.

## 2. Live TV and guide

| Feature | Version |
|---|---|
| Channel list with groups, search, hide, reorder | 0.0.3 |
| Number zapping, last channel, up and down zapping | 0.0.3 |
| Channel overlay while playing, now and next | 0.0.3 |
| Guide that stays current: refresh on app start, while open and in the background, conditional download, keeps old data on failure, per channel fallback | 0.0.1 |
| Full EPG grid (day and week), program details | 0.0.4 |
| Program reminders from the guide | 1.x |
| Record from the guide, one tap | 0.0.6 |
| Catch-up from the guide | 0.0.8 |

## 3. Engine features

| Feature | Version |
|---|---|
| Parallel streams, multiview | 0.0.7 |
| Time shift (pause, rewind, jump to live) | 0.0.5 |
| Scheduled recording to internal or external storage | 0.0.6 |
| One provider connection per channel for watch, time shift and recording | 0.0.5 |
| Connection budget per account | 0.0.5 |
| Failover to an equal channel, stall detection | 0.0.7 |
| Picture in picture | 0.1.x |

## 4. Accounts and organization

| Feature | Version |
|---|---|
| Several accounts (Xtream and M3U), each with its own credentials | 0.0.2 |
| Account status: expiry, connection limit, active connections | 0.0.2 |
| Active account switch or merged list | 0.0.3 |
| Backup and restore of accounts and settings to a file | 0.0.8 |
| Parental PIN, locked groups | 0.0.8 |
| Phone setup with QR code | 1.x |

## 5. Player engine and fallback

The market reference offers two built-in players (ExoPlayer and VLC) so the user can switch when a channel does not play. ProIPTV uses one engine, Media3, and covers the same need in this order:

1. Tolerant stream settings by default (relaxed transport stream parsing, audio codec detection, retry with another buffer profile)
2. Per-account and per-channel options (user agent, headers, buffer profile, decoder choice between hardware and software)
3. Failover to an equal channel
4. Only if real streams still fail: an optional second engine behind the same interface (not part of 1.0, a separate decision with its APK size cost)

## 6. Where ProIPTV aims to be better

| Weakness reported for comparable apps | ProIPTV answer |
|---|---|
| Large playlists are slow to browse on budget sticks | streaming import, Room with FTS, paging, benchmark target in CI |
| Few options to organize channel groups | hide, rename, reorder and merge groups per account |
| Recording fails when the storage is low or the connection limit is hit | storage check before start, connection budget, one connection per channel through the hub |
| Plain, less customizable UI | modern Compose UI with themes (design review in 0.0.3) |
| Closed source, trust problems with branded copies | MIT, signed releases, fingerprint and checksums on every release page |

## 7. Reference notes on the market app

The XCIPTV Player (OTTRun) is a useful benchmark. The following is taken from its public listing and independent guides, and some points come from a single source, so they MUST be verified in the app itself before they are used as a requirement.

| Point | Evidence |
|---|---|
| Xtream Codes and M3U login, EZServer also named in the listing | listing and guides |
| Two built-in players, ExoPlayer and VLC | guide |
| Adaptive HLS, picture in picture on some devices | guide |
| EPG with now/next and weekly view, catch-up from the guide, reminders | listing and guides |
| Movies with IMDb info, series by season and episode | listing and guides |
| Scheduled recording to internal or external storage | listing and guides |
| Favorites for channels, movies and series, backup and restore with sync | guide |
| Several profiles with separate credentials | one FAQ source only |
| Parental control, themes, multiview | not confirmed in the sources found |

A hands-on review of the app (install, add a test account, walk through live, guide, VOD, series, recording) SHOULD be done once by the maintainer with a legal test source, with notes added here. Nothing is copied from the app, only features and flows are compared.


## 8. Sources and servers

ProIPTV is built around small **source adapters**, one per source type, behind one interface (`SourceProvider`: login, status, categories, channels, guide, VOD, series, stream URL). A new source type is one new module, so the app stays lean even with many sources, and a user who needs only one source pays nothing for the others at run time.

| Source type | What it is | Content | Tier | Version |
|---|---|---|---|---|
| Xtream Codes API | login with server, user name and password, `player_api.php` | live, VOD, series, EPG, catch-up | 1 | 0.0.2 |
| M3U/M3U8 playlist | URL or local file, gzip, huge lists | live, VOD and series by detection | 1 | 0.0.2 |
| XMLTV guide | URL or file, plain or gzip, several merged | EPG | 1 | 0.0.4 |
| Stalker portal (Ministra) | portal URL plus a MAC address, see section 8.1 | live, VOD, series | 1 | 0.0.9 |
| HDHomeRun | network TV tuner in your own LAN (`lineup.json`) | live | 1 | 0.0.9 |
| Enigma2 (OpenWebif) | satellite or cable receiver in your own LAN, bouquets | live, EPG, recordings of the box | 1 | 0.0.9 |
| TVHeadend | server in your own LAN or on the internet (HTTP API) | live, EPG, recordings of the server | 1 | 0.0.9 |
| Jellyfin and Emby Live TV | your own media server | live, EPG, recordings of the server | 2 | 1.x |
| NextPVR, DVBLink | your own PVR server | live, EPG | 2 | 1.x |
| SAT>IP | network satellite tuner in your own LAN (RTSP) | live | 2 | 1.x |
| Plex Live TV | your own Plex server | live | 3, only if the API allows it | later |

Tier 1 is part of the 1.0 goal, tier 2 follows, tier 3 depends on the API and its terms. Every source adapter MUST be tested against recorded fixtures or a fake server, never against a real provider in CI.

**Legal line.** ProIPTV supports open protocols for content the user has the right to watch: their own provider account, their own tuner, their own server. It ships no channel lists, offers no search for playlists, and does not help bypass a provider's limits, encryption or region locks. Sources that exist only to make unlicensed content available are not added. Store text, README and the first start screen say that the user supplies the source.

### 8.1 What is a Stalker portal

"Stalker" is the name of the older middleware (today **Ministra**, by the company Infomir) that many IPTV providers use to run their service for **set-top boxes of the MAG type**. The box is not logged in with a user name and password. It is identified by its **MAC address** and the **portal URL** of the provider. The box asks the portal for a session (handshake), then for the profile, the channel list, the guide and, for every channel, a short-lived stream link. Providers that use it usually hand out a portal URL and a MAC address instead of an Xtream login.

For ProIPTV this means: a Stalker source has two fields, the **portal URL** and the **MAC address** the provider registered, and the app speaks the portal protocol (`portal.php` or `stalker_portal/server/load.php`) to list channels, VOD and series and to request stream links.

Notes and limits:

- The protocol itself is neutral, many legal providers use it. Whether the content is legal depends on the provider and the user's contract, not on the protocol.
- The portal expects the client to behave like a MAG box (it sends a device type and a user agent). ProIPTV only sends what the protocol needs, with the MAC address the user enters. It does not ship a list of MAC addresses and does not generate or guess addresses.
- Stalker portals differ a lot between providers and versions. The adapter needs a tolerant implementation and many fixtures, so it is planned for `0.0.9`, after the Xtream path is stable.
- Stream links expire quickly, so the engine MUST ask for a fresh link on every channel start and on every reconnect.

## 9. Languages

The app is fully translated and the language can be changed inside the app without changing the system language ("Automatic" follows the device).

| Tier | Languages | When |
|---|---|---|
| 1 | English (default), German, Turkish, French, Italian, Spanish, Portuguese, Dutch, Polish, Russian, Arabic | from version 0.0.1 with the placeholder texts, complete at 0.1.0-rc1 |
| 2 | Romanian, Swedish, Greek, Ukrainian, Bulgarian, Hungarian, Czech, Serbian, Croatian, Persian, Hindi, Indonesian, Japanese, Korean, Chinese (simplified) | after 1.0, through contributions |

Rules:

- Texts live in `app/src/main/res/values/` (English) and `values-xx/strings.xml`. The unit test checks that **every** language folder has the same keys and the same placeholders as English, so a missing text breaks CI.
- Right to left languages (Arabic, later Persian) MUST be laid out correctly. `supportsRtl` is on, and the UI uses start and end, never left and right.
- Texts for the store listing are translated for the same languages.
- Machine drafts MUST be reviewed by a native speaker before a release says "complete" for that language. Until then the language is marked as draft in the documentation.
- To add a language: add `values-xx/strings.xml` with all keys and add it to the language list of the settings screen.

## 10. Favorites and manual order

| Feature | Detail | Version |
|---|---|---|
| Favorites | one tap or one key from the channel list, the player or the guide | 0.0.3 |
| Several favorite lists | named lists such as "Sport" or "Kids", a channel can be in several lists | 0.0.3 |
| Manual sort of favorites | touch: drag and drop. Remote control: press OK on a channel to pick it up, up and down move it, OK puts it down, back cancels | 0.0.3 |
| Manual sort of any group | the same for the channels of a normal group, plus custom channel numbers | 0.0.5 |
| Per account or merged | favorite lists belong to an account, a merged list can hold channels of several accounts | 0.0.3 |
| Survives a resync | channels are matched by a stable id (Xtream stream id, Stalker id, or `tvg-id` and URL hash for M3U), so a provider refresh does not lose the order | 0.0.3 |
| Gone channels | a favorite whose channel disappeared stays in the list, greyed out, for a grace period | 0.0.3 |
| Backup | lists and order are part of the backup file | 0.0.8 |
| Zapping | up and down zapping follows the active favorite list and its order | 0.0.3 |

The order is stored as a sort key per list entry (a gap based key, so moving one channel changes one row, not the whole list).

## 11. Checklist

- [ ] Content types of section 1 covered by parser tests with recorded Xtream and M3U fixtures
- [ ] M3U content type detection overridable per group
- [ ] Hands-on review of the market app done and notes added to section 7
- [ ] Decision on a second engine postponed to after 0.1.0
- [ ] Every source adapter has recorded fixtures or a fake server test
- [ ] Every language folder passes the string test
- [ ] Favorites keep their order after a resync (test with a changed provider list)

## 12. Open Questions

1. Should EZServer compatibility be added (named in the listing of a market app), or are the sources of section 8 enough?
2. Which tier 2 sources are wanted first (Jellyfin and Emby, NextPVR, SAT>IP)?
