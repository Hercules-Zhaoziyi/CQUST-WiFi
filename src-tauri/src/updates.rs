use serde::Serialize;
use serde_json::Value;
use std::{io::Read,sync::Mutex,time::Duration};
const REPO:&str="https://github.com/Hercules-Zhaoziyi/CQUST-WiFi";
#[derive(Clone,Serialize)]pub struct Update{pub current:String,pub latest:String,pub available:bool,pub notes:String,pub url:String,pub download:Option<String>,pub digest:Option<String>,pub size:u64}
#[derive(Default)]pub struct Updates(pub Mutex<Option<Update>>,pub Mutex<Option<(Update,std::path::PathBuf)>>,pub std::sync::atomic::AtomicBool);
pub fn check()->Result<Update,String>{
 let client=reqwest::blocking::Client::builder().connect_timeout(Duration::from_secs(6)).timeout(Duration::from_secs(18)).redirect(reqwest::redirect::Policy::none()).build().map_err(|_|"无法初始化更新检测")?;
 let response=client.get("https://api.github.com/repos/Hercules-Zhaoziyi/CQUST-WiFi/releases/latest").header("User-Agent",format!("CQUST-WiFi/{}",env!("CARGO_PKG_VERSION"))).header("Accept","application/vnd.github+json").send().map_err(|_|"连接 GitHub 失败，请检查网络后重试。")?;
 if response.status().as_u16()==403||response.status().as_u16()==429{return Err("GitHub 请求次数受限，请稍后重试。".into())}if !response.status().is_success(){return Err("暂时无法获取 GitHub 最新版本。".into())}
 let mut bytes=Vec::new();response.take(2_000_001).read_to_end(&mut bytes).map_err(|_|"读取更新信息失败")?;if bytes.len()>2_000_000{return Err("更新信息异常，请前往 GitHub 查看。".into())}
 let value:Value=serde_json::from_slice(&bytes).map_err(|_|"更新信息格式无效")?;parse(&value,env!("CARGO_PKG_VERSION"))
}
pub fn parse(v:&Value,current:&str)->Result<Update,String>{
 let tag=v["tag_name"].as_str().ok_or("更新信息缺少版本号")?;let latest=semver::Version::parse(tag.trim_start_matches('v')).map_err(|_|"更新版本号格式无效")?;
 let current_version=semver::Version::parse(current).map_err(|_|"本机版本号无效")?;
 if v["draft"]==true||v["prerelease"]==true||!latest.pre.is_empty(){return Err("暂无可用的正式版更新。".into())}
 let url=format!("{REPO}/releases/tag/{tag}");if v["html_url"].as_str()!=Some(&url){return Err("更新来源不符合预期，已停止。".into())}
 let prefix=format!("{REPO}/releases/download/{tag}/");
 let asset=v["assets"].as_array().and_then(|list|list.iter().find(|a|{let name=a["name"].as_str().unwrap_or("");let link=a["browser_download_url"].as_str().unwrap_or("");let size=a["size"].as_u64().unwrap_or(0);let supported=if cfg!(target_os="macos"){name.starts_with("CQUST")&&name.ends_with(".dmg")}else{name.ends_with(".exe")&&name.contains("Tauri")};supported&&size>0&&size<=200_000_000&&link==format!("{prefix}{name}")}));
 let download=asset.and_then(|a|a["browser_download_url"].as_str()).map(str::to_owned);
 let digest=asset.and_then(|a|a["digest"].as_str()).filter(|s|valid_digest(s)).map(str::to_owned);
 Ok(Update{current:current.into(),latest:latest.to_string(),available:latest>current_version,notes:v["body"].as_str().unwrap_or("暂无更新说明").chars().take(16000).collect(),url,download,digest,size:asset.and_then(|a|a["size"].as_u64()).unwrap_or(0)})
}
fn valid_digest(s:&str)->bool{s.strip_prefix("sha256:").map(|s|s.len()==64&&s.bytes().all(|b|b.is_ascii_hexdigit())).unwrap_or(false)}
fn trusted_asset_url(url:&url::Url)->bool{url.scheme()=="https"&&url.username().is_empty()&&url.password().is_none()&&url.port_or_known_default()==Some(443)&&matches!(url.host_str(),Some("github.com"|"release-assets.githubusercontent.com"|"objects.githubusercontent.com"))}
pub fn download(app:&tauri::AppHandle,u:&Update)->Result<std::path::PathBuf,String>{
 use std::io::Write;use tauri::{Manager,Emitter};
 if !u.available||u.size==0||u.size>200_000_000||u.digest.as_ref().filter(|d|valid_digest(d)).is_none(){return Err("发布包缺少有效校验信息，请在 GitHub 发布页查看。".into())}
 semver::Version::parse(&u.latest).map_err(|_|"版本无效")?;
 let link=u.download.as_ref().ok_or("暂无适用于本系统的安装包")?;
 if !link.starts_with(&format!("{REPO}/releases/download/v{}/",u.latest)){return Err("下载来源无效".into())}
 let client=reqwest::blocking::Client::builder().connect_timeout(Duration::from_secs(10)).timeout(Duration::from_secs(600)).redirect(reqwest::redirect::Policy::custom(|attempt|{if attempt.previous().len()<5&&trusted_asset_url(attempt.url()){attempt.follow()}else{attempt.stop()}})).build().map_err(|_|"无法初始化下载")?;
 let mut response=client.get(link).header("User-Agent","CQUST-WiFi").send().map_err(|_|"下载连接失败")?;
 if !response.status().is_success(){return Err("下载未成功，请稍后重试。".into())}
 let folder=app.path().app_cache_dir().map_err(|_|"缓存目录不可用")?.join("updates");std::fs::create_dir_all(&folder).map_err(|_|"无法创建更新目录")?;
 let ext=if cfg!(target_os="macos"){"dmg"}else{"exe"};let path=folder.join(format!("CQUST-WiFi-{}.{ext}",u.latest));let partial=path.with_extension("part");
 let result=(||{let mut file=std::fs::File::create(&partial).map_err(|_|"无法写入安装包")?;let mut total=0u64;let mut buffer=[0u8;65536];let mut last=0;
 loop{let size=response.read(&mut buffer).map_err(|_|"下载中断，请重试。")?;if size==0{break}total+=size as u64;if total>u.size{return Err("安装包长度异常，已停止下载。".into())}file.write_all(&buffer[..size]).map_err(|_|"写入安装包失败")?;let percent=total*100/u.size;if percent!=last{last=percent;let _=app.emit("cqust-update-progress",percent);}}
 file.sync_all().map_err(|_|"写入安装包失败")?;drop(file);verify_file(&partial,u)?;std::fs::rename(&partial,&path).map_err(|_|"无法保存安装包")?;Ok(path.clone())})();
 if result.is_err(){let _=std::fs::remove_file(partial);}result
}
fn verify_file(path:&std::path::Path,u:&Update)->Result<(),String>{
 use sha2::{Digest,Sha256};let mut file=std::fs::File::open(path).map_err(|_|"安装包不存在")?;if file.metadata().map_err(|_|"无法读取安装包")?.len()!=u.size{return Err("安装包长度不符，已阻止安装。".into())}
 let mut hash=Sha256::new();let mut buffer=[0u8;65536];loop{let n=file.read(&mut buffer).map_err(|_|"无法读取安装包")?;if n==0{break}hash.update(&buffer[..n]);}
 let actual=format!("sha256:{:x}",hash.finalize());if !u.digest.as_ref().map(|d|d.eq_ignore_ascii_case(&actual)).unwrap_or(false){return Err("安装包校验失败，已阻止安装。".into())}Ok(())
}
pub fn install(u:&Update,path:&std::path::Path)->Result<(),String>{verify_file(path,u)?;
 #[cfg(windows)]{std::process::Command::new(path).spawn().map(|_|()).map_err(|_|"无法启动安装程序。".into())}
 #[cfg(target_os="macos")]{std::process::Command::new("/usr/bin/open").arg(path).spawn().map(|_|()).map_err(|_|"无法打开安装包。".into())}
}
#[cfg(test)]mod tests{use super::*;use serde_json::json;
#[test]fn installer_hash_and_redirect_policy(){
 assert!(valid_digest(&format!("sha256:{}","a".repeat(64))));assert!(!valid_digest("sha256:short"));
 for address in ["http://github.com/setup","https://github.com.evil.test/setup","https://user@github.com/setup","https://github.com:444/setup"]{assert!(!trusted_asset_url(&url::Url::parse(address).unwrap()));}
 assert!(trusted_asset_url(&url::Url::parse("https://release-assets.githubusercontent.com/package").unwrap()));
 let path=std::env::temp_dir().join(format!("cqust-update-hash-{}-{}.tmp",std::process::id(),std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));std::fs::write(&path,b"abc").unwrap();
 let u=Update{current:"5.2.1".into(),latest:"5.3.0".into(),available:true,notes:String::new(),url:String::new(),download:None,size:3,digest:Some("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".into())};assert!(verify_file(&path,&u).is_ok());std::fs::write(&path,b"abd").unwrap();assert!(verify_file(&path,&u).is_err());std::fs::remove_file(path).unwrap();
}
#[test]#[ignore="explicit read-only GitHub network test"]fn github_latest_readonly(){let update=check().expect("GitHub release request");assert!(!update.latest.is_empty());}
#[test]fn semver_and_trusted_download(){let name=if cfg!(target_os="macos"){"CQUST-Wi-Fi_5.10.0_aarch64.dmg"}else{"CQUST-Tauri-Setup.exe"};let mut v=json!({"tag_name":"v5.10.0","html_url":format!("{REPO}/releases/tag/v5.10.0"),"body":"<script>not executable</script>","assets":[{"name":name,"size":10,"browser_download_url":format!("{REPO}/releases/download/v5.10.0/{name}")}]});let u=parse(&v,"5.9.0").unwrap();assert!(u.available);assert!(u.download.is_some());assert!(!parse(&v,"5.10.0").unwrap().available);v["assets"][0]["browser_download_url"]=json!("https://evil.test/setup.exe");assert!(parse(&v,"5.9.0").unwrap().download.is_none());v["html_url"]=json!("https://evil.test");assert!(parse(&v,"5.9.0").is_err());}#[test]fn reject_preview_and_draft(){let mut v=json!({"tag_name":"v6.0.0-beta.1","html_url":format!("{REPO}/releases/tag/v6.0.0-beta.1")});assert!(parse(&v,"5.1.0").is_err());v["tag_name"]=json!("v6.0.0");v["draft"]=json!(true);assert!(parse(&v,"5.1.0").is_err());}}
