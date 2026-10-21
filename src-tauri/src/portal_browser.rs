//! No passwords, IPC privileges, persisted browsing data or external navigation.
//! Used only when the plain-HTTP redirect probe cannot find a login page.
use std::sync::{Arc,atomic::{AtomicUsize,Ordering},mpsc};
use std::time::Duration;
use tauri::{Manager,WebviewUrl,WebviewWindowBuilder};
use url::Url;

pub fn candidate(url:&Url)->bool{
 crate::auth::trusted(url)&&((url.path().ends_with("/index.jsp")&&url.query_pairs().any(|(key,v)|key.eq_ignore_ascii_case("wlanuserip")&&!v.is_empty()))
 ||(url.path().ends_with("/success.jsp")&&url.query_pairs().any(|(key,v)|key=="userIndex"&&crate::auth::valid_index(&v))))
}
fn allowed(url:&Url)->bool{crate::auth::trusted(url)||url.as_str()=="http://www.msftconnecttest.com/redirect"}
#[cfg(feature="verify-ui")]fn diagnostic(message:String){if let Some(folder)=std::env::var_os("CQUST_VERIFY_DIR"){use std::io::Write;if let Ok(mut f)=std::fs::OpenOptions::new().append(true).create(true).open(std::path::PathBuf::from(folder).join("portal-probe.log")){let _=writeln!(f,"{message}");}}}
pub fn discover(app:&tauri::AppHandle)->Option<Url>{
 probe(app,&["http://aaa.cqust.edu.cn/","http://www.msftconnecttest.com/redirect"])
}
#[cfg(feature="verify-ui")]pub fn verify_dynamic_redirect(app:&tauri::AppHandle)->bool{
 let fixture="about:blank";
 probe(app,&[fixture]).map(|u|candidate(&u)&&u.query_pairs().any(|(k,v)|k=="wlanuserip"&&v=="synthetic-test")).unwrap_or(false)
}
fn probe(app:&tauri::AppHandle,entries:&[&str])->Option<Url>{
 static IDS:AtomicUsize=AtomicUsize::new(0);
 for entry in entries{
  let entry=entry.to_string();
  let label=format!("portal-probe-{}",IDS.fetch_add(1,Ordering::Relaxed));
  let (send,recv)=mpsc::channel();let nav_send=send.clone();let count=Arc::new(AtomicUsize::new(0));
  let handle=app.clone();let window_label=label.clone();
  // This function runs on the blocking authentication thread, never the UI thread.
  if app.run_on_main_thread(move||{
   let start=Url::parse(&entry).unwrap();let initial=start.clone();
   #[cfg(feature="verify-ui")]let is_fixture=initial.as_str()=="about:blank";
   let builder=WebviewWindowBuilder::new(&handle,&window_label,WebviewUrl::External(start))
    .visible(false).incognito(true).initialization_script("window.addEventListener('DOMContentLoaded',()=>{document.querySelectorAll('form').forEach(f=>f.addEventListener('submit',e=>e.preventDefault()));});");
   #[cfg(feature="verify-ui")]let builder=if initial.as_str()=="about:blank"{builder.initialization_script("if(location.href==='about:blank'){const base='http://aaa.cqust.edu.cn/eportal/';const query=['wlanuserip=synthetic-test','mac=demo'].join('&');location.replace(base+'index.jsp?'+query);}")}else{builder};
   let result=builder
    .on_new_window(|_,_|tauri::webview::NewWindowResponse::Deny)
    .on_navigation(move|url|{let fixture=cfg!(feature="verify-ui")&&initial.as_str()=="about:blank"&&url==&initial;
     #[cfg(feature="verify-ui")]diagnostic(format!("navigation scheme={} host={} allowed={} fixture={} candidate={}",url.scheme(),url.host_str().unwrap_or(""),allowed(url),fixture,candidate(url)));
     if (!allowed(url)&&!fixture)||count.fetch_add(1,Ordering::Relaxed)>15{let _=nav_send.send(None);return false}if candidate(url){let _=nav_send.send(Some(url.clone()));return false}true})
    .build();
   match result{Ok(window)=>{#[cfg(feature="verify-ui")]if is_fixture{let _=window.eval("const base='http://aaa.cqust.edu.cn/eportal/';const query=['wlanuserip=synthetic-test','mac=demo'].join('&');location.replace(base+'index.jsp?'+query);");}let _=window;},Err(error)=>{#[cfg(feature="verify-ui")]diagnostic(format!("build error: {error}"));let _=error;let _=send.send(None);}}
  }).is_err(){return None}
  let found=recv.recv_timeout(Duration::from_secs(12)).ok().flatten();
  let cleanup=app.clone();let _=app.run_on_main_thread(move||{if let Some(window)=cleanup.get_webview_window(&label){let _=window.destroy();}});
  if found.is_some(){return found}
 }None
}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn accepts_only_complete_school_destinations(){for url in ["http://aaa.cqust.edu.cn/eportal/index.jsp?wlanuserip=test&mac=aa","http://172.28.228.2:8080/eportal/success.jsp?userIndex=demo"]{assert!(candidate(&Url::parse(url).unwrap()))}for url in ["http://evil.test/eportal/index.jsp?wlanuserip=test","http://aaa.cqust.edu.cn/","http://aaa.cqust.edu.cn/eportal/index.jsp?wlanuserip=","http://aaa.cqust.edu.cn/eportal/success.jsp?userIndex=%3Cscript%3E"]{assert!(!candidate(&Url::parse(url).unwrap()))}}
}
