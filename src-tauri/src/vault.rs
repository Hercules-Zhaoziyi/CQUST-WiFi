use windows_sys::Win32::Security::Credentials::*;
use zeroize::Zeroizing;
fn wide(s:&str)->Vec<u16>{s.encode_utf16().chain(Some(0)).collect()}
unsafe fn string(p:*const u16)->String{if p.is_null(){return String::new()}let mut n=0;while n<2048&&*p.add(n)!=0{n+=1}String::from_utf16_lossy(std::slice::from_raw_parts(p,n))}
fn read(target:&str)->Option<(String,Zeroizing<String>)>{
 unsafe{
  let mut p=std::ptr::null_mut();if CredReadW(wide(target).as_ptr(),CRED_TYPE_GENERIC,0,&mut p)==0{return None}
  let c=&*p;let user=string(c.UserName);
  if c.CredentialBlobSize==0||c.CredentialBlob.is_null(){CredFree(p.cast());return Some((user,Zeroizing::new(String::new())))}
  let bytes=std::slice::from_raw_parts(c.CredentialBlob,c.CredentialBlobSize as usize);
  let units=Zeroizing::new(bytes.chunks_exact(2).map(|b|u16::from_le_bytes([b[0],b[1]])).collect::<Vec<u16>>());
  let password=Zeroizing::new(String::from_utf16_lossy(&units));CredFree(p.cast());Some((user,password))
 }
}
pub fn get(user:&str)->Zeroizing<String>{
 for target in ["CQUST Wifi".to_string(),format!("{user}@CQUST Wifi")]{if let Some((name,pw))=read(&target){if name==user{return pw}}}Zeroizing::new(String::new())
}
fn write(target:&str,user:&str,password:&str)->Result<(),String>{
 let mut target=wide(target);let mut username=wide(user);
 let mut blob=Zeroizing::new(password.encode_utf16().flat_map(u16::to_le_bytes).collect::<Vec<u8>>());
 let mut c:CREDENTIALW=unsafe{std::mem::zeroed()};c.Type=CRED_TYPE_GENERIC;c.TargetName=target.as_mut_ptr();c.UserName=username.as_mut_ptr();c.CredentialBlob=blob.as_mut_ptr();c.CredentialBlobSize=blob.len() as u32;c.Persist=CRED_PERSIST_LOCAL_MACHINE;
 if unsafe{CredWriteW(&c,0)}==0{Err("Windows 凭据保存失败，请下次重新输入密码。".into())}else{Ok(())}
}
pub fn set(user:&str,password:&str)->Result<(),String>{
 write(&format!("{user}@CQUST Wifi"),user,password)?;
 if read("CQUST Wifi").map(|(name,_)|name==user).unwrap_or(false){write("CQUST Wifi",user,password)?}Ok(())
}
pub fn remove(user:&str)->Result<(),String>{
 for target in ["CQUST Wifi".to_string(),format!("{user}@CQUST Wifi")]{if let Some((name,_))=read(&target){if name==user&&unsafe{CredDeleteW(wide(&target).as_ptr(),CRED_TYPE_GENERIC,0)}==0{return Err("删除保存的密码失败。".into())}}}Ok(())
}
