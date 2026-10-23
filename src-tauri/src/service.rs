use crate::{auth::{self,Auth,Error,Session},vault};
use serde::{Serialize,Deserialize};
use serde_json::{json,Value};
use std::{path::PathBuf,time::{Duration,Instant,SystemTime,UNIX_EPOCH}};
use tauri::{Emitter,AppHandle,Manager};
use zeroize::Zeroizing;

#[derive(Clone,Serialize,Deserialize)]#[serde(default)]
pub struct Settings{pub keep_connected:bool,pub auto_login:bool,pub check_interval:u64,pub theme:String,pub service:String,pub animations:bool,pub window_snapping:bool,#[serde(default)]pub snapping_revision:u8}
impl Default for Settings{fn default()->Self{Self{keep_connected:false,auto_login:false,check_interval:10,theme:"system".into(),service:String::new(),animations:true,window_snapping:false,snapping_revision:2}}}
#[derive(Default,Serialize,Deserialize)]pub struct Accounts{pub accounts:Vec<String>,pub last_username:String}
pub struct Service{pub settings:Settings,pub accounts:Vec<String>,pub username:String,pub display_name:String,password:Zeroizing<String>,remember:bool,folder:PathBuf,auth:Auth,session:Option<Session>,started:f64,ip:String,logs:Vec<String>,manual:bool,next_poll:Instant,next_retry:Option<Instant>,isolated:bool}
impl Service{
 pub fn proxy_identity(&self,need_password:bool)->Result<(String,Zeroizing<String>),String>{
  let password=if !need_password{Zeroizing::new(String::new())}else if !self.password.is_empty(){self.password.clone()}else if !self.isolated{vault::get(&self.username)}else{Zeroizing::new(String::new())};
  if need_password&&(self.username.is_empty()||password.is_empty()){return Err("未取得校园网密码，请先登录校园网，或选择自定义代理密码。".into())}
  Ok((self.username.clone(),password))
 }
 pub fn enable_browser_probe(&mut self,app:AppHandle){self.auth.browser_probe=Some(std::sync::Arc::new(move||crate::portal_browser::discover(&app)));}
 pub fn folder(&self)->&PathBuf{&self.folder}
 // 课表同步只在学号与当前校园网账号一致时复用已保存的校园网密码。
 pub fn campus_context(&self,student_id:&str)->crate::schedule::CampusContext{
  let password=if !student_id.is_empty()&&student_id==self.username{
   let value=if !self.password.is_empty(){self.password.clone()}else if !self.isolated{vault::get(&self.username)}else{Zeroizing::new(String::new())};
   if value.is_empty(){None}else{Some(value)}
  }else{None};
  crate::schedule::CampusContext{password}
 }
 pub fn new(custom:Option<PathBuf>)->Self{
  let isolated=custom.is_some();let folder=custom.unwrap_or_else(default_folder);let _=std::fs::create_dir_all(&folder);
  let mut settings:Settings=read(&folder.join("settings.json")).unwrap_or_default();if ![5,10,120].contains(&settings.check_interval){settings.check_interval=10}if !["system","light","dark"].contains(&settings.theme.as_str()){settings.theme="system".into()}if cfg!(target_os="macos"){settings.window_snapping=false}
  let accounts:Accounts=read(&folder.join("accounts.json")).unwrap_or_default();
  // One-time opt-out migration; later manual opt-in remains persistent.
  if settings.snapping_revision<2{settings.window_snapping=false;settings.snapping_revision=2;let path=folder.join("settings.json");if let Ok(bytes)=serde_json::to_vec_pretty(&settings){let temporary=path.with_extension("tmp");let _=std::fs::write(&temporary,bytes).and_then(|_|std::fs::rename(temporary,path));}}
  let next_retry=if !isolated&&settings.auto_login&&!accounts.last_username.is_empty(){Some(Instant::now()+Duration::from_secs(2))}else{None};
  Self{settings,accounts:accounts.accounts,username:accounts.last_username,display_name:String::new(),password:Zeroizing::new(String::new()),remember:true,folder,auth:Auth::default(),session:None,started:0.,ip:auth::lan_ip(),logs:vec![],manual:false,next_poll:Instant::now(),next_retry,isolated}
 }
 pub fn snapshot(&self)->Value{json!({"connected":self.session.is_some(),"username":self.username,"display_name":self.session.as_ref().map(|s|s.display_name.as_str()).filter(|s|!s.is_empty()).unwrap_or(&self.display_name),"accounts":self.accounts,"started":self.started,"ip":self.session.as_ref().map(|s|s.ip.as_str()).unwrap_or(&self.ip),"logs":self.logs,"settings":self.settings})}
 fn emit(&self,app:&AppHandle){let _=app.emit("cqust-event",json!({"event":"state","data":self.snapshot()}));}
 fn log(&mut self,app:&AppHandle,message:&str){let line=format!("{}  {}",chrono::Local::now().format("%H:%M:%S"),message);self.logs.push(line.clone());if self.logs.len()>250{self.logs.remove(0);}let _=app.emit("cqust-event",json!({"event":"log","data":line}));}
 fn write<T:Serialize>(&self,name:&str,data:&T)->Result<(),Error>{let path=self.folder.join(name);let temp=path.with_extension("tmp");let bytes=serde_json::to_vec_pretty(data).map_err(|_|Error::new("error","偏好保存失败。"))?;std::fs::write(&temp,bytes).and_then(|_|std::fs::rename(&temp,&path)).map_err(|_|Error::new("error","本机配置保存失败，请检查用户目录权限。"))}
 fn login(&mut self,app:&AppHandle,args:Value,reconnect:bool,silent:bool)->Result<Value,Error>{
  if self.session.is_some(){return Ok(self.snapshot())}
  if !reconnect{
   self.username=args["username"].as_str().unwrap_or("").trim().to_string();self.password=Zeroizing::new(args["password"].as_str().unwrap_or("").to_string());self.remember=args["remember"].as_bool().unwrap_or(true);
  }
  if self.password.is_empty()&&!self.isolated{self.password=vault::get(&self.username)}
  if self.username.is_empty()||self.password.is_empty(){return Err(Error::new("credentials","请输入校园网账号和密码。"))}
  self.manual=false;self.next_retry=None;
  let session=match self.auth.login(&self.username,&self.password,&args,&self.settings.service){Ok(s)=>s,Err(e)=>{if silent&&e.retryable{self.next_retry=Some(Instant::now()+Duration::from_secs(self.settings.check_interval))}return Err(e)}};
  self.settings.service=session.context.service.clone();self.display_name=session.display_name.clone();self.session=Some(session);self.started=SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs_f64();self.next_poll=Instant::now()+Duration::from_secs(self.settings.check_interval);
  if !self.accounts.contains(&self.username){self.accounts.push(self.username.clone())}
  if self.write("settings.json",&self.settings).is_err()||self.write("accounts.json",&Accounts{accounts:self.accounts.clone(),last_username:self.username.clone()}).is_err(){self.log(app,"已连接，但本机偏好保存失败。")}
  if !self.isolated{if self.remember{if vault::set(&self.username,&self.password).is_err(){self.log(app,"已连接，但凭据保存失败；下次需重新输入密码。")}}else{let _=vault::remove(&self.username);}}
  self.log(app,"校园网已连接。");self.emit(app);Ok(self.snapshot())
 }
 pub fn command(&mut self,app:&AppHandle,method:&str,args:Value)->Result<Value,Error>{
  match method{
   "snapshot"=>Ok(self.snapshot()),
   "captcha"=>self.auth.refresh_captcha(),
   "login"=>self.login(app,args,false,false),
   "reconnect"=>self.login(app,args,true,false),
   "disconnect"|"logout"=>{
    if let Some(session)=&self.session{session.logout()?}
    self.session=None;self.manual=true;self.next_retry=None;self.started=0.;
    if method=="logout"{self.password=Zeroizing::new(String::new());self.display_name.clear();self.auth.clear_account();}
    self.log(app,if method=="logout"{"已退出登录。"}else{"已手动断开，点击连接可重新连接。"});self.emit(app);Ok(self.snapshot())
   },
   "switchOnline"=>{self.auth.logout_discovered()?;std::thread::sleep(Duration::from_secs(1));let reconnect=args["reconnect"].as_bool().unwrap_or(false);self.login(app,args,reconnect,false)},
   "settings"=>{
    let old=self.settings.clone();
    if let Some(v)=args["keep_connected"].as_bool(){self.settings.keep_connected=v}
    if let Some(v)=args["auto_login"].as_bool(){self.settings.auto_login=v}
    if let Some(v)=args["animations"].as_bool(){self.settings.animations=v}
    if let Some(v)=args["window_snapping"].as_bool(){self.settings.window_snapping=v&&cfg!(windows)}
    if let Some(v)=args["check_interval"].as_u64(){if [5,10,120].contains(&v){self.settings.check_interval=v}}
    if let Some(v)=args["theme"].as_str(){if ["light","dark","system"].contains(&v){self.settings.theme=v.into()}}
    if let Some(v)=args["service"].as_str(){if v.len()<512{self.settings.service=v.into()}}
    if let Err(error)=self.write("settings.json",&self.settings){self.settings=old;return Err(error)}
    app.state::<crate::Snapping>().enabled.store(self.settings.window_snapping,std::sync::atomic::Ordering::Relaxed);
    app.state::<crate::Snapping>().animations.store(self.settings.animations,std::sync::atomic::Ordering::Relaxed);
    self.next_poll=Instant::now()+Duration::from_secs(self.settings.check_interval);if !self.settings.keep_connected{self.next_retry=None}self.emit(app);Ok(self.snapshot())
   },
   "forget"=>{
    let user=args["username"].as_str().unwrap_or("");if !self.isolated{vault::remove(user).map_err(|s|Error::new("error",&s))?}
    self.accounts.retain(|x|x!=user);self.write("accounts.json",&Accounts{accounts:self.accounts.clone(),last_username:if self.accounts.contains(&self.username){self.username.clone()}else{String::new()}})?;Ok(self.snapshot())
   },
   _=>Err(Error::new("error","不支持的操作。"))
  }
 }
 pub fn tick(&mut self,app:&AppHandle){
  let now=Instant::now();
  if let Some(session)=&mut self.session{let _=session.keep_alive();}
  if self.session.is_some()&&now>=self.next_poll{
   self.next_poll=now+Duration::from_secs(self.settings.check_interval);
   let status=self.session.as_mut().unwrap().status();
   match status{Ok((false,kicked))=>{
    self.session=None;self.started=0.;self.log(app,"检测到校园网连接已断开。");self.emit(app);
    if self.settings.keep_connected&&!self.manual{self.next_retry=Some(now)}else{let _=app.emit("cqust-event",json!({"event":"notice","data":if kicked{"已在其他设备登录，已下线。"}else{"校园网连接已断开。"}}));}
   },Ok((true,_))=>self.emit(app),Err(_)=>self.log(app,"暂时无法确认在线状态，稍后重试。")}
  }
  if self.session.is_none()&&!self.manual&&self.next_retry.map(|n|now>=n).unwrap_or(false){
   self.next_retry=None;let _=app.emit("cqust-event",json!({"event":"busy","data":true}));if let Err(error)=self.login(app,json!({}),true,true){self.log(app,&format!("自动连接未完成：{}",error.message));}let _=app.emit("cqust-event",json!({"event":"busy","data":false}));
  }
 }
}
fn read<T:serde::de::DeserializeOwned>(path:&PathBuf)->Option<T>{std::fs::read(path).ok().and_then(|bytes|serde_json::from_slice(&bytes).ok())}
#[cfg(windows)]
fn default_folder()->PathBuf{PathBuf::from(std::env::var_os("APPDATA").unwrap_or_default()).join("CQUST Wifi")}
#[cfg(target_os="macos")]
fn default_folder()->PathBuf{PathBuf::from(std::env::var_os("HOME").expect("HOME is required on macOS")).join("Library/Application Support/CQUST Wi-Fi")}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn migration_and_no_password_export(){let folder=std::env::temp_dir().join(format!("cqust-rust-test-{}",std::process::id()));let _=std::fs::create_dir_all(&folder);std::fs::write(folder.join("settings.json"),r#"{"check_interval":7,"theme":"dark","window_snapping":true}"#).unwrap();let mut service=Service::new(Some(folder));assert_eq!(service.settings.check_interval,10);assert_eq!(service.settings.theme,"dark");assert!(!service.settings.window_snapping);assert_eq!(service.settings.snapping_revision,2);service.password=Zeroizing::new("synthetic-secret".into());assert!(!service.snapshot().to_string().contains("synthetic-secret"));assert!(service.next_retry.is_none());}
}
