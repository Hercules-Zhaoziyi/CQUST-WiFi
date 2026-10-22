use std::{fs, path::PathBuf};

const LABEL: &str = "cn.ziyis.cqustwifi";

fn agent_path() -> Result<PathBuf, String> {
    let home = std::env::var_os("HOME").ok_or("无法找到用户目录。")?;
    Ok(PathBuf::from(home).join("Library/LaunchAgents").join(format!("{LABEL}.plist")))
}

fn xml_escape(value: &str) -> String {
    value.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;").replace('"', "&quot;").replace('\'', "&apos;")
}

fn agent_contents() -> Result<String, String> {
    let executable = std::env::current_exe().map_err(|_| "无法定位应用程序。")?;
    let executable = xml_escape(&executable.to_string_lossy());
    Ok(format!("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n<plist version=\"1.0\"><dict>\n<key>Label</key><string>{LABEL}</string>\n<key>ProgramArguments</key><array><string>{executable}</string><string>--hidden</string></array>\n<key>RunAtLoad</key><true/>\n</dict></plist>\n"))
}

pub fn enabled() -> bool {
    let Ok(path) = agent_path() else { return false };
    let Ok(expected) = agent_contents() else { return false };
    fs::read_to_string(path).map(|actual| actual == expected).unwrap_or(false)
}

pub fn set(value: bool) -> Result<bool, String> {
    let path = agent_path()?;
    if value {
        let contents = agent_contents()?;
        fs::create_dir_all(path.parent().ok_or("启动项路径无效。")?).map_err(|_| "无法创建登录启动项目录。")?;
        fs::write(&path, contents).map_err(|_| "无法保存登录启动项。")?;
    } else if path.exists() {
        fs::remove_file(&path).map_err(|_| "无法删除登录启动项。")?;
    }
    if enabled() == value { Ok(value) } else { Err("登录启动项未能更新。".into()) }
}

#[cfg(test)]
mod tests {
    use super::xml_escape;

    #[test]
    fn escapes_launch_agent_path() {
        assert_eq!(xml_escape("a&<>'\"b"), "a&amp;&lt;&gt;&apos;&quot;b");
    }
}
