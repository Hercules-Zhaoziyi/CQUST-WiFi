#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
mod auth;
mod vault;
mod startup;
mod service;
mod portal_browser;
mod snapping;
mod updates;
use snapping::Snapping;
#[cfg(feature="verify-ui")]mod verify;
use std::sync::{Arc,Mutex};
use serde_json::{json,Value};
use tauri::{Manager,Emitter,menu::{Menu,MenuItem},tray::{TrayIconBuilder,TrayIconEvent,MouseButton,MouseButtonState}};

#[derive(Clone)]struct Backend(Arc<Mutex<service::Service>>,bool);
#[tauri::command]
async fn request(app:tauri::AppHandle,window:tauri::WebviewWindow,method:String,args:Value)->Result<Value,String>{
 if window.label()!="main"{return Err("不允许的调用来源".into())}
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
 if window.label()!="main"{return Err("不允许的调用来源".into())}
 let value=value.unwrap_or(Value::Null);
 #[cfg(feature="verify-ui")]if app.state::<Backend>().1{if command=="startup"{return Ok(json!({"ok":true,"value":value}))}if command=="link"||command=="campus"{return Ok(json!(true))}}
 match command.as_str(){
  #[cfg(feature="verify-ui")]
  "verifyPortalRedirect" if app.state::<Backend>().1=>{let handle=app.clone();let ok=tauri::async_runtime::spawn_blocking(move||portal_browser::verify_dynamic_redirect(&handle)).await.map_err(|_|"探测测试失败")?;Ok(json!(ok))},
  #[cfg(feature="verify-ui")]
  "verifySnapping" if app.state::<Backend>().1=>{let handle=app.clone();let w=window.clone();tauri::async_runtime::spawn_blocking(move||snapping::verify(&handle,&w)).await.map_err(|_|"吸附测试未完成".into())},
  "info"=>Ok(json!({"startup":startup::enabled(),"theme":if window.theme().ok()==Some(tauri::Theme::Dark){"dark"}else{"light"},"version":env!("CARGO_PKG_VERSION"),"packaged":true,"resizable":window.is_resizable().unwrap_or(true)})),
  "checkUpdates"=>{
   #[cfg(feature="verify-ui")]if app.state::<Backend>().1{return Ok(json!({"current":"5.1.0","latest":"5.2.0","available":true,"notes":"# 测试更新说明\n\n**重点修复**与 `inline`\n\n- 第二项改进\n- 列表项\n\n```rust\nlet safe = true;\n```\n\n| 项目 | 状态 |\n| --- | --- |\n| 吸附 | 已修复 |\n\n[发布页](https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases)\n\n[危险链接](javascript:alert(1))<img src=x onerror=alert(1)><script>window.markdownUnsafe=true</script>","url":"https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases/tag/v5.2.0","download":null}))}
   let update=tauri::async_runtime::spawn_blocking(updates::check).await.map_err(|_|"更新检测未完成")??;*app.state::<updates::Updates>().0.lock().map_err(|_|"更新状态不可用")?=Some(update.clone());Ok(serde_json::to_value(update).map_err(|_|"更新信息异常")?)
  },
  "updateDownload"|"updateRelease"=>{let cache=app.state::<updates::Updates>();let guard=cache.0.lock().map_err(|_|"更新状态不可用")?;let u=guard.as_ref().ok_or("请先检查更新")?;let url=if command=="updateDownload"{u.download.as_ref().unwrap_or(&u.url)}else{&u.url};std::process::Command::new("rundll32.exe").arg("url.dll,FileProtocolHandler").arg(url).spawn().map_err(|_|"打开下载页面失败")?;Ok(json!(true))},
  "dockHold"=>{app.state::<Snapping>().hold.store(value.as_bool().unwrap_or(false),std::sync::atomic::Ordering::Relaxed);Ok(json!(true))},
  "markdownLink"=>{let target=value.as_str().ok_or("链接无效")?;if target.len()>2048||target.chars().any(char::is_control){return Err("链接无效".into())}let url=url::Url::parse(target).map_err(|_|"链接无效")?;if !matches!(url.scheme(),"https"|"http")||url.host_str().is_none()||!url.username().is_empty()||url.password().is_some(){return Err("链接无效".into())}std::process::Command::new("rundll32.exe").arg("url.dll,FileProtocolHandler").arg(url.as_str()).spawn().map_err(|_|"打开链接失败")?;Ok(json!(true))},
  "startup"=>{let enabled=value.as_bool().ok_or("设置无效")?;match startup::set(enabled){Ok(value)=>Ok(json!({"ok":true,"value":value})),Err(message)=>Ok(json!({"ok":false,"message":message}))}},
  "theme"=>{let mode=value.as_str().unwrap_or("system");let theme=match mode{"light"=>Some(tauri::Theme::Light),"dark"=>Some(tauri::Theme::Dark),_=>None};window.set_theme(theme).map_err(|_|"主题设置失败")?;Ok(json!(if window.theme().ok()==Some(tauri::Theme::Dark){"dark"}else{"light"}))},
  "resizeHeight"=>{app.state::<Snapping>().release(&window);let requested=value.as_f64().ok_or("高度无效")?;if !requested.is_finite(){return Err("高度无效".into())}let scale=window.scale_factor().map_err(|_|"读取窗口比例失败")?;let size=window.inner_size().map_err(|_|"读取窗口大小失败")?;let limit=window.current_monitor().ok().flatten().map(|monitor|monitor.size().height as f64/monitor.scale_factor()-40.).unwrap_or(1200.).max(650.);let height=(requested.clamp(650.,limit)*scale).round() as u32;window.set_size(tauri::PhysicalSize::new(size.width,height)).map_err(|_|"调整窗口高度失败")?;Ok(json!(true))},
  "hide"=>{window.hide().map_err(|_|"隐藏窗口失败")?;Ok(json!(true))},
  "minimize"=>{window.minimize().map_err(|_|"最小化失败")?;Ok(json!(true))},
  "close"=>{window.hide().map_err(|_|"隐藏窗口失败")?;Ok(json!(true))},
  "quit"=>{app.exit(0);Ok(json!(true))},
  "campus"=>{let id=value.as_str().ok_or("链接无效")?;let sites:Vec<Value>=serde_json::from_str(include_str!("../../ui/sites.json")).map_err(|_|"网站清单无效")?;let url=sites.iter().find(|site|site["id"]==id).and_then(|site|site["url"].as_str()).ok_or("链接无效")?;let parsed=url::Url::parse(url).map_err(|_|"链接无效")?;if !matches!(parsed.scheme(),"http"|"https")||!parsed.host_str().map(|host|host=="cqust.edu.cn"||host.ends_with(".cqust.edu.cn")).unwrap_or(false)||!matches!(parsed.port_or_known_default(),Some(80|443))||!parsed.username().is_empty()||parsed.password().is_some(){return Err("链接无效".into())}std::process::Command::new("rundll32.exe").arg("url.dll,FileProtocolHandler").arg(url).spawn().map_err(|_|"打开链接失败")?;Ok(json!(true))},
  "link"=>{let url=match value.as_str(){Some("downloadSite")=>"https://cqust-wifi.ziyis.cn/",Some("website")=>"https://ziyis.cn",Some("github")=>"https://github.com/Hercules-Zhaoziyi",Some("source")=>"https://github.com/Hercules-Zhaoziyi/CQUST-WiFi",Some("auth")=>"http://aaa.cqust.edu.cn/",Some("author")=>"https://www.xiaohongshu.com/user/profile/60d40af7000000000101f612?xsec_token=YBykuSDNRY8IJg1x1y0oc6r7YL7n6Htxy_UtidvkbYg8k=&xsec_source=app_share&xhsshare=CopyLink&appuid=60d40af7000000000101f612&apptime=1737012920&share_id=bd20550e989148b1ba9d719e8b478d24",Some("thanks")=>"https://github.com/Gloridust/RuijieWIFI-AutoLogin",_=>return Err("链接无效".into())};std::process::Command::new("rundll32.exe").arg("url.dll,FileProtocolHandler").arg(url).spawn().map_err(|_|"打开链接失败")?;Ok(json!(true))},
  _=>Err("不支持的窗口操作".into())
 }
}
fn show(app:&tauri::AppHandle){if let Some(w)=app.get_webview_window("main"){let _=w.unminimize();let _=w.show();if let Some(s)=app.try_state::<Snapping>(){s.reveal(&w)}let _=w.set_focus();}}
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
  #[cfg(feature="verify-ui")]if window.label()=="main"&&event.event()==tauri::webview::PageLoadEvent::Finished&&window.app_handle().try_state::<Backend>().map(|s|s.1).unwrap_or(false){let w=window.clone();std::thread::spawn(move||{std::thread::sleep(std::time::Duration::from_millis(500));let _=w.eval(include_str!("../../verification/native.js"));});}
  let _=(window,event);
 }).setup(|app|{
  let verification=cfg!(feature="verify-ui")&&std::env::args().any(|a|a=="--verify");
  let mut service=service::Service::new(if verification{Some(std::env::temp_dir().join(format!("cqust-verify-{}",std::process::id())))}else{None});service.enable_browser_probe(app.handle().clone());app.manage(Snapping::new(service.settings.window_snapping));app.manage(updates::Updates::default());let state=Backend(Arc::new(Mutex::new(service)),verification);app.manage(state.clone());
  app.state::<Snapping>().animations.store(state.0.lock().unwrap().settings.animations,std::sync::atomic::Ordering::Relaxed);
  let snap_handle=app.handle().clone();std::thread::spawn(move||loop{std::thread::sleep(std::time::Duration::from_millis(16));snap_handle.state::<Snapping>().tick(&snap_handle);});
  let show_item=MenuItem::with_id(app,"show","显示主窗口",true,None::<&str>)?;let quit_item=MenuItem::with_id(app,"quit","退出软件",true,None::<&str>)?;
  let menu=Menu::with_items(app,&[&show_item,&quit_item])?;
  TrayIconBuilder::new().icon(app.default_window_icon().unwrap().clone()).tooltip("CQUST Wi-Fi").menu(&menu).show_menu_on_left_click(false).on_menu_event(|app,event|match event.id.as_ref(){"show"=>show(app),"quit"=>app.exit(0),_=>()}).on_tray_icon_event(|tray,event|{if matches!(event,TrayIconEvent::Click{button:MouseButton::Left,button_state:MouseButtonState::Up,..}){show(tray.app_handle())}}).build(app)?;
  if !std::env::args().any(|arg|arg=="--hidden"){show(app.handle())}
  if !verification{let handle=app.handle().clone();std::thread::spawn(move||{loop{std::thread::sleep(std::time::Duration::from_millis(600));if let Ok(mut service)=state.0.try_lock(){service.tick(&handle)}}});}
  Ok(())
 }).on_window_event(|window,event|{if window.label()!="main"{return}match event{tauri::WindowEvent::Moved(position)=>{if let Some(s)=window.app_handle().try_state::<Snapping>(){if s.moved(*position){if let Some(w)=window.app_handle().get_webview_window("main"){s.unclip(&w)}}}},tauri::WindowEvent::CloseRequested{api,..}=>{api.prevent_close();let _=window.hide();},tauri::WindowEvent::ThemeChanged(theme)=>{let _=window.emit("cqust-event",json!({"event":"theme","data":if *theme==tauri::Theme::Dark{"dark"}else{"light"}}));},_=>()}}).run(tauri::generate_context!()).expect("Tauri runtime initialization failed");
}
