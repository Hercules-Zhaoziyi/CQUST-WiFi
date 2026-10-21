"""Cross-check with the actual school's security.js (optional external reference)."""
import json
from pathlib import Path
import shutil
import subprocess
import unittest
from auth_adapter import AuthAdapter, encode_twice

REFERENCE = Path(__file__).resolve().parents[2] / 'protocol_reference' / 'security.js'
MODULUS = ('94dd2a8675fb779e6b9f7103698634cd400f27a154afa67af6166a43fc26417222'
           'a79506d34cacc7641946abda1785b7acf9910ad6a0978c91ec84d40b71d289137'
           '9af19ffb333e7517e390bd26ac312fe940c340466b4a5d4af1d65c3b5944078f'
           '96a1a51a5a53e4bc302818b7c9f63c4a1b07bd7d874cef1c3d4b2f5eb7871')
RUNNER = r'''
const fs = require('fs'), vm = require('vm');
const input = JSON.parse(fs.readFileSync(0, 'utf8'));
const box = {};
box.window = box;
vm.createContext(box);
vm.runInContext(fs.readFileSync(input.reference, 'utf8'), box, {timeout:3000});
box.window.RSAUtils.setMaxDigits(400);
const key = box.window.RSAUtils.getKeyPair('10001','',input.modulus);
const result = input.cases.map(([password,mac])=>box.window.RSAUtils.encryptedString(key,(password+'>'+(mac||'111111111')).split('').reverse().join('')));
process.stdout.write(JSON.stringify({ciphertexts:result,encoded:input.values.map(x=>encodeURIComponent(encodeURIComponent(x)))}));
'''

class JavaScriptParityTests(unittest.TestCase):
    @unittest.skipUnless(REFERENCE.exists() and shutil.which('node'), 'Needs original security.js and Node.js')
    def test_original_security_js_and_component_encoding(self):
        cases = [('demo', 'AA:BB:CC:DD:EE:FF'), (' space !+% ', 'aa%3Abb'),
                 ('1234', ''), ('a'*120, '001122'), ('b'*126,'mac'),
                 ('c'*127,'mac'), ('d'*280,'mac')]
        values = ['互联网', 'wlanuserip=x&MAC=aa%3Abb&url=a%2520b', " a+b!~*'() "]
        payload = {'reference':str(REFERENCE),'modulus':MODULUS,'cases':cases,'values':values}
        result = subprocess.run(['node','-e',RUNNER], input=json.dumps(payload), text=True,
                                capture_output=True, check=False, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        expected = json.loads(result.stdout)
        for (password, mac), cipher in zip(cases,expected['ciphertexts']):
            with self.subTest(length=len(password), mac=mac):
                self.assertEqual(AuthAdapter.encrypt_password(password,mac,'10001',MODULUS),cipher)
        self.assertEqual([encode_twice(v) for v in values],expected['encoded'])
