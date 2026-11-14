# Adds libVLC (a separate, unmodified LGPL library) to the app folder of the Windows installer.
# Usage: pwsh tools/bundle-vlc.ps1 <app folder>   (the folder that contains ProIPTV.exe)
param([Parameter(Mandatory = $true)][string]$AppDir)
$ErrorActionPreference = 'Stop'

$version = '3.0.21'
$sha256 = 'a0b7ec02b50adf6417eed014fb8df50af39690505a4225b85b3dc2ed17d14843'
$url = "https://download.videolan.org/pub/videolan/vlc/$version/win64/vlc-$version-win64.zip"
$work = Join-Path $env:RUNNER_TEMP 'vlc'
New-Item -ItemType Directory -Force -Path $work | Out-Null
$zip = Join-Path $work 'vlc.zip'

Invoke-WebRequest -Uri $url -OutFile $zip -UserAgent 'ProIPTV-build'
$actual = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower()
if ($actual -ne $sha256) { throw "libVLC download has the wrong checksum: $actual (size $((Get-Item $zip).Length) bytes)" }
Expand-Archive -Path $zip -DestinationPath $work -Force
$src = Join-Path $work "vlc-$version"

$dest = Join-Path $AppDir 'vlc'
New-Item -ItemType Directory -Force -Path (Join-Path $dest 'plugins') | Out-Null
Copy-Item (Join-Path $src 'libvlc.dll') $dest
Copy-Item (Join-Path $src 'libvlccore.dll') $dest

$list = Get-Content (Join-Path $PSScriptRoot '..\desktop\vlc\plugins.txt') | Where-Object { $_ -and -not $_.StartsWith('#') }
foreach ($entry in $list) {
    $from = Join-Path (Join-Path $src 'plugins') $entry
    if (-not (Test-Path $from)) { throw "Plugin $entry is not part of libVLC $version. Check desktop/vlc/plugins.txt." }
    $to = Join-Path (Join-Path $dest 'plugins') $entry
    New-Item -ItemType Directory -Force -Path (Split-Path $to) | Out-Null
    Copy-Item $from $to
}

# the plugin cache makes the first start faster, it is rebuilt by libVLC when it is missing
Copy-Item (Join-Path $src 'vlc-cache-gen.exe') $dest
try { & (Join-Path $dest 'vlc-cache-gen.exe') (Join-Path $dest 'plugins') } catch { Write-Warning "vlc-cache-gen failed: $_" }
Remove-Item (Join-Path $dest 'vlc-cache-gen.exe') -Force

Invoke-WebRequest -Uri 'https://www.gnu.org/licenses/lgpl-2.1.txt' -OutFile (Join-Path $dest 'LICENSE-LGPL-2.1.txt')
@"
This folder contains libVLC $version, unmodified, from https://download.videolan.org/pub/videolan/vlc/$version/win64/
libVLC is licensed under the GNU Lesser General Public License 2.1 or later (see LICENSE-LGPL-2.1.txt).
Source code: https://download.videolan.org/pub/videolan/vlc/$version/vlc-$version.tar.xz
ProIPTV loads libvlc.dll as a separate library. You can replace the files in this folder with another build of libVLC 3.0.
Only the plugins that are needed for IPTV playback are shipped.
"@ | Set-Content (Join-Path $dest 'NOTICE.txt') -Encoding UTF8
Write-Host "libVLC $version added to $dest"
