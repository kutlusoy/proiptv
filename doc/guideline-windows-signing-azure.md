# ProIPTV: Windows code signing with Azure Artifact Signing

- **Version:** 0.1 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers who set up signing for the Windows installer with Azure. This is the **alternative** route, the chosen route is the free SignPath Foundation certificate: [`guideline-windows-signing-signpath.md`](./guideline-windows-signing-signpath.md)
- **Reference implementation:** the Windows job described in [`guideline-windows-version.md`](./guideline-windows-version.md) section 4 (the job is added to `.github/workflows/release.yml` when the desktop app exists)
- **See also:** [`guideline-release-signing.md`](./guideline-release-signing.md) for the Android key, which is a different key

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

The menu names of the Azure portal change from time to time. If a name below does not exist, search the portal or the Microsoft documentation for the quoted term. Azure Artifact Signing was called "Trusted Signing" until late 2025, some pages and the older action name `trusted-signing-action` still use the old name.

---

## 1. Check first: can you use it

Azure Artifact Signing issues a short-lived certificate for every signing run, tied to a **verified identity**. There is no certificate file and no hardware token.

| Condition | Rule (verify on the current Microsoft page before you start) |
|---|---|
| Organization (a registered company, for example a GmbH, KG or UG) | Public Trust is available to organizations in the USA, Canada, the EU, the UK and several other countries |
| Individual developer without a company | Public Trust is available **only in the USA and Canada**. In Germany or any other EU country a private person cannot use it. |
| Azure subscription | a **paid** subscription is required. Free, trial and sponsored subscriptions do not work. |
| Price | about 10 USD per month for the Basic tier with 5,000 signatures (check the current price). One release uses a handful of signatures. |
| Time | identity validation of an organization takes 1 to 20 business days. Start it early. |

If you publish as a private person outside the USA and Canada, choose another route from [`guideline-windows-version.md`](./guideline-windows-version.md) section 4: SignPath Foundation (free for open source projects, check eligibility), or an OV certificate from a certificate authority. Do **not** start the steps below in that case.

You need for the organization validation: the legal name, the website, two e-mail addresses, the business identifier (for example the commercial register number), the business address and a representative who can verify their identity with a government photo ID.

## 2. Steps in the Azure portal

### 2.1 Account and subscription

1. Open `portal.azure.com` and sign in or create a Microsoft account.
2. Create an Azure subscription of type pay-as-you-go (menu "Subscriptions", "Add"). Add a payment method.
3. Note the **Subscription ID** (Subscriptions, your subscription, Overview). It is needed in section 3.

### 2.2 Register the resource provider

1. Subscriptions, your subscription, "Resource providers".
2. Search `Microsoft.CodeSigning`, select it, press "Register". Wait until the status says "Registered".

Same with the command line:

```bash
az provider register --namespace "Microsoft.CodeSigning"
az provider show --namespace "Microsoft.CodeSigning" --query registrationState
```

### 2.3 Create the signing account

1. Search "Artifact Signing Accounts" in the portal search bar, press "Create".
2. Resource group: "Create new", name `proiptv-signing`.
3. Account name: 3 to 24 letters and digits, globally unique, for example `proiptvsigning`. Note it, it is `PROIPTV_SIGNING_ACCOUNT` later.
4. Region: for a European organization choose **West Europe** or **North Europe**.
5. Pricing tier: Basic.
6. "Review + create", "Create".
7. Open the new account, Overview, copy the **endpoint** of the region (for West Europe it looks like `https://weu.codesigning.azure.net`). The endpoint MUST match the region of the account.

### 2.4 Give yourself the verifier role

1. Open the signing account, "Access control (IAM)", "Add", "Add role assignment".
2. Role: **Artifact Signing Identity Verifier**. Members: your own user. "Review + assign".

### 2.5 Identity validation

1. In the signing account open "Identity validations", "New identity", "Organization".
2. Choose type **Public** and the validation type **Public Trust**. (A **Public Trust Test** validation MAY be created first to test the pipeline, its certificate is not trusted by Windows.)
3. Enter the organization data exactly as in your registration documents. Documents MUST be younger than 12 months.
4. The representative opens the link that the portal sends and verifies their identity (government photo ID, a Microsoft Authenticator based verified ID).
5. Wait until the status says **Completed**. Until then the production profile cannot sign.

### 2.6 Create the certificate profile

1. Signing account, "Certificate profiles", "Create".
2. Profile type: **Public Trust**. Name: for example `proiptv-public` (5 to 100 letters and digits). It is `PROIPTV_SIGNING_PROFILE` later.
3. Identity validation: choose the completed one. "Create".

## 3. Let GitHub sign without a password

GitHub proves its identity to Azure with a short-lived token (OpenID Connect). No secret key is stored on GitHub.

### 3.1 App registration

1. Portal, "Microsoft Entra ID", "App registrations", "New registration".
2. Name `proiptv-github-signing`, supported account types: "Accounts in this organizational directory only". "Register".
3. Note **Application (client) ID** and **Directory (tenant) ID** from the Overview page.

### 3.2 Federated credential

1. In the app registration: "Certificates & secrets", tab "Federated credentials", "Add credential".
2. Scenario: **GitHub Actions deploying Azure resources**.
3. Organization: `kutlusoy`. Repository: `proiptv`. Entity type: **Environment**. Environment name: `release`.
4. Name: `github-release`. "Add".

### 3.3 Signing role for the app

1. Signing account, "Access control (IAM)", "Add", "Add role assignment".
2. Role: **Artifact Signing Certificate Profile Signer**.
3. Members: "User, group, or service principal", search `proiptv-github-signing`, select it. "Review + assign".

## 4. What you enter in GitHub

Open the repository on GitHub.

1. Settings, "Environments", "New environment", name `release`. (The release job uses this environment, the federated credential only accepts runs of it. You MAY add yourself as required reviewer, then every Windows release waits for your approval.)
2. Settings, "Secrets and variables", "Actions", tab **Secrets**, "New repository secret":

| Secret | Value |
|---|---|
| `PROIPTV_AZURE_CLIENT_ID` | Application (client) ID of 3.1 |
| `PROIPTV_AZURE_TENANT_ID` | Directory (tenant) ID of 3.1 |
| `PROIPTV_AZURE_SUBSCRIPTION_ID` | Subscription ID of 2.1 |

3. Same page, tab **Variables**, "New repository variable". These are not secret:

| Variable | Value |
|---|---|
| `PROIPTV_SIGNING_ENDPOINT` | the endpoint of 2.3, for example `https://weu.codesigning.azure.net` |
| `PROIPTV_SIGNING_ACCOUNT` | the account name of 2.3 |
| `PROIPTV_SIGNING_PROFILE` | the profile name of 2.6 |

The secrets `PROIPTV_WIN_CERT_BASE64`, `PROIPTV_WIN_CERT_PASSWORD` and `PROIPTV_WIN_CERT_SHA256` named in the Windows plan are **not needed** with Azure. They are only for a certificate file from another route.

## 5. The workflow step

This is the signing part of the Windows job. It is added to `.github/workflows/release.yml` when the desktop installer exists. Pin the versions at that time, the ones below are the current major versions.

```yaml
  windows:
    name: Build and sign the Windows installer
    runs-on: windows-latest
    environment: release
    permissions:
      id-token: write
      contents: write
    steps:
      - uses: actions/checkout@v4
      # build the installer here, result: dist\ProIPTV-<version>-windows-x64.msi

      - name: Azure login
        uses: azure/login@v3
        with:
          client-id: ${{ secrets.PROIPTV_AZURE_CLIENT_ID }}
          tenant-id: ${{ secrets.PROIPTV_AZURE_TENANT_ID }}
          subscription-id: ${{ secrets.PROIPTV_AZURE_SUBSCRIPTION_ID }}

      - name: Sign the installer
        uses: azure/artifact-signing-action@v2
        with:
          endpoint: ${{ vars.PROIPTV_SIGNING_ENDPOINT }}
          signing-account-name: ${{ vars.PROIPTV_SIGNING_ACCOUNT }}
          certificate-profile-name: ${{ vars.PROIPTV_SIGNING_PROFILE }}
          files-folder: ${{ github.workspace }}\dist
          files-folder-filter: msi,exe
          file-digest: SHA256
          timestamp-rfc3161: http://timestamp.acs.microsoft.com
          timestamp-digest: SHA256
```

Notes:

- The runner MUST be `windows-2022` or `windows-2025` (`windows-latest`). Windows ARM runners are not supported by the action.
- The timestamp keeps the signature valid after the short-lived certificate has expired. It MUST NOT be left out.
- If `azure/login` reports that no subscription was found, add `allow-no-subscriptions: true` to the login step.
- The certificate changes with every run, so a pinned fingerprint like `PROIPTV_RELEASE_CERT_SHA256` on Android does not work. The release check compares the **subject** (the verified organization name) of the signature instead. The release page shows that subject and the SHA-256 of the installer.

## 6. Check a signed file

On Windows: right click the `.msi`, "Properties", tab "Digital Signatures", the signer must be your organization, and "This digital signature is OK". In PowerShell:

```powershell
Get-AuthenticodeSignature .\ProIPTV-0.3.0-windows-x64.msi | Format-List Status, SignerCertificate
```

`Status` MUST be `Valid`. SmartScreen builds trust with the number of downloads, a new signed file MAY still show a warning in the first days, but then with the verified organization name instead of "unknown publisher".

## 7. Troubleshooting

| Message | Cause and fix |
|---|---|
| HTTP 403 when signing | The identity validation is not completed yet, or the app has no role **Artifact Signing Certificate Profile Signer** (3.3), or the federated credential does not match the repository, environment or branch (3.2). |
| `AADSTS70021` or "No matching federated identity record" | The job does not run in the environment `release`, or organization, repository or environment name in 3.2 is spelled differently. |
| Endpoint error or "account not found" | The endpoint does not belong to the region of the account (2.3). |
| Subscription cannot be used | Free, trial or sponsored subscription. Use pay-as-you-go. |
| `Microsoft.CodeSigning` not registered | Do 2.2. |
| NuGet provider message on a self-hosted runner | Install the NuGet package provider on the runner. GitHub hosted runners do not need it. |

## 8. Checklist

- [ ] Eligibility checked (organization in a supported country, paid subscription)
- [ ] Resource provider registered, signing account created in a European region
- [ ] Identity validation requested and **Completed**
- [ ] Certificate profile created
- [ ] App registration, federated credential for environment `release`, signer role assigned
- [ ] GitHub environment `release`, three secrets and three variables set
- [ ] Windows job added to the release workflow, first signed installer verified on Windows

## 9. Open Questions

1. Is the project published under a registered organization, or as a private person? Azure needs an organization in the EU.
2. If not, SignPath Foundation or an OV certificate instead?
