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
assert.ok(js.includes('76430446f0c4843a2a3dc7d032a4d3bfac1124d145dc010bae2f4c34fe2ce22b'));
assert.ok(js.includes('f8f405a7b570001af1e1fa21cd011d96e04e8122b16eee3d1126b1624a4103ab'));
assert.ok(html.includes('copy-android-checksum')&&html.includes('统一而顺手'));
assert.ok(html.includes('20261024-w532-m511-a110beta02'));
assert.ok(html.includes('532-android-110beta02'));
assert.ok(html.includes('实际可用性以校园网策略为准'));
assert.ok(css.includes('grid-template-columns:24px minmax(0,1fr) 20px'));
assert.ok(css.includes('.gallery-tabs button>span:last-child'));
assert.equal(html.split('href="https://qm.qq.com/q/Rn7B5NBbiK"').length-1,2);
assert.ok(html.includes('>藏ICP备2024002229号-1</a>')&&html.includes('href="https://beian.miit.gov.cn/"'));
for(const match of html.matchAll(/(?:href|src)="\.\/([^"]+)"/g))assert.ok(fs.existsSync('dist/'+match[1].split('?')[0]),'Missing asset: '+match[1]);
for(const area of ['sites','schedule','proxy'])for(const theme of ['light','dark'])assert.ok(fs.existsSync('dist/assets/'+area+'-'+theme+'.png'));
const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);assert.equal(new Set(ids).size,ids.length,'Duplicate IDs');
const source=js.slice(js.indexOf('const isMacOS='),js.indexOf('let currentGallery='));
for(const [platform,touch,clientPlatform,userAgent,expected] of [
 ['MacIntel',0,undefined,'Mac','macos'],['MacIntel',5,undefined,'iPad','other'],['Win32',0,'Windows','Windows','other'],
 ['Linux x86_64',0,'Linux','Linux','other'],['',0,'macOS','Mac','macos'],['Linux armv8l',5,'Android','Android 17','android'],
 ['Linux armv8l',5,undefined,'Mozilla/5.0 (Linux; Android 17)','android']]){
 const elements={};for(const id of ['hero-macos','hero-windows','hero-android','mac-download','windows-download','android-download'])elements[id]={id};
 for(const id of ['hero-downloads','platform-downloads'])elements[id]={prepend(child){this.first=child.id;}};
 const root={dataset:{}};vm.runInNewContext(source,{navigator:{platform,maxTouchPoints:touch,userAgent,userAgentData:clientPlatform?{platform:clientPlatform}:undefined},root,document:{getElementById:id=>elements[id]}});
 assert.equal(root.dataset.platform,expected);
 assert.equal(elements['hero-downloads'].first,expected==='macos'?'hero-macos':expected==='android'?'hero-android':'hero-windows');
 assert.equal(elements['platform-downloads'].first,expected==='macos'?'mac-download':expected==='android'?'android-download':'windows-download');
}
console.log('Passed: Windows 5.3.2, Android beta02, real macOS 5.1.1 links, checksum, cache marker, QQ/ICP, preview assets and seven platform ordering cases.');
