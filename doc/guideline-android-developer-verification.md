# ProIPTV: Android developer verification

- **Version:** 0.1 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers who register the app with Google
- **Reference implementation:** `.github/workflows/release.yml` (check of the proof), `tools/release-notes.sh` (line on the release page)
- **See also:** [`guideline-release-signing.md`](./guideline-release-signing.md)

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. What it is

Google is introducing a verification of developers for apps that are installed on certified Android devices, also for apps outside Google Play. The package name and the signing key of the app are registered with the developer account. Check the current rules and dates in the Android Developer Console documentation before you register, they change. The registration is described on [Register Android package names](https://support.google.com/googleplay/android-developer/answer/16761053) (read the English page when in doubt).

ProIPTV has no Google account and no Google service inside. The registration only identifies you as the publisher of the package, it does not change the app. The registration is done once for `com.kutlusoy.proiptv` and holds for GitHub builds and later for the Play version, because both use the same key.

## 2. What is prepared in the repository

| Item | Where | What it does |
|---|---|---|
| Proof of ownership | `app/src/main/assets/adi-registration.properties` | The snippet of the console. The release APK contains it, so the APK of a normal release can be uploaded as proof. |
| Check in the release | `.github/workflows/release.yml` | Warns when the file is missing, stops when it does not look like a snippet or is not part of the APK. |
| Line on the release page | `tools/release-notes.sh` | "Android developer verification proof: included in the APK" or "not included". |
| Signing fingerprint | release page | SHA-256 and SHA-1 of the release certificate, with colons as the console wants them. |

The file is not in the repository yet. Until it is added (§4) a release is built without the proof and the workflow prints a warning. The snippet comes from the console and belongs to the developer account.

## 3. Register the package name

1. Create the release key and set the secrets, see `guideline-release-signing.md`.
2. Open the Play Console (a personal account is enough, distribution through Google Play is not required), "Identity verification for Android developers", tab "Packages", "Register package name".
3. Enter the package name `com.kutlusoy.proiptv` and a friendly name, for example `ProIPTV`.
4. A package name that was never used on Android needs only the public certificate: choose "Add key" and enter the **SHA-256 fingerprint of the release certificate**. It is printed on every release page and by `keytool -list -v -keystore proiptv-release.jks -alias proiptv-release`. The console accepts it with colons.
5. The console then shows the **snippet** and asks for a proof of ownership: an APK that is signed with the private release key and contains the snippet.

Do **not** register the debug application ID `com.kutlusoy.proiptv.debug` and do not register the key of the debug builds (`app/debug.keystore`, it is public). Debug builds have their own ID on purpose, so they are never mixed up with the registered release app.

## 4. Put the snippet into the app

1. Copy the snippet exactly as the console shows it.
2. Create the file `app/src/main/assets/adi-registration.properties` with the snippet as its only content, no extra text. In PowerShell, in the folder of the repository:

```powershell
Set-Content -Path app\src\main\assets\adi-registration.properties -Value 'PASTE-THE-SNIPPET-HERE' -Encoding ascii
```

3. Commit the file. The snippet identifies the account and is not a secret. Whether the file may be removed after the registration is not stated on the Google page, so it stays in the repository.
4. Publish a release (see `guideline-release-signing.md` §4). The release page now says that the proof is included.
5. Download the APK of that release and upload it in the console under "Upload". The APK is only used to check the key and the snippet, it is not published. Android checks the signature and registers the ownership.

## 5. After the registration

- Keep the release key unchanged for the lifetime of the app. A key change needs Android key rotation and a new registration.
- Keep the account and its identity documents up to date. A lapsed verification can block installs on certified devices.
- Installing a build through `adb` stays possible for developers and testers, also for builds that are signed with the debug key.
- Every release SHOULD still contain the file, so the proof is always available.

## 6. Troubleshooting

| Message | Cause and fix |
|---|---|
| Warning `adi-registration.properties is missing` | The file does not exist yet, see §4. The release is built anyway. |
| Error `does not look like the snippet of the console` | The file is empty or very long. It must contain only the snippet. |
| Error `exists but is not part of the APK` | The file is not in `app/src/main/assets/`, or the path or name is spelled differently. The name is fixed. |
| The console rejects the uploaded APK | The APK is not signed with the registered key (a debug or an older build), or it was built before the file was added. Upload the APK of a release that says "included". |

## 7. Checklist

- [ ] Release key created and the secrets set
- [ ] Package name `com.kutlusoy.proiptv` registered with the SHA-256 fingerprint of the release certificate
- [ ] Snippet saved as `app/src/main/assets/adi-registration.properties` and committed
- [ ] Release published, the page says "included in the APK"
- [ ] APK of that release uploaded in the console, registration confirmed
