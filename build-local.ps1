param([ValidateSet('test','verify','bundle')][string]$Mode='bundle')
$ErrorActionPreference='Stop'
$project=$PSScriptRoot
$workspace=[IO.Path]::GetFullPath((Join-Path $project '..\..'))
$tools=Join-Path $workspace '.tooling'
if(Test-Path -LiteralPath (Join-Path $tools 'cargo\bin\cargo.exe')){
 $env:CARGO_HOME=Join-Path $tools 'cargo'
 $env:RUSTUP_HOME=Join-Path $tools 'rustup'
 $env:PATH="$env:CARGO_HOME\bin;$env:PATH"
}
$portable=Join-Path $tools 'msvc'
if(Test-Path -LiteralPath $portable){
 $vc=(Get-ChildItem -LiteralPath (Join-Path $portable 'VC\Tools\MSVC') -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
 $sdk=Join-Path $portable 'Windows Kits\10'
 $sdkVersion=(Get-ChildItem -LiteralPath (Join-Path $sdk 'Lib') -Directory | Sort-Object Name -Descending | Select-Object -First 1).Name
 $env:VCToolsInstallDir="$vc\";$env:VCINSTALLDIR=Join-Path $portable 'VC\'
 $env:WindowsSdkDir="$sdk\";$env:WindowsSDKVersion="$sdkVersion\"
 $env:VSCMD_ARG_HOST_ARCH='x64';$env:VSCMD_ARG_TGT_ARCH='x64'
 $env:PATH="$vc\bin\Hostx64\x64;$sdk\bin\$sdkVersion\x64;$env:PATH"
 $env:INCLUDE="$vc\include;$sdk\Include\$sdkVersion\ucrt;$sdk\Include\$sdkVersion\shared;$sdk\Include\$sdkVersion\um;$sdk\Include\$sdkVersion\winrt"
 $env:LIB="$vc\lib\x64;$sdk\Lib\$sdkVersion\ucrt\x64;$sdk\Lib\$sdkVersion\um\x64"
 $env:CARGO_TARGET_X86_64_PC_WINDOWS_MSVC_LINKER="$vc\bin\Hostx64\x64\link.exe"
 $env:RC="$sdk\bin\$sdkVersion\x64\rc.exe"
}
Push-Location -LiteralPath $project
try{
 switch($Mode){
  'test' { cargo test --release --manifest-path src-tauri\Cargo.toml }
  'verify' { cargo build --release --features verify-ui --manifest-path src-tauri\Cargo.toml }
  'bundle' { npm.cmd run build }
 }
 if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
}finally{Pop-Location}
