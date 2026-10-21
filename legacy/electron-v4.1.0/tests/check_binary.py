"""Packaged backend pipe check in a temporary, credential-free APPDATA folder."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

with tempfile.TemporaryDirectory() as folder:
    env=dict(os.environ,APPDATA=folder)
    executable=Path(__file__).resolve().parents[1]/'backend-dist'/'auth-service.exe'
    commands=[dict(id=1,method='snapshot',args={}),dict(id=2,method='settings',args=dict(check_interval=120))]
    result=subprocess.run([str(executable)],input=''.join(json.dumps(x)+'\n' for x in commands),
        capture_output=True,text=True,encoding='utf-8',env=env,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
    assert result.returncode==0,'Backend exit failure'
    replies=[json.loads(x) for x in result.stdout.splitlines() if x.strip()]
    responses={x['id']:x for x in replies if 'id' in x}
    assert responses[1]['ok'] and not responses[1]['data']['connected']
    assert responses[2]['data']['settings']['check_interval']==120
    assert all('password' not in x.get('data',{}) for x in replies if isinstance(x.get('data'),dict))
    print('Packaged backend pipe check passed; no real account/config accessed.')
