// Development-only browser harness. Electron is not a Tauri runtime dependency.
const {app,BrowserWindow,ipcMain}=require('electron');
const path=require('node:path'),fs=require('node:fs');
const report={};const events=[];
let win;
let state={connected:false,username:'demo',display_name:'测试同学',accounts:['demo'],started:0,ip:'10.0.0.2',logs:[],settings:{keep_connected:false,auto_login:false,check_interval:10,theme:'light',animations:true,service:''}};
function emit(){win.webContents.send('event',{event:'state',data:state});}
ipcMain.handle('request',(_e,{method,args})=>{
 events.push(method);
 if(method==='settings')state.settings={...state.settings,...args};
 if(method==='login'||method==='reconnect'){state.connected=true;state.started=Date.now()/1000;}
 if(method==='disconnect'||method==='logout'){state.connected=false;state.started=0;}
 emit();return {ok:true,data:state};
});
ipcMain.handle('desktop',(_e,{command,value})=>{
 if(command==='info')return {startup:false,theme:'light',version:'5.0.0',packaged:true};
 if(command==='startup')return {ok:true,value};
 if(command==='theme')return value==='system'?'light':value;
 if(command==='link'){events.push('link:'+value);return true;}
 return true;
});
app.whenReady().then(async()=>{
 const folder=path.join(__dirname,'output');fs.mkdirSync(folder,{recursive:true});
 win=new BrowserWindow({width:540,height:820,show:true,webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,backgroundThrottling:false}});
 await win.loadFile(path.join(__dirname,'../ui/index.html'));
 await new Promise(r=>setTimeout(r,500));
 Object.assign(report,await win.webContents.executeJavaScript(`(async()=>{
 const wait=ms=>new Promise(r=>setTimeout(r,ms)),$=id=>document.getElementById(id),r={};
 r.noSaveButton=!$('save-settings');r.logoNotDraggable=!document.querySelector('.school-logo').draggable;
 const controls=document.querySelector('.window-controls');r.fusedTitlebar=!!controls;
 document.querySelector('[data-page=settings]').click();$('keep-connected').click();await wait(120);
 r.instantSetting=$('keep-connected').checked;document.querySelector('[data-interval="120"]').click();await wait(120);
 r.instantInterval=document.querySelector('[data-interval="120"]').getAttribute('aria-checked')==='true';
 document.querySelector('[data-page=about]').click();const header=document.querySelector('.sticky-top'),top=header.getBoundingClientRect().top;
 document.querySelector('main').scrollTop=900;await wait(50);r.headerFixed=header.getBoundingClientRect().top===top;
 r.schoolAtBottom=!!document.querySelector('#about>.about-school img');r.signature=document.querySelector('.signature').textContent==='@ 十七号旅客';
 for(const id of ['github','source','thanks'])$(id).click();
 document.querySelector('[data-page=home]').click();$('password').value='test-only';$('login-form').requestSubmit();await wait(180);
 r.realName=$('display-name').textContent==='测试同学';r.logoutLabel=$('switch-account').textContent.trim()==='退出登录';
 const a=document.querySelector('.metrics>div'),s=getComputedStyle(a);r.centeredMetrics=s.alignItems==='center';
 $('power').click();await wait(100);$('power').click();await wait(120);r.reconnectDashboard=!$('dashboard').hidden;
 return r;
 })()`));
 await new Promise(r=>setTimeout(r,500));fs.writeFileSync(path.join(folder,'dashboard-light.png'),(await win.webContents.capturePage()).toPNG());
 win.setSize(440,760);await new Promise(r=>setTimeout(r,200));
 Object.assign(report,await win.webContents.executeJavaScript(`(async()=>{
 const $=id=>document.getElementById(id),wait=ms=>new Promise(r=>setTimeout(r,ms));
 $('switch-account').click();$('confirm-yes').click();await wait(150);
 const logo=document.querySelector('.school-logo').getBoundingClientRect(),heading=document.querySelector('.card-heading>div').getBoundingClientRect();
 const r={logoutReturnsLogin:!$('login-card').hidden,logoSameRow:logo.top<heading.bottom&&logo.bottom>heading.top};
 document.querySelector('[data-page=settings]').click();const picker=$('interval').getBoundingClientRect(),label=$('interval').previousElementSibling.getBoundingClientRect();
 r.intervalSameRow=picker.top<label.bottom&&picker.bottom>label.top;
 const width=document.querySelector('main').clientWidth;document.querySelector('[data-page=about]').click();await wait(50);r.scrollbarNoShift=width===document.querySelector('main').clientWidth;
 r.noHorizontalOverflow=document.querySelector('main').scrollWidth<=document.querySelector('main').clientWidth;
 return r;
 })()`));
 report.settingsInvoked=events.includes('settings');report.logoutInvoked=events.includes('logout');report.linksCorrect=['github','source','thanks'].every(x=>events.includes('link:'+x));
 await win.webContents.executeJavaScript(`document.querySelector('#theme-toggle').click()`);await new Promise(r=>setTimeout(r,650));
 fs.writeFileSync(path.join(folder,'about-dark-narrow.png'),(await win.webContents.capturePage()).toPNG());
 fs.writeFileSync(path.join(folder,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));app.exit(Object.values(report).every(Boolean)?0:1);
}).catch(error=>{console.error(error);app.exit(1);});
