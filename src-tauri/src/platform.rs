use std::process::Command;

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
