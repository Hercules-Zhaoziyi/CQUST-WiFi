'use strict';
const proxyStyle=document.createElement('link');proxyStyle.rel='stylesheet';proxyStyle.href='proxy.css';document.head.append(proxyStyle);
window.cqust.desktop('info').then(info=>{document.querySelector('#about .version').textContent=`${info.version} · Tauri`;}).catch(()=>{});
document.querySelector('.changelog h3').insertAdjacentHTML('afterend','<article><span>5.2.1</span><div><strong>更清晰的代理连接</strong><p>支持复用校园网凭据或自定义代理账号密码；连接信息更清晰，增加防火墙授权指引。</p></div></article><article><span>5.2.0</span><div><strong>边框修复与桌面代理</strong><p>修复贴边后的边框残留；新增 HTTP / HTTPS CONNECT 与 SOCKS5 代理，支持密码认证。</p></div></article>');
// Only static, local markup is inserted. Runtime data uses textContent/value.
const proxyNav=document.createElement('button');proxyNav.dataset.page='proxy';proxyNav.innerHTML='<span data-icon="shield"></span>代理';document.querySelector('nav [data-page="settings"]').before(proxyNav);icon(proxyNav.querySelector('span'),'shield');proxyNav.onclick=()=>page('proxy');
const betaButton=document.createElement('button');betaButton.id='beta-group';betaButton.type='button';betaButton.className='secondary about-download';betaButton.innerHTML='<span data-icon="user"></span>加入内测群<span data-icon="arrow"></span>';$('download-site').before(betaButton);betaButton.querySelectorAll('[data-icon]').forEach(n=>icon(n,n.dataset.icon));betaButton.onclick=()=>window.cqust.desktop('betaGroup').catch(()=>toast('暂时无法打开内测群。'));
const proxyPage=document.createElement('section');proxyPage.id='proxy';proxyPage.className='page';proxyPage.innerHTML=`<div class="page-heading"><span class="eyebrow">LOCAL NETWORK PROXY</span><h1>代理</h1><p>在自己的设备之间，建立显式代理连接。</p></div>
<div class="card proxy-card"><div class="setting"><div><h3>启用代理</h3><p id="proxy-status" role="status">已关闭 · 不监听任何端口</p></div><label class="switch"><input type="checkbox" id="proxy-enabled" aria-label="启用代理"><span></span></label></div>
<div class="proxy-fields" id="proxy-fields"><label>代理协议</label><div class="interval-picker proxy-protocol" role="radiogroup" aria-label="代理协议"><button type="button" role="radio" data-proxy-mode="mixed" aria-checked="true">HTTP + SOCKS5</button><button type="button" role="radio" data-proxy-mode="http" aria-checked="false">HTTP / HTTPS</button><button type="button" role="radio" data-proxy-mode="socks5" aria-checked="false">SOCKS5</button></div>
<label for="proxy-port">监听端口</label><input id="proxy-port" type="number" min="1024" max="65535" value="7890" inputmode="numeric">
<label for="proxy-username">代理用户名</label><input id="proxy-username" type="text" maxlength="255" autocomplete="off" spellcheck="false" placeholder="默认使用当前校园网账号"><p>留空使用当前校园网账号，也可以自行填写。</p>
<label>代理密码</label><label class="proxy-campus-choice"><input id="proxy-campus-password" type="checkbox" checked>使用校园网密码</label>
<p id="proxy-campus-notice">由后台安全读取当前校园网密码，不显示、不复制。建议在可信网络中使用；独立代理密码更安全。</p>
<div id="proxy-custom-row" hidden><div class="proxy-password"><input id="proxy-custom-password" type="text" minlength="8" maxlength="64" autocomplete="off" spellcheck="false" aria-label="自定义代理密码" placeholder="8–64 位英文字符"><button id="proxy-custom-eye" class="text-button proxy-eye" type="button" aria-label="隐藏密码"></button></div><button id="proxy-recommend" class="text-button" type="button">推荐密码</button><p>可自行设置；只用于本次代理认证，不写入配置。</p></div>
<div class="setting"><div><h3>允许局域网设备连接</h3><p>关闭时仅本机可用；开启后仍需账号密码认证</p></div><label class="switch"><input id="proxy-lan" type="checkbox" checked aria-label="允许局域网设备连接"><span></span></label></div></div>
<div id="proxy-details" hidden><div class="proxy-address"><strong id="proxy-endpoint"></strong><button id="proxy-copy-address" class="text-button" type="button">复制地址</button></div><dl class="proxy-connection"><div><dt>服务器</dt><dd id="proxy-server"></dd></div><div><dt>端口</dt><dd id="proxy-active-port"></dd></div><div><dt>代理用户名</dt><dd id="proxy-active-user"></dd></div></dl><p id="proxy-uri-label"></p><code id="proxy-uri"></code><button id="proxy-copy-uri" class="text-button" type="button">复制链接</button>
<div class="proxy-credentials"><label>代理密码<div class="proxy-password"><input id="proxy-password" type="text" readonly aria-label="代理密码"><button type="button" id="proxy-active-eye" class="text-button proxy-eye" aria-label="隐藏密码"></button><button type="button" id="proxy-copy-password" class="text-button">复制</button></div></label><p id="proxy-active-campus" hidden>正在使用校园网密码，为保护账号安全，不提供显示或复制。</p></div><p id="proxy-statistics" aria-live="polite"></p><p>修改配置请先关闭代理。关闭会断开转发；下次启动软件时代理保持关闭。</p></div>
<div class="proxy-firewall"><h3>其他设备连不上？</h3><p>若首次防火墙提示被取消或拒绝，Windows 可能不再重复弹窗。</p><button id="proxy-firewall" type="button" class="secondary">重新打开防火墙设置</button><ol><li>在系统窗口点击“更改设置”，按需确认管理员权限。</li><li>找到 CQUST Wi-Fi，允许当前网络类型；校园网可能被识别为“公用网络”。若有重复条目，请核对程序位置。</li><li>确认系统设置后，关闭再开启代理，并检查设备能否互访。</li></ol><p>此按钮仅打开系统设置，不代表已获得放行；请勿关闭整个防火墙。若仍失败，检查高级入站规则是否存在此程序的阻止规则，或联系网络管理员。网络客户端隔离也会阻止连接。</p></div></div>
<div class="proxy-note"><h3>连接方式</h3><p>HTTP / HTTPS：填写服务器、端口及代理用户名和密码。HTTPS 使用 CONNECT 隧道，不解密内容；此处不提供 TLS 加密的 HTTPS 代理服务器。</p><p>SOCKS5：支持 TCP、UDP ASSOCIATE 和服务端域名解析。客户端须支持用户名 / 密码认证。支持服务端 DNS 的客户端也可使用 socks5h://服务器:端口。</p><p>复制的链接不包含密码，账号密码请在客户端单独填写。HTTP 与 SOCKS5 的认证本身不加密，请仅在可信网络和自己的设备之间使用。</p><p>不提供透明代理、TUN 或 VPN；不转发到内网管理地址和邮件发送端口。请遵守网络使用规则。</p></div>`;
document.querySelector('main').append(proxyPage);
let proxyMode='mixed',proxyWorking=false,proxyInfo={running:false};
let proxyUsernameEdited=false;
$('proxy-username').oninput=()=>{proxyUsernameEdited=$('proxy-username').value.trim()!=='';};
function proxyEye(input,button){icon($(button),$(input).type==='text'?'eyeOff':'eye');$(button).setAttribute('aria-label',$(input).type==='text'?'隐藏密码':'显示密码');}
function syncProxyPassword(){const campus=$('proxy-campus-password').checked;$('proxy-custom-row').hidden=campus;$('proxy-campus-notice').hidden=!campus;if(campus)$('proxy-custom-password').value='';}
$('proxy-campus-password').onchange=syncProxyPassword;
for(const [input,button] of [['proxy-custom-password','proxy-custom-eye'],['proxy-password','proxy-active-eye']]){$(button).onclick=()=>{$(input).type=$(input).type==='text'?'password':'text';proxyEye(input,button);};proxyEye(input,button);}
function drawProxy(value){
 proxyInfo=value;const running=value.running===true,campus=value.campus_password===true;
 if(!running&&!proxyUsernameEdited&&typeof state!=='undefined')$('proxy-username').value=state.username||'';
 $('proxy-enabled').checked=running;$('proxy-details').hidden=!running;$('proxy-fields').hidden=running;
 if(!running&&$('proxy-qr-dialog')?.open)$('proxy-qr-dialog').close();
 const qrAction=document.querySelector('.proxy-scan-entry');if(qrAction){qrAction.disabled=!running||!value.lan||value.mode==='http';qrAction.title=qrAction.disabled?'开启局域网 SOCKS5 或混合代理后可扫码连接':'显示手机连接二维码';}
 document.querySelectorAll('#proxy-fields input,#proxy-fields button').forEach(n=>n.disabled=running||proxyWorking);$('proxy-enabled').disabled=proxyWorking;
 $('proxy-status').textContent=running?(value.lan?'局域网代理运行中 · 账号密码认证已启用':'仅本机代理运行中'):'已关闭 · 不监听任何端口';
 const endpoint=running?`${value.ip}:${value.port}`:'';
 $('proxy-endpoint').textContent=endpoint;$('proxy-server').textContent=running?value.ip:'';$('proxy-active-port').textContent=running?value.port:'';$('proxy-active-user').textContent=running?value.username:'';
 const scheme=value.mode==='http'?'http':'socks5';$('proxy-uri').textContent=running?`${scheme}://${endpoint}`:'';
 $('proxy-uri-label').textContent=value.mode==='mixed'?'SOCKS5 链接（同一端口也支持 HTTP / HTTPS CONNECT）':'代理链接';
 $('proxy-password').closest('label').hidden=campus;$('proxy-active-campus').hidden=!campus;$('proxy-password').value=running&&!campus?value.password||'':'';
 $('proxy-statistics').textContent=running?`连接 ${value.active||0} · 已接入 ${value.accepted||0} · 上传 ${formatProxyBytes(value.up)} · 下载 ${formatProxyBytes(value.down)}`:'';
}
function formatProxyBytes(value){let n=Number(value)||0;for(const unit of ['B','KiB','MiB','GiB']){if(n<1024||unit==='GiB')return `${n.toFixed(unit==='B'?0:1)} ${unit}`;n/=1024;}}
document.querySelectorAll('[data-proxy-mode]').forEach(button=>{button.onclick=()=>{proxyMode=button.dataset.proxyMode;document.querySelectorAll('[data-proxy-mode]').forEach(n=>n.setAttribute('aria-checked',String(n===button)));};button.onkeydown=event=>{if(!['ArrowRight','ArrowLeft'].includes(event.key))return;event.preventDefault();const choices=[...document.querySelectorAll('[data-proxy-mode]')],next=choices[(choices.indexOf(button)+(event.key==='ArrowRight'?1:2))%3];next.click();next.focus();};});
$('proxy-enabled').onchange=async()=>{
 if(proxyWorking)return;const enable=$('proxy-enabled').checked,campus=$('proxy-campus-password').checked;
 if(enable&&!campus&&$('proxy-custom-password').value.length<8){$('proxy-enabled').checked=false;toast('请设置至少 8 位代理密码，或点击推荐密码。');return;}
 proxyWorking=true;drawProxy(proxyInfo);
 try{proxyInfo=await window.cqust.desktop(enable?'proxyStart':'proxyStop',enable?{port:Number($('proxy-port').value),mode:proxyMode,lan:$('proxy-lan').checked,username:$('proxy-username').value.trim(),campus_password:campus,password:campus?'':$('proxy-custom-password').value}:null);toast(enable?'代理已启动；其他设备连接还需防火墙放行':'代理已关闭');}
 catch(error){toast(typeof error==='string'?error:'代理操作未完成。');}
 finally{proxyWorking=false;drawProxy(proxyInfo);}
};
async function recommendProxyPassword(){try{$('proxy-campus-password').checked=false;syncProxyPassword();$('proxy-custom-password').value=await window.cqust.desktop('proxyPassword');$('proxy-custom-password').type='text';proxyEye('proxy-custom-password','proxy-custom-eye');}catch{toast('密码生成失败，请手动设置。');}}
$('proxy-recommend').onclick=recommendProxyPassword;
async function copyProxy(text){try{await navigator.clipboard.writeText(text);toast('已复制');}catch{toast('无法访问剪贴板，请检查系统权限。');}}
$('proxy-copy-address').onclick=()=>copyProxy($('proxy-endpoint').textContent);$('proxy-copy-uri').onclick=()=>copyProxy($('proxy-uri').textContent);
$('proxy-copy-password').onclick=()=>{if(!proxyInfo.campus_password)copyProxy($('proxy-password').value);};
$('proxy-firewall').onclick=async()=>{try{await window.cqust.desktop('proxyFirewall');toast('请在系统设置中确认放行；软件不会自动更改规则。');}catch(error){toast(typeof error==='string'?error:'无法打开防火墙设置。');}};
async function refreshProxy(){if(proxyWorking)return;try{drawProxy(await window.cqust.desktop('proxyStatus'));}catch{}}
refreshProxy();const proxyPoll=setInterval(()=>{if(!document.hidden&&proxyPage.classList.contains('active'))refreshProxy();},1500);
