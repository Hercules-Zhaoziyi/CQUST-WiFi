use base64::Engine;
use num_bigint::BigUint;
use regex::Regex;
use reqwest::blocking::{Client,Response};
use scraper::{Html,Selector};
use serde::{Serialize,Deserialize};
use serde_json::Value;
#[cfg(test)]use serde_json::json;
use std::{collections::HashSet,time::{Duration,Instant}};
use url::Url;
use std::io::Read;
fn captcha_url(page:&Url,path:&str)->Option<Url>{let path=path.trim();if path.is_empty(){return None}page.join(path).ok().filter(trusted)}
fn image_mime(bytes:&[u8])->Option<&'static str>{if bytes.starts_with(b"\x89PNG\r\n\x1a\n"){Some("image/png")}else if bytes.starts_with(&[255,216,255]){Some("image/jpeg")}else if bytes.starts_with(b"GIF87a")||bytes.starts_with(b"GIF89a"){Some("image/gif")}else if bytes.starts_with(b"BM"){Some("image/bmp")}else if bytes.len()>12&&bytes.starts_with(b"RIFF")&&&bytes[8..12]==b"WEBP"{Some("image/webp")}else{None}}

const ENTRIES:[&str;4]=["http://aaa.cqust.edu.cn/","http://www.msftconnecttest.com/redirect","http://172.28.228.2/","http://123.123.123.123/"];
#[derive(Clone,Debug,Serialize,Deserialize)]
pub struct Error{pub kind:String,pub message:String,#[serde(skip_serializing_if="Vec::is_empty",default)]pub services:Vec<String>,#[serde(skip_serializing_if="Option::is_none",default)]pub image:Option<String>,#[serde(skip)]pub retryable:bool}
impl Error{pub fn new(kind:&str,message:&str)->Self{Self{kind:kind.into(),message:message.into(),services:vec![],image:None,retryable:kind=="network"}}}
type Result<T>=std::result::Result<T,Error>;
fn rx(pattern:&str)->Regex{Regex::new(pattern).expect("static regular expression")}
fn text(response:Response)->Result<String>{use std::io::Read;let mut bytes=response.bytes().map_err(|_|network())?.to_vec();if bytes.starts_with(&[0x1f,0x8b]){let mut output=Vec::new();flate2::read::GzDecoder::new(bytes.as_slice()).read_to_end(&mut output).map_err(|_|network())?;bytes=output;}if let Ok(s)=std::str::from_utf8(&bytes){Ok(s.trim_start_matches('\u{feff}').to_string())}else{Ok(encoding_rs::GBK.decode(&bytes).0.into_owned())}}
fn network()->Error{Error::new("network","校园认证服务器暂时不可达，请检查校园网后重试。")}
fn capture(pattern:&str,s:&str)->Option<String>{rx(pattern).captures(s).and_then(|c|c.get(1)).map(|m|m.as_str().replace("&amp;","&"))}
pub fn trusted(url:&Url)->bool{matches!(url.scheme(),"http"|"https")&&matches!(url.host_str(),Some("aaa.cqust.edu.cn"|"172.28.228.2"))&&matches!(url.port_or_known_default(),Some(80|443|8080))&&url.username().is_empty()&&url.password().is_none()}
pub fn component(value:&str)->String{let mut s=String::new();for b in value.bytes(){if b.is_ascii_alphanumeric()||b"-_.!~*'()".contains(&b){s.push(b as char)}else{s.push_str(&format!("%{b:02X}"))}}s}
pub fn twice(value:&str)->String{component(&component(value))}
pub fn mac(query:&str)->String{capture(r"(?i)(?:^|&)mac=([^&]+)",query).unwrap_or_else(||"111111111".into())}
pub fn encrypt(password:&str,mac:&str,exponent:&str,modulus:&str)->Result<String>{
 let mut bytes=format!("{password}>{}",if mac.is_empty(){"111111111"}else{mac}).into_bytes();
 if !bytes.is_ascii(){return Err(Error::new("error","当前校园算法仅验证 ASCII 密码，含中文等字符请使用官方页面。"))}
 bytes.reverse();
 let n=BigUint::parse_bytes(modulus.as_bytes(),16).ok_or_else(||Error::new("context","当前页面公钥无效。"))?;
 let e=BigUint::parse_bytes(exponent.as_bytes(),16).ok_or_else(||Error::new("context","当前页面公钥无效。"))?;
 if n.bits()<17||n.bits()>8192||e.bits()==0{return Err(Error::new("context","当前页面公钥无效。"))}
 let chunk=2*((n.bits()-1)/16) as usize;while bytes.len()%chunk!=0{bytes.push(0)}
 Ok(bytes.chunks(chunk).map(|block|{let s=BigUint::from_bytes_le(block).modpow(&e,&n).to_str_radix(16);format!("{:0>width$}",s,width=s.len().div_ceil(4)*4)}).collect::<Vec<_>>().join(" "))
}
#[derive(Clone)]
pub struct Context{pub client:Client,pub page:Url,pub api:Url,pub query:String,pub mac:String,pub encrypt:bool,pub exponent:String,pub modulus:String,pub service:String,pub prefix:String,pub captcha:Option<Url>}
pub struct Session{pub context:Context,pub index:String,pub username:String,pub display_name:String,pub ip:String,pub keepalive:f64,pub next_keepalive:Instant}
#[derive(Clone)]pub struct Online{pub page:Url,pub index:String}
#[derive(Default)]pub struct Auth{pub challenge:Option<Context>,pub online:Option<Online>}
pub fn client()->Result<Client>{Client::builder().cookie_store(true).no_proxy().redirect(reqwest::redirect::Policy::none()).connect_timeout(Duration::from_secs(5)).timeout(Duration::from_secs(15)).user_agent("Mozilla/5.0").build().map_err(|_|network())}
fn json_response(response:Response)->Result<Value>{if response.status().is_redirection(){return Err(Error::new("error","认证接口异常跳转，已停止发送凭据。"))}if !response.status().is_success(){return Err(network())}serde_json::from_str(&text(response)?).map_err(|_|Error::new("error","认证服务器返回格式异常，未确认结果。"))}
fn post(client:&Client,url:&Url,body:String,page:&Url)->Result<Value>{
 if !trusted(url){return Err(Error::new("error","认证服务器地址不受信任。"))}
 let origin=page.origin().ascii_serialization();
 json_response(client.post(url.clone()).header("Content-Type","application/x-www-form-urlencoded; charset=UTF-8").header("Referer",page.as_str()).header("Origin",origin).body(body).send().map_err(|_|network())?)
}
fn endpoint(api:&Url,method:&str)->Url{let mut u=api.clone();u.set_query(Some(&format!("method={method}")));u}
fn field(value:&Value,key:&str)->String{match &value[key]{Value::String(s)=>s.clone(),Value::Bool(b)=>b.to_string(),Value::Number(n)=>n.to_string(),_=>String::new()}}
fn page_fields(html:&str)->(std::collections::HashMap<String,String>,Vec<String>,String){
 let document=Html::parse_document(html);let mut values=std::collections::HashMap::new();
 for e in document.select(&Selector::parse("input").unwrap()){if let Some(id)=e.value().attr("id").or_else(||e.value().attr("name")){values.insert(id.to_string(),e.value().attr("value").unwrap_or("").to_string());}}
 let services=document.select(&Selector::parse("select#net_access_type option, select#service option, select[name=service] option").unwrap()).filter_map(|e|e.value().attr("value")).filter(|s|!s.is_empty()).map(str::to_string).collect();
 let refresh=document.select(&Selector::parse("meta[http-equiv]").unwrap()).find_map(|e|{if e.value().attr("http-equiv")?.eq_ignore_ascii_case("refresh"){capture(r"(?i)url\s*=\s*(.+)",e.value().attr("content")?)}else{None}}).unwrap_or_default().trim_matches(['\'','"']).to_string();
 (values,services,refresh)
}
impl Auth{
 fn follow(&mut self,client:&Client,entry:&str)->Result<Option<(Url,String)>>{
  let mut url=Url::parse(entry).map_err(|_|Error::new("context","请输入完整校园认证网址。"))?;let mut seen=HashSet::new();
  for _ in 0..7{
   if !seen.insert(url.to_string()){break}
   if !ENTRIES.contains(&url.as_str())&&!trusted(&url){break}
   let response=client.get(url.clone()).header("Cache-Control","no-cache").timeout(Duration::from_secs(5)).send().map_err(|_|network())?;
   if response.status().is_redirection(){if let Some(target)=response.headers().get("Location").and_then(|h|h.to_str().ok()){url=url.join(target).map_err(|_|network())?;continue}else{break}}
   if !response.status().is_success(){return Err(network())}let html=text(response)?;
   if trusted(&url)&&url.path().to_lowercase().ends_with("/success.jsp"){
    if let Some((_,index))=url.query_pairs().find(|(k,_)|k=="userIndex"){if valid_index(&index){self.online=Some(Online{page:url.clone(),index:index.into_owned()});return Err(Error::new("online","当前设备已有在线会话。确认下线后将自动继续登录。"))}}
   }
   let query=url.query().unwrap_or("");
   if trusted(&url)&&rx(r"(?i)(?:^|&)wlanuserip=").is_match(query)&&(html.contains("passwordEncrypt")||html.contains("AuthInterFace")){return Ok(Some((url,html)))}
   let (_,_,refresh)=page_fields(&html);
   let target=if !refresh.is_empty(){Some(refresh)}else{capture(r#"(?i)(?:(?:window|top|self|document)\.)?location(?:\.href)?\s*=\s*["']([^"']+)"#,&html).or_else(||capture(r#"(?i)(?:window\.)?location\.(?:replace|assign)\(\s*["']([^"']+)"#,&html))};
   if let Some(target)=target{url=url.join(&target).map_err(|_|network())?}else{break}
  }Ok(None)
 }
 pub fn discover(&mut self,manual:&str,selected:&str)->Result<Context>{
  let client=client()?;if !manual.is_empty()&&!Url::parse(manual).map(|u|trusted(&u)).unwrap_or(false){return Err(Error::new("context","仅接受学校域名或认证 IP 的网址。"))}
  let entries=if manual.is_empty(){ENTRIES.to_vec()}else{vec![manual]};let mut found=None;
  for entry in entries{match self.follow(&client,entry){Ok(Some(data))=>{found=Some(data);break},Err(error)if error.kind=="online"=>return Err(error),_=>()}}
  let (page,html)=found.ok_or_else(||Error::new("context","未取得当前认证登录页。请确认连接校园网，或粘贴浏览器当前完整认证网址。"))?;
  let query=page.query().unwrap_or("").to_string();let (mut values,mut services,_)=page_fields(&html);
  let init=capture(r#"AuthInterFace\.init\(\s*["']([^"']*)["']"#,&html).unwrap_or_else(||"./".into());
  let api=page.join(&format!("{init}InterFace.do")).map_err(|_|network())?;if !trusted(&api){return Err(Error::new("context","当前页面的认证接口无效。"))}
  let info=post(&client,&endpoint(&api,"pageInfo"),format!("queryString={}",component(&query)),&page)?;
  for key in ["passwordEncrypt","publicKeyExponent","publicKeyModulus"]{if info.get(key).is_some(){values.insert(key.to_string(),field(&info,key));}}
  let encrypt=match values.get("passwordEncrypt").map(|s|s.to_lowercase()).as_deref(){Some("true")=>true,Some("false")=>false,_=>return Err(Error::new("context","当前页面没有给出密码处理方式。"))};
  let exponent=values.get("publicKeyExponent").cloned().unwrap_or_default();let modulus=values.get("publicKeyModulus").cloned().unwrap_or_default();
  if encrypt&&(!rx("^[0-9a-fA-F]+$").is_match(&exponent)||!rx("^[0-9a-fA-F]+$").is_match(&modulus)){return Err(Error::new("context","当前页面没有有效公钥。"))}
  if services.is_empty(){
   let mut u=endpoint(&api,"getServices");u.set_query(Some(&format!("method=getServices&queryString={}",component(&query))));let data=post(&client,&u,String::new(),&page)?;
   let items=if data.is_array(){data}else{data.get("services").or_else(||data.get("serviceList")).cloned().unwrap_or(Value::Null)};
   let items=if let Some(s)=items.as_str(){serde_json::from_str(s).unwrap_or(Value::Null)}else{items};
   if let Some(list)=items.as_array(){services=list.iter().filter_map(|v|v["serviceName"].as_str().map(str::to_string)).collect()}
  }
  if services.is_empty(){if let Some(s)=values.get("net_access_type"){if !s.is_empty(){services.push(s.clone())}}}
  let mut seen=HashSet::new();services.retain(|s|!s.is_empty()&&seen.insert(s.clone()));
  let service=if services.iter().any(|s|s==selected){selected.to_string()}else if services.len()==1{services[0].clone()}else if services.is_empty(){return Err(Error::new("context","未取得认证服务列表，请重新探测。"))}else{let mut error=Error::new("services","请选择当前页面提供的校园网服务。");error.services=services;return Err(error)};
  for key in ["isOperatorPwd","isOperatorUserId"]{if field(&info,key)=="true"{return Err(Error::new("context","当前服务需要运营商账号，请选择普通校园网服务。"))}}
  let prefix=if rx(r#"(?i)<[^>]+id=["']Tj_yes["'][^>]+style=["'][^"']*display\s*:\s*block"#).is_match(&html){capture(r#"prefixValue\s*=\s*["']([^"']*)"#,&html).unwrap_or_default()}else{String::new()};
  let captcha=info["validCodeUrl"].as_str().and_then(|s|captcha_url(&page,s));
  Ok(Context{client,page,api,mac:mac(&query),query,encrypt,exponent,modulus,service,prefix,captcha})
 }
 fn challenge_error(&self,c:&Context)->Error{
  let mut error=Error::new("captcha","请输入图片中的验证码。可点击刷新更换图片。");
  let image=(||->Option<String>{
   let mut url=c.captcha.clone()?;
   let nonce=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).ok()?.as_nanos().to_string();
   url.query_pairs_mut().append_pair("_captcha", &nonce);
   for _ in 0..5{
    if !trusted(&url){return None}
    let response=c.client.get(url.clone()).header("Referer",c.page.as_str()).header("Accept","image/png,image/jpeg,image/gif,image/webp,image/bmp;q=0.9,*/*;q=0.1").header("Cache-Control","no-cache").send().ok()?;
    if response.status().is_redirection(){url=url.join(response.headers().get("Location")?.to_str().ok()?).ok()?;continue}
    if !response.status().is_success(){return None}
    let mut bytes=Vec::new();response.take(1_000_001).read_to_end(&mut bytes).ok()?;
    if bytes.len()>1_000_000{return None}
    let mime=image_mime(&bytes)?;
    return Some(format!("data:{mime};base64,{}",base64::engine::general_purpose::STANDARD.encode(bytes)))
   }None
  })();
  error.image=image;
  if error.image.is_none(){error.message="验证码图片加载失败，请点击刷新重试；请保持连接校园网。".into()}
  error
 }
 pub fn refresh_captcha(&self)->Result<Value>{
  let c=self.challenge.as_ref().ok_or_else(||Error::new("context","认证会话已失效，请重新登录获取验证码。"))?;
  Ok(serde_json::to_value(self.challenge_error(c)).unwrap())
 }
 pub fn login(&mut self,user:&str,password:&str,args:&Value,service:&str)->Result<Session>{
  let c=if args["challenge"]==true{self.challenge.take().map(Ok).unwrap_or_else(||self.discover(args["url"].as_str().unwrap_or(""),service))?}else{self.challenge=None;self.discover(args["url"].as_str().unwrap_or(""),service)?};
  let captcha=args["captcha"].as_str().unwrap_or("").trim();
  if c.captcha.is_some()&&captcha.is_empty(){let error=self.challenge_error(&c);self.challenge=Some(c);return Err(error)}
  let body=login_body(user,password,&c,captcha)?;
  let result=post(&c.client,&endpoint(&c.api,"login"),body,&c.page)?;
  if result["result"]!="success"{
   if let Some(url)=result["validCodeUrl"].as_str().and_then(|path|captcha_url(&c.page,path)){let mut c=c;c.captcha=Some(url);let error=self.challenge_error(&c);self.challenge=Some(c);return Err(error)}
   let msg=field(&result,"message").to_lowercase();
   return Err(Error::new("error",if ["password","密码","用户不存在","账号不存在"].iter().any(|s|msg.contains(s)){"服务器拒绝账号或密码，请核对输入。"}else if ["在线","online"].iter().any(|s|msg.contains(s)){"服务器提示当前账号或设备已在线，请先下线。"}else{"校园认证未通过，请核对账号权限和服务。"}))
  }
  let index=field(&result,"userIndex");if !valid_index(&index){return Err(Error::new("error","认证服务器未返回有效会话。"))}
  let username=format!("{}{}",c.prefix,user.trim());
  let mut session=Session{context:c,index,username,display_name:String::new(),ip:lan_ip(),keepalive:0.,next_keepalive:Instant::now()};session.update_keepalive(&result);
  let (online,_)=session.status()?;if !online{return Err(Error::new("error","登录响应成功，但尚未确认在线状态。"))}Ok(session)
 }
 pub fn logout_discovered(&mut self)->Result<()>{
  let online=self.online.take().ok_or_else(||Error::new("error","请重新探测当前会话。"))?;
  let client=client()?;let api=online.page.join("./InterFace.do").map_err(|_|network())?;
  let value=post(&client,&endpoint(&api,"getOnlineUserInfo"),format!("userIndex={}",online.index),&online.page)?;
  if matches!(value["result"].as_str(),Some("success"|"wait")){let result=post(&client,&endpoint(&api,"logout"),format!("userIndex={}",online.index),&online.page)?;if result["result"]!="success"{return Err(Error::new("error","服务器未确认下线，请稍后重试。"))}}Ok(())
 }
}
pub fn login_body(user:&str,password:&str,c:&Context,captcha:&str)->Result<String>{
 if user.trim().is_empty()||password.is_empty(){return Err(Error::new("credentials","请输入账号和密码。"))}
 if !captcha.is_empty()&&!rx("^[A-Za-z0-9]{1,16}$").is_match(captcha){return Err(Error::new("error","验证码应为字母或数字。"))}
 let value=if c.encrypt{encrypt(password,&c.mac,&c.exponent,&c.modulus)?}else{password.to_string()};
 let fields=[("userId",format!("{}{}",c.prefix,user.trim())),("password",value),("service",c.service.clone()),("queryString",c.query.clone()),("operatorPwd",String::new()),("operatorUserId",String::new())];
 Ok(format!("{}&validcode={captcha}&passwordEncrypt={}",fields.into_iter().map(|(k,v)|format!("{k}={}",twice(&v))).collect::<Vec<_>>().join("&"),c.encrypt))
}
pub fn valid_index(index:&str)->bool{rx("^[A-Za-z0-9_.:-]{1,1024}$").is_match(index)}
pub fn lan_ip()->String{std::net::UdpSocket::bind("0.0.0.0:0").and_then(|s|{s.connect("172.28.228.2:80")?;Ok(s.local_addr()?.ip().to_string())}).unwrap_or_default()}
pub fn display_name(data:&Value)->String{for key in ["userName","realName","displayName","name"]{if let Some(s)=data[key].as_str(){let s=s.trim();if !s.is_empty()&&s.len()<256{return s.to_string()}}}String::new()}
impl Session{
 fn call(&self,method:&str)->Result<Value>{if !valid_index(&self.index){return Err(Error::new("error","会话无效，请重新登录。"))}post(&self.context.client,&endpoint(&self.context.api,method),format!("userIndex={}",self.index),&self.context.page)}
 fn update_keepalive(&mut self,v:&Value){if let Some(value)=v.get("keepaliveInterval"){let minutes=value.as_f64().or_else(||value.as_str().and_then(|s|s.parse::<f64>().ok())).unwrap_or(0.);self.keepalive=if minutes>0.&&minutes<=1440.{minutes*60.}else{0.};self.next_keepalive=Instant::now()+Duration::from_secs_f64(self.keepalive);}}
 pub fn status(&mut self)->Result<(bool,bool)>{let data=self.call("getOnlineUserInfo")?;match data["result"].as_str(){Some("success"|"wait")=>{
  if let Some(user)=data["userId"].as_str(){if user!=self.username{return Ok((false,false))}}
  let name=display_name(&data);if !name.is_empty(){self.display_name=name}
  if let Some(ip)=data["userIp"].as_str(){if ip.parse::<std::net::IpAddr>().is_ok(){self.ip=ip.to_string()}}
  if self.keepalive==0.{self.update_keepalive(&data)}Ok((true,false))},Some("fail"|"failure"|"offline")=>{let msg=field(&data,"message").to_lowercase();Ok((false,["其他设备","其它设备","顶替","挤下","another device","replaced"].iter().any(|s|msg.contains(s))))},_=>Err(network())}}
 pub fn keep_alive(&mut self)->Result<()>{if self.keepalive==0.||Instant::now()<self.next_keepalive{return Ok(())}let result=self.call("keepalive");self.next_keepalive=Instant::now()+Duration::from_secs_f64(self.keepalive.max(10.));let data=result?;if matches!(data["result"].as_str(),Some("success"|"wait")){Ok(())}else{Err(network())}}
 pub fn logout(&self)->Result<()>{if self.call("logout")?["result"]=="success"{Ok(())}else{Err(Error::new("error","服务器未确认下线，已保留当前连接状态。"))}}
}

#[cfg(test)]mod tests{
 use super::*;
 #[test]fn captcha_address_and_image_detection(){let page=Url::parse("http://aaa.cqust.edu.cn/eportal/index.jsp?wlanuserip=demo").unwrap();assert!(captcha_url(&page,"").is_none());assert!(captcha_url(&page,"  ").is_none());assert!(captcha_url(&page,"https://example.com/image").is_none());assert_eq!(captcha_url(&page,"./validcode").unwrap().path(),"/eportal/validcode");assert_eq!(image_mime(b"\x89PNG\r\n\x1a\nimage"),Some("image/png"));assert_eq!(image_mime(&[255,216,255,224]),Some("image/jpeg"));assert_eq!(image_mime(b"<html>login</html>"),None);}

 #[test]fn js_encoding(){assert_eq!(twice("互联网"),"%25E4%25BA%2592%25E8%2581%2594%25E7%25BD%2591");assert_eq!(mac("x=1&MAC=aa%3Abb"),"aa%3Abb");assert_eq!(mac("x=1"),"111111111");}
 #[test]fn restrict_server(){assert!(trusted(&Url::parse("http://aaa.cqust.edu.cn/eportal/").unwrap()));for u in ["http://evil.test/","http://aaa.cqust.edu.cn@evil.test/","http://aaa.cqust.edu.cn:9999/"]{assert!(!trusted(&Url::parse(u).unwrap()))}}
 #[test]fn parse_fields(){let (v,s,_)=page_fields("<input id='passwordEncrypt' value='true'><select id='service'><option value='互联网'>上网</option></select>");assert_eq!(v["passwordEncrypt"],"true");assert_eq!(s,vec!["互联网"]);}
 #[test]fn names_are_real_fields(){assert_eq!(display_name(&json!({"userName":"测试同学","userId":"demo"})),"测试同学");assert_eq!(display_name(&json!({"userId":"demo"})),"");}
 #[test]fn original_school_js_rsa_parity(){let data:Value=serde_json::from_str(include_str!("rsa_vectors.json")).unwrap();for item in data["cases"].as_array().unwrap(){assert_eq!(encrypt(item["password"].as_str().unwrap(),item["mac"].as_str().unwrap(),data["exponent"].as_str().unwrap(),data["modulus"].as_str().unwrap()).unwrap(),item["ciphertext"].as_str().unwrap());}}
 #[test]fn wire_body_has_exactly_two_encoding_layers(){let c=Context{client:client().unwrap(),page:Url::parse("http://aaa.cqust.edu.cn/eportal/").unwrap(),api:Url::parse("http://aaa.cqust.edu.cn/eportal/InterFace.do").unwrap(),query:"wlanuserip=x&MAC=aa%3Abb&url=a%2520b".into(),mac:"aa%3Abb".into(),encrypt:false,exponent:String::new(),modulus:String::new(),service:"互联网".into(),prefix:String::new(),captcha:None};let body=login_body(" demo "," a+b% ",&c,"").unwrap();assert!(body.contains("userId=demo&"));assert!(body.contains(&format!("password={}",twice(" a+b% "))));assert!(body.contains(&format!("queryString={}",twice(&c.query))));assert!(!body.contains("userIndex="));assert!(body.ends_with("passwordEncrypt=false"));}
}
