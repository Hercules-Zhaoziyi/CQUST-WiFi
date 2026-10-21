"""Package explicit source files; scan legacy secrets without importing config.py."""
import ast
import json
from pathlib import Path
import zipfile

root=Path(__file__).resolve().parent
names=['package.json','package-lock.json','README.md','.gitignore','build-local.ps1','pack_source.py',
 'src-tauri/Cargo.toml','src-tauri/Cargo.lock','src-tauri/build.rs','src-tauri/tauri.conf.json',
 'src-tauri/capabilities/default.json','src-tauri/icons/icon.ico',
 'src-tauri/src/main.rs','src-tauri/src/auth.rs','src-tauri/src/service.rs','src-tauri/src/startup.rs','src-tauri/src/vault.rs','src-tauri/src/verify.rs','src-tauri/src/rsa_vectors.json',
 'ui/index.html','ui/renderer.js','ui/style.css','ui/bridge.js','ui/wifi.svg','ui/school_logo.png',
 'verification/native.js','verification/frontend.cjs','verification/preload.cjs','verification/vectors.cjs']
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
binary=root/'src-tauri/target/release/cqust-wifi.exe'
payload=binary.read_bytes()
if any(secret and secret in payload for secret in secrets):raise RuntimeError('Private data found in executable; release rejected')
for synthetic in ('synthetic-secret','test-only','测试同学'):
    if synthetic.encode() in payload:raise RuntimeError('Verification backend or fixture found in release executable')
output=root.parent.parent/'outputs'/'CQUST_Wifi_Tauri_v5_Source.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for name in names:archive.write(root/name,'CQUST_Wifi_Tauri/'+name)
print('Source archive and release executable passed credential/verification-backend scan.')
