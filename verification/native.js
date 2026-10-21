(async()=>{
const wait=ms=>new Promise(r=>setTimeout(r,ms)),$=id=>document.getElementById(id),r={nativeTauri:!!window.__TAURI__,noSaveButton:!document.getElementById('save-settings')};
try{
 const initialWidth=window.innerWidth,initialHeight=window.innerHeight;r.fixedWidthNoSystemResize=desktop.resizable===false;await window.cqust.desktop('resizeHeight',initialHeight+20);await wait(250);r.heightResizeKeepsWidth=Math.abs(window.innerWidth-initialWidth)<=2;await window.cqust.desktop('resizeHeight',initialHeight);await wait(250);
 document.querySelector('[data-page=settings]').click();$('keep-connected').click();await wait(180);r.instantSetting=$('keep-connected').checked;
 document.querySelector('[data-interval="120"]').click();await wait(180);r.instantInterval=document.querySelector('[data-interval="120"]').getAttribute('aria-checked')==='true';
 await wait(400);r.settingsCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'settings'});
 document.querySelector('[data-page=home]').click();r.logoNotDraggable=!document.querySelector('.school-logo').draggable;
 const logo=document.querySelector('.school-logo').getBoundingClientRect(),heading=document.querySelector('.card-heading>div').getBoundingClientRect();r.logoSameRow=logo.top<heading.bottom&&logo.bottom>heading.top;
 await wait(400);r.loginCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'login'});
 $('password').value='test-only';$('login-form').requestSubmit();await wait(350);r.nameShown=$('display-name').textContent==='测试同学';
 $('power').click();await wait(200);$('power').click();await wait(250);r.reconnectStays=!$('dashboard').hidden&&$('status-title').textContent==='已连接';
 const metric=getComputedStyle(document.querySelector('.metrics>div'));r.metricsCentered=metric.alignItems==='center';r.statusLetterSpacing=parseFloat(getComputedStyle($('status-title')).letterSpacing)>0;r.scrollPaddingBalanced=getComputedStyle(document.querySelector('main')).paddingLeft===getComputedStyle(document.querySelector('main')).paddingRight;
 r.dashboardCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'dashboard'});
 document.querySelector('[data-page=about]').click();const top=document.querySelector('.sticky-top').getBoundingClientRect().top;const width=document.querySelector('main').clientWidth;document.querySelector('main').scrollTop=999;await wait(100);
 r.fixedHeader=document.querySelector('.sticky-top').getBoundingClientRect().top===top;r.stableWidth=width===document.querySelector('main').clientWidth;
 r.aboutLogo=!!document.querySelector('#about>.about-school img');r.signature=document.querySelector('.signature').textContent==='@ 十七号旅客';r.authorIconLocal=$('author').querySelector('img').complete&&$('author').querySelector('img').naturalWidth>0;r.links=['github','source','thanks'].every(id=>!!$(id));
 r.noHorizontalOverflow=document.querySelector('main').scrollWidth<=document.querySelector('main').clientWidth;
 await wait(400);r.aboutCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'about'});
 document.querySelector('[data-page=sites]').click();await wait(500);r.siteCount=document.querySelectorAll('[data-campus]').length===20;r.siteIconsLocal=[...document.querySelectorAll('.site-tile img')].every(img=>img.complete&&img.naturalWidth>0&&img.getAttribute('src').startsWith('sites/'));r.siteNavigationOrder=[...document.querySelectorAll('nav [data-page]')].map(x=>x.dataset.page).join(',')==='home,sites,settings,logs,about';r.sitesNoOverflow=document.querySelector('main').scrollWidth<=document.querySelector('main').clientWidth;r.siteTitleSpacing=document.querySelector('.site-grid').getBoundingClientRect().top-document.querySelector('#sites .page-title').getBoundingClientRect().bottom>=23;r.scrollbarBreathingRoom=parseFloat(getComputedStyle(document.querySelector('main')).paddingRight)>=12;r.sitesCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'sites'});
 document.querySelector('[data-page=home]').click();$('switch-account').click();$('confirm-yes').click();await wait(250);r.logoutReturnsLogin=!$('login-card').hidden;
 help({kind:'captcha',message:'测试图片加载失败'});r.captchaFailureNoBrokenImage=$('captcha-image').hidden&&$('auth-submit').disabled&&!$('captcha-status').hidden;
 $('captcha').value='http://aaa.cqust.edu.cn/';
 $('captcha-refresh').click();await wait(500);
 r.captchaImageDisplayed=!$('captcha-image').hidden&&$('captcha-image').complete&&$('captcha-image').naturalWidth>0;
 r.captchaRefreshClearsInput=$('captcha').value===''&&!$('captcha').disabled;
 r.captchaUrlSeparated=$('url-row').hidden;
 $('captcha').value='http://invalid/';$('auth-submit').click();await wait(100);r.captchaRejectsUrl=$('auth-dialog').open&&!$('login-card').hidden;
 $('captcha').value='AB12';await wait(400);r.captchaCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'captcha'});$('auth-dialog').close();
 help();r.authGuideVisible=!$('url-row').hidden&&document.querySelectorAll('.auth-guide li').length===3;r.authOpenButton=!!$('auth-open');$('auth-open').click();await wait(100);$('auth-url').value='http://aaa.cqust.edu.cn/';$('auth-submit').click();await wait(100);r.authRejectsBareHomepage=!$('auth-url-tip').hidden&&$('auth-dialog').open;
 $('auth-url').value='';$('auth-url').dispatchEvent(new Event('input'));await wait(400);r.authGuideCapture=await window.__TAURI__.core.invoke('verification_capture',{name:'auth-help'});$('auth-dialog').close();
 const scrollMain=document.querySelector('main');scrollMain.dispatchEvent(new WheelEvent('wheel',{deltaY:50}));r.scrollbarReveals=scrollMain.classList.contains('scroll-active');const stableWidth=scrollMain.clientWidth;await wait(1950);r.scrollbarHides=!scrollMain.classList.contains('scroll-active');r.scrollbarHideNoLayoutShift=stableWidth===scrollMain.clientWidth;
}catch{r.completed=false}
await window.__TAURI__.core.invoke('verification_result',{report:r});
})();
