# ProIPTV: Release Signing and GitHub Secrets

- **Version:** 0.1 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers who create the release key and run the release workflow
- **Reference implementation:** `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `app/build.gradle` (signing config) in this repository

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Workflows

| Workflow | Start | Signing | Result |
|---|---|---|---|
| `CI` | every push, pull requests, manual | none (the fixed debug key `app/debug.keystore`) | test results, lint report, debug APK as build artifact |
| `Release` | push of a tag `v*`, or manual with the version | the release keystore from the repository secrets | GitHub release with APK, AAB, `SHA256SUMS.txt` and R8 mapping |

`CI` needs no secrets. The `Release` workflow stops with an error when a required secret is missing, so it cannot publish an unsigned or wrongly signed release by accident.

A tag with a suffix such as `v0.1.0-rc1` is published as a pre-release. The tag MUST match the content of `app/version.txt` (`0.1.0-rc1` in this example), otherwise the workflow stops.

Only repository secrets are used. The tab "Variables" is not needed, there are no repository variables.

## 2. Create the release keystore

Do this once, offline, on a machine you trust. You need a JDK (`keytool` is part of it).

Bash:

```bash
keytool -genkeypair -v -keystore proiptv-release.jks -alias proiptv-release \
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 50040
```

PowerShell (the line continuation is a backtick, not a backslash):

```powershell
keytool -genkeypair -v -keystore proiptv-release.jks -alias proiptv-release `
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 50040
```

Both work as one single line as well.

If PowerShell reports that `keytool` is not recognized, the JDK is not installed or not in the path. Android Studio brings its own JDK, call `keytool` from there (adjust the path if you installed Android Studio somewhere else):

```powershell
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
& $keytool -genkeypair -v -keystore proiptv-release.jks -alias proiptv-release `
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 50040
```

To find a `keytool.exe` on the computer: `Get-ChildItem "C:\Program Files" -Recurse -Filter keytool.exe -ErrorAction SilentlyContinue | Select-Object -First 5 FullName`. Without Android Studio, install a JDK instead: `winget install EclipseAdoptium.Temurin.17.JDK`, then open a new PowerShell window. The same call (`& $keytool ...`) is used for printing the fingerprint below.

- `-validity 50040` is 137 years. The number is symbolic, Android only needs the certificate to outlive the app.
- The keystore password and the key password MUST be long and unique. Store them in a password manager. When keytool asks for the key password, pressing Enter reuses the keystore password, which is fine here.
- Keep at least two offline backups of `proiptv-release.jks` in different places. **If this file is lost, no update can be signed any more, and every user has to uninstall and reinstall the app.**
- The keystore MUST NOT be committed, pasted into chats or stored anywhere except the offline backups and the repository secrets. `.gitignore` already excludes `*.jks`, `*.keystore` and `app/signing/`.
- Print the certificate fingerprint and note it down:

Bash:

```bash
keytool -list -v -keystore proiptv-release.jks -alias proiptv-release | grep SHA256
```

PowerShell:

```powershell
keytool -list -v -keystore proiptv-release.jks -alias proiptv-release | Select-String SHA256
```

The `SHA256:` line under "Certificate fingerprints" is the fingerprint.

## 3. Repository secrets

Open the repository on GitHub, then Settings, Secrets and variables, Actions, tab "Secrets", button "New repository secret". Enter the name exactly as written, paste the value, "Add secret".

| Secret | Required | Value |
|---|---|---|
| `PROIPTV_KEYSTORE_BASE64` | yes | the Base64 text of the keystore file, see below |
| `PROIPTV_KEYSTORE_PASSWORD` | yes | keystore password |
| `PROIPTV_KEY_ALIAS` | yes | `proiptv-release` |
| `PROIPTV_KEY_PASSWORD` | no | key password, defaults to the keystore password |
| `PROIPTV_RELEASE_CERT_SHA256` | no | the expected certificate SHA-256 from §2, then every release run fails if the APK carries another certificate |

Secrets cannot be read again after saving, only replaced. Use "Repository secrets", not "Environment secrets" (the workflow does not use environments).

### 3.1 Base64 text of the keystore

The keystore value is one very long line, so the easiest way is to write it to a text file and copy it from there.

Windows (PowerShell, not `cmd`), run in the folder that holds the keystore:

```powershell
cd C:\path\to\folder
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\proiptv-release.jks).Path)) | Out-File -Encoding ascii -NoNewline keystore-base64.txt
notepad keystore-base64.txt
```

Linux:

```bash
base64 -w0 proiptv-release.jks > keystore-base64.txt
```

macOS:

```bash
base64 -i proiptv-release.jks | tr -d '\n' > keystore-base64.txt
```

Open `keystore-base64.txt`, select everything (Ctrl+A), copy it (Ctrl+C) and paste it into the secret field `PROIPTV_KEYSTORE_BASE64`. The text must be one single line with no spaces and no line breaks, and the file does not need to be saved again. When the secret is stored, delete `keystore-base64.txt`, because it contains your signing key in readable form.

`Resolve-Path` is needed because .NET resolves a bare file name against its own working directory, which can differ from the folder shown by PowerShell, and then reports a missing file. To copy to the clipboard without a file, replace `Out-File ...` with `Set-Clipboard`.

### 3.2 Alternative: GitHub CLI

With the [GitHub CLI](https://cli.github.com/) installed and logged in (`gh auth login`), the secrets can be set from a terminal in the keystore folder. The values are read from the prompt or from files, so they do not end up in the shell history.

```bash
gh secret set PROIPTV_KEYSTORE_BASE64 --repo kutlusoy/proiptv < keystore-base64.txt
gh secret set PROIPTV_KEYSTORE_PASSWORD --repo kutlusoy/proiptv
gh secret set PROIPTV_KEY_ALIAS --repo kutlusoy/proiptv --body proiptv-release
gh secret set PROIPTV_KEY_PASSWORD --repo kutlusoy/proiptv
gh secret set PROIPTV_RELEASE_CERT_SHA256 --repo kutlusoy/proiptv
```

Check which secrets exist (names only, never values):

```bash
gh secret list --repo kutlusoy/proiptv
```

## 4. Run a release

1. Change the version in `app/version.txt` (`MAJOR.MINOR.PATCH`, or with a suffix such as `-rc1` for a pre-release) and merge the change to `main`. This one file is the version of the app: the version name is its text, the version code is derived from it by `tools/version.py` and the build (`major * 1,000,000 + minor * 1,000 + patch`). The version code MUST grow with every release, Android refuses an update with a lower or equal code, so always raise the number.
2. Tag the commit with `v` plus the version and push the tag:

```bash
git tag v0.0.1
git push origin v0.0.1
```

   Or open Actions, workflow "Release", "Run workflow" and enter the version without the `v`.
3. The workflow checks the tag against `app/version.txt`, runs the unit tests, builds the signed APK and AAB and verifies the APK: application ID, version name, version code and signing certificate. It publishes the files and `SHA256SUMS.txt`. The release page lists the signing certificate (SHA-256 and SHA-1), the SHA-256 of the APK and the bundle, the version code, the minimum and target Android version and the source commit, followed by the commands to verify a download.
4. Compare the fingerprint in the release notes with the one you noted in §2.

A failed run can be started again with "Re-run jobs". A run for an existing release replaces its files.

## 5. Signed build on your own machine

For a local signed build, create `app/signing/release.properties`:

```properties
storeFile=proiptv-release.jks
storePassword=...
keyAlias=proiptv-release
keyPassword=...
```

Copy the keystore into `app/signing/` and run `./gradlew assembleRelease`. The APK is written to `app/build/outputs/apk/release/`. Without this file the release build is unsigned.

## 6. Android Developer Verification

Google is introducing a verification of developers for apps installed on certified Android devices, also for apps outside Google Play. Check the current rules and dates in the Android Developer Console documentation before you register, they change.

- A personal account is enough. Distribution through Google Play is not required.
- Register the package name `com.kutlusoy.proiptv` with the SHA-256 fingerprint of the release certificate from §2. The steps and the proof file are in [`guideline-android-developer-verification.md`](./guideline-android-developer-verification.md).
- Keep the release key unchanged for the lifetime of the app. A key change requires Android key rotation and a new registration.
- Keep the account and its identity documents up to date. A lapsed verification can block installs on certified devices.

## 7. Google Play uses the same key

ProIPTV is first published on GitHub only. When it goes to Google Play later, the same private key MUST be used for Play App Signing, so an APK from GitHub and the app from Play carry the same certificate and can update each other. The steps (which button, which file, which command) are in [`guideline-google-play.md`](./guideline-google-play.md).

## 8. Troubleshooting

| Message | Cause and fix |
|---|---|
| `The repository secret for ... is missing` | The secret name is misspelled or the secret is set as an environment secret. Check §3, names are case sensitive. |
| `keytool` is not recognized (Windows) | No JDK in the path. Use the `keytool.exe` of Android Studio or install a JDK, see §2. |
| `base64: invalid input` | The value of `PROIPTV_KEYSTORE_BASE64` contains spaces or line breaks. Create it again as one line, see §3.1. |
| `Keystore was tampered with, or password was incorrect` | `PROIPTV_KEYSTORE_PASSWORD` is wrong. |
| `Cannot recover key` | `PROIPTV_KEY_PASSWORD` is wrong, or the key password differs from the keystore password and `PROIPTV_KEY_PASSWORD` is not set. |
| `app/version.txt says ... but the version is ...` | The tag and `app/version.txt` differ. Change the file and push a new tag, or delete the tag and tag again. |
| `does not match PROIPTV_RELEASE_CERT_SHA256` | The APK is signed with another key than expected. Check the keystore secret, or update the expected fingerprint when you changed the key on purpose. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on a device | An app with the same application ID is installed that was signed with another key, for example a release build that was signed with a different release key. Uninstall it once. Debug builds have their own ID (`com.kutlusoy.proiptv.debug`) and cannot cause this. |

## 9. Checklist

- [ ] Release keystore created offline (§2), two backups stored
- [ ] Certificate fingerprint noted
- [ ] Repository secrets set (§3)
- [ ] Package name registered with the fingerprint (§6)
- [ ] Same key enrolled in Play App Signing when Play is opened (§7)
- [ ] First release run green, fingerprint in the notes matches
- [ ] Update from the first release to the next one tested on a device
