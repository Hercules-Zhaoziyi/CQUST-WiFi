use serde_json::{json,Value};
use std::sync::{OnceLock,Mutex};
use tauri::Emitter;
use base64::Engine;
fn data()->&'static Mutex<Value>{static DATA:OnceLock<Mutex<Value>>=OnceLock::new();DATA.get_or_init(||Mutex::new(json!({"connected":false,"username":"demo","display_name":"测试同学","accounts":["demo"],"ip":"10.0.0.2","started":0,"logs":[],"settings":{"keep_connected":false,"auto_login":false,"check_interval":10,"theme":"light","animations":true,"service":"","window_snapping":false}})))}
fn schedule_data()->&'static Mutex<Value>{
 static DATA:OnceLock<Mutex<Value>>=OnceLock::new();
 DATA.get_or_init(||{
  if let Some(value)=std::env::var("CQUST_VERIFY_SCHEDULE").ok().and_then(|path|std::fs::read(path).ok()).and_then(|bytes|serde_json::from_slice::<Value>(&bytes).ok()){
   return Mutex::new(value);
  }
  let value=json!({"syncedAt":0,"profile":{"studentId":"demo","name":"测试同学","college":"测试学院","major":"测试专业","className":"测试1班"},"semesters":[{"id":"2025-2026-1","schoolYear":"2025-2026","name":"1","label":"2025-2026学年 第1学期"}],"semesterId":"2025-2026-1","semesterLabel":"2025-2026学年 第1学期","startDate":"","defaultStartDate":"2025-09-07","totalWeeks":20,"slots":[
 {"name":"高等数学","code":"A101","courseType":"theory","teacher":"教师甲","location":"教学主楼 A101","project":"","day":1,"start":1,"end":2,"weeks":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20],"credit":"4","category":"必修","color":0},
 {"name":"大学英语","code":"A102","courseType":"theory","teacher":"教师乙","location":"博学楼 B203","project":"","day":2,"start":3,"end":4,"weeks":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20],"credit":"3","category":"必修","color":1},
 {"name":"大学物理","code":"A103","courseType":"theory","teacher":"教师丙","location":"笃行楼 K302","project":"","day":3,"start":5,"end":6,"weeks":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20],"credit":"3","category":"必修","color":2},
 {"name":"物理实验","code":"A103","courseType":"practical","teacher":"教师丁","location":"实验楼 L101","project":"实验一","day":4,"start":7,"end":8,"weeks":[3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18],"credit":"3","category":"必修","color":2}
]}
);
  Mutex::new(value)
 })
}

pub fn request(app:&tauri::AppHandle,method:&str,args:Value)->Value{
 if method.starts_with("schedule"){let mut schedule=schedule_data().lock().unwrap();match method{"scheduleSync"=>{schedule["syncedAt"]=json!(1);},"scheduleStartDate"=>{schedule["startDate"]=args["startDate"].clone();},_=>()}return json!({"ok":true,"data":*schedule})}
 if method=="captcha"{return json!({"ok":true,"data":{"kind":"captcha","message":"请输入图片中的验证码","image":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1sAAAAASUVORK5CYII="}})}let mut data=data().lock().unwrap();match method{"settings"=>{if let Some(patch)=args.as_object(){for(k,v)in patch{data["settings"][k]=v.clone()}}},"login"|"reconnect"=>{data["connected"]=json!(true);data["started"]=json!(chrono::Utc::now().timestamp());},"disconnect"|"logout"=>{data["connected"]=json!(false);data["started"]=json!(0);},_=>()}let _=app.emit("cqust-event",json!({"event":"state","data":*data}));json!({"ok":true,"data":*data})}
fn folder()->std::path::PathBuf{let folder=std::env::var_os("CQUST_VERIFY_DIR").map(std::path::PathBuf::from).unwrap_or_else(||std::env::temp_dir().join("cqust-native-verification"));let _=std::fs::create_dir_all(&folder);folder}
pub async fn capture(window:tauri::WebviewWindow,name:String)->Result<bool,String>{
 if !["proxy","dashboard","about","about-top","updates","login","settings","captcha","sites","schedule","schedule-setup","schedule-settings","schedule-dark","schedule-stack","auth-help"].contains(&name.as_str()){return Err("Invalid capture".into())}
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
