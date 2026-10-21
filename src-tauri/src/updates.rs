use serde::Serialize;
use serde_json::Value;
use std::{io::Read,sync::Mutex,time::Duration};
const REPO:&str="https://github.com/Hercules-Zhaoziyi/CQUST-WiFi";
#[derive(Clone,Serialize)]pub struct Update{pub current:String,pub latest:String,pub available:bool,pub notes:String,pub url:String,pub download:Option<String>}
#[derive(Default)]pub struct Updates(pub Mutex<Option<Update>>);
pub fn check()->Result<Update,String>{
 let client=reqwest::blocking::Client::builder().connect_timeout(Duration::from_secs(6)).timeout(Duration::from_secs(18)).redirect(reqwest::redirect::Policy::none()).build().map_err(|_|"无法初始化更新检测")?;
 let response=client.get("https://api.github.com/repos/Hercules-Zhaoziyi/CQUST-WiFi/releases/latest").header("User-Agent","CQUST-WiFi/5.1.0").header("Accept","application/vnd.github+json").send().map_err(|_|"连接 GitHub 失败，请检查网络后重试。")?;
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
 let download=v["assets"].as_array().and_then(|list|list.iter().find_map(|a|{let name=a["name"].as_str()?;let link=a["browser_download_url"].as_str()?;let size=a["size"].as_u64()?;if name.ends_with(".exe")&&name.contains("Tauri")&&size>0&&link==format!("{prefix}{name}"){Some(link.to_string())}else{None}}));
 Ok(Update{current:current.into(),latest:latest.to_string(),available:latest>current_version,notes:v["body"].as_str().unwrap_or("暂无更新说明").chars().take(16000).collect(),url,download})
}
#[cfg(test)]mod tests{use super::*;use serde_json::json;
#[test]#[ignore="explicit read-only GitHub network test"]fn github_latest_readonly(){let update=check().expect("GitHub release request");assert!(!update.latest.is_empty());}
#[test]fn semver_and_trusted_download(){let mut v=json!({"tag_name":"v5.10.0","html_url":format!("{REPO}/releases/tag/v5.10.0"),"body":"<script>not executable</script>","assets":[{"name":"CQUST-Tauri-Setup.exe","size":10,"browser_download_url":format!("{REPO}/releases/download/v5.10.0/CQUST-Tauri-Setup.exe")}]});let u=parse(&v,"5.9.0").unwrap();assert!(u.available);assert!(u.download.is_some());assert!(!parse(&v,"5.10.0").unwrap().available);v["assets"][0]["browser_download_url"]=json!("https://evil.test/setup.exe");assert!(parse(&v,"5.9.0").unwrap().download.is_none());v["html_url"]=json!("https://evil.test");assert!(parse(&v,"5.9.0").is_err());}#[test]fn reject_preview_and_draft(){let mut v=json!({"tag_name":"v6.0.0-beta.1","html_url":format!("{REPO}/releases/tag/v6.0.0-beta.1")});assert!(parse(&v,"5.1.0").is_err());v["tag_name"]=json!("v6.0.0");v["draft"]=json!(true);assert!(parse(&v,"5.1.0").is_err());}}
