#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
mod auth;
mod vault;
mod startup;
mod service;
#[cfg(feature="verify-ui")]mod verify;
use std::sync::{Arc,Mutex};
use serde_json::{json,Value};
use tauri::{Manager,Emitter,menu::{Menu,MenuItem},tray::{TrayIconBuilder,TrayIconEvent,MouseButton,MouseButtonState}};

#[derive(Clone)]struct Backend(Arc<Mutex<service::Service>>,bool);
#[tauri::command]
async fn request(app:tauri::AppHandle,method:String,args:Value)->Result<Value,String>{
 #[cfg(feature="verify-ui")]if app.state::<Backend>().1{return Ok(verify::request(&app,&method,args))}
 let backend=app.state::<Backend>().0.clone();Ok(tauri::async_runtime::spawn_blocking(move||{
  let busy=matches!(method.as_str(),"login"|"reconnect"|"disconnect"|"logout"|"switchOnline");
  if busy{let _=app.emit("cqust-event",json!({"event":"busy","data":true}));}
  let reply=if let Ok(mut service)=backend.lock(){service.command(&app,&method,args)}else{Err(auth::Error::new("error","后台服务暂不可用，请重启软件。"))};
  if busy{let _=app.emit("cqust-event",json!({"event":"busy","data":false}));}
  match reply{Ok(data)=>json!({"ok":true,"data":data}),Err(error)=>json!({"ok":false,"error":error})}
 }).await.unwrap_or_else(|_|json!({"ok":false,"error":{"kind":"error","message":"操作未完成，请稍后重试。"}})))
}
#[tauri::command]
async fn desktop(app:tauri::AppHandle,window:tauri::WebviewWindow,command:String,value:Option<Value>)->Result<Value,String>{
 let value=value.unwrap_or(Value::Null);
 #[cfg(feature="verify-ui")]if app.state::<Backend>().1{if command=="startup"{return Ok(json!({"ok":true,"value":value}))}if command=="link"||command=="campus"{return Ok(json!(true))}}
 match command.as_str(){
  "info"=>Ok(json!({"startup":startup::enabled(),"theme":if window.theme().ok()==Some(tauri::Theme::Dark){"dark"}else{"light"},"version":"5.0.1","packaged":true})),
  "startup"=>{let enabled=value.as_bool().ok_or("设置无效")?;match startup::set(enabled){Ok(value)=>Ok(json!({"ok":true,"value":value})),Err(message)=>Ok(json!({"ok":false,"message":message}))}},
  "theme"=>{let mode=value.as_str().unwrap_or("system");let theme=match mode{"light"=>Some(tauri::Theme::Light),"dark"=>Some(tauri::Theme::Dark),_=>None};window.set_theme(theme).map_err(|_|"主题设置失败")?;Ok(json!(if window.theme().ok()==Some(tauri::Theme::Dark){"dark"}else{"light"}))},
  "hide"=>{window.hide().map_err(|_|"隐藏窗口失败")?;Ok(json!(true))},
  "minimize"=>{window.minimize().map_err(|_|"最小化失败")?;Ok(json!(true))},
  "close"=>{window.hide().map_err(|_|"隐藏窗口失败")?;Ok(json!(true))},
  "quit"=>{app.exit(0);Ok(json!(true))},
  "campus"=>{let id=value.as_str().ok_or("链接无效")?;let sites:Vec<Value>=serde_json::from_str(include_str!("../../ui/sites.json")).map_err(|_|"网站清单无效")?;let url=sites.iter().find(|site|site["id"]==id).and_then(|site|site["url"].as_str()).ok_or("链接无效")?;let parsed=url::Url::parse(url).map_err(|_|"链接无效")?;if !matches!(parsed.scheme(),"http"|"https")||!parsed.host_str().map(|host|host=="cqust.edu.cn"||host.ends_with(".cqust.edu.cn")).unwrap_or(false)||!matches!(parsed.port_or_known_default(),Some(80|443))||!parsed.username().is_empty()||parsed.password().is_some(){return Err("链接无效".into())}std::process::Command::new("rundll32.exe").arg("url.dll,FileProtocolHandler").arg(url).spawn().map_err(|_|"打开链接失败")?;Ok(json!(true))},
  "link"=>{let url=match value.as_str(){Some("website")=>"https://ziyis.cn",Some("github")=>"https://github.com/Hercules-Zhaoziyi",Some("source")=>"https://github.com/Hercules-Zhaoziyi/CQUST-WiFi",Some("thanks")=>"https://github.com/Gloridust/RuijieWIFI-AutoLogin",_=>return Err("链接无效".into())};std::process::Command::new("rundll32.exe").arg("url.dll,FileProtocolHandler").arg(url).spawn().map_err(|_|"打开链接失败")?;Ok(json!(true))},
  _=>Err("不支持的窗口操作".into())
 }
}
fn show(app:&tauri::AppHandle){if let Some(w)=app.get_webview_window("main"){let _=w.unminimize();let _=w.show();let _=w.set_focus();}}
#[tauri::command]async fn verification_capture(window:tauri::WebviewWindow,name:String)->Result<bool,String>{
 #[cfg(feature="verify-ui")]if window.app_handle().state::<Backend>().1{return verify::capture(window,name).await}
 let _=(window,name);Err("Verification disabled".into())
}
#[tauri::command]fn verification_result(app:tauri::AppHandle,report:Value)->Result<bool,String>{
 #[cfg(feature="verify-ui")]if app.state::<Backend>().1{verify::finish(app,report);return Ok(true)}
 let _=(app,report);Err("Verification disabled".into())
}
fn main(){
 tauri::Builder::default().invoke_handler(tauri::generate_handler![request,desktop,verification_capture,verification_result]).on_page_load(|window,event|{
  #[cfg(feature="verify-ui")]if event.event()==tauri::webview::PageLoadEvent::Finished&&window.app_handle().try_state::<Backend>().map(|s|s.1).unwrap_or(false){let w=window.clone();std::thread::spawn(move||{std::thread::sleep(std::time::Duration::from_millis(500));let _=w.eval(include_str!("../../verification/native.js"));});}
  let _=(window,event);
 }).setup(|app|{
  let verification=cfg!(feature="verify-ui")&&std::env::args().any(|a|a=="--verify");
  let service=service::Service::new(if verification{Some(std::env::temp_dir().join(format!("cqust-verify-{}",std::process::id())))}else{None});let state=Backend(Arc::new(Mutex::new(service)),verification);app.manage(state.clone());
  let show_item=MenuItem::with_id(app,"show","显示主窗口",true,None::<&str>)?;let quit_item=MenuItem::with_id(app,"quit","退出软件",true,None::<&str>)?;
  let menu=Menu::with_items(app,&[&show_item,&quit_item])?;
  TrayIconBuilder::new().icon(app.default_window_icon().unwrap().clone()).tooltip("CQUST Wi-Fi").menu(&menu).show_menu_on_left_click(false).on_menu_event(|app,event|match event.id.as_ref(){"show"=>show(app),"quit"=>app.exit(0),_=>()}).on_tray_icon_event(|tray,event|{if matches!(event,TrayIconEvent::Click{button:MouseButton::Left,button_state:MouseButtonState::Up,..}){show(tray.app_handle())}}).build(app)?;
  if !std::env::args().any(|arg|arg=="--hidden"){show(app.handle())}
  if !verification{let handle=app.handle().clone();std::thread::spawn(move||{loop{std::thread::sleep(std::time::Duration::from_millis(600));if let Ok(mut service)=state.0.try_lock(){service.tick(&handle)}}});}
  Ok(())
 }).on_window_event(|window,event|{match event{tauri::WindowEvent::CloseRequested{api,..}=>{api.prevent_close();let _=window.hide();},tauri::WindowEvent::ThemeChanged(theme)=>{let _=window.emit("cqust-event",json!({"event":"theme","data":if *theme==tauri::Theme::Dark{"dark"}else{"light"}}));},_=>()}}).run(tauri::generate_context!()).expect("Tauri runtime initialization failed");
}
