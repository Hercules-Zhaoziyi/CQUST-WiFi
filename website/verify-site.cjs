const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync('dist/index.html','utf8'),js=fs.readFileSync('dist/app.js','utf8'),css=fs.readFileSync('dist/style.css','utf8');
const base='https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases/download/';
for(const asset of ['v5.3.2/CQUST-WiFi-5.3.2-Tauri-Setup.exe','v5.1.1/CQUST-Wi-Fi_5.1.1_macOS_arm64.dmg','android-v1.1.0-beta02/CQUST-WiFi-Android-1.1.0-beta02.apk'])assert.equal(html.split('href="'+base+asset+'"').length-1,2,asset);
assert.ok(!html.includes('敬请期待'));
assert.ok(!/__WINDOWS_|__ANDROID_/.test(html+js));
assert.ok(html.includes('Android 10+'));
assert.ok(html.includes('旧数据会清除')||html.includes('会清除旧数据'));
assert.ok(html.includes('课表不依赖上网服务开通'));
assert.ok(html.includes('macOS v5.1.1 尚未同步本次新功能'));
assert.ok(js.includes('1bd1f20e81a2e24b2284405f6acbc2eacce17d727c2657ba18f0ee2f15f1fc2c'));
assert.ok(js.includes('e0c1f9c1866d52ec70cbf17afe0b4a398f2441a02a432afb3f97d0b0d87e6815'));
assert.ok(html.includes('copy-android-checksum')&&html.includes('统一而顺手'));
assert.ok(html.includes('20261024-w532-m511-a110beta02'));
assert.ok(html.includes('532-android-110beta02'));
assert.ok(html.includes('实际可用性以校园网策略为准'));
assert.ok(css.includes('grid-template-columns:24px minmax(0,1fr) 20px'));
assert.ok(css.includes('.gallery-tabs button>span:last-child'));
assert.ok(!html.includes('data-icon="arrow-down"'));
assert.ok(html.includes('assets/joint-mark.svg')&&js.includes('android:'));
assert.ok(css.includes('.secondary-platforms{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))'));
assert.equal(html.split('href="https://qm.qq.com/q/Rn7B5NBbiK"').length-1,2);
assert.ok(html.includes('>藏ICP备2024002229号-1</a>')&&html.includes('href="https://beian.miit.gov.cn/"'));
for(const match of html.matchAll(/(?:href|src)="\.\/([^"]+)"/g))assert.ok(fs.existsSync('dist/'+match[1].split('?')[0]),'Missing asset: '+match[1]);
for(const area of ['sites','schedule','proxy'])for(const theme of ['light','dark'])assert.ok(fs.existsSync('dist/assets/'+area+'-'+theme+'-w532.png'));
const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);assert.equal(new Set(ids).size,ids.length,'Duplicate IDs');
const source=js.slice(js.indexOf('const isMacOS='),js.indexOf('let currentGallery='));
for(const [platform,touch,clientPlatform,userAgent,expected] of [
 ['MacIntel',0,undefined,'Mac','macos'],['MacIntel',5,undefined,'iPad','other'],['Win32',0,'Windows','Windows','other'],
 ['Linux x86_64',0,'Linux','Linux','other'],['',0,'macOS','Mac','macos'],['Linux armv8l',5,'Android','Android 17','android'],
 ['Linux armv8l',5,undefined,'Mozilla/5.0 (Linux; Android 17)','android']]){
 const elements={};
 function node(id){return {id,children:[],classes:new Set(),classList:{add(name){elements[id].classes.add(name)}},prepend(child){if(child.parent)child.parent.children=child.parent.children.filter(x=>x!==child);child.parent=this;this.children.unshift(child)},append(child){if(child.parent)child.parent.children=child.parent.children.filter(x=>x!==child);child.parent=this;this.children.push(child)}}}
 for(const id of ['hero-macos','hero-windows','hero-android','mac-download','windows-download','android-download','hero-downloads','platform-downloads','secondary-downloads'])elements[id]=node(id);
 for(const id of ['hero-windows','hero-macos','hero-android'])elements['hero-downloads'].append(elements[id]);
 for(const id of ['windows-download','mac-download','android-download'])elements['platform-downloads'].append(elements[id]);
 const root={dataset:{}};vm.runInNewContext(source,{navigator:{platform,maxTouchPoints:touch,userAgent,userAgentData:clientPlatform?{platform:clientPlatform}:undefined},root,document:{getElementById:id=>elements[id]}});
 assert.equal(root.dataset.platform,expected);
 const primary=expected==='macos'?'mac-download':expected==='android'?'android-download':'windows-download';
 assert.equal(elements['hero-downloads'].children[0].id,expected==='macos'?'hero-macos':expected==='android'?'hero-android':'hero-windows');
 assert.equal(elements['platform-downloads'].children.length,1);
 assert.equal(elements['platform-downloads'].children[0].id,primary);
 assert.equal(elements['secondary-downloads'].children.length,2);
 assert.ok(elements[primary].classes.has('download-current'));
 assert.ok(elements['secondary-downloads'].children.every(n=>n.classes.has('download-secondary')));
}
console.log('Passed: Windows 5.3.2, Android beta02, real macOS 5.1.1 links, checksum, cache marker, QQ/ICP, preview assets and seven platform ordering cases.');
