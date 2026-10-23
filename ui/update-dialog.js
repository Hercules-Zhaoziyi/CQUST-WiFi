'use strict';
// Only metadata supplied by the native GitHub checker is rendered. Install remains explicit.
document.body.insertAdjacentHTML('beforeend',`<dialog id="new-version-dialog"><div class="dialog-top"><h2 id="new-version-title">软件更新</h2><button id="new-version-close" class="icon-button" aria-label="关闭">×</button></div><div id="new-version-notes" class="markdown-body"></div><p id="new-version-status" role="status"></p><div class="update-dialog-links"><button id="new-version-group" class="text-button">加入 QQ 内测群 ↗</button><button id="new-version-release" class="text-button">GitHub 发布页 ↗</button></div><p class="update-safety">安装包下载后将校验 SHA-256；点击安装后仍需在系统安装界面确认。</p><div class="update-dialog-actions"><button id="new-version-later" class="secondary">稍后</button><button id="new-version-download" class="primary">下载更新</button></div></dialog>`);
let offeredUpdate=null,updateDownloading=false,updatePrepared=false,updateAutoChecking=false;
function showVersion(update){
 if(updateDownloading)return;offeredUpdate=update;updatePrepared=false;
 $('new-version-title').textContent=`发现新版本 ${update.latest}`;
 window.cqustMarkdown.render($('new-version-notes'),update.notes,update.url);
 $('new-version-status').textContent='';$('new-version-download').textContent='下载更新';
 $('new-version-download').disabled=!update.download;$('new-version-later').disabled=false;$('new-version-close').disabled=false;
 if(!$('new-version-dialog').open)$('new-version-dialog').showModal();
}
function dismissVersion(){if(updateDownloading)return;if(offeredUpdate)localStorage.setItem('cqust-dismissed-update',JSON.stringify({version:offeredUpdate.latest,time:Date.now()}));$('new-version-dialog').close();}
$('new-version-close').onclick=dismissVersion;$('new-version-later').onclick=dismissVersion;
$('new-version-dialog').addEventListener('cancel',event=>{event.preventDefault();dismissVersion();});
$('new-version-group').onclick=()=>window.cqust.desktop('betaGroup');
$('new-version-release').onclick=()=>window.cqust.desktop('updateRelease');
$('new-version-download').onclick=async()=>{
 const button=$('new-version-download');if(updateDownloading)return;
 if(updatePrepared){try{await window.cqust.desktop('updateInstall');$('new-version-status').textContent='安装程序已打开，请按系统提示确认。';}catch(error){$('new-version-status').textContent=String(error);}return;}
 updateDownloading=true;button.disabled=true;$('new-version-later').disabled=true;$('new-version-close').disabled=true;
 $('new-version-status').textContent='正在下载…';
 try{await window.cqust.desktop('updatePrepare');updatePrepared=true;button.textContent='安装更新';$('new-version-status').textContent='下载完成，SHA-256 校验通过。';}
 catch(error){$('new-version-status').textContent=String(error);button.textContent='重试下载';}
 finally{updateDownloading=false;button.disabled=false;$('new-version-later').disabled=false;$('new-version-close').disabled=false;}
};
window.__TAURI__.event.listen('cqust-update-progress',event=>{if(updateDownloading)$('new-version-status').textContent=`正在下载 ${event.payload}%…`;});
const previousCheck=$('check-updates').onclick;
$('check-updates').onclick=async()=>{await previousCheck();if(!$('update-download').hidden){try{const value=await window.cqust.desktop('checkUpdates');if(value.available)showVersion(value);}catch{}}};
$('update-download').onclick=async()=>{try{const value=await window.cqust.desktop('checkUpdates');if(value.available)showVersion(value);}catch(error){toast(String(error));}};
let lastAutoAttempt=0;
async function automaticVersionCheck(){
 if(updateAutoChecking||document.hidden||Date.now()-lastAutoAttempt<6*3600000)return;
 lastAutoAttempt=Date.now();updateAutoChecking=true;
 try{const update=await window.cqust.desktop('checkUpdates');let dismissed={};try{dismissed=JSON.parse(localStorage.getItem('cqust-dismissed-update')||'{}');}catch{}
 if(update.available&&(dismissed.version!==update.latest||Date.now()-dismissed.time>86400000)&&!document.querySelector('dialog[open]')&&!busy)showVersion(update);
 }catch{}finally{updateAutoChecking=false;}
}
setTimeout(automaticVersionCheck,12000);setInterval(automaticVersionCheck,6*3600000);document.addEventListener('visibilitychange',automaticVersionCheck);
