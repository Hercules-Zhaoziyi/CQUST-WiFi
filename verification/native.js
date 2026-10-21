(async()=>{
const wait=ms=>new Promise(r=>setTimeout(r,ms)),$=id=>document.getElementById(id),r={nativeTauri:!!window.__TAURI__,noSaveButton:!document.getElementById('save-settings')};
try{
 document.querySelector('[data-page=settings]').click();$('keep-connected').click();await wait(180);r.instantSetting=$('keep-connected').checked;
 document.querySelector('[data-interval="120"]').click();await wait(180);r.instantInterval=document.querySelector('[data-interval="120"]').getAttribute('aria-checked')==='true';
 await wait(400);r.settingsCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'settings'});
 document.querySelector('[data-page=home]').click();r.logoNotDraggable=!document.querySelector('.school-logo').draggable;
 const logo=document.querySelector('.school-logo').getBoundingClientRect(),heading=document.querySelector('.card-heading>div').getBoundingClientRect();r.logoSameRow=logo.top<heading.bottom&&logo.bottom>heading.top;
 await wait(400);r.loginCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'login'});
 $('password').value='test-only';$('login-form').requestSubmit();await wait(350);r.nameShown=$('display-name').textContent==='测试同学';
 $('power').click();await wait(200);$('power').click();await wait(250);r.reconnectStays=!$('dashboard').hidden&&$('status-title').textContent==='已连接';
 const metric=getComputedStyle(document.querySelector('.metrics>div'));r.metricsCentered=metric.alignItems==='center';
 r.dashboardCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'dashboard'});
 document.querySelector('[data-page=about]').click();const top=document.querySelector('.sticky-top').getBoundingClientRect().top;const width=document.querySelector('main').clientWidth;document.querySelector('main').scrollTop=999;await wait(100);
 r.fixedHeader=document.querySelector('.sticky-top').getBoundingClientRect().top===top;r.stableWidth=width===document.querySelector('main').clientWidth;
 r.aboutLogo=!!document.querySelector('#about>.about-school img');r.signature=document.querySelector('.signature').textContent==='@ 十七号旅客';r.links=['github','source','thanks'].every(id=>!!$(id));
 r.noHorizontalOverflow=document.querySelector('main').scrollWidth<=document.querySelector('main').clientWidth;
 await wait(400);r.aboutCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'about'});
 document.querySelector('[data-page=home]').click();$('switch-account').click();$('confirm-yes').click();await wait(250);r.logoutReturnsLogin=!$('login-card').hidden;
}catch{r.completed=false}
await window.__TAURI__.core.invoke('verification_result',{report:r});
})();
