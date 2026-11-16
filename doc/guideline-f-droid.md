# ProIPTV: F-Droid and other channels

- **Version:** 0.1 (draft)
- **Date:** November 16, 2026
- **Audience:** Maintainers who publish ProIPTV on F-Droid and other app stores
- **Reference implementation:** `app/build.gradle` (`storeBuild`, `SELF_UPDATE`), `app/src/store/AndroidManifest.xml`, `fastlane/metadata/android/`
- **See also:** [`guideline-google-play.md`](./guideline-google-play.md), [`guideline-release-signing.md`](./guideline-release-signing.md), [F-Droid inclusion policy](https://f-droid.org/docs/Inclusion_Policy/)

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. What the repository provides

| Item | Where |
|---|---|
| Store variant without self update | `./gradlew assembleRelease -PstoreBuild=1` |
| Store manifest overlay (removes `REQUEST_INSTALL_PACKAGES` and the install receiver) | `app/src/store/AndroidManifest.xml` |
| Store texts, icon and changelogs (`en-US`, `de-DE`) | `fastlane/metadata/android/` |
| No Google dependencies, no tracking, MIT license | checked, see section 4 |
| No dependency block in the APK | `dependenciesInfo` in `app/build.gradle` |

With `-PstoreBuild=1` the settings group "Update", the daily update worker and the installer permission are gone, because F-Droid and other stores deliver the updates. The GitHub build keeps the in-app update.

## 2. Same key on all channels (reproducible build)

F-Droid signs with its own key by default. Then the F-Droid app and the GitHub or Play app have different signatures and cannot update each other. To keep the one release key, ask F-Droid for a **reproducible build**: F-Droid builds the tag itself, compares the result with the APK signed by you and publishes your signed APK.

- The recipe needs `Binaries:` (URL of the signed release APK) and `AllowedAPKSigningKeys:` (SHA-256 of the release certificate, lowercase, no colons).
- The build MUST be identical byte for byte. Check it locally before the request (section 5).
- If the build is not reproducible, F-Droid signs with its own key and the F-Droid version is a separate line of installs. Users then have to uninstall to switch channel.

## 3. Recipe for fdroiddata

The recipe is a file `metadata/com.kutlusoy.proiptv.yml` in a merge request to [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata). Draft:

```yaml
Categories:
  - Multimedia
License: MIT
WebSite: https://github.com/kutlusoy/proiptv
SourceCode: https://github.com/kutlusoy/proiptv
IssueTracker: https://github.com/kutlusoy/proiptv/issues

AutoName: ProIPTV

RepoType: git
Repo: https://github.com/kutlusoy/proiptv.git

Binaries: https://github.com/kutlusoy/proiptv/releases/download/v%v/<apk-name>.apk

Builds:
  - versionName: 0.0.9
    versionCode: 9
    commit: v0.0.9
    subdir: app
    sudo:
      - apt-get update
      - apt-get install -y openjdk-17-jdk-headless
      - update-java-alternatives -a
    gradle:
      - yes
    gradleprops:
      - storeBuild=1
    output: build/outputs/apk/release/app-release-unsigned.apk

AllowedAPKSigningKeys: <sha256 of the release certificate>

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+$
CurrentVersion: 0.0.9
CurrentVersionCode: 9
```

Notes:
- `<apk-name>` MUST be replaced by the real asset name of the release, see the release page.
- The `output` path of the unsigned APK MUST be checked with a local build, it depends on the signing setup.
- `gradleprops` hands `-PstoreBuild=1` to Gradle. Without it the variant still contains the installer permission.
- The version code follows `major * 1000000 + minor * 1000 + patch`, so 0.0.9 is 9.

## 4. Inclusion policy check

| Rule | State |
|---|---|
| Free license | MIT, `LICENSE` |
| Source only builds, no binary blobs | Gradle build from source, `desktop` and the Windows libVLC are not part of the Android build |
| No Google Play Services, Firebase, ads, analytics | none in `app/build.gradle` |
| No self update | removed with `storeBuild` |
| No Google dependency metadata | `dependenciesInfo` off |
| Network use | only the provider, TMDB if the user enters a key, and GitHub for the update check (not in the store build) |
| Metadata in the repository | `fastlane/metadata/android/` |

The scanner of F-Droid MUST pass on the unsigned release APK. Run it locally (section 5) and fix findings before the merge request.

## 5. Local check

```bash
./gradlew assembleRelease -PstoreBuild=1
```

Then check the APK:

- `aapt2 dump permissions` MUST NOT list `REQUEST_INSTALL_PACKAGES`.
- The settings screen MUST NOT show the update group.
- Build twice in clean folders and compare with `diffoscope` or `apksigcopier compare` (reproducibility).

With `fdroidserver` installed in a checkout of `fdroiddata`:

```bash
fdroid lint com.kutlusoy.proiptv
fdroid build -v -l com.kutlusoy.proiptv
fdroid scanner com.kutlusoy.proiptv
```

## 6. Release routine

1. Raise `app/version.txt` and add `fastlane/metadata/android/<lang>/changelogs/<versionCode>.txt` (maximum 500 bytes).
2. Tag `vX.Y.Z`, the release workflow publishes the signed APK.
3. F-Droid picks up the tag (`UpdateCheckMode: Tags`), builds, compares and publishes.

## 7. Other channels

| Channel | Notes |
|---|---|
| IzzyOnDroid | Takes the signed GitHub release APK directly, needs the same F-Droid style metadata, no recipe build. Use the store variant (`-PstoreBuild=1`) APK or accept the update permission. |
| Google Play | See [`guideline-google-play.md`](./guideline-google-play.md). Self update via installer MUST NOT be active in the Play build, use `-PstoreBuild=1` there as well. |
| Amazon Appstore, Aptoide and others | Same store variant. |

## 8. Checklist

- [ ] Screenshots added under `fastlane/metadata/android/en-US/phoneScreenshots/` (and `tenInchScreenshots/`, `tvScreenshots/`)
- [ ] Release key fingerprint entered in `AllowedAPKSigningKeys`
- [ ] Local build is reproducible
- [ ] `fdroid lint` and `fdroid scanner` pass
- [ ] Merge request to `fdroiddata` opened

## 9. Open Questions

1. Should the GitHub release publish a second APK built with `-PstoreBuild=1` for IzzyOnDroid, or one APK for all?
