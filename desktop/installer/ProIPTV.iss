; Inno Setup script of the ProIPTV Windows installer. Built by .github/workflows/windows-build.yml
; Defines passed on the command line: AppVersion, OutputName, SourceDir (the app folder with ProIPTV.exe), IconFile, LicenseFile
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef OutputName
  #define OutputName "ProIPTV-setup"
#endif

[Setup]
AppId={{6C1F0E22-5D53-4B6E-9B84-4E1B3F2A7C91}
AppName=ProIPTV
AppVersion={#AppVersion}
AppPublisher=Ali Kutlusoy
AppPublisherURL=https://github.com/kutlusoy/proiptv
AppSupportURL=https://github.com/kutlusoy/proiptv/issues
AppUpdatesURL=https://github.com/kutlusoy/proiptv/releases
DefaultDirName={autopf}\ProIPTV
DefaultGroupName=ProIPTV
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\ProIPTV.exe
SetupIconFile={#IconFile}
LicenseFile={#LicenseFile}
OutputDir=output
OutputBaseFilename={#OutputName}
Compression=lzma2/max
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
WizardStyle=modern
CloseApplications=yes
CloseApplicationsFilter=ProIPTV.exe
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "german"; MessagesFile: "compiler:Languages\German.isl"
Name: "turkish"; MessagesFile: "compiler:Languages\Turkish.isl"
Name: "french"; MessagesFile: "compiler:Languages\French.isl"
Name: "italian"; MessagesFile: "compiler:Languages\Italian.isl"
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "dutch"; MessagesFile: "compiler:Languages\Dutch.isl"
Name: "polish"; MessagesFile: "compiler:Languages\Polish.isl"
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\ProIPTV"; Filename: "{app}\ProIPTV.exe"
Name: "{autodesktop}\ProIPTV"; Filename: "{app}\ProIPTV.exe"; Tasks: desktopicon

[Run]
; interactive setup: offer to start the app, silent setup (the in-app update): start it right away
Filename: "{app}\ProIPTV.exe"; Description: "{cm:LaunchProgram,ProIPTV}"; Flags: nowait postinstall skipifsilent
Filename: "{app}\ProIPTV.exe"; Flags: nowait runasoriginaluser; Check: WizardSilent
