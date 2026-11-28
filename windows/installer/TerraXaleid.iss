; TerraXaleid — Windows installer definition
;
; Build (needs Inno Setup 6 on PATH or full path to ISCC.exe):
;   ISCC.exe windows\installer\TerraXaleid.iss
; The app itself must be published first:
;   dotnet publish windows\TerraXaleid.App\TerraXaleid.App.csproj -c Release -r win-x64 ^
;     --self-contained false -p:PublishSingleFile=false -o dist\windows\TerraXaleid-0.1.0-beta.2
;
; per-user install (no admin): files land in %LOCALAPPDATA%\Programs\TerraXaleid,
; so the app keeps writing its config to %APPDATA%\TerraXaleid without elevation.

#define AppName "TerraXaleid"
#define AppVersion "0.1.0-beta.3"
#define AppPublisher "Dragr0wl"
#define AppUrl "https://github.com/Dragrowl/TerraXaleid"
#define SourceDir "..\..\dist\windows\TerraXaleid-0.1.0-beta.3"
#define OutputDir "..\..\dist"

[Setup]
AppId={{9C8A31A7-5E2C-4E1A-9F17-7C3D6B04A1E2}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppUrl}
AppSupportURL={#AppUrl}
AppUpdatesURL={#AppUrl}/releases
VersionInfoVersion=0.1.0.1
VersionInfoDescription={#AppName} setup
VersionInfoProductName={#AppName}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir={#OutputDir}
OutputBaseFilename={#AppName}-{#AppVersion}-win-x64-setup
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayName={#AppName}
UninstallDisplayIcon={app}\{#AppName}.exe
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "chinesesimplified"; MessagesFile: "ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "japanese"; MessagesFile: "compiler:Languages\Japanese.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppName}.exe"
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppName}.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppName}.exe"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent
