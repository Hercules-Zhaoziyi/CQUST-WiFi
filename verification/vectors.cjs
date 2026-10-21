const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=path.join(__dirname,'../../protocol_reference/security.js');
const box={};box.window=box;vm.createContext(box);vm.runInContext(fs.readFileSync(source,'utf8'),box);
box.RSAUtils.setMaxDigits(400);
const modulus='94dd2a8675fb779e6b9f7103698634cd400f27a154afa67af6166a43fc26417222a79506d34cacc7641946abda1785b7acf9910ad6a0978c91ec84d40b71d2891379af19ffb333e7517e390bd26ac312fe940c340466b4a5d4af1d65c3b5944078f96a1a51a5a53e4bc302818b7c9f63c4a1b07bd7d874cef1c3d4b2f5eb7871';
const key=box.RSAUtils.getKeyPair('10001','',modulus);
const cases=[['demo','AA:BB:CC:DD:EE:FF'],[' space !+% ','aa%3Abb'],['1234',''],['a'.repeat(120),'001122'],['b'.repeat(126),'mac'],['c'.repeat(127),'mac'],['d'.repeat(280),'mac']].map(([password,mac])=>({password,mac,ciphertext:box.RSAUtils.encryptedString(key,(password+'>'+(mac||'111111111')).split('').reverse().join(''))}));
console.log(JSON.stringify({modulus,exponent:'10001',cases}));
