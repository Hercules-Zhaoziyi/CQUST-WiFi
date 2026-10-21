const $=id=>document.getElementById(id);
const shapes={
 minus:'<path d="M5 12h14"/>',logout:'<path d="M10 4H5v16h5M9 12h12m-4-4 4 4-4 4"/>',userCheck:'<circle cx="9" cy="7" r="3"/><path d="M2 21v-2a7 7 0 0 1 14 0v2m0-10 2 2 4-5"/>',code:'<path d="m8 7-5 5 5 5m8-10 5 5-5 5m-3-13-2 22"/>',
 wifi:'<path d="M3 8a14 14 0 0 1 18 0M6 12a9 9 0 0 1 12 0M9 16a4 4 0 0 1 6 0"/><circle cx="12" cy="20" r=".7"/>',
 moon:'<path d="M20 15A9 9 0 0 1 9 4a9 9 0 1 0 11 11Z"/>',sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
 tray:'<path d="M4 14H2v7h20v-7h-2M12 3v12m-4-4 4 4 4-4"/>',
 sliders:'<path d="M4 4v5m0 4v7M12 4v9m0 4v3M20 4v2m0 4v10M1 9h6m2 8h6m2-11h6"/>',
 list:'<path d="M8 6h13M8 12h13M8 18h13M3 6h.1M3 12h.1M3 18h.1"/>',info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.1"/>',
 user:'<circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',lock:'<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/>',
 chevron:'<path d="m7 10 5 5 5-5"/>',eye:'<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
 eyeOff:'<path d="m3 3 18 18M10 5c7-1 12 7 12 7a19 19 0 0 1-4 5M6 6a20 20 0 0 0-4 6s4 7 10 7a11 11 0 0 0 4-1"/>',
 arrow:'<path d="M4 12h16m-5-5 5 5-5 5"/>',shield:'<path d="m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6Z"/><path d="m8 12 3 3 5-6"/>',
 power:'<path d="M12 2v10M6 5a9 9 0 1 0 12 0"/>',check:'<path d="m5 12 4 4 10-10"/>',
 close:'<path d="m6 6 12 12M6 18 18 6"/>',globe:'<circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/>',
 github:'<path fill="currentColor" stroke="none" d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.86c-2.78.6-3.37-1.18-3.37-1.18-.45-1.15-1.11-1.46-1.11-1.46-.91-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.89 1.53 2.34 1.09 2.91.83.09-.65.35-1.09.64-1.34-2.22-.25-4.56-1.11-4.56-4.94 0-1.09.39-1.98 1.03-2.68-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.75 1.02A9.58 9.58 0 0 1 12 6.83c.85 0 1.71.11 2.51.34 1.91-1.29 2.75-1.02 2.75-1.02.55 1.38.2 2.4.1 2.65.64.7 1.03 1.59 1.03 2.68 0 3.84-2.34 4.69-4.58 4.94.36.31.68.92.68 1.85v2.74c0 .27.18.58.69.48A10 10 0 0 0 12 2Z"/>'
};
function icon(node,name){node.innerHTML=`<svg viewBox="0 0 24 24" aria-hidden="true">${shapes[name]||shapes.info}</svg>`;}
document.querySelectorAll('[data-icon]').forEach(node=>icon(node,node.dataset.icon));
let state=null,desktop=null,busy=false,showDashboard=false,authKind='context',captchaReady=false,captchaLoading=false,toastTimer,logs=[];
function toast(message){$('toast').textContent=message;$('toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),3400);}
let themeTarget=null,themeTransition=null;
function theme(value){
 if(themeTarget===value)return;
 const first=themeTarget===null;themeTarget=value;
 const change=()=>{document.documentElement.dataset.theme=value;icon($('theme-toggle'),value==='dark'?'sun':'moon');};
 if(!first&&document.startViewTransition&&document.documentElement.dataset.motion==='full'){
   themeTransition?.skipTransition();themeTransition=document.startViewTransition(change);
 }else change();
}
function page(id){document.querySelector('main').scrollTop=0;document.querySelectorAll('.page').forEach(node=>node.classList.toggle('active',node.id===id));document.querySelectorAll('[data-page]').forEach(node=>node.classList.toggle('selected',node.dataset.page===id));if(id==='settings')fillSettings();}
document.querySelectorAll('[data-page]').forEach(node=>node.addEventListener('click',()=>page(node.dataset.page)));
function apply(next){state=next;document.documentElement.dataset.motion=next.settings.animations===false?'off':'full';if(next.connected)showDashboard=true;$('login-card').hidden=showDashboard;$('dashboard').hidden=!showDashboard;$('dashboard').classList.toggle('online',next.connected);$('status-pill').textContent=next.connected?'● 已连接':'未连接';$('status-title').textContent=next.connected?'已连接':'已断开';$('status-detail').textContent=next.connected?greeting():'点击连接按钮，重新连接校园网。';$('display-name').textContent=next.display_name||'校园网用户';$('power').setAttribute('aria-label',next.connected?'断开校园网':'连接校园网');$('metric-user').textContent=next.username||'—';$('metric-ip').textContent=next.ip||'—';$('footer').textContent=`每 ${next.settings.check_interval===120?'2 分钟':next.settings.check_interval+' 秒'} 检查 · ${next.settings.keep_connected?'自动保持连接':'手动连接模式'}`;if(next.logs){logs=[...next.logs];drawLogs();}renderAccounts();clock();}
function setBusy(value){busy=value;document.body.classList.toggle('busy',value);['login','power','switch-account','auth-submit'].forEach(id=>$(id).disabled=value);if(authKind==='captcha')$('auth-submit').disabled=value||!captchaReady;$('login').firstChild.textContent=value?'正在连接…':'登录';}
function fillSettings(){if(!state||!desktop)return;$('startup').checked=desktop.startup;$('animations').checked=state.settings.animations!==false;$('window-snapping').checked=state.settings.window_snapping!==false;$('auto-login').checked=state.settings.auto_login;$('keep-connected').checked=state.settings.keep_connected;$('update-current').textContent=`当前版本 ${desktop.version}`;setIntervalChoice(state.settings.check_interval);}
let intervalChoice=10;
function setIntervalChoice(value){intervalChoice=Number(value);document.querySelectorAll('[data-interval]').forEach(button=>{const selected=Number(button.dataset.interval)===intervalChoice;button.setAttribute('aria-checked',String(selected));button.tabIndex=selected?0:-1;});}
const intervalButtons=[...document.querySelectorAll('[data-interval]')];
intervalButtons.forEach((button,index)=>{
 button.onclick=()=>{setIntervalChoice(button.dataset.interval);savePreferences({check_interval:intervalChoice});};
 button.onkeydown=event=>{let target;if(event.key==='ArrowRight'||event.key==='ArrowDown')target=(index+1)%3;else if(event.key==='ArrowLeft'||event.key==='ArrowUp')target=(index+2)%3;else if(event.key==='Home')target=0;else if(event.key==='End')target=2;else return;event.preventDefault();setIntervalChoice(intervalButtons[target].dataset.interval);intervalButtons[target].focus();savePreferences({check_interval:intervalChoice});};
});
function drawLogs(){$('log-lines').textContent=logs.length?logs.join('\n'):'暂无日志';$('log-lines').scrollTop=$('log-lines').scrollHeight;}
function renderAccounts(){const box=$('account-list');box.replaceChildren();for(const username of state?.accounts||[]){const row=document.createElement('div');const select=document.createElement('button');select.type='button';select.textContent=username;select.onclick=()=>{$('username').value=username;$('password').value='';$('password').placeholder='密码已保存，可留空直接登录';box.hidden=true;};const remove=document.createElement('button');remove.type='button';remove.textContent='移除';remove.title='删除该账号及其保存的密码';remove.onclick=async()=>{const result=await call('forget',{username});if(result.ok)apply(result.data);};row.append(select,remove);box.append(row);}if(!box.children.length){box.textContent='暂无保存的账号';}}
async function call(method,args={}){try{return await window.cqust.request(method,args);}catch{return {ok:false,error:{message:'应用服务暂时不可用，请重新启动。'}};}}
function credentials(){return {reconnect:showDashboard,username:$('username').value.trim()||state?.username||'',password:$('password').value,remember:$('remember').checked,url:$('auth-url').value.trim(),captcha:$('captcha').value.trim(),challenge:authKind==='captcha'};}
function help(error={kind:'context',message:'通常会自动获取认证参数。若失败，可粘贴当前校园网登录页的完整网址。'}){authKind=error.kind;$('auth-title').textContent=authKind==='online'?'发现已有在线会话':'认证帮助';$('auth-message').textContent=error.message;$('auth-url-tip').hidden=true;$('auth-submit').disabled=busy;$('url-row').hidden=authKind!=='context';$('service').hidden=authKind!=='services';$('captcha-row').hidden=authKind!=='captcha';$('auth-submit').textContent=authKind==='online'?'确认下线并继续登录':'继续登录';$('service').replaceChildren();for(const value of error.services||[]){const option=document.createElement('option');option.value=value;option.textContent=value;$('service').append(option);}if(authKind==='captcha')showCaptcha(error);if(!$('auth-dialog').open)$('auth-dialog').showModal();}
function showCaptcha(error){
 captchaReady=false;const img=$('captcha-image');img.hidden=true;img.removeAttribute('src');$('captcha').value='';$('captcha').disabled=true;$('auth-submit').disabled=true;$('captcha-status').hidden=false;
 $('captcha-status').textContent=error.image?'正在加载验证码…':'图片加载失败，请点击刷新重试';
 img.onload=()=>{captchaReady=true;img.hidden=false;$('captcha-status').hidden=true;$('captcha').disabled=false;$('auth-submit').disabled=busy;$('captcha').focus();};
 img.onerror=()=>{captchaReady=false;img.hidden=true;$('captcha-status').hidden=false;$('captcha-status').textContent='图片加载失败，请点击刷新重试';$('auth-submit').disabled=true;};
 if(error.image&&/^data:image\/(png|jpeg|gif|bmp|webp);base64,/.test(error.image))img.src=error.image;
}
$('captcha-refresh').onclick=async()=>{if(captchaLoading||busy)return;captchaLoading=true;captchaReady=false;$('auth-submit').disabled=true;$('captcha').disabled=true;$('captcha-refresh').disabled=true;try{const r=await call('captcha');if(r.ok){$('auth-message').textContent=r.data.message;showCaptcha(r.data);}else if(r.error.kind==='context')help(r.error);else toast(r.error.message);}finally{captchaLoading=false;$('captcha-refresh').disabled=false;}};
async function login(method='login'){if(busy)return;setBusy(true);const result=await call(method,credentials());setBusy(false);if(result.ok){$('password').value='';$('auth-url').value='';$('captcha').value='';$('auth-dialog').close();apply(result.data);document.querySelector('main').scrollTop=0;}else if(['online','context','services','captcha'].includes(result.error.kind))help(result.error);else toast(result.error.message);}
$('login-form').addEventListener('submit',event=>{event.preventDefault();login();});
$('eye').onclick=()=>{const reveal=$('password').type==='password';$('password').type=reveal?'text':'password';icon($('eye'),reveal?'eyeOff':'eye');$('eye').title=reveal?'隐藏密码':'显示密码';$('eye').setAttribute('aria-label',$('eye').title);};
$('accounts-toggle').onclick=()=>{$('account-list').hidden=!$('account-list').hidden;};
$('auth-help').onclick=()=>help();$('auth-close').onclick=()=>$('auth-dialog').close();
$('auth-submit').onclick=async()=>{if(authKind==='context'){let valid=false;try{const u=new URL($('auth-url').value.trim());valid=['http:','https:'].includes(u.protocol)&&['aaa.cqust.edu.cn','172.28.228.2'].includes(u.hostname)&&/(?:^|&)wlanuserip=/i.test(u.search.slice(1));}catch{}if(!valid){$('auth-url-tip').textContent='请复制浏览器跳转后的完整网址，包括 ? 后的内容；不要只填写认证首页地址。';$('auth-url-tip').hidden=false;$('auth-url').focus();return;}}if(authKind==='captcha'&&(!captchaReady||!/^[A-Za-z0-9]{1,16}$/.test($('captcha').value.trim()))){toast('请输入图片中的字母或数字验证码');return;}if(authKind==='services'){const r=await call('settings',{service:$('service').value});if(!r.ok){toast(r.error.message);return;}}await login(authKind==='online'?'switchOnline':showDashboard?'reconnect':'login');};
$('power').onclick=async()=>{if(busy)return;if(!state.connected){await login('reconnect');return;}setBusy(true);const result=await call('disconnect');setBusy(false);if(result.ok)apply(result.data);else toast(result.error.message);};
$('switch-account').onclick=()=>{if(state.connected)$('confirm-dialog').showModal();else logout();};
$('confirm-cancel').onclick=()=>$('confirm-dialog').close();$('confirm-yes').onclick=async()=>{if(busy)return;$('confirm-dialog').close();setBusy(true);await logout();};
let preferenceQueue=Promise.resolve();
function savePreferences(patch){
 preferenceQueue=preferenceQueue.then(async()=>{const result=await call('settings',patch);if(result.ok){apply(result.data);toast('已生效');}else{fillSettings();toast(result.error.message);}});return preferenceQueue;
}
for(const [id,key] of [['auto-login','auto_login'],['keep-connected','keep_connected'],['animations','animations'],['window-snapping','window_snapping']]){
 $(id).onchange=()=>{if(key==='animations')document.documentElement.dataset.motion=$(id).checked?'full':'off';savePreferences({[key]:$(id).checked});};
}
$('startup').onchange=async()=>{const toggle=$('startup');const desired=toggle.checked;toggle.disabled=true;try{const result=await window.cqust.desktop('startup',desired);if(result.ok){desktop.startup=result.value;toast(desired?'已开启开机自启':'已关闭开机自启');}else{toggle.checked=desktop.startup;toast(result.message);}}catch{toggle.checked=desktop.startup;toast('启动项操作失败，请稍后重试。');}finally{toggle.disabled=false;}};
async function logout(){setBusy(true);const result=await call('logout');setBusy(false);if(result.ok){showDashboard=false;$('password').value='';apply(result.data);document.querySelector('main').scrollTop=0;}else toast(result.error.message);}
function greeting(hour=new Date().getHours()){if(hour>=5&&hour<11)return '早上好，新的一天，从容出发。';if(hour>=11&&hour<14)return '中午好，忙碌之余，也别忘了休息。';if(hour>=14&&hour<18)return '下午好，愿你的灵感始终在线。';if(hour>=18&&hour<23)return '晚上好，留一点时间给热爱的事。';return '夜深了，网络在线，也记得早点休息。';}
$('window-minimize').onclick=()=>window.cqust.desktop('minimize');
$('window-close').onclick=()=>window.cqust.desktop('close');
$('theme-toggle').onclick=async()=>{const value=document.documentElement.dataset.theme==='dark'?'light':'dark';theme(await window.cqust.desktop('theme',value));const result=await call('settings',{theme:value});if(!result.ok)toast('主题已切换，但偏好保存失败。');};
$('system-theme').onclick=async()=>{theme(await window.cqust.desktop('theme','system'));const result=await call('settings',{theme:'system'});toast(result.ok?'已跟随系统主题':'主题偏好保存失败');};
$('to-tray').onclick=()=>window.cqust.desktop('hide');$('website').onclick=()=>window.cqust.desktop('link','website');$('github').onclick=()=>window.cqust.desktop('link','github');$('source').onclick=()=>window.cqust.desktop('link','source');$('thanks').onclick=()=>window.cqust.desktop('link','thanks');
function clock(){if(state?.connected)$('status-detail').textContent=greeting();const seconds=state?.connected?Math.max(0,Math.floor(Date.now()/1000-state.started)):0;$('metric-time').textContent=[Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(x=>String(x).padStart(2,'0')).join(':');}setInterval(clock,1000);
document.addEventListener('copy',event=>event.preventDefault());document.addEventListener('cut',event=>event.preventDefault());document.addEventListener('contextmenu',event=>event.preventDefault());document.addEventListener('dragstart',event=>{if(event.target.tagName==='IMG')event.preventDefault();});document.querySelectorAll('img').forEach(img=>img.draggable=false);document.addEventListener('keydown',event=>{if(event.key==='F11'||(event.altKey&&event.key==='Enter')||((event.ctrlKey||event.metaKey)&&['c','x','p','s'].includes(event.key.toLowerCase())))event.preventDefault();});
document.addEventListener('visibilitychange',()=>document.body.classList.toggle('background',document.hidden));
window.cqust.subscribe(({event,data})=>{if(event==='state')apply(data);if(event==='theme')theme(data);if(event==='busy')setBusy(data);if(event==='notice')toast(data);if(event==='log'){logs.push(data);logs=logs.slice(-250);drawLogs();}});
(async()=>{desktop=await window.cqust.desktop('info');theme(desktop.theme);const result=await call('snapshot');if(result.ok){apply(result.data);$('username').value=state.username;if(state.accounts.includes(state.username))$('password').placeholder='密码已保存，可留空直接登录';fillSettings();}else toast(result.error.message);})();


document.querySelectorAll('[data-campus]').forEach(button=>button.onclick=async()=>{try{await window.cqust.desktop('campus',button.dataset.campus);}catch{toast('暂时无法打开信息门户，请稍后重试。');}});


$('auth-open').onclick=async()=>{try{await window.cqust.desktop('link','auth');}catch{toast('无法打开浏览器，请手动访问 aaa.cqust.edu.cn。');}};
$('auth-url').addEventListener('input',()=>$('auth-url-tip').hidden=true);
$('auth-url').addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();$('auth-submit').click();}});
$('author').onclick=()=>window.cqust.desktop('link','author').catch(()=>toast('暂时无法打开个人主页。'));
$('download-site').onclick=()=>window.cqust.desktop('link','downloadSite').catch(()=>toast('暂时无法打开官网。'));
let checkingUpdate=false;
$('check-updates').onclick=async()=>{
 if(checkingUpdate)return;checkingUpdate=true;const button=$('check-updates');button.disabled=true;button.textContent='正在检查…';$('update-status').textContent='正在连接 GitHub…';$('update-result').hidden=true;
 try{const update=await window.cqust.desktop('checkUpdates');$('update-status').textContent=update.available?`发现新版本 ${update.latest}`:`当前已是最新正式版本（${update.current}）`;$('update-status').classList.toggle('has-update',update.available);$('update-notes').textContent=update.notes||'暂无更新说明。';$('update-download').hidden=!update.available;$('update-result').hidden=false;$('update-result').querySelector('details').open=update.available;}
 catch(error){$('update-status').textContent=typeof error==='string'?error:'检查失败，请稍后重试。';$('update-status').classList.remove('has-update');}
 finally{checkingUpdate=false;button.disabled=false;button.textContent='重新检查';}
};
for(const [id,command] of [['update-download','updateDownload'],['update-release','updateRelease']])$(id).onclick=()=>window.cqust.desktop(command).catch(()=>toast('无法打开下载页面，请稍后重试。'));
const scrollArea=document.querySelector('main');let scrollHideTimer;
function revealScroll(){scrollArea.classList.add('scroll-active');clearTimeout(scrollHideTimer);scrollHideTimer=setTimeout(()=>scrollArea.classList.remove('scroll-active'),1800);}
scrollArea.addEventListener('scroll',revealScroll,{passive:true});scrollArea.addEventListener('wheel',revealScroll,{passive:true});scrollArea.addEventListener('pointerdown',revealScroll);
let resizeStart=null,resizeFrame=0,resizeTarget=null,resizePending=false;
async function sendHeight(){if(resizePending||resizeTarget===null)return;resizePending=true;const target=resizeTarget;resizeTarget=null;try{await window.cqust.desktop('resizeHeight',target);}finally{resizePending=false;if(resizeTarget!==null)sendHeight();}}
$('height-resize').addEventListener('pointerdown',event=>{if(event.button!==0)return;resizeStart={y:event.screenY,height:window.innerHeight};event.target.setPointerCapture(event.pointerId);event.preventDefault();});
$('height-resize').addEventListener('pointermove',event=>{if(!resizeStart)return;resizeTarget=Math.max(650,Math.round(resizeStart.height+event.screenY-resizeStart.y));if(!resizeFrame)resizeFrame=requestAnimationFrame(()=>{resizeFrame=0;sendHeight();});});
$('height-resize').addEventListener('pointerup',()=>{resizeStart=null;sendHeight();});$('height-resize').addEventListener('pointercancel',()=>{resizeStart=null;});
$('height-resize').addEventListener('keydown',event=>{if(['ArrowUp','ArrowDown'].includes(event.key)){event.preventDefault();window.cqust.desktop('resizeHeight',window.innerHeight+(event.key==='ArrowDown'?20:-20));}});
