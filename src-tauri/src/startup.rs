use winreg::{RegKey,enums::*};
const RUN:&str="Software\\Microsoft\\Windows\\CurrentVersion\\Run";
const APPROVED:&str="Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run";
const NAME:&str="CQUST Wi-Fi";
fn command()->Result<String,String>{let path=std::env::current_exe().map_err(|_|"无法定位程序路径。")?;Ok(format!("\"{}\" --hidden",path.display()))}
pub fn enabled()->bool{
 let root=RegKey::predef(HKEY_CURRENT_USER);
 let value=root.open_subkey(RUN).ok().and_then(|k|k.get_value::<String,_>(NAME).ok());
 if value.as_deref()!=command().ok().as_deref(){return false}
 let disabled=root.open_subkey(APPROVED).ok().and_then(|k|k.get_raw_value(NAME).ok()).map(|v|v.bytes.first()==Some(&3)).unwrap_or(false);
 !disabled
}
pub fn set(value:bool)->Result<bool,String>{
 let root=RegKey::predef(HKEY_CURRENT_USER);let (key,_)=root.create_subkey(RUN).map_err(|_|"无法访问当前用户的启动项。")?;
 if value{
  key.set_value(NAME,&command()?).map_err(|_|"写入启动项失败。")?;
  if let Ok(approved)=root.open_subkey_with_flags(APPROVED,KEY_READ|KEY_WRITE){let _=approved.delete_value(NAME);}
 }else{match key.delete_value(NAME){Ok(())=>(),Err(e)if e.kind()==std::io::ErrorKind::NotFound=>(),Err(_)=>return Err("移除启动项失败。".into())}}
 let actual=enabled();if actual!=value{Err("启动项未能更新，请稍后重试。".into())}else{Ok(actual)}
}
#[cfg(test)]mod tests{
 use super::*;
 struct Restore{run:Option<winreg::RegValue>,approved:Option<winreg::RegValue>}
 impl Drop for Restore{fn drop(&mut self){let root=RegKey::predef(HKEY_CURRENT_USER);if let Ok((key,_))=root.create_subkey(RUN){if let Some(value)=&self.run{key.set_raw_value(NAME,value).expect("restore original startup entry");}else{let _=key.delete_value(NAME);}}if let Ok((key,_))=root.create_subkey(APPROVED){if let Some(value)=&self.approved{key.set_raw_value(NAME,value).expect("restore original startup approval");}else{let _=key.delete_value(NAME);}}}}
 #[test]fn startup_roundtrip_restores_previous_values(){let root=RegKey::predef(HKEY_CURRENT_USER);let _restore=Restore{run:root.open_subkey(RUN).ok().and_then(|k|k.get_raw_value(NAME).ok()),approved:root.open_subkey(APPROVED).ok().and_then(|k|k.get_raw_value(NAME).ok())};assert_eq!(set(true).unwrap(),true);assert!(enabled());assert_eq!(set(false).unwrap(),false);assert!(!enabled());}
}
