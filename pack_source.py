"""Package explicit source files; scan legacy secrets without importing config.py."""
import ast
import argparse
import json
from pathlib import Path
import zipfile

root=Path(__file__).resolve().parent
names=['package.json','package-lock.json','README.md','.gitignore','build-local.ps1','pack_source.py',
 'src-tauri/Cargo.toml','src-tauri/Cargo.lock','src-tauri/build.rs','src-tauri/tauri.conf.json',
 'src-tauri/capabilities/default.json','src-tauri/icons/icon.ico',
 'src-tauri/src/main.rs','src-tauri/src/auth.rs','src-tauri/src/service.rs','src-tauri/src/startup.rs','src-tauri/src/vault.rs','src-tauri/src/verify.rs','src-tauri/src/rsa_vectors.json',
 'ui/xiaohongshu-logo.svg','ui/index.html','ui/renderer.js','ui/style.css','ui/bridge.js','ui/wifi.svg','ui/school_logo.png',
 'verification/native.js','verification/frontend.cjs','verification/preload.cjs','verification/vectors.cjs']
names+=['ui/sites.json','prepare-installer-assets.ps1','src-tauri/icons/school-seal.png','src-tauri/icons/installer-header.bmp','src-tauri/icons/installer-school.ico']
names+=['src-tauri/src/portal_browser.rs','src-tauri/src/snapping.rs','src-tauri/src/updates.rs']
names+=['ui/markdown.js','ui/proxy.js','ui/proxy.css','src-tauri/src/proxy.rs','ui/platform.js','src-tauri/src/platform.rs','src-tauri/src/snapping_macos.rs','src-tauri/src/startup_macos.rs','src-tauri/src/vault_macos.rs','src-tauri/tauri.macos.conf.json','src-tauri/icons/icon.icns','src-tauri/icons/icon.png','src-tauri/icons/tray-macos.png']
names+=['ui/schedule.js','ui/schedule.css','src-tauri/src/schedule.rs']
names+=['ui/unified.js','ui/unified.css','ui/update-dialog.js']
names+=['docs/PROXY.md']
names+=['src-tauri/icons/icon-macos.svg','src-tauri/icons/tray-macos.svg']
names+=[path.relative_to(root).as_posix() for path in sorted((root/'docs').glob('*.md')) if path.relative_to(root).as_posix() not in names]
names+=[path.relative_to(root).as_posix() for path in sorted((root/'ui/vendor').glob('*')) if path.is_file()]
names+=[path.relative_to(root).as_posix() for path in sorted((root/'ui/sites').glob('*')) if path.is_file()]
original=Path.home()/'Desktop'/'CQUST Wifi'/'config.py'
profile={}
if original.exists():
    for node in ast.parse(original.read_text(encoding='utf-8-sig')).body:
        if isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='dataLogin' for t in node.targets):
            try:profile=ast.literal_eval(node.value)
            except (ValueError,TypeError):pass
secrets=[str(profile.get(key,'')).encode() for key in ('userId','password','queryString','userIndex')]
for name in names:
    if any(secret and secret in (root/name).read_bytes() for secret in secrets):raise RuntimeError('Private data found; release rejected')
import os
binary=Path(os.environ.get('CARGO_TARGET_DIR',str(root/'src-tauri/target')))/'release/cqust-wifi.exe'
options=argparse.ArgumentParser()
options.add_argument('--installer-payload',type=Path)
args=options.parse_args()
payload=binary.read_bytes()
payloads=[payload]
if args.installer_payload:
    bundled=args.installer_payload.read_bytes()
    # Tauri changes precisely this documented bundle marker for NSIS, then
    # restores the unbundled executable. No other payload difference is allowed.
    if bundled!=payload.replace(b'__TAURI_BUNDLE_TYPE_VAR_UNK',b'__TAURI_BUNDLE_TYPE_VAR_NSS',1):
        raise RuntimeError('Unexpected installer payload difference; release rejected')
    payloads.append(bundled)
for executable in payloads:
    if any(secret and secret in executable for secret in secrets):raise RuntimeError('Private data found in executable; release rejected')
    for synthetic in ('synthetic-secret','synthetic-test','private-fixture','local-test-password','verifyPortalRedirect','test-only','测试同学'):
        if synthetic.encode() in executable:raise RuntimeError('Verification backend or fixture found in release executable')
output=root.parent.parent/'outputs'/'CQUST_Wifi_Tauri_v5.3.2_Source.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for name in names:archive.write(root/name,'CQUST_Wifi_Tauri/'+name)
print('Source archive and release executable passed credential/verification-backend scan.')
