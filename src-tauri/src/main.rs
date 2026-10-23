#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
mod auth;
#[cfg(windows)]mod vault;
#[cfg(target_os="macos")]
#[path="vault_macos.rs"]mod vault;
#[cfg(windows)]mod startup;
#[cfg(target_os="macos")]
#[path="startup_macos.rs"]mod startup;
mod platform;
mod service;
mod portal_browser;
#[cfg(windows)]mod snapping;
#[cfg(target_os="macos")]
#[path="snapping_macos.rs"]mod snapping;
mod updates;
mod proxy;
mod schedule;
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
 if method.starts_with("schedule"){
  let backend=app.state::<Backend>().0.clone();
  let student_id=args["student_id"].as_str().unwrap_or("").to_string();
  let context=if !args["password"].as_str().unwrap_or("").is_empty(){schedule::CampusContext::default()}else if let Ok(service)=backend.try_lock(){service.campus_context(&student_id)}else{
   let password=if student_id.len()<=32&&student_id.chars().all(|c|c.is_ascii_alphanumeric()||c=='_'||c=='-'){vault::get(&student_id)}else{zeroize::Zeroizing::new(String::new())};
   schedule::CampusContext{password:if password.is_empty(){None}else{Some(password)}}
  };
  let verified=if method=="scheduleSignIn"{Some((student_id.clone(),zeroize::Zeroizing::new(args["password"].as_str().filter(|s|!s.is_empty()).map(str::to_owned).or_else(||context.password.as_ref().map(|s|s.to_string())).unwrap_or_default()),args["remember"].as_bool().unwrap_or(false),args["network_requested"].as_bool().unwrap_or(false)))}else{None};
  let state=app.state::<schedule::Schedule>().inner().clone();
  let handle=app.clone();
  let result=tauri::async_runtime::spawn_blocking(move||match schedule::command(&handle,&state,&method,args,context){
   Ok(data)=>json!({"ok":true,"data":data}),Err(error)=>json!({"ok":false,"error":error})
  }).await.map_err(|_|"课表操作未完成，请稍后重试。".to_string())?;
  if result["ok"]==true{if let Some((username,password,remember,network_requested))=verified{
   if remember{let _=vault::set(&username,&password);}else{let _=vault::remove(&username);}
   let app=app.clone();tauri::async_runtime::spawn_blocking(move||{if let Ok(mut service)=backend.lock(){service.remember_identity(&app,username,password,remember,network_requested)}});
  }}
  return Ok(result);
 }
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
  "verifyProxyPort" if app.state::<Backend>().1=>{let listener=std::net::TcpListener::bind("127.0.0.1:0").map_err(|_|"Port unavailable")?;Ok(json!(listener.local_addr().map_err(|_|"Port unavailable")?.port()))},
  "proxyStatus"=>Ok(app.state::<proxy::ProxyManager>().snapshot()),
  "proxyQr"=>Ok(json!(app.state::<proxy::ProxyManager>().qr_svg()?)),
  "qrPrivacy"=>{window.set_content_protected(value.as_bool().unwrap_or(false)).map_err(|_|"屏幕保护设置不可用")?;Ok(json!(true))},
  "proxyPassword"=>Ok(json!(proxy::recommend_password()?)),
  "proxyFirewall"=>{platform::open_firewall()?;Ok(json!(true))},
  "proxyStop"=>Ok(app.state::<proxy::ProxyManager>().stop()),
  "proxyStart"=>{let mut options:proxy::Options=serde_json::from_value(value).map_err(|_|"代理配置无效")?;let backend=app.state::<Backend>();let service=backend.0.lock().map_err(|_|"账号状态不可用")?;let (username,password)=service.proxy_identity(options.campus_password)?;if options.username.trim().is_empty(){options.username=username;}if options.username.is_empty(){return Err("请填写代理用户名，或先登录校园网。".into())}if options.campus_password{options.password=password.to_string();}drop(service);app.state::<proxy::ProxyManager>().start(options)},
  "betaGroup"=>{platform::open_url("https://qm.qq.com/q/Rn7B5NBbiK")?;Ok(json!(true))},
  #[cfg(feature="verify-ui")]
  "verifyPortalRedirect" if app.state::<Backend>().1=>{let handle=app.clone();let ok=tauri::async_runtime::spawn_blocking(move||portal_browser::verify_dynamic_redirect(&handle)).await.map_err(|_|"探测测试失败")?;Ok(json!(ok))},
  #[cfg(feature="verify-ui")]
  "verifySnapping" if app.state::<Backend>().1=>{let handle=app.clone();let w=window.clone();tauri::async_runtime::spawn_blocking(move||snapping::verify(&handle,&w)).await.map_err(|_|"吸附测试未完成".into())},
  "info"=>Ok(json!({"startup":startup::enabled(),"theme":if window.theme().ok()==Some(tauri::Theme::Dark){"dark"}else{"light"},"version":env!("CARGO_PKG_VERSION"),"packaged":true,"resizable":window.is_resizable().unwrap_or(true)})),
  "checkUpdates"=>{
   #[cfg(feature="verify-ui")]if app.state::<Backend>().1{return Ok(json!({"current":"5.1.0","latest":"5.2.0","available":true,"notes":"# 测试更新说明\n\n**重点修复**与 `inline`\n\n- 第二项改进\n- 列表项\n\n```rust\nlet safe = true;\n```\n\n| 项目 | 状态 |\n| --- | --- |\n| 吸附 | 已修复 |\n\n[发布页](https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases)\n\n[危险链接](javascript:alert(1))<img src=x onerror=alert(1)><script>window.markdownUnsafe=true</script>","url":"https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases/tag/v5.2.0","download":null}))}
   let update=tauri::async_runtime::spawn_blocking(updates::check).await.map_err(|_|"更新检测未完成")??;*app.state::<updates::Updates>().0.lock().map_err(|_|"更新状态不可用")?=Some(update.clone());Ok(serde_json::to_value(update).map_err(|_|"更新信息异常")?)
  },
  "updatePrepare"=>{
   let state=app.state::<updates::Updates>();if state.2.swap(true,std::sync::atomic::Ordering::AcqRel){return Err("安装包正在下载".into())}
   let cached=state.0.lock().map_err(|_|"更新状态不可用")?.clone();let handle=app.clone();
   let result=tauri::async_runtime::spawn_blocking(move||{let update=cached.ok_or("请先检查更新")?;let path=updates::download(&handle,&update)?;*handle.state::<updates::Updates>().1.lock().map_err(|_|"更新状态不可用")?=Some((update,path));Ok::<_,String>(json!(true))}).await;
   app.state::<updates::Updates>().2.store(false,std::sync::atomic::Ordering::Release);result.map_err(|_|"下载任务未完成")?
  },
  "updateInstall"=>{let prepared=app.state::<updates::Updates>().1.lock().map_err(|_|"更新状态不可用")?.clone().ok_or("请先下载并校验安装包")?;updates::install(&prepared.0,&prepared.1)?;Ok(json!(true))},
  "updateDownload"|"updateRelease"=>{let cache=app.state::<updates::Updates>();let guard=cache.0.lock().map_err(|_|"更新状态不可用")?;let u=guard.as_ref().ok_or("请先检查更新")?;platform::open_url(&u.url)?;Ok(json!(true))},
  "dockHold"=>{app.state::<Snapping>().hold.store(value.as_bool().unwrap_or(false),std::sync::atomic::Ordering::Relaxed);Ok(json!(true))},
  "markdownLink"=>{let target=value.as_str().ok_or("链接无效")?;if target.len()>2048||target.chars().any(char::is_control){return Err("链接无效".into())}let url=url::Url::parse(target).map_err(|_|"链接无效")?;if !matches!(url.scheme(),"https"|"http")||url.host_str().is_none()||!url.username().is_empty()||url.password().is_some(){return Err("链接无效".into())}platform::open_url(url.as_str())?;Ok(json!(true))},
  "startup"=>{let enabled=value.as_bool().ok_or("设置无效")?;match startup::set(enabled){Ok(value)=>Ok(json!({"ok":true,"value":value})),Err(message)=>Ok(json!({"ok":false,"message":message}))}},
  "theme"=>{let mode=value.as_str().unwrap_or("system");let theme=match mode{"light"=>Some(tauri::Theme::Light),"dark"=>Some(tauri::Theme::Dark),_=>None};window.set_theme(theme).map_err(|_|"主题设置失败")?;Ok(json!(if window.theme().ok()==Some(tauri::Theme::Dark){"dark"}else{"light"}))},
  "resizeHeight"=>{app.state::<Snapping>().release(&window);let requested=value.as_f64().ok_or("高度无效")?;if !requested.is_finite(){return Err("高度无效".into())}let scale=window.scale_factor().map_err(|_|"读取窗口比例失败")?;let size=window.inner_size().map_err(|_|"读取窗口大小失败")?;let limit=window.current_monitor().ok().flatten().map(|monitor|monitor.size().height as f64/monitor.scale_factor()-40.).unwrap_or(1200.).max(650.);let height=(requested.clamp(650.,limit)*scale).round() as u32;window.set_size(tauri::PhysicalSize::new(size.width,height)).map_err(|_|"调整窗口高度失败")?;Ok(json!(true))},
  "hide"=>{window.hide().map_err(|_|"隐藏窗口失败")?;Ok(json!(true))},
  "minimize"=>{window.minimize().map_err(|_|"最小化失败")?;Ok(json!(true))},
  "close"=>{window.hide().map_err(|_|"隐藏窗口失败")?;Ok(json!(true))},
  "quit"=>{app.exit(0);Ok(json!(true))},
  "campus"=>{let id=value.as_str().ok_or("链接无效")?;let sites:Vec<Value>=serde_json::from_str(include_str!("../../ui/sites.json")).map_err(|_|"网站清单无效")?;let url=sites.iter().find(|site|site["id"]==id).and_then(|site|site["url"].as_str()).ok_or("链接无效")?;let parsed=url::Url::parse(url).map_err(|_|"链接无效")?;if !matches!(parsed.scheme(),"http"|"https")||!parsed.host_str().map(|host|host=="cqust.edu.cn"||host.ends_with(".cqust.edu.cn")).unwrap_or(false)||!matches!(parsed.port_or_known_default(),Some(80|443))||!parsed.username().is_empty()||parsed.password().is_some(){return Err("链接无效".into())}platform::open_url(url)?;Ok(json!(true))},
  "link"=>{let url=match value.as_str(){Some("downloadSite")=>"https://cqust-wifi.ziyis.cn/",Some("website")=>"https://ziyis.cn",Some("github")=>"https://github.com/yuexps",Some("source")=>"https://github.com/Hercules-Zhaoziyi/CQUST-WiFi",Some("auth")=>"http://aaa.cqust.edu.cn/",Some("author")=>"https://www.xiaohongshu.com/user/profile/60d40af7000000000101f612",Some("thanks")=>"https://github.com/Gloridust/RuijieWIFI-AutoLogin",Some("scheduleThanks")=>"https://github.com/yuexps/cqust-schedule-miuix",Some("probeThanks")=>"https://github.com/Zelm05/campus-autologin",Some("tunnelThanks")=>"https://github.com/heiher/hev-socks5-tunnel",_=>return Err("链接无效".into())};platform::open_url(url)?;Ok(json!(true))},
  _=>Err("不支持的窗口操作".into())
 }
}
fn show(app:&tauri::AppHandle){if let Some(w)=app.get_webview_window("main"){let _=w.unminimize();let _=w.show();#[cfg(windows)]if let Some(s)=app.try_state::<Snapping>(){s.reveal(&w)}let _=w.set_focus();}}
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
  app.manage(proxy::ProxyManager::new()?);
  let mut service=service::Service::new(if verification{Some(std::env::temp_dir().join(format!("cqust-verify-{}",std::process::id())))}else{None});service.enable_browser_probe(app.handle().clone());let schedule=schedule::Schedule::new(service.folder());app.manage(schedule);app.manage(Snapping::new(service.settings.window_snapping));app.manage(updates::Updates::default());let state=Backend(Arc::new(Mutex::new(service)),verification);app.manage(state.clone());
  app.state::<Snapping>().animations.store(state.0.lock().unwrap().settings.animations,std::sync::atomic::Ordering::Relaxed);
  #[cfg(windows)]{let snap_handle=app.handle().clone();std::thread::spawn(move||loop{std::thread::sleep(std::time::Duration::from_millis(16));snap_handle.state::<Snapping>().tick(&snap_handle);});}
  let show_item=MenuItem::with_id(app,"show","显示主窗口",true,None::<&str>)?;let quit_item=MenuItem::with_id(app,"quit","退出软件",true,None::<&str>)?;
  let menu=Menu::with_items(app,&[&show_item,&quit_item])?;
  #[cfg(target_os="macos")]let tray_icon=tauri::image::Image::from_bytes(include_bytes!("../icons/tray-macos.png"))?;
  #[cfg(windows)]let tray_icon=app.default_window_icon().unwrap().clone();
  let tray=TrayIconBuilder::new().icon(tray_icon).tooltip("CQUST Wi-Fi").menu(&menu).show_menu_on_left_click(false);
  #[cfg(target_os="macos")]let tray=tray.icon_as_template(true).title("CQUST");
  tray.on_menu_event(|app,event|match event.id.as_ref(){"show"=>show(app),"quit"=>app.exit(0),_=>()}).on_tray_icon_event(|tray,event|{if matches!(event,TrayIconEvent::Click{button:MouseButton::Left,button_state:MouseButtonState::Up,..}){show(tray.app_handle())}}).build(app)?;
  if !std::env::args().any(|arg|arg=="--hidden"){show(app.handle())}
  if !verification{let handle=app.handle().clone();std::thread::spawn(move||{loop{std::thread::sleep(std::time::Duration::from_millis(600));if let Ok(mut service)=state.0.try_lock(){service.tick(&handle)}}});}
  Ok(())
 }).on_window_event(|window,event|{if window.label()!="main"{return}match event{#[cfg(windows)]tauri::WindowEvent::Moved(position)=>{if let Some(s)=window.app_handle().try_state::<Snapping>(){if s.moved(*position){if let Some(w)=window.app_handle().get_webview_window("main"){s.unclip(&w)}}}},tauri::WindowEvent::CloseRequested{api,..}=>{api.prevent_close();let _=window.hide();},tauri::WindowEvent::ThemeChanged(theme)=>{let _=window.emit("cqust-event",json!({"event":"theme","data":if *theme==tauri::Theme::Dark{"dark"}else{"light"}}));},_=>()}}).build(tauri::generate_context!()).expect("Tauri initialization failed").run(|app,event|{#[cfg(target_os="macos")]if matches!(event,tauri::RunEvent::Reopen{..}){show(app)}#[cfg(windows)]let _=(app,event);});
}
