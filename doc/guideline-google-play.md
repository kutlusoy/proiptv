# ProIPTV: Google Play, application ID and signing key

- **Version:** 0.1 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers who register the app ID and later publish on Google Play
- **Reference implementation:** `app/build.gradle` (application ID, signing config), `.github/workflows/release.yml`
- **See also:** [`guideline-release-signing.md`](./guideline-release-signing.md), [`guideline-android-developer-verification.md`](./guideline-android-developer-verification.md), [`guideline-product-plan.md`](./guideline-product-plan.md) section 11

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

The names of menus in the Play Console change from time to time. If a name below does not exist, search the console help for the quoted term, the steps stay the same.

---

## 1. The rule in one sentence

ProIPTV is published first on GitHub only, but the application ID and the signing key are chosen now for both channels: the ID is `com.kutlusoy.proiptv` and the **same private release key** signs the GitHub APK and, through Play App Signing, the Play version. Then an update works from either channel over the other.

| Item | Value | Where it is set |
|---|---|---|
| Application ID | `com.kutlusoy.proiptv` | `app/build.gradle`, `applicationId` (already set) |
| Debug application ID | `com.kutlusoy.proiptv.debug` | automatic, never registered anywhere |
| Release key | `proiptv-release.jks`, alias `proiptv-release` | created by you, [`guideline-release-signing.md`](./guideline-release-signing.md) §2 |
| Certificate fingerprint (SHA-256) | printed by `keytool`, shown on every release page | needed for the registration below |

The application ID MUST NOT change after the first upload to Google Play, it is permanent.

## 2. What you enter where (overview)

| Step | Where | What you enter |
|---|---|---|
| 1 | your computer | create the release keystore and note the SHA-256 fingerprint |
| 2 | GitHub, Settings, Secrets and variables, Actions | the five `PROIPTV_*` secrets, see the signing guideline §3 |
| 3 | Play Console, Android developer verification | package name `com.kutlusoy.proiptv` and the SHA-256 fingerprint |
| 4 | repository | the snippet from the console as `app/src/main/assets/adi-registration.properties` |
| 5 | GitHub, Actions | run the release, download the APK of the release |
| 6 | Play Console | upload that APK as proof of ownership |
| 7 | Play Console, create app | app name, package name, language, free app |
| 8 | Play Console, App signing | export your release key and upload it, see §4 |

Steps 1 to 6 are needed for GitHub releases already (developer verification). Steps 7 and 8 are done when Google Play is opened.

## 3. Register the package name with the fingerprint

This is the registration that ties `com.kutlusoy.proiptv` to your key. The full steps and the proof file are in [`guideline-android-developer-verification.md`](./guideline-android-developer-verification.md). In short:

1. Open the Play Console (a personal developer account is enough, publishing in the Play store is not required for this step).
2. Open "Identity verification for Android developers", tab "Packages", button "Register package name".
3. Package name: `com.kutlusoy.proiptv`. Friendly name: `ProIPTV`.
4. Choose "Add key" and paste the **SHA-256 fingerprint** of your release certificate. Print it with:

```bash
keytool -list -v -keystore proiptv-release.jks -alias proiptv-release | grep SHA256
```

PowerShell:

```powershell
keytool -list -v -keystore proiptv-release.jks -alias proiptv-release | Select-String SHA256
```

The console accepts the fingerprint with colons (`AB:CD:...`).
5. Copy the snippet the console shows into `app/src/main/assets/adi-registration.properties` (only the snippet, nothing else) and commit it.
6. Publish a release, download the APK, and upload it in the console as the proof.

Do **not** register `com.kutlusoy.proiptv.debug` and never register the key `app/debug.keystore` (it is public).

## 4. Play App Signing with your own key (when Google Play is opened)

By default Google creates and holds a new signing key for you. For ProIPTV this MUST NOT be accepted, because a different key would break updates between GitHub and Play. Instead, the existing release key is handed to Play once.

1. In the Play Console create the app: "Create app", app name `ProIPTV`, default language, "App", "Free", accept the declarations. The package name is fixed by the first upload.
2. Open the page for your first release (Test and release, Testing, Internal testing, "Create new release"). The step "App integrity" or "Choose signing" offers: "Use Play App Signing", then the option **"Upload a key exported from Java Keystore"** (the wording may be "Use existing app signing key").
3. The console shows a link to Google's **PEPK tool** (`pepk.jar`) and a public key file (`encryption_public_key.pem`). Download both into the folder with your keystore.
4. Export the key. The command is shown by the console, it looks like this (one line):

```bash
java -jar pepk.jar --keystore=proiptv-release.jks --alias=proiptv-release --output=proiptv-play-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
```

PowerShell (call `java` from the Android Studio JDK if it is not in the path, see the signing guideline §2):

```powershell
java -jar pepk.jar --keystore=proiptv-release.jks --alias=proiptv-release --output=proiptv-play-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
```

Java asks for the keystore password and the key password. The output file is encrypted so only Google can read it.
5. Upload `proiptv-play-key.zip` in the console where it asks for the exported key. Delete the zip afterwards.
6. Upload the AAB of the release (`ProIPTV-<version>.aab`, it is part of every GitHub release) and finish the release.
7. In "Test and release, App integrity, App signing" check that the **SHA-256 of the app signing key certificate** is the same as the fingerprint you noted in §3. If it differs, Play created its own key and the setup has to be redone before real users install.

An **upload key** is a second key that only authorizes uploads to the console. Google recommends a separate one. If the console offers to create one, accept it and store `proiptv-upload.jks` like the release key (two offline backups, never in the repository). The release key itself then stays the signing key for both channels.

## 5. Two builds, one ID (later)

Google Play does not allow an app to install updates from outside the store. When Play is opened, the update code and the permission `REQUEST_INSTALL_PACKAGES` move into a `direct` flavor for GitHub, and a `play` flavor without them goes to Play. Both flavors keep the application ID `com.kutlusoy.proiptv` and the same key. Until then there is one build.

## 6. Store listing checklist

| Item | Where in the console |
|---|---|
| App name, short and full description in English, German, Turkish | Grow, Store presence, Main store listing |
| Icon 512 x 512, feature graphic 1024 x 500, screenshots (phone and TV) | same page, generated from the logo SVG |
| Privacy policy URL | App content, Privacy policy |
| Data safety form (no data collected, no analytics, no ads) | App content, Data safety |
| Ads declaration: no ads | App content, Ads |
| Content rating questionnaire | App content, Content rating |
| Target audience | App content, Target audience |
| Android TV: leanback launcher, banner 320 x 180, TV screenshots | built into the app, listing assets |

The listing text MUST say that ProIPTV is a player without content and that the user supplies the source. Screenshots MUST only show legal test content. A contact for takedown requests MUST be given.

## 7. Troubleshooting

| Message | Cause and fix |
|---|---|
| The console says the package name is already taken | Another developer uses the ID. It cannot be reused, a new ID would have to be chosen before the first upload. |
| The upload is rejected because of the signing key | The AAB was signed with another key than the one enrolled in Play App Signing. Build the release with the release key. |
| App signing certificate differs from the GitHub release | Play created its own key. Redo §4 with the existing key before real users install. |
| `pepk.jar` asks for a password again and again | Wrong keystore or key password. Compare with the signing guideline §2. |
| Users get `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when switching channels | The two channels carry different certificates. Check §4 step 7. |

## 8. Checklist

- [ ] Release key created offline, two backups, fingerprint noted
- [ ] Package name registered with the fingerprint (§3)
- [ ] Snippet in `app/src/main/assets/adi-registration.properties`, release says "included"
- [ ] Play app created with `com.kutlusoy.proiptv`
- [ ] Release key exported with PEPK and uploaded, certificate fingerprints match (§4)
- [ ] Upload key created and stored (optional but recommended)
- [ ] Store listing and policy forms filled (§6)
