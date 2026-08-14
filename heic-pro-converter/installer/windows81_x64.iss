#define MyAppName "HEIC Pro Converter"
#define MyAppVersion "6.0"
#define MyAppPublisher "HEIC Pro Converter"
#define MyAppExeName "HEIC-Pro-Converter-v6-Silver-Studio.exe"
#define VCRedistName "VC_redist.x64.exe"

[Setup]
AppId={{8A7B7094-6063-4A2A-91E1-9476F16DD33D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName=HEIC Pro Converter V6 Silver Studio — Windows 8.1 x64 Edition
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\HEIC Pro Converter
DefaultGroupName=HEIC Pro Converter
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64os
ArchitecturesInstallIn64BitMode=x64os
MinVersion=6.3
PrivilegesRequired=admin
OutputDir=..\dist_installer_win81
OutputBaseFilename=HEIC-Pro-Converter-v6-Silver-Studio-Setup-Windows81-x64
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
UninstallDisplayName=HEIC Pro Converter V6 Silver Studio
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion=6.0.81.0
VersionInfoProductName=HEIC Pro Converter
VersionInfoProductVersion=6.0
VersionInfoDescription=HEIC / HEIF / JPG batch converter for Windows 8.1 x64 and newer
VersionInfoCompany=HEIC Pro Converter

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "..\dist\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\vendor\{#VCRedistName}"; DestDir: "{tmp}"; Flags: deleteafterinstall

[Icons]
Name: "{autoprograms}\HEIC Pro Converter"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\HEIC Pro Converter"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{tmp}\{#VCRedistName}"; Parameters: "/install /quiet /norestart"; StatusMsg: "Installing Microsoft Visual C++ runtime for Windows 8.1 compatibility..."; Flags: waituntilterminated
Filename: "{app}\{#MyAppExeName}"; Description: "Launch HEIC Pro Converter"; Flags: nowait postinstall skipifsilent
