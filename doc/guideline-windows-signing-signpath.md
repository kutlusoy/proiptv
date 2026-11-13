# ProIPTV: Windows code signing with SignPath Foundation (free)

- **Version:** 0.1 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers who set up free code signing for the Windows installer
- **Reference implementation:** the Windows job described in [`guideline-windows-version.md`](./guideline-windows-version.md) section 4 (the job is added to `.github/workflows/release.yml` when the desktop app exists)
- **See also:** [`guideline-windows-signing-azure.md`](./guideline-windows-signing-azure.md) (alternative that needs a registered company), [`guideline-release-signing.md`](./guideline-release-signing.md) for the Android key, which is a different key, [`../PRIVACY.md`](../PRIVACY.md)

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

The conditions below come from the public pages of SignPath Foundation (`signpath.org/terms`) and the SignPath documentation (`docs.signpath.io`) as read on the date above. The application form itself and some SignPath screens could not be checked, the points marked **verify** MUST be compared with the current pages before you rely on them.

---

## 1. What you get and what you must accept

SignPath Foundation gives open source projects free Authenticode code signing. The signing happens at SignPath, GitHub never holds a key or a certificate file.

| Point | Consequence for ProIPTV |
|---|---|
| The certificate belongs to **SignPath Foundation** | Windows shows **SignPath Foundation** as the publisher of the installer, not "ProIPTV" and not your name. SmartScreen warnings fade with the number of downloads, as with every certificate. |
| Only for projects that are already released | The first Windows installer MUST be published **unsigned** (GitHub pre-release with a note about the SmartScreen warning). Apply after that. |
| All components under an OSI approved license, no proprietary code | ProIPTV is MIT. The bundled Java runtime and libVLC are open source. Unsigned third party files (for example the libVLC DLLs) MAY be inside the signed installer. |
| Only your own code from your own build | The installer is built from this repository by the GitHub workflow. Forks and other people's binaries are not signed. |
| Every release needs manual approval | You press "Approve" in SignPath for each signing request. A release is never signed by accident. |
| Everybody with access uses multi-factor authentication | On GitHub and on SignPath. |
| Public "Code signing policy" and a privacy statement | README and download page, see section 3. |
| Product name and version in the file metadata are checked | The installer and its executables MUST carry the product name `ProIPTV` and a consistent product version (**verify** the exact rule in the application and in the SignPath project settings). |
| Acceptance is discretionary | SignPath Foundation may refuse, pause or revoke. The fallback is Azure (company needed) or a release without signature. |

The Android app is not affected, it keeps its own release key.

## 2. Before you apply: checklist

- [ ] The repository `kutlusoy/proiptv` is public and the Windows installer is built by the GitHub workflow
- [ ] A Windows release has been published (unsigned pre-release is fine)
- [ ] The release page or README describes what the app does
- [ ] [`PRIVACY.md`](../PRIVACY.md) is in the repository and linked from the README
- [ ] Two-factor authentication is on for your GitHub account: GitHub, Settings, "Password and authentication", "Two-factor authentication"
- [ ] The project is actively maintained (recent commits)

## 3. The code signing policy on README and download page

Add this section to the README and to the download page (the release notes text of the Windows installer). It MUST NOT be added before SignPath Foundation has accepted the project, because the statement would not be true yet.

```markdown
## Code signing policy

Free code signing provided by [SignPath.io](https://signpath.io), certificate by [SignPath Foundation](https://signpath.org).

Team roles:

- Authors, reviewers and approvers: [kutlusoy](https://github.com/kutlusoy)

Only installers built from the source code of this repository by the GitHub workflow in `.github/workflows/release.yml` are signed. Every release is approved by hand before signing.

Privacy: see [PRIVACY.md](PRIVACY.md).
```

Add more people to the role list when the project has more maintainers. For a project with one maintainer all three roles are the same person, say so in the application (**verify** that the Foundation accepts this, it is common for small projects).

## 4. Apply

1. Open `signpath.org`, "Apply" (`signpath.org/apply`).
2. Fill the form. It asks for the data of the project and of you, for example project name `ProIPTV`, repository `https://github.com/kutlusoy/proiptv`, license `MIT`, a short description, the download page (the GitHub releases page), the link to the code signing policy and the privacy statement, and your contact data (**verify** the current fields).
3. Wait for the answer by e-mail. Reply quickly to questions, the Foundation may ask for changes to the policy text.

## 5. Set up SignPath after the acceptance

You get access to the SignPath portal (`app.signpath.io`). The menu names may differ slightly.

1. **Multi-factor authentication:** enable it for your SignPath user (profile, security).
2. **Organization ID:** shown in the portal under the organization settings. Note it.
3. **Trusted build system:** Organization, "Trusted build systems", the predefined entry **GitHub.com** MUST be linked to your project (Project, "Trusted build systems", add GitHub.com).
4. **Project:** the project (slug for example `proiptv`) is created with the acceptance or by you. Note the **project slug**.
5. **Signing policy:** one policy for releases, normally with the slug `release-signing`, with **manual approval** switched on and you as approver. Note the **signing policy slug**.
6. **Artifact configuration:** Project, "Artifact configurations", "New". GitHub uploads artifacts as a ZIP file, so the root is a zip. Example for one installer (**verify** the element names in the SignPath documentation "Artifact configuration"):

```xml
<artifact-configuration xmlns="http://signpath.io/artifact-configuration/v1">
  <zip-file>
    <msi-file path="ProIPTV-*-windows-x64.msi">
      <authenticode-sign/>
    </msi-file>
  </zip-file>
</artifact-configuration>
```

   Note the **artifact configuration slug**. If the executable inside the app is signed as well, add a `<pe-file>` entry for it.
7. **CI user and API token:** "Users", "New CI user" (name `github-actions`), give it the role **submitter** for the project and the signing policy `release-signing`, then create and copy its **API token**. The token is shown only once.

## 6. What you enter in GitHub

Open the repository, Settings, "Secrets and variables", "Actions".

Tab **Secrets**, "New repository secret":

| Secret | Value |
|---|---|
| `PROIPTV_SIGNPATH_API_TOKEN` | the API token of the CI user (5.7) |

Tab **Variables**, "New repository variable" (not secret):

| Variable | Value |
|---|---|
| `PROIPTV_SIGNPATH_ORG_ID` | organization ID (5.2) |
| `PROIPTV_SIGNPATH_PROJECT` | project slug (5.4) |
| `PROIPTV_SIGNPATH_POLICY` | signing policy slug, for example `release-signing` (5.5) |
| `PROIPTV_SIGNPATH_ARTIFACT_CONFIG` | artifact configuration slug (5.6) |

If the repository is private or the workflow permissions are read-only (Settings, "Actions", "General", "Workflow permissions"), the signing job also needs `actions: read` and `contents: read`.

## 7. The workflow steps

This is the signing part of the Windows job. It is added to `.github/workflows/release.yml` when the desktop installer exists. Pin the versions at that time, the ones below are the current major versions.

```yaml
  windows:
    name: Build and sign the Windows installer
    runs-on: windows-latest
    permissions:
      contents: write
      actions: read
    steps:
      - uses: actions/checkout@v4
      # build the installer here, result: dist\ProIPTV-<version>-windows-x64.msi

      - name: Upload the unsigned installer
        id: upload-unsigned
        uses: actions/upload-artifact@v4
        with:
          name: windows-unsigned
          path: dist/*.msi

      - name: Sign with SignPath
        uses: signpath/github-action-submit-signing-request@v3
        with:
          api-token: ${{ secrets.PROIPTV_SIGNPATH_API_TOKEN }}
          organization-id: ${{ vars.PROIPTV_SIGNPATH_ORG_ID }}
          project-slug: ${{ vars.PROIPTV_SIGNPATH_PROJECT }}
          signing-policy-slug: ${{ vars.PROIPTV_SIGNPATH_POLICY }}
          artifact-configuration-slug: ${{ vars.PROIPTV_SIGNPATH_ARTIFACT_CONFIG }}
          github-artifact-id: ${{ steps.upload-unsigned.outputs.artifact-id }}
          wait-for-completion: true
          output-artifact-directory: signed
```

Notes:

- The unsigned artifact MUST be uploaded as an artifact first, SignPath fetches it from GitHub. This also proves that the file comes from this workflow run.
- `actions/upload-artifact` makes a ZIP file, that is why the artifact configuration starts with `<zip-file>`.
- The signed file lands in the folder `signed`. The following steps compute the checksum of **that** file, add it to `SHA256SUMS.txt` and attach it to the GitHub release.
- Because every release needs manual approval, the step waits until you approve in SignPath. Approve soon after the tag is pushed, SignPath also sends an e-mail.
- The timestamp is added by SignPath, the signature stays valid after the certificate expires.

## 8. Every release

1. Push the tag, the workflow builds the unsigned installer and submits the signing request.
2. Open SignPath, "Signing requests", check that the request comes from the expected repository, tag and workflow, press **Approve**.
3. The workflow continues, publishes the signed installer and the checksums.
4. Check the file on Windows: right click, "Properties", "Digital Signatures", signer **SignPath Foundation**, "This digital signature is OK". In PowerShell:

```powershell
Get-AuthenticodeSignature .\\ProIPTV-0.3.0-windows-x64.msi | Format-List Status, SignerCertificate
```

`Status` MUST be `Valid`. The release page lists the SHA-256 of the signed installer. A fixed certificate fingerprint cannot be pinned, because SignPath Foundation renews the certificate.

## 9. Troubleshooting

| Observation | Cause and fix |
|---|---|
| The step waits for a long time | The request waits for your approval, open SignPath and approve it. |
| `403` or "not authorized" | The CI user has no submitter role for the project and the signing policy, or the API token is wrong or expired (5.7). |
| "Trusted build system" error | GitHub.com is not linked to the project (5.3), or the request does not come from the repository that was registered. |
| Artifact configuration error | The names in the configuration do not match the files in the ZIP (file name pattern, zip root), or an element name is wrong (compare the SignPath documentation). |
| Metadata restriction error | Product name or product version in the file does not match the SignPath project settings (section 1). |
| Application refused or paused | Read the answer of the Foundation, fix the named point, ask again. In the meantime release unsigned, or use the Azure route if a company exists. |

## 10. Decision summary

| Route | Cost | Publisher in Windows | Needs |
|---|---|---|---|
| **SignPath Foundation (chosen)** | free | SignPath Foundation | open source, public repository, an earlier release, manual approval per release |
| Azure Artifact Signing | about 10 USD per month | your organization | registered company in a supported country |
| Unsigned | free | unknown publisher | nothing, SmartScreen warns |

## 11. Checklist

- [ ] First Windows installer published unsigned
- [ ] Two-factor authentication on GitHub and SignPath
- [ ] `PRIVACY.md` published
- [ ] Application sent at `signpath.org/apply` and accepted
- [ ] Code signing policy added to README and download page
- [ ] SignPath: GitHub.com linked, signing policy with manual approval, artifact configuration, CI user and token
- [ ] GitHub: one secret and four variables set
- [ ] Windows job added to the release workflow, first signed installer approved and verified

## 12. Open Questions

1. Is the application accepted for a project with one maintainer who holds all three roles?
2. Are the executables inside the MSI signed as well, or only the installer? (decided when the installer is built)
