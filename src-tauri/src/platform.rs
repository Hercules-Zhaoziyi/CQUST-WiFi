use std::process::Command;

pub fn open_firewall()->Result<(),String>{
    #[cfg(windows)]{Command::new("control.exe").args(["/name","Microsoft.WindowsFirewall","/page","pageConfigureApps"]).spawn().map(|_|()).map_err(|_|"无法打开 Windows 防火墙设置。".into())}
    #[cfg(target_os="macos")]{open_url("x-apple.systempreferences:com.apple.preference.security?Firewall")}
}

pub fn open_url(url: &str) -> Result<(), String> {
    #[cfg(windows)]
    let mut command = {
        let mut command = Command::new("rundll32.exe");
        command.arg("url.dll,FileProtocolHandler");
        command
    };
    #[cfg(target_os = "macos")]
    let mut command = Command::new("/usr/bin/open");

    command.arg(url).spawn().map(|_| ()).map_err(|_| "无法打开系统浏览器。".into())
}
