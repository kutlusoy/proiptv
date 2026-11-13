# ProIPTV: Feature Matrix and Content Types

- **Version:** 0.1 (draft)
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

## 8. Checklist

- [ ] Content types of section 1 covered by parser tests with recorded Xtream and M3U fixtures
- [ ] M3U content type detection overridable per group
- [ ] Hands-on review of the market app done and notes added to section 7
- [ ] Decision on a second engine postponed to after 0.1.0

## 9. Open Questions

1. Should EZServer compatibility be added (named in the market app listing), or only Xtream Codes, M3U and XMLTV?
2. Should Stalker portal be planned for 1.x?
