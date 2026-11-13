#!/usr/bin/env bash
# Facts of a built APK and the text of the release notes.
#   release-notes.sh facts <apk>
#   release-notes.sh notes <apk> <aab> <version> <commit>
set -euo pipefail

sdk=${ANDROID_HOME:-/usr/local/lib/android/sdk}
apksigner=$(ls "$sdk"/build-tools/*/apksigner | sort -V | tail -1)
aapt2=$(ls "$sdk"/build-tools/*/aapt2 | sort -V | tail -1)

read_facts() {
  local apk=$1 badging verify
  badging=$("$aapt2" dump badging "$apk")
  verify=$("$apksigner" verify --print-certs "$apk")
  field() { sed -n "$1" <<<"$badging" | head -1; }
  digest() { sed -n "s/^.*[Ss]igner.*certificate $1 digest: *//p" <<<"$verify" | head -1 | tr -d ':' | tr 'A-F' 'a-f'; }
  PACKAGE_NAME=$(field "s/^package: name='\([^']*\)'.*/\1/p")
  APK_VERSION_NAME=$(field "s/^package:.*versionName='\([^']*\)'.*/\1/p")
  APK_VERSION_CODE=$(field "s/^package:.*versionCode='\([0-9]*\)'.*/\1/p")
  MIN_SDK=$(field "s/^\(min\)\?[sS]dkVersion:'\([0-9]*\)'.*/\2/p")
  TARGET_SDK=$(field "s/^targetSdkVersion:'\([0-9]*\)'.*/\1/p")
  CERT_SHA256=$(digest SHA-256)
  CERT_SHA1=$(digest SHA-1)
  if unzip -Z1 "$apk" | grep -qx 'assets/adi-registration.properties'; then ADI_FILE=yes; else ADI_FILE=no; fi
}

colon() { tr 'a-f' 'A-F' <<<"$1" | sed 's/../&:/g; s/:$//'; }
sum() { sha256sum "$1" | cut -d' ' -f1; }

case "${1:-}" in
  facts)
    read_facts "$2"
    for name in PACKAGE_NAME APK_VERSION_NAME APK_VERSION_CODE MIN_SDK TARGET_SDK CERT_SHA256 CERT_SHA1 ADI_FILE; do
      echo "$name=${!name}"
    done
    ;;
  notes)
    apk=$2 aab=$3 version=$4 commit=$5
    read_facts "$apk"
    if [[ $ADI_FILE == yes ]]; then
      adi="included in the APK (assets/adi-registration.properties)"
    else
      adi="not included"
    fi
    cat <<TEXT
ProIPTV v$version

IPTV player for Android TV, tablets and phones. It plays the streams of your own provider. No content is included.

| Item | Value |
|---|---|
| Application ID | $PACKAGE_NAME |
| Version | $APK_VERSION_NAME |
| Version code | $APK_VERSION_CODE |
| Android | minimum $MIN_SDK, target $TARGET_SDK |
| Build type | release |
| Source commit | $commit |
| Signing certificate SHA-256 | \`$(colon "$CERT_SHA256")\` |
| Signing certificate SHA-1 | \`$(colon "$CERT_SHA1")\` |
| Android developer verification proof | $adi |
| APK SHA-256 | \`$(sum "$apk")\` |
| AAB SHA-256 | \`$(sum "$aab")\` |

Files:

- \`$(basename "$apk")\`: the app, install this file on the device
- \`$(basename "$aab")\`: app bundle for store uploads
- \`ProIPTV-$version-mapping.txt\`: R8 mapping to read crash traces
- \`SHA256SUMS.txt\`: checksums of the files

Verify a download:

\`\`\`
sha256sum -c SHA256SUMS.txt
apksigner verify --print-certs $(basename "$apk")
\`\`\`

The printed certificate SHA-256 must match the one above. Updates only work between builds that are signed with the same key. A debug build or a build of someone else has to be uninstalled first.
TEXT
    ;;
  *)
    echo "usage: release-notes.sh facts <apk> | notes <apk> <aab> <version> <commit>" >&2
    exit 2
    ;;
esac
