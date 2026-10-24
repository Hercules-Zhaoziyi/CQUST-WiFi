const icons={
  android:'<path d="m6.4 8-2-3.5m13.2 3.5 2-3.5" stroke-width="1.8"/><path fill="currentColor" stroke="none" fill-rule="evenodd" d="M2 18a10 10 0 0 1 20 0H2Zm5.5-5.5a1 1 0 1 0 0 2 1 1 0 0 0 0-2Zm9 0a1 1 0 1 0 0 2 1 1 0 0 0 0-2Z"/>',
  apple:'<path fill="currentColor" stroke="none" d="M16.4 5.1c.9-1 1.4-2.3 1.3-3.6-1.3.1-2.7.9-3.5 1.8-.8.9-1.4 2.2-1.2 3.5 1.4.1 2.6-.7 3.4-1.7ZM20.5 17.5c-.5 1.2-.8 1.7-1.5 2.7-.9 1.3-2.1 2.9-3.6 2.9-1.3 0-1.6-.8-3.4-.8s-2.1.8-3.4.8c-1.5.1-2.6-1.4-3.5-2.7C2.6 16.8 2 12.2 3.5 9.8c1.1-1.8 2.8-2.8 4.5-2.8 1.4 0 2.3.8 3.5.8 1.1 0 1.9-.8 3.5-.8 1.4 0 2.9.8 4 2.1-3.5 1.9-2.9 6.8 1.5 8.4Z"/>',
  users:'<circle cx="9" cy="7" r="3"/><path d="M2 21v-3a7 7 0 0 1 14 0v3M16 4a3 3 0 0 1 0 6m2 4a6 6 0 0 1 4 6v1"/>',
  switch:'<path d="M3 7h17m-4-4 4 4-4 4M21 17H4m4-4-4 4 4 4"/>',
  sparkles:'<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5ZM21 2v4m-2-2h4"/>',
  moon:'<path d="M20 15A9 9 0 0 1 9 4a9 9 0 1 0 11 11Z"/>',
  sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
  'arrow-right':'<path d="M4 12h16m-5-5 5 5-5 5"/>','arrow-down':'<path d="M12 3v17m-5-5 5 5 5-5"/>','arrow-up-right':'<path d="M6 18 18 6M6 6h12v12"/>',
  windows:'<path fill="currentColor" stroke="none" d="M2 4.3 10.8 3v8H2V4.3Zm10-1.6L23 1v10H12V2.7ZM2 13h8.8v8L2 19.7V13Zm10 0h11v10l-11-1.7V13Z"/>',
  wifi:'<path d="M3 8a14 14 0 0 1 18 0M6 12a9 9 0 0 1 12 0M9 16a4 4 0 0 1 6 0"/><circle cx="12" cy="20" r=".7"/>',
  power:'<path d="M12 2v10M6 5a9 9 0 1 0 12 0"/>',shield:'<path d="m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6Z"/><path d="m8 12 3 3 5-6"/>',
  lock:'<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/>',
  refresh:'<path d="M21 10a9 9 0 0 0-15-6L3 7m0-5v5h5M3 14a9 9 0 0 0 15 6l3-3m0 5v-5h-5"/>',
  user:'<circle cx="9" cy="7" r="3"/><path d="M2 21v-2a7 7 0 0 1 14 0v2m0-10 2 2 4-5"/>',
  logout:'<path d="M10 4H5v16h5M9 12h12m-4-4 4 4-4 4"/>',
  globe:'<circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/>',
  sliders:'<path d="M4 3v5m0 4v9M12 3v10m0 4v4M20 3v2m0 4v12M1 8h6m2 9h6m2-12h6"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.1"/>',clock:'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>',
  download:'<path d="M12 3v12m-4-4 4 4 4-4M4 16v5h16v-5"/>',copy:'<rect x="8" y="8" width="12" height="13" rx="2"/><path d="M16 8V3H3v13h5"/>',menu:'<path d="M4 6h16M4 12h16M4 18h16"/>',
  github:'<path fill="currentColor" stroke="none" d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.86c-2.78.6-3.37-1.18-3.37-1.18-.45-1.15-1.11-1.46-1.11-1.46-.91-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.89 1.53 2.34 1.09 2.91.83.09-.65.35-1.09.64-1.34-2.22-.25-4.56-1.11-4.56-4.94 0-1.09.39-1.98 1.03-2.68-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.75 1.02A9.58 9.58 0 0 1 12 6.83c.85 0 1.71.11 2.51.34 1.91-1.29 2.75-1.02 2.75-1.02.55 1.38.2 2.4.1 2.65.64.7 1.03 1.59 1.03 2.68 0 3.85-2.34 4.69-4.58 4.94.36.31.68.92.68 1.85v2.74c0 .27.18.58.69.48A10 10 0 0 0 12 2Z"/>'
};
function setIcon(node,name){node.innerHTML='<svg class="icon-'+name+'" viewBox="0 0 24 24" aria-hidden="true">'+(icons[name]||icons.info)+'</svg>';}
document.querySelectorAll('[data-icon]').forEach(node=>setIcon(node,node.dataset.icon));
const root=document.documentElement,media=matchMedia('(prefers-color-scheme: dark)'),reduceMotion=matchMedia('(prefers-reduced-motion: reduce)');
// Reorder real DOM links for both visual order and keyboard/screen-reader order.
// iPad desktop browsing can report MacIntel; do not treat a touch iPad as a Mac.
const isMacOS=(navigator.userAgentData?.platform==='macOS'||/Mac/i.test(navigator.platform||''))&&!(navigator.maxTouchPoints>1);
const isAndroid=navigator.userAgentData?.platform==='Android'||/Android/i.test(navigator.userAgent||'');
root.dataset.platform=isMacOS?'macos':isAndroid?'android':'other';
const preferredHero=isMacOS?'hero-macos':isAndroid?'hero-android':'hero-windows';
const preferredPlatform=isMacOS?'mac-download':isAndroid?'android-download':'windows-download';
document.getElementById('hero-downloads').prepend(document.getElementById(preferredHero));document.getElementById('platform-downloads').prepend(document.getElementById(preferredPlatform));
const secondaryPlatforms=document.getElementById('secondary-downloads');
for(const id of ['windows-download','mac-download','android-download']){
  const card=document.getElementById(id);
  if(id===preferredPlatform)card.classList.add('download-current');
  else{card.classList.add('download-secondary');secondaryPlatforms.append(card);}
}
let currentGallery='sites',toastTimer,explicitTheme=false;
try{const saved=localStorage.getItem('cqust-site-theme');if(saved==='dark'||saved==='light'){root.dataset.theme=saved;explicitTheme=true;}else root.dataset.theme=media.matches?'dark':'light';}catch{root.dataset.theme=media.matches?'dark':'light';}
const galleryNames={sites:'常用网站',schedule:'课程表',proxy:'代理与扫码连接'};
function updateGallery(){
  const theme=root.dataset.theme==='dark'?'dark':'light',image=document.getElementById('gallery-image');
  image.src='./assets/'+currentGallery+'-'+theme+'-w532.png';image.alt='CQUST Wi-Fi '+(theme==='dark'?'深色':'亮色')+galleryNames[currentGallery]+'界面';
  document.getElementById('gallery-label').textContent=galleryNames[currentGallery]+' · '+(theme==='dark'?'深色模式':'亮色模式');
  document.getElementById('gallery-panel').setAttribute('aria-labelledby','tab-'+currentGallery);
}
function syncTheme(){
  const dark=root.dataset.theme==='dark';setIcon(document.querySelector('#theme-toggle span'),dark?'sun':'moon');document.getElementById('theme-toggle').setAttribute('aria-label',dark?'切换至亮色主题':'切换至暗色主题');
  document.querySelectorAll('.mock-theme [data-icon],.theme-text [data-icon]:first-child').forEach(node=>setIcon(node,dark?'sun':'moon'));
  document.querySelector('.theme-text').childNodes.forEach(node=>{if(node.nodeType===Node.TEXT_NODE)node.textContent=dark?'也看看亮色模式':'也看看深色模式';});
  document.getElementById('feature-theme-toggle').setAttribute('aria-checked',String(dark));
  document.querySelector('meta[name=theme-color]').content=dark?'#0f1623':'#f7f8fa';updateGallery();
}
let themeTransition;
function toggleTheme(){
  const next=root.dataset.theme==='dark'?'light':'dark';explicitTheme=true;
  try{localStorage.setItem('cqust-site-theme',next);}catch{}
  const change=()=>{root.dataset.theme=next;syncTheme();};
  if(document.startViewTransition&&!reduceMotion.matches){themeTransition?.skipTransition();themeTransition=document.startViewTransition(change);}else change();
}
document.getElementById('theme-toggle').addEventListener('click',toggleTheme);document.querySelectorAll('[data-theme-action]').forEach(button=>button.addEventListener('click',toggleTheme));document.getElementById('feature-theme-toggle').addEventListener('click',toggleTheme);
media.addEventListener('change',event=>{if(!explicitTheme){root.dataset.theme=event.matches?'dark':'light';syncTheme();}});
syncTheme();
const tabs=[...document.querySelectorAll('[data-gallery]')];
function selectGallery(button){currentGallery=button.dataset.gallery;tabs.forEach(tab=>{const selected=tab===button;tab.setAttribute('aria-selected',String(selected));tab.tabIndex=selected?0:-1;});updateGallery();}
tabs.forEach((button,index)=>{button.addEventListener('click',()=>selectGallery(button));button.addEventListener('keydown',event=>{let target;if(event.key==='ArrowDown'||event.key==='ArrowRight')target=(index+1)%tabs.length;else if(event.key==='ArrowUp'||event.key==='ArrowLeft')target=(index+tabs.length-1)%tabs.length;else if(event.key==='Home')target=0;else if(event.key==='End')target=tabs.length-1;else return;event.preventDefault();selectGallery(tabs[target]);tabs[target].focus();});});
let demoOnline=true,demoSeconds=768;
function updateDemoTime(){const s=demoOnline?demoSeconds:0;document.getElementById('demo-time').textContent=[Math.floor(s/3600),Math.floor(s/60)%60,s%60].map(n=>String(n).padStart(2,'0')).join(':');}
document.getElementById('demo-power').addEventListener('click',()=>{
  demoOnline=!demoOnline;demoSeconds=0;document.getElementById('demo-panel').classList.toggle('mock-off',!demoOnline);
  document.getElementById('demo-title').textContent=demoOnline?'已连接':'已断开';document.getElementById('demo-message').textContent=demoOnline?'让灵感在线，让日常自在。':'点一下，重新回到在线。';
  const pill=document.getElementById('demo-pill');pill.replaceChildren(document.createElement('i'),document.createTextNode(demoOnline?'已连接':'未连接'));
  document.getElementById('demo-power').setAttribute('aria-label',demoOnline?'演示：断开连接':'演示：重新连接');document.getElementById('demo-power').setAttribute('aria-pressed',String(demoOnline));updateDemoTime();
});
setInterval(()=>{if(demoOnline&&!document.hidden){demoSeconds++;updateDemoTime();}},1000);
const menu=document.getElementById('menu-toggle'),navigation=document.getElementById('site-nav');
function closeMenu(){navigation.classList.remove('open');menu.setAttribute('aria-expanded','false');menu.setAttribute('aria-label','展开导航');}
menu.addEventListener('click',()=>{const open=!navigation.classList.contains('open');navigation.classList.toggle('open',open);menu.setAttribute('aria-expanded',String(open));menu.setAttribute('aria-label',open?'收起导航':'展开导航');});
navigation.querySelectorAll('a').forEach(link=>link.addEventListener('click',closeMenu));document.addEventListener('keydown',event=>{if(event.key==='Escape')closeMenu();});document.addEventListener('click',event=>{if(!event.target.closest('.nav-wrap'))closeMenu();});
function toast(message){const node=document.getElementById('toast');node.textContent=message;node.classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>node.classList.remove('visible'),3000);}
document.getElementById('copy-checksum').addEventListener('click',async()=>{try{await navigator.clipboard.writeText('1bd1f20e81a2e24b2284405f6acbc2eacce17d727c2657ba18f0ee2f15f1fc2c');toast('完整 SHA-256 校验值已复制');}catch{toast('请在 GitHub Release 中查看 SHA256SUMS.txt');}});
document.getElementById('copy-android-checksum').addEventListener('click',async()=>{try{await navigator.clipboard.writeText('e0c1f9c1866d52ec70cbf17afe0b4a398f2441a02a432afb3f97d0b0d87e6815');toast('Android 完整 SHA-256 校验值已复制');}catch{toast('请在 Android 发布页查看 SHA-256 校验值');}});
if('IntersectionObserver'in window&&!reduceMotion.matches){root.classList.add('js-motion');const observer=new IntersectionObserver(entries=>{for(const entry of entries)if(entry.isIntersecting){entry.target.classList.add('in-view');observer.unobserve(entry.target);}},{threshold:.08});document.querySelectorAll('[data-reveal]').forEach(node=>observer.observe(node));}
