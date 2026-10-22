use security_framework::passwords::{delete_generic_password, generic_password, set_generic_password, PasswordOptions};
use zeroize::Zeroizing;

const SERVICE: &str = "cn.ziyis.cqustwifi";
const ITEM_NOT_FOUND: i32 = -25300;

pub fn get(user: &str) -> Zeroizing<String> {
    if user.is_empty() {
        return Zeroizing::new(String::new());
    }
    let options = PasswordOptions::new_generic_password(SERVICE, user);
    let password = generic_password(options).ok().and_then(|bytes| String::from_utf8(bytes).ok()).unwrap_or_default();
    Zeroizing::new(password)
}

pub fn set(user: &str, password: &str) -> Result<(), String> {
    set_generic_password(SERVICE, user, password.as_bytes())
        .map_err(|_| "macOS 钥匙串保存失败，请下次重新输入密码。".into())
}

pub fn remove(user: &str) -> Result<(), String> {
    match delete_generic_password(SERVICE, user) {
        Ok(()) => Ok(()),
        Err(error) if error.code() == ITEM_NOT_FOUND => Ok(()),
        Err(_) => Err("无法从 macOS 钥匙串删除保存的密码。".into()),
    }
}
