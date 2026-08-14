#define MyAppName "HEIC Pro Converter"
#define MyAppVersion "6.0"
#define MyAppPublisher "HEIC Pro Converter"
#define MyAppExeName "HEIC-Pro-Converter-v6-Silver-Studio.exe"

[Setup]
AppId={{8A7B7094-6063-4A2A-91E1-9476F16DD33D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\HEIC Pro Converter
DefaultGroupName=HEIC Pro Converter
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
PrivilegesRequired=admin
OutputDir=..\dist_installer
OutputBaseFilename=HEIC-Pro-Converter-v6-Silver-Studio-Setup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
UninstallDisplayName=HEIC Pro Converter V6 Silver Studio
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion=6.0.0.0
VersionInfoProductName=HEIC Pro Converter
VersionInfoProductVersion=6.0
VersionInfoDescription=HEIC / HEIF / JPG batch converter for Windows 10/11 x64
VersionInfoCompany=HEIC Pro Converter

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "..\dist\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\HEIC Pro Converter"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\HEIC Pro Converter"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch HEIC Pro Converter"; Flags: nowait postinstall skipifsilent
