const {app,BrowserWindow,ipcMain,Tray,Menu,nativeTheme,shell,Notification} = require('electron');
const {spawn}=require('node:child_process');
const path=require('node:path');
const fs=require('node:fs');
const readline=require('node:readline');
const {pathToFileURL}=require('node:url');
const smoke=process.argv.includes('--smoke');
let win,tray,child,quitting=false,sequence=0,latest=null,backendError=false;
const pending=new Map();
const external={website:'https://ziyis.cn',github:'https://github.com/Hercules-Zhaoziyi/CQUST-WiFi'};
const html=path.join(__dirname,'ui','index.html');
const allowed=new Set(['snapshot','login','disconnect','switchOnline','settings','forget']);
if(smoke) app.setPath('userData',path.join(app.getPath('temp'),'cqust-electron-smoke'));
if(!app.requestSingleInstanceLock()) app.quit();
else {
app.on('second-instance',()=>show());
app.whenReady().then(start).catch(()=>{app.exit(1);});
}
function show(){if(win){win.show();if(win.isMinimized())win.restore();win.focus();}}
function send(event,data){if(win&&!win.isDestroyed())win.webContents.send('event',{event,data});}
function startup(){
  if(smoke||!app.isPackaged)return false;
  const item=app.getLoginItemSettings({path:process.execPath,args:['--hidden']});
  return item.openAtLogin && (process.platform!=='win32'||item.executableWillLaunchAtLogin);
}
function theme(){return nativeTheme.shouldUseDarkColors?'dark':'light';}
function request(method,args={}){
  if(!allowed.has(method)||!args||typeof args!=='object'||Array.isArray(args)||JSON.stringify(args).length>24000) return Promise.resolve({ok:false,error:{message:'请求无效。'}});
  if(smoke){
    if(method==='settings')latest.settings={...latest.settings,...args};
    if(method==='login'){latest.connected=true;latest.started=Date.now()/1000;latest.username='demo';}
    if(method==='disconnect'){latest.connected=false;latest.started=0;}
    send('state',latest);return Promise.resolve({ok:true,data:latest});
  }
  if(!child||backendError)return Promise.resolve({ok:false,error:{message:'认证进程不可用，请退出后重新打开软件。'}});
  return new Promise(resolve=>{
    const id=++sequence;
    // Discovery may try several HTTP probes. Do not retry login on an IPC timeout.
    const timer=setTimeout(()=>{pending.delete(id);resolve({ok:false,error:{message:'认证耗时较长，请等待状态更新；勿重复登录。'}});},150000);
    pending.set(id,{resolve,timer});
    child.stdin.write(JSON.stringify({id,method,args})+'\n',err=>{if(err)failBackend();});
  });
}
function failBackend(){
  backendError=true;
  for(const {resolve,timer} of pending.values()){clearTimeout(timer);resolve({ok:false,error:{message:'认证进程已停止，请重新启动软件。'}});}
  pending.clear();send('busy',false);send('notice','认证进程已停止，请退出后重新启动软件。');
}
function bootBackend(){
  if(smoke){latest={connected:false,username:'',accounts:[],ip:'10.0.0.2',started:0,logs:[],settings:{keep_connected:false,auto_login:false,check_interval:10,theme:'system',service:''}};return;}
  const exe=app.isPackaged?path.join(process.resourcesPath,'backend','auth-service.exe'):path.join(__dirname,'backend-dist','auth-service.exe');
  child=spawn(exe,[],{windowsHide:true,stdio:['pipe','pipe','pipe']});
  child.on('error',failBackend);child.on('exit',()=>{if(!quitting)failBackend();});
  child.stdin.on('error',()=>{if(!quitting)failBackend();});
  child.stderr.on('data',()=>{}); // Never forward Python diagnostics containing request context.
  readline.createInterface({input:child.stdout}).on('line',line=>{
    let data;try{data=JSON.parse(line);}catch{return;}
    if(data.event){
      if(data.event==='state'){latest=data.data;nativeTheme.themeSource=latest.settings.theme;updateTray();}
      send(data.event,data.data);
      if(data.event==='notice'&&!win.isVisible()&&Notification.isSupported())new Notification({title:'CQUST Wi-Fi',body:String(data.data),icon:path.join(__dirname,'assets','wifi.ico')}).show();
    } else if(pending.has(data.id)){
      const item=pending.get(data.id);clearTimeout(item.timer);pending.delete(data.id);item.resolve(data);
    }
  });
}
function updateTray(){
  if(!tray)return;
  tray.setToolTip('CQUST Wi-Fi · '+(latest?.connected?'已连接':'未连接'));
  tray.setContextMenu(Menu.buildFromTemplate([
    {label:'显示主窗口',click:show},
    {label:latest?.connected?'校园网已连接':'校园网未连接',enabled:false},
    {type:'separator'},
    {label:'退出软件（不会主动注销校园网）',click:()=>app.quit()}
  ]));
}
async function start(){
  app.setAppUserModelId('cn.ziyis.cqustwifi');Menu.setApplicationMenu(null);
  win=new BrowserWindow({width:720,height:860,minWidth:560,minHeight:650,show:false,
    title:'CQUST Wi-Fi',icon:path.join(__dirname,'assets','wifi.ico'),backgroundColor:'#f5f7fb',
    maximizable:false,fullscreenable:false,resizable:true,autoHideMenuBar:true,
    webPreferences:{preload:path.join(__dirname,'preload.cjs'),nodeIntegration:false,contextIsolation:true,sandbox:true}});
  win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
  win.webContents.on('will-navigate',event=>event.preventDefault());
  win.webContents.session.setPermissionRequestHandler((_wc,_permission,callback)=>callback(false));
  win.webContents.session.setPermissionCheckHandler(()=>false);
  win.webContents.on('before-input-event',(event,input)=>{if(input.key==='F11'||(input.alt&&input.key==='Enter'))event.preventDefault();});
  win.on('close',event=>{if(!quitting&&tray){event.preventDefault();win.hide();}});
  win.on('session-end',()=>{quitting=true;child?.kill();});
  tray=new Tray(path.join(__dirname,'assets','wifi.ico'));
  tray.on('double-click',show);tray.on('click',show);updateTray();
  const check=(event)=>{if(event.sender!==win.webContents||event.senderFrame!==win.webContents.mainFrame||event.senderFrame.url!==pathToFileURL(html).href)throw Error('Unauthorized');};
  ipcMain.handle('request',async(event,method,args)=>{check(event);return request(method,args);});
  ipcMain.handle('desktop',async(event,command,value)=>{
    check(event);
    if(command==='info')return {startup:startup(),theme:theme(),version:app.getVersion(),packaged:app.isPackaged};
    if(command==='startup'){
      if(typeof value!=='boolean')throw Error('Invalid setting');
      if(!app.isPackaged||smoke)return {ok:false,message:'请使用安装版设置开机自启。'};
      app.setLoginItemSettings({openAtLogin:value,enabled:value,path:process.execPath,args:['--hidden'],name:'CQUST Wi-Fi'});
      const actual=startup();return {ok:actual===value,value:actual};
    }
    if(command==='theme'&&['light','dark','system'].includes(value)){nativeTheme.themeSource=value;return theme();}
    if(command==='hide'){win.hide();return true;}
    if(command==='quit'){app.quit();return true;}
    if(command==='link'&&Object.hasOwn(external,value)){if(!smoke)await shell.openExternal(external[value]);return true;}
    return false;
  });
  nativeTheme.on('updated',()=>send('theme',theme()));
  bootBackend();await win.loadFile(html);
  if(!process.argv.includes('--hidden')||smoke)win.show();
  if(smoke)await runSmoke();
}
async function runSmoke(){
  // Isolated test renderer + fake backend: no credentials, registry changes, login or logout.
  await new Promise(r=>setTimeout(r,1200));
  const report=await win.webContents.executeJavaScript(`(async()=>{
    const result={bridge:!!window.cqust,nodeHidden:typeof require==='undefined'};
    document.querySelector('[data-page="settings"]').click();
    document.querySelector('#interval').value='120';
    document.querySelector('#save-settings').click();
    await new Promise(r=>setTimeout(r,150));
    result.settingsStay=document.querySelector('#settings').classList.contains('active');
    result.interval=document.querySelector('#interval').value==='120';
    document.querySelector('[data-page="about"]').click();
    result.githubIcon=!!document.querySelector('#github svg');
    document.querySelector('[data-page="home"]').click();
    result.homeVisible=document.querySelector('#home').classList.contains('active');
    return result;
  })()`);
  report.noMaximize=!win.isMaximizable();report.noFullscreen=!win.isFullScreenable();report.tray=!!tray;
  const folder=path.join(app.isPackaged?path.dirname(process.execPath):__dirname,'smoke-output');fs.mkdirSync(folder,{recursive:true});
  await new Promise(r=>setTimeout(r,400));
  fs.writeFileSync(path.join(folder,'light.png'),(await win.webContents.capturePage()).toPNG());
  await win.webContents.executeJavaScript(`document.querySelector('#theme-toggle').click()`);
  await new Promise(r=>setTimeout(r,350));
  fs.writeFileSync(path.join(folder,'dark.png'),(await win.webContents.capturePage()).toPNG());
  win.setSize(560,760);await new Promise(r=>setTimeout(r,350));
  report.noHorizontalOverflow=await win.webContents.executeJavaScript(`document.documentElement.scrollWidth<=innerWidth`);
  fs.writeFileSync(path.join(folder,'narrow.png'),(await win.webContents.capturePage()).toPNG());
  await win.webContents.executeJavaScript(`document.getElementById('username').value='demo';document.getElementById('password').value='test-only';document.getElementById('login-form').requestSubmit()`);
  await new Promise(r=>setTimeout(r,500));
  report.dashboard=await win.webContents.executeJavaScript(`!document.getElementById('dashboard').hidden`);
  fs.writeFileSync(path.join(folder,'dashboard.png'),(await win.webContents.capturePage()).toPNG());
  await win.webContents.executeJavaScript(`document.querySelector('[data-page="about"]').click()`);
  await new Promise(r=>setTimeout(r,350));
  fs.writeFileSync(path.join(folder,'about.png'),(await win.webContents.capturePage()).toPNG());
  win.close();report.closeHides=!win.isVisible();show();report.trayRestore=win.isVisible();
  fs.writeFileSync(path.join(folder,'report.json'),JSON.stringify(report,null,2));
  quitting=true;app.exit(Object.values(report).every(Boolean)?0:1);
}
app.on('before-quit',()=>{quitting=true;child?.stdin.end();child?.kill();});
app.on('window-all-closed',()=>{});
