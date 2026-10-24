'use strict';
// Shared desktop/mobile workflow; no credential values are logged or persisted in JS.
const unifiedStyle=document.createElement('link');unifiedStyle.rel='stylesheet';unifiedStyle.href='unified.css';document.head.append(unifiedStyle);
document.querySelector('nav [data-page="about"]')?.remove();
$('to-tray').title='关于';$('to-tray').setAttribute('aria-label','关于');icon($('to-tray'),'info');$('to-tray').onclick=()=>page('about');
$('website').remove();$('author').querySelector('span').textContent='十七号旅客';
const aboutLinks=document.querySelector('#about .links');aboutLinks.prepend($('author'));
$('github').lastChild.textContent='GitHub';
const aboutActions=document.createElement('div');aboutActions.className='about-actions';$('download-site').before(aboutActions);aboutActions.append($('beta-group'),$('download-site'));
const thanks=document.querySelector('.acknowledgements');thanks.querySelector('p')?.remove();
const thanksList=document.createElement('div');thanksList.className='thanks-links';
for(const [target,label] of [['thanks','AutoLogin · 锐捷认证'],['probeThanks','campus-autologin · 网络探测'],['tunnelThanks','HevSocks5Tunnel · 转发核心'],['scheduleThanks','cqust-schedule-miuix · 课程表']]){
 const button=document.createElement('button');button.className='text-button';button.type='button';
 const mark=document.createElement('span');icon(mark,'github');button.append(mark,document.createTextNode(label));
 button.onclick=()=>window.cqust.desktop('link',target);thanksList.append(button);
}
thanks.append(thanksList);
const release=document.createElement('article');release.innerHTML='<span>5.3.0</span><div><strong>课表与设备，轻松连接</strong><p>共用账号密码入口，课表认证不依赖校园网开通；通用代理二维码一键连接安卓；优化账号列表、导航与关于页面。</p></div>';
document.querySelector('.changelog h3').after(release);
const patchRelease=document.createElement('article');patchRelease.innerHTML='<span>5.3.1</span><div><strong>课表访问与扫码体验优化</strong><p>兼容学校官方 HTTP 与 CAS 跳转，保留未知来源拦截；重新设计手机扫码入口和连接二维码面板。</p></div>';document.querySelector('.changelog h3').after(patchRelease);

document.querySelector('#login-card h1').textContent='统一登录';
document.querySelector('#login-card .card-heading p').textContent='一套账号，校园网与课表按需使用。';
document.querySelector('label[for="password"]').textContent='统一认证密码';
const purposes=document.createElement('div');purposes.className='login-purposes';purposes.setAttribute('role','radiogroup');purposes.setAttribute('aria-label','登录用途');
let purpose=localStorage.getItem('cqust-login-purpose')||'network',unifiedBusy=false;
const status=document.createElement('p');status.className='unified-status';status.setAttribute('role','status');
for(const [value,label] of [['network','校园网'],['schedule','课表'],['both','两者']]){const button=document.createElement('button');button.type='button';button.textContent=label;button.dataset.purpose=value;button.setAttribute('role','radio');button.onclick=()=>choosePurpose(value);purposes.append(button);}
$('login').before(purposes,status);
function choosePurpose(value){purpose=['network','schedule','both'].includes(value)?value:'network';localStorage.setItem('cqust-login-purpose',purpose);purposes.querySelectorAll('button').forEach(n=>n.setAttribute('aria-checked',String(n.dataset.purpose===purpose)));status.textContent=purpose==='network'?'仅进行校园网上网认证。':'课表使用统一认证，不要求开通校园网；两条认证链路独立运行。';}
choosePurpose(purpose);
window.openUnifiedLogin=(target='schedule',message='')=>{choosePurpose(target);forceLoginForm=true;if(state)apply(state);page('home');if(message)status.textContent=message;$('username').focus();};
window.unifiedSignIn=async()=>{
 if(unifiedBusy||busy)return;const input={...credentials(),purpose};
 if(!input.username){$('username').focus();return;}
 unifiedBusy=true;
 try{
  const course=purpose!=='network'?window.cqustSchedule.signIn(input):null;
  const network=purpose!=='schedule'?login('login',input):null;
  if(course){status.textContent='统一认证与课表同步中…';const result=await course;
   if(result.ok){status.textContent='课表已同步。';forceLoginForm=false;if(state)apply(state);page('schedule');if(!network)$('password').value='';}
   else{status.textContent=result.error?.message||'课表同步未完成，原缓存已保留。';}
  }
  if(network)await network;
 }finally{unifiedBusy=false;}
};
for(const node of document.querySelectorAll('#schedule-setup label,#schedule-student,.schedule-password,#schedule-password-hint'))node.hidden=true;
$('schedule-sync').textContent='前往共用登录入口';$('schedule-sync').onclick=()=>window.openUnifiedLogin('schedule');
$('schedule-change-account').onclick=()=>window.openUnifiedLogin('schedule');
$('schedule-sync-again').onclick=()=>window.openUnifiedLogin('schedule');
document.querySelector('#schedule-setup p').textContent='只看课表无需开通校园网，可通过移动热点等可用网络登录统一认证。';

// Explicit credential disclosure only after the user asks to show a QR code.
const qrButton=document.createElement('button');qrButton.type='button';qrButton.className='proxy-scan-entry';qrButton.setAttribute('aria-label','扫码连接手机');
qrButton.innerHTML='<span class="proxy-scan-mark" data-icon="qr"></span><span class="proxy-scan-copy"><strong>扫码连接手机</strong><small>自动导入地址与账号，无需逐项填写</small></span><span data-icon="chevronRight"></span>';
shapes.qr='<rect x="3" y="3" width="6" height="6" rx="1"/><rect x="15" y="3" width="6" height="6" rx="1"/><rect x="3" y="15" width="6" height="6" rx="1"/><path d="M15 15h3v3h3m-6 3h3m3-6v-3m-9-9v3m0 6h3M3 12h3m6 3v6"/>';
qrButton.querySelectorAll('[data-icon]').forEach(node=>icon(node,node.dataset.icon));document.querySelector('#proxy-details .proxy-address').after(qrButton);
document.body.insertAdjacentHTML('beforeend','<dialog id="proxy-qr-dialog"><div class="dialog-top"><h2>连接到这台电脑</h2><button id="proxy-qr-close" class="icon-button" aria-label="关闭二维码" data-icon="close"></button></div><p class="qr-intro">在手机 CQUST Wi-Fi 的代理页，点击“扫码连接”</p><div class="qr-paper"><img id="proxy-qr-image" alt="局域网代理连接二维码"></div><div class="qr-endpoint" id="proxy-qr-endpoint"></div><p class="qr-hint">二维码含代理凭据，仅供本人可信设备使用，请勿公开分享。</p></dialog>');icon($('proxy-qr-close'),'close');
qrButton.onclick=async()=>{try{
 await window.cqust.desktop('qrPrivacy',true).catch(()=>{});
 const svg=await window.cqust.desktop('proxyQr');$('proxy-qr-image').src='data:image/svg+xml;base64,'+btoa(svg);$('proxy-qr-endpoint').textContent=$('proxy-endpoint').textContent;$('proxy-qr-dialog').showModal();
}catch(error){await window.cqust.desktop('qrPrivacy',false).catch(()=>{});toast(typeof error==='string'?error:'当前代理配置不能生成二维码。');}};
$('proxy-qr-close').onclick=()=>$('proxy-qr-dialog').close();
$('proxy-qr-dialog').addEventListener('close',()=>{$('proxy-qr-image').removeAttribute('src');window.cqust.desktop('qrPrivacy',false).catch(()=>{});});

