use serde_json::{json,Value};
use std::sync::{OnceLock,Mutex};
use tauri::Emitter;
use base64::Engine;
fn data()->&'static Mutex<Value>{static DATA:OnceLock<Mutex<Value>>=OnceLock::new();DATA.get_or_init(||Mutex::new(json!({"connected":false,"username":"demo","display_name":"测试同学","accounts":["demo"],"ip":"10.0.0.2","started":0,"logs":[],"settings":{"keep_connected":false,"auto_login":false,"check_interval":10,"theme":"light","animations":true,"service":""}})))}
pub fn request(app:&tauri::AppHandle,method:&str,args:Value)->Value{if method=="captcha"{return json!({"ok":true,"data":{"kind":"captcha","message":"请输入图片中的验证码","image":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1sAAAAASUVORK5CYII="}})}let mut data=data().lock().unwrap();match method{"settings"=>{if let Some(patch)=args.as_object(){for(k,v)in patch{data["settings"][k]=v.clone()}}},"login"|"reconnect"=>{data["connected"]=json!(true);data["started"]=json!(chrono::Utc::now().timestamp());},"disconnect"|"logout"=>{data["connected"]=json!(false);data["started"]=json!(0);},_=>()}let _=app.emit("cqust-event",json!({"event":"state","data":*data}));json!({"ok":true,"data":*data})}
fn folder()->std::path::PathBuf{let folder=std::env::var_os("CQUST_VERIFY_DIR").map(std::path::PathBuf::from).unwrap_or_else(||std::env::temp_dir().join("cqust-native-verification"));let _=std::fs::create_dir_all(&folder);folder}
pub async fn capture(window:tauri::WebviewWindow,name:String)->Result<bool,String>{
 if !["dashboard","about","login","settings","captcha","sites","auth-help"].contains(&name.as_str()){return Err("Invalid capture".into())}
 tauri::async_runtime::spawn_blocking(move||{
  let (send,recv)=std::sync::mpsc::channel();let file=folder().join(format!("{name}.png"));
  window.with_webview(move|webview|{unsafe{
   use webview2_com::CallDevToolsProtocolMethodCompletedHandler;
   let core=webview.controller().CoreWebView2();
   if let Ok(core)=core{
    let callback=CallDevToolsProtocolMethodCompletedHandler::create(Box::new(move|result,json|{
     let ok=result.is_ok()&&serde_json::from_str::<Value>(&json).ok().and_then(|v|v["data"].as_str().map(str::to_owned)).and_then(|s|base64::engine::general_purpose::STANDARD.decode(s).ok()).map(|bytes|std::fs::write(&file,bytes).is_ok()).unwrap_or(false);
     let _=send.send(ok);Ok(())
    }));
    let _=core.CallDevToolsProtocolMethod(windows::core::w!("Page.captureScreenshot"),windows::core::w!("{\"format\":\"png\"}"),&callback);
   }
  }}).map_err(|_|"Capture initialization failed".to_string())?;
  recv.recv_timeout(std::time::Duration::from_secs(8)).map_err(|_|"Capture timed out".to_string())
 }).await.map_err(|_|"Capture failed".to_string())?
}
pub fn finish(app:tauri::AppHandle,report:Value){let _=std::fs::write(folder().join("report.json"),serde_json::to_vec_pretty(&report).unwrap());let passed=report.as_object().map(|r|r.values().all(|v|v==&json!(true))).unwrap_or(false);std::thread::spawn(move||{std::thread::sleep(std::time::Duration::from_millis(100));app.exit(if passed{0}else{1});});}
