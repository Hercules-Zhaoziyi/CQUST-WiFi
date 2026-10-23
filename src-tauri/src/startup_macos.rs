//! Per-user, next-login LaunchAgent. No launchctl calls: do not start a second app.
//! Keep the storage helpers independent of HOME/current_exe for offline tests.
use std::{
    fs::{self, OpenOptions},
    io::{ErrorKind, Write},
    path::{Path, PathBuf},
    sync::{
        atomic::{AtomicU64, Ordering},
        Mutex,
    },
};

const LABEL: &str = "cn.ziyis.cqustwifi";
static LOCK: Mutex<()> = Mutex::new(());
static NEXT_TEMP: AtomicU64 = AtomicU64::new(0);

fn agent_path() -> Result<PathBuf, String> {
    let home = std::env::var_os("HOME").ok_or("无法找到用户目录。")?;
    Ok(PathBuf::from(home)
        .join("Library/LaunchAgents")
        .join(format!("{LABEL}.plist")))
}

fn xml_escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&apos;")
}

fn agent_contents(executable: &Path) -> Result<String, String> {
    let executable = executable.to_str().ok_or("应用路径不是有效的 UTF-8。")?;
    if !Path::new(executable).is_absolute() || executable.chars().any(char::is_control) {
        return Err("应用路径无效。".into());
    }
    let executable = xml_escape(executable);
    Ok(format!("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n<plist version=\"1.0\"><dict>\n<key>Label</key><string>{LABEL}</string>\n<key>ProgramArguments</key><array><string>{executable}</string><string>--hidden</string></array>\n<key>RunAtLoad</key><true/>\n</dict></plist>\n"))
}

// Deliberately recognize only the exact template emitted by this app, including
// the legacy version. Never interpret arbitrary XML or silently overwrite custom jobs.
fn managed_agent(contents: &str) -> bool {
    let template = agent_contents(Path::new("/__CQUST_EXECUTABLE__")).unwrap();
    let (prefix, suffix) = template.split_once("/__CQUST_EXECUTABLE__").unwrap();
    let Some(encoded) = contents
        .strip_prefix(prefix)
        .and_then(|s| s.strip_suffix(suffix))
    else {
        return false;
    };
    let mut path = String::new();
    let mut rest = encoded;
    while !rest.is_empty() {
        if rest.starts_with('&') {
            let Some((entity, ch)) = [
                ("&amp;", '&'),
                ("&lt;", '<'),
                ("&gt;", '>'),
                ("&quot;", '"'),
                ("&apos;", '\''),
            ]
            .into_iter()
            .find(|(entity, _)| rest.starts_with(entity)) else {
                return false;
            };
            path.push(ch);
            rest = &rest[entity.len()..];
        } else {
            let ch = rest.chars().next().unwrap();
            path.push(ch);
            rest = &rest[ch.len_utf8()..];
        }
    }
    agent_contents(Path::new(&path))
        .map(|canonical| canonical == contents)
        .unwrap_or(false)
}

#[derive(Debug, PartialEq)]
enum State {
    Missing,
    Current,
    Stale,
    Unrecognized,
}

fn read_agent(path: &Path) -> Result<Option<String>, String> {
    match fs::symlink_metadata(path) {
        Ok(meta) if !meta.file_type().is_file() => {
            return Err("启动项不是普通文件，请手动检查。".into())
        }
        Ok(_) => (),
        Err(e) if e.kind() == ErrorKind::NotFound => return Ok(None),
        Err(_) => return Err("无法读取登录启动项状态。".into()),
    }
    let bytes = fs::read(path).map_err(|_| "无法读取登录启动项。")?;
    // Non-UTF-8/binary/custom plists are unrecognized, not candidates for auto repair.
    Ok(Some(String::from_utf8(bytes).unwrap_or_default()))
}

fn state(path: &Path, executable: &Path) -> Result<State, String> {
    let Some(actual) = read_agent(path)? else {
        return Ok(State::Missing);
    };
    let expected = agent_contents(executable)?;
    Ok(if actual == expected {
        State::Current
    } else if managed_agent(&actual) {
        State::Stale
    } else {
        State::Unrecognized
    })
}

fn writable_executable(executable: &Path) -> Result<String, String> {
    if executable
        .components()
        .any(|part| part.as_os_str() == "AppTranslocation")
    {
        return Err(
            "应用正在临时隔离目录运行，请移入最终安装位置并重新打开后设置开机启动。".into(),
        );
    }
    agent_contents(executable)
}

fn write_agent(path: &Path, contents: &str) -> Result<(), String> {
    // Reject symlinks, directories, and unreadable files before replacing anything.
    read_agent(path)?;
    let parent = path.parent().ok_or("启动项路径无效。")?;
    fs::create_dir_all(parent).map_err(|_| "无法创建登录启动项目录。")?;
    // Same-directory rename avoids leaving a truncated plist after an interrupted write.
    let temporary = parent.join(format!(
        ".{LABEL}.{}-{}.tmp",
        std::process::id(),
        NEXT_TEMP.fetch_add(1, Ordering::Relaxed)
    ));
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options
        .open(&temporary)
        .map_err(|_| "无法创建启动项临时文件。")?;
    let result = (|| {
        file.write_all(contents.as_bytes())
            .map_err(|_| "无法保存登录启动项。")?;
        file.sync_all().map_err(|_| "无法保存登录启动项。")?;
        drop(file);
        fs::rename(&temporary, path).map_err(|_| "无法替换登录启动项。")?;
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(temporary);
    }
    result
}

fn set_at(path: &Path, executable: Option<&Path>, value: bool) -> Result<bool, String> {
    if value {
        let executable = executable.ok_or("无法定位应用程序。")?;
        let contents = writable_executable(executable)?;
        write_agent(path, &contents)?;
        if state(path, executable)? != State::Current {
            return Err("登录启动项未能更新。".into());
        }
    } else {
        // Disabling must work even when current_exe cannot be resolved, or the old
        // executable no longer exists. Removal errors must not masquerade as success.
        read_agent(path)?;
        match fs::remove_file(path) {
            Ok(()) => (),
            Err(e) if e.kind() == ErrorKind::NotFound => (),
            Err(_) => return Err("无法删除登录启动项。".into()),
        }
        if read_agent(path)?.is_some() {
            return Err("登录启动项未能关闭。".into());
        }
    }
    Ok(value)
}

fn reconcile_at(path: &Path, executable: &Path) -> Result<bool, String> {
    match state(path, executable)? {
        State::Missing | State::Current => Ok(false),
        State::Stale => {
            set_at(path, Some(executable), true)?;
            Ok(true)
        }
        State::Unrecognized => {
            Err("登录启动项已损坏或经过手动修改；请关闭后重新开启开机启动以重新绑定。".into())
        }
    }
}

/// Called once at startup. An existing managed job records prior user consent;
/// absence never enables startup. The currently opened copy wins, even if the old
/// copy still exists. No need to access or execute the previous path.
pub fn reconcile() -> Result<bool, String> {
    let _guard = LOCK.lock().map_err(|_| "启动项状态不可用。")?;
    let path = agent_path()?;
    let executable = std::env::current_exe().map_err(|_| "无法定位应用程序。")?;
    reconcile_at(&path, &executable)
}

/// Read-only: false for a stale/unrecognized job, without concealing it from repair.
pub fn enabled() -> bool {
    let Ok(_guard) = LOCK.lock() else {
        return false;
    };
    let Ok(path) = agent_path() else { return false };
    let Ok(executable) = std::env::current_exe() else {
        return false;
    };
    matches!(state(&path, &executable), Ok(State::Current))
}

pub fn set(value: bool) -> Result<bool, String> {
    let _guard = LOCK.lock().map_err(|_| "启动项状态不可用。")?;
    let path = agent_path()?;
    let executable = if value {
        Some(std::env::current_exe().map_err(|_| "无法定位应用程序。")?)
    } else {
        None
    };
    set_at(&path, executable.as_deref(), value)
}

#[cfg(test)]
mod tests {
    use super::*;

    struct Fixture {
        root: PathBuf,
        agent: PathBuf,
    }
    impl Fixture {
        fn new() -> Self {
            let root = std::env::temp_dir().join(format!(
                "cqust-agent-test-{}-{}",
                std::process::id(),
                NEXT_TEMP.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir(&root).unwrap();
            let agent = root.join("LaunchAgents/test.plist");
            Self { root, agent }
        }
        fn exe(&self, name: &str) -> PathBuf {
            self.root
                .join(name)
                .join("CQUST.app/Contents/MacOS/cqust-wifi")
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.root);
        }
    }

    #[test]
    fn first_enable_and_disabled_launch() {
        let f = Fixture::new();
        let exe = f.exe("Applications");
        assert_eq!(state(&f.agent, &exe).unwrap(), State::Missing);
        assert!(!reconcile_at(&f.agent, &exe).unwrap());
        assert!(!f.agent.exists());
        assert!(set_at(&f.agent, Some(&exe), true).unwrap());
        assert_eq!(state(&f.agent, &exe).unwrap(), State::Current);
        let before = fs::read(&f.agent).unwrap();
        assert!(!reconcile_at(&f.agent, &exe).unwrap());
        assert_eq!(fs::read(&f.agent).unwrap(), before);
    }

    #[test]
    fn moving_app_rebinds_even_when_old_copy_still_exists() {
        let f = Fixture::new();
        let old = f.exe("Downloads");
        let new = f.exe("Applications");
        fs::create_dir_all(old.parent().unwrap()).unwrap();
        fs::write(&old, b"fake executable").unwrap();
        set_at(&f.agent, Some(&old), true).unwrap();
        assert_eq!(state(&f.agent, &new).unwrap(), State::Stale);
        assert!(f.agent.exists()); // A stale job is not the same as disabled.
        assert!(reconcile_at(&f.agent, &new).unwrap());
        assert_eq!(state(&f.agent, &new).unwrap(), State::Current);
        assert!(old.exists());
        assert_eq!(
            fs::read_to_string(&f.agent).unwrap(),
            agent_contents(&new).unwrap()
        );
    }

    #[test]
    fn missing_old_executable_and_repeated_moves_recover() {
        let f = Fixture::new();
        let old = f.exe("old");
        let new = f.exe("new");
        let third = f.exe("third");
        fs::create_dir_all(old.parent().unwrap()).unwrap();
        fs::write(&old, b"fake executable").unwrap();
        set_at(&f.agent, Some(&old), true).unwrap();
        fs::remove_file(&old).unwrap();
        assert!(reconcile_at(&f.agent, &new).unwrap());
        assert!(reconcile_at(&f.agent, &third).unwrap());
        assert!(!reconcile_at(&f.agent, &third).unwrap());
        assert_eq!(state(&f.agent, &third).unwrap(), State::Current);
    }

    #[test]
    fn explicit_enable_rebinds_and_disable_removes_stale_job() {
        let f = Fixture::new();
        let old = f.exe("old");
        let new = f.exe("new");
        set_at(&f.agent, Some(&old), true).unwrap();
        set_at(&f.agent, Some(&new), true).unwrap();
        assert_eq!(state(&f.agent, &new).unwrap(), State::Current);
        set_at(&f.agent, Some(&old), true).unwrap();
        assert!(!set_at(&f.agent, None, false).unwrap());
        assert!(!set_at(&f.agent, None, false).unwrap());
        assert!(!reconcile_at(&f.agent, &new).unwrap());
        assert!(!f.agent.exists());
    }

    #[test]
    fn special_character_paths_round_trip_and_rebind() {
        let f = Fixture::new();
        let old = f.exe("校园 网 & <旧> ' \"");
        let new = f.exe("新 &amp; 目录");
        set_at(&f.agent, Some(&old), true).unwrap();
        let contents = fs::read_to_string(&f.agent).unwrap();
        assert!(contents.contains("校园 网 &amp; &lt;旧&gt; &apos; &quot;"));
        assert!(managed_agent(&contents));
        assert!(reconcile_at(&f.agent, &new).unwrap());
        assert!(fs::read_to_string(&f.agent).unwrap().contains("&amp;amp;"));
        assert_eq!(state(&f.agent, &new).unwrap(), State::Current);
    }

    #[test]
    fn unrecognized_jobs_are_preserved_until_explicit_user_action() {
        let f = Fixture::new();
        let exe = f.exe("Applications");
        fs::create_dir_all(f.agent.parent().unwrap()).unwrap();
        let canonical = agent_contents(&exe).unwrap();
        for bytes in [
            b"broken plist".to_vec(),
            vec![0xff],
            canonical.replace("--hidden", "--custom").into_bytes(),
            canonical.replace("cn.ziyis", "org.other").into_bytes(),
        ] {
            fs::write(&f.agent, &bytes).unwrap();
            assert_eq!(state(&f.agent, &exe).unwrap(), State::Unrecognized);
            assert!(reconcile_at(&f.agent, &exe).is_err());
            assert_eq!(fs::read(&f.agent).unwrap(), bytes);
        }
        set_at(&f.agent, None, false).unwrap();
        set_at(&f.agent, Some(&exe), true).unwrap();
        assert_eq!(state(&f.agent, &exe).unwrap(), State::Current);
    }

    #[test]
    fn unsafe_paths_and_write_failure_leave_old_job_intact() {
        let f = Fixture::new();
        let exe = f.exe("Applications");
        set_at(&f.agent, Some(&exe), true).unwrap();
        let before = fs::read(&f.agent).unwrap();
        for bad in [
            Path::new("relative/path"),
            Path::new("/bad\npath"),
            Path::new("/private/AppTranslocation/id/app"),
        ] {
            assert!(set_at(&f.agent, Some(bad), true).is_err());
            assert_eq!(fs::read(&f.agent).unwrap(), before);
        }
        let blocked = f.root.join("file");
        fs::write(&blocked, b"not a directory").unwrap();
        assert!(set_at(&blocked.join("job.plist"), Some(&exe), true).is_err());
        assert_eq!(fs::read(&blocked).unwrap(), b"not a directory");
        assert!(fs::read_dir(f.agent.parent().unwrap())
            .unwrap()
            .all(|entry| entry.unwrap().path() == f.agent));
    }

    #[test]
    fn directory_is_an_error_including_when_disabling() {
        let f = Fixture::new();
        let exe = f.exe("Applications");
        fs::create_dir_all(&f.agent).unwrap();
        assert!(state(&f.agent, &exe).is_err());
        assert!(set_at(&f.agent, None, false).is_err());
        assert!(set_at(&f.agent, Some(&exe), true).is_err());
        assert!(f.agent.is_dir());
    }

    #[cfg(unix)]
    #[test]
    fn symlinks_are_never_followed_or_replaced() {
        use std::os::unix::fs::symlink;
        let f = Fixture::new();
        let exe = f.exe("Applications");
        let target = f.root.join("target");
        fs::write(&target, agent_contents(&exe).unwrap()).unwrap();
        fs::create_dir_all(f.agent.parent().unwrap()).unwrap();
        symlink(&target, &f.agent).unwrap();
        let before = fs::read(&target).unwrap();
        assert!(reconcile_at(&f.agent, &exe).is_err());
        assert!(set_at(&f.agent, Some(&exe), true).is_err());
        assert!(set_at(&f.agent, None, false).is_err());
        assert!(fs::symlink_metadata(&f.agent)
            .unwrap()
            .file_type()
            .is_symlink());
        assert_eq!(fs::read(&target).unwrap(), before);
        fs::remove_file(&target).unwrap();
        assert!(set_at(&f.agent, None, false).is_err());
    }

    #[test]
    fn translocated_launch_does_not_rebind_an_existing_job() {
        let f = Fixture::new();
        let installed = f.exe("Applications");
        let translocated = f.exe("AppTranslocation/id");
        set_at(&f.agent, Some(&installed), true).unwrap();
        let before = fs::read(&f.agent).unwrap();
        assert!(reconcile_at(&f.agent, &translocated).is_err());
        assert_eq!(fs::read(&f.agent).unwrap(), before);
    }

    #[cfg(unix)]
    #[test]
    fn non_utf8_executable_is_rejected_without_damaging_the_job() {
        use std::{ffi::OsString, os::unix::ffi::OsStringExt};
        let f = Fixture::new();
        let installed = f.exe("Applications");
        set_at(&f.agent, Some(&installed), true).unwrap();
        let before = fs::read(&f.agent).unwrap();
        let invalid = PathBuf::from(OsString::from_vec(b"/invalid-\xff/app".to_vec()));
        assert!(set_at(&f.agent, Some(&invalid), true).is_err());
        assert_eq!(fs::read(&f.agent).unwrap(), before);
    }

    #[test]
    fn escapes_launch_agent_path() {
        assert_eq!(xml_escape("a&<>'\"b"), "a&amp;&lt;&gt;&apos;&quot;b");
    }
}
