use crate::auth::Error;
use base64::Engine;
use chrono::{Datelike, Local, NaiveDate};
use regex::Regex;
use reqwest::blocking::Client;
use reqwest::header::{HeaderMap, ACCEPT, ACCEPT_LANGUAGE, CONTENT_TYPE, COOKIE, LOCATION, REFERER, SET_COOKIE, USER_AGENT};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter};
use url::Url;
use zeroize::{Zeroize, Zeroizing};

const GATEWAY: &str = "https://web.cqust.edu.cn";
const JWNEW_HOST: &str = "jwnew.cqust.edu.cn";
const SJJX_HOST: &str = "sjjx.cqust.edu.cn";
const VPN_TICKET: &str = "wengine_vpn_ticketweb_cqust_edu_cn";
const USER_AGENT_VALUE: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
const MAX_BYTES: usize = 8 * 1024 * 1024;
const MAX_SLOTS: usize = 3000;
const MAX_WEEKS: u8 = 30;
const TOTAL_WEEKS: u8 = 20;
const COLOR_COUNT: u8 = 16;
const CAS_CHARS: &[u8] = b"ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";

fn error(message: &str) -> Error {
    Error::new("error", message)
}
fn credentials(message: &str) -> Error {
    Error::new("credentials", message)
}
fn context(message: &str) -> Error {
    Error::new("context", message)
}
fn network() -> Error {
    Error::new("network", "校园网络暂时不可用，请检查连接后重试。")
}
fn rx(pattern: &str) -> Regex {
    Regex::new(pattern).expect("static regular expression")
}
fn progress(app: Option<&AppHandle>, message: &str) {
    if let Some(app) = app {
        let _ = app.emit("cqust-event", json!({ "event": "schedule-progress", "data": message }));
    }
}
fn now_seconds() -> f64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs_f64()).unwrap_or_default()
}

// 校园网账号上下文：仅在学号与当前账号一致时复用已保存的校园网密码。
#[derive(Clone, Default)]
pub struct CampusContext {
    pub password: Option<Zeroizing<String>>,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Profile {
    pub student_id: String,
    pub name: String,
    pub college: String,
    pub major: String,
    pub class_name: String,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Semester {
    pub id: String,
    pub school_year: String,
    pub name: String,
    pub label: String,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Slot {
    pub name: String,
    pub code: String,
    pub course_type: String,
    pub teacher: String,
    pub location: String,
    pub project: String,
    pub day: u8,
    pub start: u8,
    pub end: u8,
    pub weeks: Vec<u8>,
    pub credit: String,
    pub category: String,
    pub color: u8,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Store {
    pub synced_at: f64,
    pub profile: Profile,
    pub semesters: Vec<Semester>,
    pub semester_id: String,
    pub semester_label: String,
    pub start_date: String,
    pub slots: Vec<Slot>,
}

#[derive(Clone)]
pub struct Schedule {
    cache: Arc<Mutex<Store>>,
    path: PathBuf,
}

impl Schedule {
    // 读取本地课表缓存；损坏或超限时回退为空课表。
    pub fn new(folder: &Path) -> Self {
        let path = folder.join("schedule.json");
        let store = std::fs::read(&path)
            .ok()
            .filter(|bytes| bytes.len() <= 4_000_000)
            .and_then(|bytes| serde_json::from_slice::<Store>(&bytes).ok())
            .filter(valid_store)
            .unwrap_or_default();
        Self { cache: Arc::new(Mutex::new(store)), path }
    }
    fn save(&self, store: &Store) -> Result<(), Error> {
        let bytes = serde_json::to_vec_pretty(store).map_err(|_| error("课表保存失败。"))?;
        let temporary = self.path.with_extension("tmp");
        std::fs::write(&temporary, bytes)
            .and_then(|_| std::fs::rename(&temporary, &self.path))
            .map_err(|_| error("课表保存失败，请检查用户目录权限。"))
    }
}

// 校验缓存内容，阻止异常文件造成渲染问题。
fn valid_store(store: &Store) -> bool {
    store.slots.len() <= MAX_SLOTS
        && store.slots.iter().all(|slot| {
            (1..=7).contains(&slot.day)
                && slot.start >= 1
                && slot.end <= 11
                && slot.start <= slot.end
                && slot.weeks.iter().all(|week| (1..=MAX_WEEKS).contains(week))
                && !slot.name.is_empty()
        })
        && store.semesters.len() <= 16
}

fn view(store: &Store) -> Value {
    let semester = store.semesters.iter().find(|item| item.id == store.semester_id);
    let default_start = default_start_date(
        semester.map(|item| item.school_year.as_str()).unwrap_or(""),
        semester.map(|item| item.name.as_str()).unwrap_or(""),
    );
    json!({
        "syncedAt": store.synced_at,
        "profile": store.profile,
        "semesters": store.semesters,
        "semesterId": store.semester_id,
        "semesterLabel": store.semester_label,
        "startDate": store.start_date,
        "defaultStartDate": default_start,
        "totalWeeks": TOTAL_WEEKS,
        "slots": store.slots,
    })
}

pub fn command(app: &AppHandle, state: &Schedule, method: &str, args: Value, ctx: CampusContext) -> Result<Value, Error> {
    match method {
        "schedule" => {
            let store = state.cache.lock().map_err(|_| error("课表缓存暂不可用，请重启软件。"))?;
            Ok(view(&store))
        }
        "scheduleSync" => sync(app, state, args, ctx),
        "scheduleStartDate" => set_start_date(state, args),
        _ => Err(error("不支持的课表操作。")),
    }
}

// 保存开学日期；空值回退到按学期推算的默认日期。
fn set_start_date(state: &Schedule, args: Value) -> Result<Value, Error> {
    let value = args["startDate"].as_str().unwrap_or("").trim();
    if !value.is_empty() {
        let date = NaiveDate::parse_from_str(value, "%Y-%m-%d").map_err(|_| error("开学日期格式无效。"))?;
        if !(2000..=2100).contains(&date.year()) {
            return Err(error("开学日期超出有效范围。"));
        }
    }
    let mut store = state.cache.lock().map_err(|_| error("课表缓存暂不可用，请重启软件。"))?;
    store.start_date = value.to_string();
    state.save(&store)?;
    Ok(view(&store))
}

fn sync(app: &AppHandle, state: &Schedule, args: Value, ctx: CampusContext) -> Result<Value, Error> {
    let student_id = args["student_id"].as_str().unwrap_or("").trim().to_string();
    if student_id.len() < 4
        || student_id.len() > 32
        || !student_id.chars().all(|ch| ch.is_ascii_alphanumeric() || ch == '_' || ch == '-')
    {
        return Err(credentials("请输入有效的学号。"));
    }
    let typed = args["password"].as_str().unwrap_or("").trim();
    let password = if typed.is_empty() {
        if args["use_campus_password"].as_bool().unwrap_or(true) {
            ctx.password.clone().ok_or_else(|| credentials("请输入统一身份认证密码。"))?
        } else {
            return Err(credentials("请输入统一身份认证密码。"));
        }
    } else {
        Zeroizing::new(typed.to_string())
    };
    if password.len() > 256 {
        return Err(credentials("密码长度无效。"));
    }

    let mut fresh = fetch_store(Some(app), &student_id, &password)?;
    if let Ok(existing) = state.cache.lock() {
        if existing.semester_id == fresh.semester_id {
            fresh.start_date = existing.start_date.clone();
        }
    }
    progress(Some(app), "正在保存课表");
    state.save(&fresh)?;
    let mut cache = state.cache.lock().map_err(|_| error("课表缓存暂不可用，请重启软件。"))?;
    *cache = fresh;
    Ok(view(&cache))
}

// 完成统一认证、教务抓取与实践课表合并，返回可落盘的课表缓存。
fn fetch_store(app: Option<&AppHandle>, student_id: &str, password: &str) -> Result<Store, Error> {
    progress(app, "正在登录统一身份认证");
    let mut session = VpnSession::new()?;
    session.cas_login(student_id, password)?;

    progress(app, "正在接入教务系统");
    session.connect_eams()?;

    progress(app, "正在获取学生档案与学期");
    let profile = session.fetch_profile(student_id);
    let entry = session.fetch_entry()?;
    let (semester_id, semesters) = session.fetch_semesters(&entry)?;

    progress(app, "正在获取课表正文");
    let theory = session.fetch_table(&entry, &semester_id)?;

    let term = semesters
        .iter()
        .find(|item| item.id == semester_id)
        .map(|item| format!("{}-{}", item.school_year, item.name));
    progress(app, "正在获取实践教学课表");
    let practical = match session.fetch_practical(term.as_deref()) {
        Ok(items) => items,
        Err(_) => {
            progress(app, "实践教学课表暂不可用，已保留理论课表");
            Vec::new()
        }
    };

    let slots = build_slots(&theory, &practical);
    if slots.len() > MAX_SLOTS {
        return Err(context("课表条目过多，已停止处理。"));
    }
    let semester_label = semesters
        .iter()
        .find(|item| item.id == semester_id)
        .map(|item| item.label.clone())
        .unwrap_or_default();
    Ok(Store {
        synced_at: now_seconds(),
        profile,
        semesters,
        semester_id,
        semester_label,
        start_date: String::new(),
        slots,
    })
}

// 按学期推算默认开学日期，与参考客户端保持一致。
fn default_start_date(school_year: &str, name: &str) -> String {
    let years = rx(r"\d{4}")
        .find_iter(school_year)
        .filter_map(|item| item.as_str().parse::<i32>().ok())
        .collect::<Vec<_>>();
    let is_spring = name.contains('2') || name.contains('春') || name.contains('下');
    if years.len() >= 2 {
        if is_spring {
            format!("{}-02-22", years[1])
        } else {
            format!("{}-09-07", years[0])
        }
    } else {
        let now = Local::now();
        let month = now.month();
        if (2..=7).contains(&month) {
            format!("{}-02-22", now.format("%Y"))
        } else {
            let year = if month == 1 { now.year() - 1 } else { now.year() };
            format!("{year}-09-07")
        }
    }
}

// WebVPN 只允许访问学校域名，防止重定向到外部主机。
fn school_url(url: &Url) -> bool {
    matches!(url.scheme(), "https")
        && url.port_or_known_default() == Some(443)
        && url.username().is_empty()
        && url.password().is_none()
        && url.host_str().map(|host| host == "cqust.edu.cn" || host.ends_with(".cqust.edu.cn")).unwrap_or(false)
}

fn vpn_token(host: &str) -> Option<&'static str> {
    match host {
        "cas.cqust.edu.cn" => Some("77726476706e69737468656265737421f3f652d224217d436a468ca88d1b203b"),
        "casp.cqust.edu.cn" => Some("77726476706e69737468656265737421f3f6528c693379456d1cc7a99c406d36f8"),
        "web.cqust.edu.cn" => Some("77726476706e69737468656265737421e7f243d224217d436a468ca88d1b203b"),
        "jwnew.cqust.edu.cn" => Some("77726476706e69737468656265737421fae04f99307e6b416b1b9de29d51367b4912"),
        "sjjx.cqust.edu.cn" => Some("77726476706e69737468656265737421e3fd4b84693379456d1cc7a99c406d369b"),
        "icon.cqust.edu.cn" => Some("77726476706e69737468656265737421f9f44e92693379456d1cc7a99c406d3652"),
        _ => None,
    }
}

fn vpn_url(host: &str, path: &str) -> Result<String, Error> {
    let token = vpn_token(host).ok_or_else(|| context("校园系统地址不受支持。"))?;
    let path = if path.starts_with('/') { path.to_string() } else { format!("/{path}") };
    Ok(format!("{GATEWAY}/http/{token}{path}"))
}

fn resolve(current: &str, location: &str) -> Result<String, Error> {
    let base = Url::parse(current).map_err(|_| context("学校系统返回了无效地址。"))?;
    Ok(base.join(location).map_err(|_| context("学校系统返回了无效地址。"))?.to_string())
}

fn decode(bytes: Vec<u8>) -> String {
    if let Ok(text) = std::str::from_utf8(&bytes) {
        text.trim_start_matches('\u{feff}').to_string()
    } else {
        encoding_rs::GBK.decode(&bytes).0.into_owned()
    }
}

fn form(pairs: &[(&str, &str)]) -> String {
    let mut serializer = url::form_urlencoded::Serializer::new(String::new());
    for (key, value) in pairs {
        serializer.append_pair(key, value);
    }
    serializer.finish()
}

fn encode(value: &str) -> String {
    url::form_urlencoded::byte_serialize(value.as_bytes()).collect()
}

// 清洗 HTML 文本并去除脚本内容。
fn html_text(value: &str) -> String {
    let stripped = rx(r"(?is)<[^>]+>").replace_all(value, "");
    stripped
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

fn clipped(value: &str, max: usize) -> String {
    value.chars().take(max).collect()
}

struct Page {
    status: u16,
    location: Option<String>,
    text: String,
}

struct VpnSession {
    client: Client,
    cookies: HashMap<String, String>,
    eams_base: Option<String>,
}

impl VpnSession {
    fn new() -> Result<Self, Error> {
        let client = Client::builder()
            .no_proxy()
            .redirect(reqwest::redirect::Policy::none())
            .connect_timeout(Duration::from_secs(8))
            .timeout(Duration::from_secs(25))
            .user_agent(USER_AGENT_VALUE)
            .build()
            .map_err(|_| network())?;
        Ok(Self { client, cookies: HashMap::new(), eams_base: None })
    }

    fn cookie_header(&self) -> String {
        self.cookies.iter().map(|(key, value)| format!("{key}={value}")).collect::<Vec<_>>().join("; ")
    }

    fn store_cookies(&mut self, headers: &HeaderMap) {
        for value in headers.get_all(SET_COOKIE).iter() {
            let Ok(line) = value.to_str() else { continue };
            let Some(pair) = line.split(';').next() else { continue };
            let Some((key, value)) = pair.split_once('=') else { continue };
            let key = key.trim();
            if !key.is_empty() {
                self.cookies.insert(key.to_string(), value.trim().to_string());
            }
        }
    }

    fn has_ticket(&self) -> bool {
        self.cookies.contains_key(VPN_TICKET)
    }

    fn send(&mut self, method: reqwest::Method, url: &str, body: Option<String>, referer: Option<&str>, ajax: bool, origin: Option<&str>) -> Result<Page, Error> {
        let target = Url::parse(url).map_err(|_| context("学校系统返回了无效地址。"))?;
        if !school_url(&target) {
            return Err(context("学校系统返回了不受信任的地址，已停止访问。"));
        }
        let mut request = self
            .client
            .request(method, target)
            .header(USER_AGENT, USER_AGENT_VALUE)
            .header(ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header(ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        if let Some(referer) = referer {
            request = request.header(REFERER, referer);
        }
        if ajax {
            request = request.header("X-Requested-With", "XMLHttpRequest");
        }
        if let Some(origin) = origin {
            request = request.header("Origin", origin);
        }
        let cookies = self.cookie_header();
        if !cookies.is_empty() {
            request = request.header(COOKIE, cookies);
        }
        if let Some(body) = body {
            request = request.header(CONTENT_TYPE, "application/x-www-form-urlencoded; charset=UTF-8").body(body);
        }
        let response = request.send().map_err(|_| network())?;
        let status = response.status().as_u16();
        let location = response.headers().get(LOCATION).and_then(|value| value.to_str().ok()).map(str::to_string);
        self.store_cookies(response.headers());
        let mut bytes = Vec::new();
        response.take((MAX_BYTES + 1) as u64).read_to_end(&mut bytes).map_err(|_| network())?;
        if bytes.len() > MAX_BYTES {
            return Err(context("学校系统返回内容过大，已停止处理。"));
        }
        Ok(Page { status, location, text: decode(bytes) })
    }

    fn get(&mut self, url: &str, referer: Option<&str>, ajax: bool) -> Result<Page, Error> {
        self.send(reqwest::Method::GET, url, None, referer, ajax, None)
    }

    fn post(&mut self, url: &str, body: String, referer: Option<&str>, ajax: bool) -> Result<Page, Error> {
        self.send(reqwest::Method::POST, url, Some(body), referer, ajax, None)
    }

    fn post_with_origin(&mut self, url: &str, body: String, referer: Option<&str>, origin: &str) -> Result<Page, Error> {
        self.send(reqwest::Method::POST, url, Some(body), referer, false, Some(origin))
    }

    fn follow(&mut self, start: &str, referer: Option<&str>, max_hops: usize) -> Result<(String, Page), Error> {
        let mut url = start.to_string();
        let mut current_referer = referer.map(str::to_string);
        let mut page = self.get(&url, current_referer.as_deref(), false)?;
        for _ in 0..max_hops {
            if !matches!(page.status, 301 | 302 | 303 | 307 | 308) {
                break;
            }
            let Some(location) = page.location.clone() else { break };
            let next = resolve(&url, &location)?;
            current_referer = Some(url.clone());
            url = next;
            page = self.get(&url, current_referer.as_deref(), false)?;
        }
        Ok((url, page))
    }

    // 统一身份认证登录，成功后保留 WebVPN 票据与 CAS 会话。
    fn cas_login(&mut self, student_id: &str, password: &str) -> Result<(), Error> {
        let (actual_url, page) = self.follow(&format!("{GATEWAY}/login"), None, 10)?;
        if page.status == 403 {
            return Err(Error::new("network", "统一身份认证被网关拦截(403)，请切换网络后重试。"));
        }
        if page.status != 200 {
            return Err(Error::new("network", &format!("统一身份认证登录页不可达(HTTP {})。", page.status)));
        }
        let html = &page.text;
        let salt = rx(r#"(?i)id=["']pwdEncryptSalt["']\s+value=["']([^"']*)["']"#)
            .captures(html)
            .and_then(|captures| captures.get(1))
            .map(|item| item.as_str().to_string())
            .filter(|value| !value.is_empty())
            .or_else(|| input_value(html, "pwdEncryptSalt"));
        let Some(salt) = salt else {
            if self.has_ticket() {
                return Ok(());
            }
            return Err(context("统一身份认证登录页解析失败，学校可能已改版。"));
        };
        let execution = rx(r#"(?i)id=["']execution["'][^>]*value=["']([^"']*)["']"#)
            .captures(html)
            .and_then(|captures| captures.get(1))
            .map(|item| item.as_str().to_string())
            .or_else(|| input_value(html, "execution"))
            .unwrap_or_else(|| "e1s1".into());

        if self.need_captcha(&actual_url, student_id) {
            return Err(Error::new("captcha", "该账号需要输入图形验证码，请稍后重试。"));
        }

        let encrypted = encrypt_password(password, &salt)?;
        let body = form(&[
            ("username", student_id),
            ("password", &encrypted),
            ("lt", ""),
            ("execution", &execution),
            ("_eventId", "submit"),
            ("cllt", "userNameLogin"),
            ("dllt", "generalLogin"),
            ("captcha", ""),
            ("rememberMe", "true"),
        ]);
        let response = self.post_with_origin(&actual_url, body, Some(&actual_url), GATEWAY)?;

        if let Some(location) = response.location.as_deref() {
            if location.contains("ticket=ST-") {
                let target = resolve(&actual_url, location)?;
                self.promote(&target)?;
                return Ok(());
            }
        }

        let message = rx(r#"(?i)id=["']msg["'][^>]*>([\s\S]*?)</span>"#)
            .captures(&response.text)
            .and_then(|captures| captures.get(1))
            .map(|item| html_text(item.as_str()))
            .filter(|value| !value.is_empty())
            .or_else(|| {
                rx(r#"(?i)class=["'][^"']*auth_error[^"']*["'][^>]*>([\s\S]*?)</"#)
                    .captures(&response.text)
                    .and_then(|captures| captures.get(1))
                    .map(|item| html_text(item.as_str()))
                    .filter(|value| !value.is_empty())
            });
        Err(credentials(&clipped(
            message.as_deref().unwrap_or("统一身份认证登录失败，请核对学号与统一身份认证密码。"),
            200,
        )))
    }

    fn need_captcha(&mut self, login_url: &str, student_id: &str) -> bool {
        let base = login_url.split('?').next().unwrap_or(login_url).trim_end_matches("/login");
        let url = format!(
            "{}/checkNeedCaptcha.htl?username={}&_={}",
            base,
            encode(student_id),
            Local::now().timestamp_millis()
        );
        match self.get(&url, Some(login_url), true) {
            Ok(page) if page.status == 200 => {
                page.text.contains("\"isNeed\":true") || rx(r#""isNeed"\s*:\s*true"#).is_match(&page.text)
            }
            _ => false,
        }
    }

    fn promote(&mut self, start: &str) -> Result<(), Error> {
        let mut current = start.to_string();
        for _ in 0..6 {
            let page = self.get(&current, None, false)?;
            if current.contains("/token-login") {
                return Ok(());
            }
            let Some(location) = page.location else { return Ok(()) };
            current = resolve(&current, &location)?;
        }
        Ok(())
    }

    // 通过 WebVPN 单点登录接入教务系统。
    fn connect_eams(&mut self) -> Result<(), Error> {
        let base = vpn_url(JWNEW_HOST, "/eams")?;
        let (home, page) = self.follow(&format!("{base}/unifiedLogin.action"), None, 12)?;
        if !(home.contains("eams") && page.status < 400 && !home.contains("/login")) {
            return Err(context("教务系统单点登录失败，请稍后重试。"));
        }
        self.eams_base = Some(match home.find("/eams") {
            Some(index) => home[..index + 5].to_string(),
            None => base,
        });
        Ok(())
    }

    fn base(&self) -> Result<String, Error> {
        self.eams_base.clone().ok_or_else(|| context("教务会话尚未建立，请重新同步。"))
    }

    fn fetch_profile(&mut self, student_id: &str) -> Profile {
        let mut profile = Profile { student_id: student_id.to_string(), ..Default::default() };
        let Ok(base) = self.base() else { return profile };
        let Ok((_, page)) = self.follow(&format!("{base}/stdDetail.action"), None, 8) else { return profile };
        profile.name = extract_field(&page.text, "姓名");
        profile.college = extract_field(&page.text, "院系");
        profile.major = extract_field(&page.text, "专业");
        profile.class_name = extract_field(&page.text, "行政班级");
        profile
    }

    fn fetch_entry(&mut self) -> Result<Entry, Error> {
        let base = self.base()?;
        let (_, page) = self.follow(&format!("{base}/courseTableForStd.action"), None, 8)?;
        let html = &page.text;
        let tag_id = rx(r#"(?i)id=["'](semesterBar\d+Semester)["']"#)
            .captures(html)
            .and_then(|captures| captures.get(1))
            .map(|item| item.as_str().to_string())
            .unwrap_or_else(|| "semesterBar".into());
        let default_semester_id = [
            r#"(?i)semesterCalendar\([^)]*value\s*:\s*["']?(\d+)["']?"#,
            r#"(?i)getSemesters\([^)]*value\s*:\s*["']?(\d+)["']?"#,
            r#"(?i)name=["']semester\.id["'][^>]*value=["'](\d+)["']"#,
            r#"(?i)value=["'](\d+)["'][^>]*name=["']semester\.id["']"#,
        ]
        .iter()
        .find_map(|pattern| rx(pattern).captures(html).and_then(|captures| captures.get(1)).map(|item| item.as_str().to_string()))
        .unwrap_or_default();
        let ids = [
            r#"(?i)addInput\(form,\s*["']ids["'],\s*["'](\d+)["']\)"#,
            r#"(?i)name=["']ids["'][^>]*value=["'](\d+)["']"#,
        ]
        .iter()
        .find_map(|pattern| rx(pattern).captures(html).and_then(|captures| captures.get(1)).map(|item| item.as_str().to_string()))
        .unwrap_or_default();
        if ids.is_empty() {
            return Err(context("未能获取到学生排课标识（ids）。"));
        }
        Ok(Entry { tag_id, default_semester_id, ids })
    }

    fn fetch_semesters(&mut self, entry: &Entry) -> Result<(String, Vec<Semester>), Error> {
        let base = self.base()?;
        let body = form(&[
            ("tagId", &entry.tag_id),
            ("dataType", "semesterCalendar"),
            ("value", &entry.default_semester_id),
            ("empty", "false"),
        ]);
        let page = self.post(&format!("{base}/dataQuery.action"), body, Some(&format!("{base}/courseTableForStd.action")), true)?;
        let current = rx(r#"(?i)semesterId:\s*["']?(\d+)["']?"#)
            .captures(&page.text)
            .and_then(|captures| captures.get(1))
            .map(|item| item.as_str().to_string())
            .filter(|value| !value.is_empty())
            .unwrap_or_else(|| entry.default_semester_id.clone());

        let mut semesters = Vec::new();
        for captures in rx(r#"(?i)\{[^}]*id:\s*(\d+)[^}]*schoolYear:\s*["']([^"']+)["'][^}]*name:\s*["']([^"']+)["'][^}]*\}"#).captures_iter(&page.text) {
            let id = captures.get(1).map(|item| item.as_str().to_string()).unwrap_or_default();
            let school_year = captures.get(2).map(|item| item.as_str().to_string()).unwrap_or_default();
            let name = captures.get(3).map(|item| item.as_str().to_string()).unwrap_or_default();
            if id.is_empty() || school_year.is_empty() || name.is_empty() {
                continue;
            }
            semesters.push(Semester {
                id,
                label: format!("{school_year}学年 第{name}学期"),
                school_year,
                name,
            });
        }
        semesters.sort_by(|left, right| {
            right
                .school_year
                .cmp(&left.school_year)
                .then_with(|| right.name.parse::<i32>().unwrap_or(0).cmp(&left.name.parse::<i32>().unwrap_or(0)))
                .then_with(|| right.id.parse::<i32>().unwrap_or(0).cmp(&left.id.parse::<i32>().unwrap_or(0)))
        });
        semesters.truncate(16);
        let selected = if semesters.iter().any(|item| item.id == current) {
            current
        } else if !entry.default_semester_id.is_empty() {
            entry.default_semester_id.clone()
        } else {
            semesters.first().map(|item| item.id.clone()).unwrap_or_default()
        };
        if selected.is_empty() {
            return Err(context("未能获取到教务学期信息。"));
        }
        Ok((selected, semesters))
    }

    fn fetch_table(&mut self, entry: &Entry, semester_id: &str) -> Result<Vec<RawEams>, Error> {
        let base = self.base()?;
        let body = form(&[
            ("ignoreHead", "1"),
            ("setting.kind", "std"),
            ("startWeek", "1"),
            ("project.id", "1"),
            ("semester.id", semester_id),
            ("ids", &entry.ids),
        ]);
        let page = self.post(
            &format!("{base}/courseTableForStd!courseTable.action"),
            body,
            Some(&format!("{base}/courseTableForStd.action")),
            false,
        )?;
        let html = &page.text;
        if !html.contains("TaskActivity") && !html.contains("未安排时间任务列表") {
            if html.trim().is_empty() || html.contains("loginForm") || html.contains("cqustadminweb=1") {
                return Err(context("教务会话已失效或课表页面不可用。"));
            }
            return Ok(Vec::new());
        }
        let mut courses = parse_task_activity(html);
        let plan = self.fetch_plan(semester_id).unwrap_or_default();
        for course in &mut courses {
            if let Some(info) = plan.get(&course.code) {
                if !info.category.is_empty() {
                    course.category = info.category.clone();
                }
                if !info.credit.is_empty() {
                    course.credit = info.credit.clone();
                }
                if info.students > 0 {
                    course.students = info.students;
                }
            }
        }
        Ok(courses)
    }

    fn fetch_plan(&mut self, semester_id: &str) -> Result<HashMap<String, PlanInfo>, Error> {
        let base = self.base()?;
        let body = form(&[("setting.forSemester", "1"), ("semester.id", semester_id)]);
        let page = self.post(&format!("{base}/teachingPlanSearchForStu!courseTable.action"), body, None, false)?;
        let mut plan = HashMap::new();
        for row in rx(r"(?is)<tr[^>]*>([\s\S]*?)</tr>").captures_iter(&page.text) {
            let Some(content) = row.get(1) else { continue };
            let cells = rx(r"(?is)<td[^>]*>([\s\S]*?)</td>")
                .captures_iter(content.as_str())
                .map(|cell| cell.get(1).map(|item| html_text(item.as_str())).unwrap_or_default())
                .collect::<Vec<_>>();
            if cells.len() >= 8 && !cells[2].is_empty() {
                plan.insert(
                    cells[2].clone(),
                    PlanInfo {
                        category: clipped(&cells[4], 80),
                        credit: clipped(&cells[5], 40),
                        students: cells[7].parse::<u32>().unwrap_or(0).min(100_000),
                    },
                );
            }
        }
        Ok(plan)
    }

    // 抓取实践教学系统课表；失败时由调用方降级为仅理论课。
    fn fetch_practical(&mut self, target_term: Option<&str>) -> Result<Vec<RawPractical>, Error> {
        let base = vpn_url(SJJX_HOST, "")?;
        let sso_entry = format!("{base}/cqust_sso");
        let (sso_url, sso_page) = self.follow(&sso_entry, None, 12)?;
        if sso_page.status != 200 {
            return Err(context("实践教学单点登录失败。"));
        }
        let actual_base = if sso_url.contains("web.cqust.edu.cn") {
            base.clone()
        } else {
            let parsed = Url::parse(&sso_url).map_err(|_| context("实践教学返回了无效地址。"))?;
            format!("{}://{}", parsed.scheme(), parsed.host_str().unwrap_or(""))
        };

        let step2_path = rx(r#"(?:self|window)\.location\s*=\s*['"]([^'"]+)['"]"#)
            .captures(&sso_page.text)
            .and_then(|captures| captures.get(1))
            .map(|item| item.as_str().to_string())
            .ok_or_else(|| context("未能提取实践系统跳转票据。"))?;
        let step2_url = resolve(&sso_url, &step2_path)?;
        let (step2_actual, step2_page) = self.follow(&step2_url, Some(&sso_url), 12)?;
        let step3 = rx(r#"(?:self|window)\.location\s*=\s*['"]([^'"]+)['"]"#)
            .captures(&step2_page.text)
            .and_then(|captures| captures.get(1))
            .map(|item| item.as_str().to_string());
        let final_login = match step3 {
            Some(path) if !path.contains("skin=") => resolve(&step2_actual, &path)?,
            _ => format!("{actual_base}/StuExpbook/login.jsp"),
        };
        self.follow(&final_login, Some(&step2_actual), 12)?;

        let book_url = format!("{actual_base}/StuExpbook/teach/bookResult.jsp");
        let (_, init_page) = self.follow(&book_url, Some(&final_login), 10)?;
        let (current_term, _) = practical_semesters(&init_page.text);
        let term = target_term
            .map(str::to_string)
            .filter(|value| !value.is_empty())
            .or(current_term)
            .ok_or_else(|| context("未能提取实践教学有效学期。"))?;
        let code_map = practical_code_map(&init_page.text);

        let first_body = form(&[
            ("currYearterm", &term),
            ("currTeachCourseCode", "%"),
            ("currWeek", "%"),
            ("page", "1"),
        ]);
        let first_page = self.post(&book_url, first_body, Some(&book_url), false)?;
        let mut rows = Vec::new();
        extract_practical_rows(&first_page.text, &mut rows);
        let (total, size) = practical_page_info(&first_page.text);
        if size > 0 && total > size {
            let pages = (total + size - 1) / size;
            if pages > 30 {
                return Err(context("实践课表分页过多，已停止处理。"));
            }
            for page_no in 2..=pages {
                let body = form(&[
                    ("currYearterm", &term),
                    ("currTeachCourseCode", "%"),
                    ("currWeek", "%"),
                    ("page", &page_no.to_string()),
                ]);
                let page = self.post(&book_url, body, Some(&book_url), false)?;
                extract_practical_rows(&page.text, &mut rows);
                if rows.len() > MAX_SLOTS {
                    return Err(context("实践课表条目过多，已停止处理。"));
                }
            }
        }
        let map = if code_map.is_empty() { practical_code_map(&first_page.text) } else { code_map };
        Ok(parse_practical_rows(&rows, &map))
    }
}

struct Entry {
    tag_id: String,
    default_semester_id: String,
    ids: String,
}

struct PlanInfo {
    category: String,
    credit: String,
    students: u32,
}

#[derive(Clone)]
struct RawEams {
    code: String,
    name: String,
    credit: String,
    category: String,
    students: u32,
    teacher: String,
    location: String,
    day: u8,
    start: u8,
    end: u8,
    weeks: Vec<u8>,
}

#[derive(Clone)]
struct RawPractical {
    code: String,
    name: String,
    project: String,
    day: u8,
    start: u8,
    end: u8,
    weeks: Vec<u8>,
    location: String,
    teacher: String,
}

// 提取隐藏表单字段。
fn input_value(html: &str, name: &str) -> Option<String> {
    let escaped = regex::escape(name);
    for pattern in [
        format!(r#"(?i)<input[^>]+id=["']{escaped}["'][^>]+value=["']([^"']*)["']"#),
        format!(r#"(?i)<input[^>]+value=["']([^"']*)["'][^>]+id=["']{escaped}["']"#),
        format!(r#"(?i)<input[^>]+name=["']{escaped}["'][^>]+value=["']([^"']*)["']"#),
    ] {
        if let Some(value) = rx(&pattern).captures(html).and_then(|captures| captures.get(1)) {
            return Some(value.as_str().to_string());
        }
    }
    None
}

fn extract_field(html: &str, label: &str) -> String {
    let pattern = format!(r#"{}\s*：\s*</td>\s*<td[^>]*>\s*([^<]+)"#, regex::escape(label));
    rx(&pattern)
        .captures(html)
        .and_then(|captures| captures.get(1))
        .map(|item| clipped(&html_text(item.as_str()), 80))
        .unwrap_or_default()
}

// CAS 密码 AES-128-CBC/PKCS7 加密。
fn encrypt_password(password: &str, salt: &str) -> Result<String, Error> {
    encrypt_password_with_iv(password, salt, &random_string(16))
}

fn encrypt_password_with_iv(password: &str, salt: &str, iv: &str) -> Result<String, Error> {
    use aes::Aes128;
    use cipher::block_padding::NoPadding;
    use cipher::{BlockEncryptMut, KeyIvInit};
    type Aes128CbcEnc = cbc::Encryptor<Aes128>;

    let key = salt.as_bytes();
    if key.len() != 16 || iv.len() != 16 {
        return Err(context("统一身份认证页面密钥格式异常。"));
    }
    let prefix = random_string(64);
    let mut plain = format!("{prefix}{password}").into_bytes();
    let padding = 16 - plain.len() % 16;
    plain.extend(std::iter::repeat(padding as u8).take(padding));
    let message_len = plain.len();
    let mut buffer = plain;
    let cipher = Aes128CbcEnc::new_from_slices(key, iv.as_bytes()).map_err(|_| error("统一身份认证密码加密失败。"))?;
    let encrypted = cipher
        .encrypt_padded_mut::<NoPadding>(&mut buffer, message_len)
        .map_err(|_| error("统一身份认证密码加密失败。"))?;
    let encoded = base64::engine::general_purpose::STANDARD.encode(encrypted);
    buffer.zeroize();
    Ok(encoded)
}

fn random_string(length: usize) -> String {
    let mut value = String::with_capacity(length);
    while value.len() < length {
        let mut random = [0u8; 32];
        if getrandom::fill(&mut random).is_err() {
            continue;
        }
        for byte in random {
            value.push(CAS_CHARS[(byte as usize) % CAS_CHARS.len()] as char);
            if value.len() == length {
                break;
            }
        }
    }
    value
}

// 解析课表页面中的 TaskActivity 网格脚本并合并相邻节次。
fn parse_task_activity(html: &str) -> Vec<RawEams> {
    let Some(script) = rx(r"(?is)var\s+table0\s*=\s*new\s+CourseTable[\s\S]*?</script>").find(html) else {
        return Vec::new();
    };
    let text = script.as_str();
    let activity_regex = rx(r#"(?i)new\s+TaskActivity\((.*?)\)\s*;"#);
    let index_regex = rx(r"(?i)index\s*=\s*(\d+)\s*\*\s*unitCount\s*\+\s*(\d+);");
    let code_suffix = rx(r"\(([^)]+)\)$");
    let clean_suffix = rx(r"\([A-Za-z0-9._-]+\)$");

    let activities = activity_regex
        .captures_iter(text)
        .filter_map(|captures| {
            let args = js_string_args(captures.get(1)?.as_str());
            if args.len() < 7 {
                return None;
            }
            let raw_name = args[3].clone();
            let name = clean_suffix.replace(&raw_name, "").trim().to_string();
            let name = if name.is_empty() { raw_name.clone() } else { name };
            let code = code_suffix
                .captures(&raw_name)
                .and_then(|item| item.get(1))
                .map(|item| item.as_str().to_string())
                .or_else(|| {
                    code_suffix
                        .captures(&args[2])
                        .and_then(|item| item.get(1))
                        .map(|item| item.as_str().to_string())
                })
                .unwrap_or_default();
            Some((
                captures.get(0).map(|item| item.start()).unwrap_or(0),
                RawActivity {
                    name,
                    code,
                    teacher: clipped(&args[1], 80),
                    location: clipped(&args[5], 80),
                    weeks: parse_week_bits(&args[6]),
                },
            ))
        })
        .collect::<Vec<_>>();

    let mut slots: Vec<RawSlot> = Vec::new();
    let mut current: Option<&RawActivity> = None;
    let mut activity_index = 0;
    for captures in index_regex.captures_iter(text) {
        let position = captures.get(0).map(|item| item.start()).unwrap_or(0);
        while activity_index < activities.len() && activities[activity_index].0 < position {
            current = Some(&activities[activity_index].1);
            activity_index += 1;
        }
        let Some(activity) = current else { continue };
        let day = captures.get(1).and_then(|item| item.as_str().parse::<u8>().ok()).unwrap_or(0) + 1;
        let section = captures.get(2).and_then(|item| item.as_str().parse::<u8>().ok()).unwrap_or(0) + 1;
        if day > 7 || section > 11 {
            continue;
        }
        slots.push(RawSlot {
            name: activity.name.clone(),
            code: activity.code.clone(),
            teacher: activity.teacher.clone(),
            location: activity.location.clone(),
            day,
            section,
            weeks: activity.weeks.clone(),
        });
    }
    merge_slots(slots)
}

struct RawActivity {
    name: String,
    code: String,
    teacher: String,
    location: String,
    weeks: Vec<u8>,
}

struct RawSlot {
    name: String,
    code: String,
    teacher: String,
    location: String,
    day: u8,
    section: u8,
    weeks: Vec<u8>,
}

fn merge_slots(slots: Vec<RawSlot>) -> Vec<RawEams> {
    let mut groups: Vec<(String, Vec<RawSlot>)> = Vec::new();
    let mut index: HashMap<String, usize> = HashMap::new();
    for slot in slots {
        let key = format!(
            "{}|{}|{}|{}|{}|{}",
            slot.name,
            slot.code,
            slot.teacher,
            slot.location,
            slot.day,
            slot.weeks.iter().map(|item| item.to_string()).collect::<Vec<_>>().join(",")
        );
        match index.get(&key) {
            Some(position) => groups[*position].1.push(slot),
            None => {
                index.insert(key, groups.len());
                groups.push((String::new(), vec![slot]));
            }
        }
    }

    let mut courses = Vec::new();
    for (_, group) in groups {
        let mut sections = group.iter().map(|slot| slot.section).collect::<Vec<_>>();
        sections.sort_unstable();
        sections.dedup();
        let Some(first) = group.first() else { continue };
        let mut start = sections[0];
        let mut end = start;
        for section in sections.iter().skip(1) {
            if *section == end + 1 {
                end = *section;
            } else {
                courses.push(course_from_slot(first, start, end));
                start = *section;
                end = *section;
            }
        }
        courses.push(course_from_slot(first, start, end));
    }
    courses
}

fn course_from_slot(slot: &RawSlot, start: u8, end: u8) -> RawEams {
    RawEams {
        code: slot.code.clone(),
        name: slot.name.clone(),
        credit: String::new(),
        category: String::new(),
        students: 0,
        teacher: slot.teacher.clone(),
        location: slot.location.clone(),
        day: slot.day,
        start,
        end,
        weeks: slot.weeks.clone(),
    }
}

// 提取 JS 字符串参数，支持转义引号。
fn js_string_args(input: &str) -> Vec<String> {
    let chars = input.chars().collect::<Vec<_>>();
    let mut values = Vec::new();
    let mut index = 0;
    while index < chars.len() {
        let quote = chars[index];
        if quote != '"' && quote != '\'' {
            index += 1;
            continue;
        }
        index += 1;
        let mut value = String::new();
        while index < chars.len() {
            let current = chars[index];
            if current == '\\' && index + 1 < chars.len() {
                let next = chars[index + 1];
                value.push(match next {
                    'n' => '\n',
                    'r' => '\r',
                    't' => '\t',
                    other => other,
                });
                index += 2;
                continue;
            }
            if current == quote {
                index += 1;
                break;
            }
            value.push(current);
            index += 1;
        }
        values.push(value);
    }
    values
}

// 解析周次位串，首字符为学期标记，不参与周次计算。
fn parse_week_bits(bits: &str) -> Vec<u8> {
    let mut weeks = Vec::new();
    for (index, value) in bits.chars().enumerate().skip(1) {
        if value == '1' && index <= MAX_WEEKS as usize {
            weeks.push(index as u8);
        }
    }
    weeks.dedup();
    weeks
}

fn parse_week_ranges(text: &str) -> Vec<u8> {
    let mut weeks = Vec::new();
    for segment in text.split([',', '，', '、']) {
        let segment = segment.trim().replace(['~', '—', '至'], "-");
        if segment.is_empty() {
            continue;
        }
        if let Some((start, end)) = segment.split_once('-') {
            if let (Ok(start), Ok(end)) = (start.trim().parse::<u8>(), end.trim().parse::<u8>()) {
                if start >= 1 && start <= end && end <= MAX_WEEKS {
                    weeks.extend(start..=end);
                }
            }
        } else if let Ok(week) = segment.parse::<u8>() {
            if (1..=MAX_WEEKS).contains(&week) {
                weeks.push(week);
            }
        }
    }
    weeks.sort_unstable();
    weeks.dedup();
    weeks
}

struct PracticalSemester {
    year_term: String,
    selected: bool,
}

fn practical_semesters(html: &str) -> (Option<String>, Vec<PracticalSemester>) {
    let Some(select) = rx(r#"(?is)<select[^>]*name=["']?currYearterm["']?[^>]*>([\s\S]*?)</select>"#).captures(html) else {
        return (None, Vec::new());
    };
    let Some(content) = select.get(1) else { return (None, Vec::new()) };
    let mut list = Vec::new();
    for captures in rx(r"(?is)<option\s+([^>]*?)>([^<]*)</option>").captures_iter(content.as_str()) {
        let attributes = captures.get(1).map(|item| item.as_str()).unwrap_or_default();
        let value = rx(r#"(?i)value=["']?([^"'\s>]+)"#)
            .captures(attributes)
            .and_then(|item| item.get(1))
            .map(|item| item.as_str().to_string())
            .unwrap_or_default();
        if value.is_empty() {
            continue;
        }
        list.push(PracticalSemester {
            year_term: value,
            selected: attributes.to_lowercase().contains("selected"),
        });
    }
    let mut current = list.iter().find(|item| item.selected).map(|item| item.year_term.clone());
    if current.is_none() {
        current = list.first().map(|item| item.year_term.clone());
    }
    (current, list)
}

fn practical_code_map(html: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    let Some(select) = rx(r#"(?is)<select[^>]*name=["']?currTeachCourseCode["']?[^>]*>([\s\S]*?)</select>"#).captures(html) else {
        return map;
    };
    let Some(content) = select.get(1) else { return map };
    for captures in rx(r#"(?is)<option\s+[^>]*value=["']?([^"'\s>]+)["']?[^>]*>([^<]*)</option>"#).captures_iter(content.as_str()) {
        let code = captures.get(1).map(|item| item.as_str().trim().to_string()).unwrap_or_default();
        let text = captures.get(2).map(|item| html_text(item.as_str())).unwrap_or_default();
        if code.is_empty() || code == "%" || text.is_empty() {
            continue;
        }
        let name = text.split_once('-').map(|(_, value)| value.trim().to_string()).unwrap_or(text);
        if name.is_empty() {
            continue;
        }
        map.insert(name.clone(), code.clone());
        let stripped = rx(r"^[（(].*?[）)]").replace(&name, "").trim().to_string();
        if !stripped.is_empty() && stripped != name {
            map.entry(stripped).or_insert(code);
        }
    }
    map
}

fn practical_page_info(html: &str) -> (usize, usize) {
    rx(r"createPageBar\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)")
        .captures(html)
        .map(|captures| {
            let total = captures.get(1).and_then(|item| item.as_str().parse().ok()).unwrap_or(0);
            let size = captures.get(2).and_then(|item| item.as_str().parse().ok()).unwrap_or(10);
            (total, size)
        })
        .unwrap_or((0, 10))
}

fn extract_practical_rows(html: &str, output: &mut Vec<Vec<String>>) {
    for row in rx(r"(?is)<tr[^>]*>([\s\S]*?)</tr>").captures_iter(html) {
        let Some(content) = row.get(1) else { continue };
        if content.as_str().to_lowercase().contains("<th") {
            continue;
        }
        let cells = rx(r"(?is)<td[^>]*>([\s\S]*?)</td>")
            .captures_iter(content.as_str())
            .map(|cell| cell.get(1).map(|item| html_text(item.as_str())).unwrap_or_default())
            .collect::<Vec<_>>();
        if cells.len() >= 10 && !cells[0].is_empty() && cells[0] != "课程名称" {
            output.push(cells);
        }
    }
}

struct ParsedTime {
    day: u8,
    start: u8,
    end: u8,
    weeks: Vec<u8>,
}

fn parse_time_text(text: &str) -> Option<ParsedTime> {
    let captures = rx(r"([\d,\-~—至]+)\s*周\s*(?:星期|周)\s*([一二三四五六日天])\s*(\d+)\s*[-~—至]\s*(\d+)\s*节")
        .captures(text)?;
    let weeks = parse_week_ranges(captures.get(1)?.as_str());
    if weeks.is_empty() {
        return None;
    }
    let day = match captures.get(2)?.as_str().chars().next()? {
        '一' => 1,
        '二' => 2,
        '三' => 3,
        '四' => 4,
        '五' => 5,
        '六' => 6,
        '日' | '天' => 7,
        _ => return None,
    };
    let start = captures.get(3)?.as_str().parse::<u8>().ok()?;
    let end = captures.get(4)?.as_str().parse::<u8>().ok()?;
    if start < 1 || end > 11 || start > end {
        return None;
    }
    Some(ParsedTime { day, start, end, weeks })
}

fn parse_practical_rows(rows: &[Vec<String>], codes: &HashMap<String, String>) -> Vec<RawPractical> {
    let mut items = Vec::new();
    for row in rows {
        let Some(course_name) = row.first().filter(|value| !value.is_empty()) else { continue };
        let project = row.get(1).cloned().unwrap_or_default();
        let time_text = row.get(2).cloned().unwrap_or_default();
        let location = row.get(3).cloned().unwrap_or_default();
        let teacher = row.get(4).cloned().unwrap_or_default();
        let Some(time) = parse_time_text(&time_text) else { continue };
        let stripped = rx(r"^[（(].*?[）)]").replace(course_name, "").trim().to_string();
        let code = codes
            .get(course_name)
            .or_else(|| codes.get(&stripped))
            .cloned()
            .unwrap_or_default();
        items.push(RawPractical {
            code,
            name: clipped(course_name, 80),
            project: clipped(&project, 80),
            day: time.day,
            start: if time.start == 10 && time.end == 11 { 9 } else { time.start },
            end: if time.start == 10 && time.end == 11 { 10 } else { time.end },
            weeks: time.weeks,
            location: normalize_location(&location),
            teacher: clipped(&teacher, 80),
        });
    }
    items
}

// 将虎溪校区字母楼栋转换为标准楼名。
fn normalize_location(raw: &str) -> String {
    let trimmed = raw.trim();
    if trimmed.is_empty() || trimmed == "待定" || trimmed.contains("金凤") {
        return clipped(trimmed, 80);
    }
    let clean = trimmed.replace("虎溪校区", "");
    let clean = clean.trim();
    let Some(captures) = rx(r"^([A-Za-z])(?:栋)?([A-Za-z]?\d+.*)$").captures(clean) else {
        return clipped(trimmed, 80);
    };
    let letter = captures.get(1).map(|item| item.as_str().to_uppercase()).unwrap_or_default();
    let raw_room = captures.get(2).map(|item| item.as_str()).unwrap_or_default();
    let room = raw_room
        .strip_prefix(letter.as_str())
        .or_else(|| if raw_room.len() > 1 && raw_room[..1].eq_ignore_ascii_case(&letter) { Some(&raw_room[1..]) } else { None })
        .unwrap_or(raw_room);
    let building = match letter.as_str() {
        "A" | "B" | "C" | "D" => "教学主楼",
        "K" => "笃行楼",
        "L" => "砺志楼",
        "M" => "知行楼",
        "E" | "F" => "博学楼",
        "G" | "H" => "厚德楼",
        "I" => "逸夫楼",
        _ => "",
    };
    if building.is_empty() {
        return clipped(trimmed, 80);
    }
    clipped(&format!("{building} {letter}{room}"), 80)
}

// 合并理论课与实践课，统一分配配色并生成可展示的课程时段。
fn build_slots(theory: &[RawEams], practical: &[RawPractical]) -> Vec<Slot> {
    let mut colors: HashMap<String, u8> = HashMap::new();
    let mut next_color: u8 = 0;
    let mut theory_names: Vec<(String, String)> = Vec::new();
    let mut theory_index: HashMap<String, usize> = HashMap::new();
    for course in theory {
        if !theory_index.contains_key(&course.name) {
            theory_index.insert(course.name.clone(), theory_names.len());
            theory_names.push((course.name.clone(), course.code.clone()));
        }
    }

    let mut slots = Vec::new();
    for course in theory {
        if course.day < 1 || course.day > 7 || course.start < 1 || course.end > 11 || course.start > course.end {
            continue;
        }
        let color = allocate_color(&mut colors, &mut next_color, &course.name);
        slots.push(Slot {
            name: course.name.clone(),
            code: course.code.clone(),
            course_type: "theory".into(),
            teacher: course.teacher.clone(),
            location: course.location.clone(),
            project: String::new(),
            day: course.day,
            start: course.start,
            end: course.end,
            weeks: course.weeks.clone(),
            credit: course.credit.clone(),
            category: course.category.clone(),
            color,
        });
    }

    let mut theory_by_code: HashMap<String, (String, String, String, u8)> = HashMap::new();
    let mut theory_by_name: HashMap<String, (String, String, String, u8)> = HashMap::new();
    for (name, code) in &theory_names {
        let Some(course) = theory.iter().find(|course| &course.name == name && &course.code == code) else { continue };
        let color = colors.get(name).copied().unwrap_or(0);
        let info = (course.code.clone(), course.credit.clone(), course.category.clone(), color);
        theory_by_name.insert(name.clone(), info.clone());
        if code.is_empty() {
            continue;
        }
        theory_by_code.insert(code.clone(), info.clone());
        let base = code.split('.').next().unwrap_or(code).trim().to_string();
        if !base.is_empty() {
            theory_by_code.entry(base).or_insert(info);
        }
    }

    for item in practical {
        if item.day < 1 || item.day > 7 || item.start < 1 || item.end > 11 || item.start > item.end {
            continue;
        }
        let base = item.code.split('.').next().unwrap_or(&item.code).trim().to_string();
        let matched = theory_by_code
            .get(&item.code)
            .or_else(|| theory_by_code.get(&base))
            .or_else(|| theory_by_name.get(&item.name));
        let color = match matched {
            Some((_, _, _, color)) => *color,
            None => allocate_color(&mut colors, &mut next_color, &item.name),
        };
        slots.push(Slot {
            name: item.name.clone(),
            code: if item.code.is_empty() { matched.map(|value| value.0.clone()).unwrap_or_default() } else { base.clone() },
            course_type: "practical".into(),
            teacher: item.teacher.clone(),
            location: item.location.clone(),
            project: item.project.clone(),
            day: item.day,
            start: item.start,
            end: item.end,
            weeks: item.weeks.clone(),
            credit: matched.map(|value| value.1.clone()).unwrap_or_default(),
            category: matched
                .map(|value| value.2.clone())
                .filter(|value| !value.is_empty())
                .unwrap_or_else(|| "实践教学".into()),
            color,
        });
    }

    slots.sort_by(|left, right| {
        left.day
            .cmp(&right.day)
            .then_with(|| left.start.cmp(&right.start))
            .then_with(|| left.end.cmp(&right.end))
            .then_with(|| left.name.cmp(&right.name))
    });
    slots
}

fn allocate_color(colors: &mut HashMap<String, u8>, next: &mut u8, key: &str) -> u8 {
    if let Some(color) = colors.get(key) {
        return *color;
    }
    let color = *next % COLOR_COUNT;
    *next = next.wrapping_add(1);
    colors.insert(key.to_string(), color);
    color
}

#[cfg(test)]
mod tests {
    use super::*;
    use cipher::block_padding::NoPadding;
    use cipher::{BlockDecryptMut, KeyIvInit};

    #[test]
    fn week_bits_ignore_leading_marker() {
        assert_eq!(parse_week_bits("0110"), vec![1, 2]);
        assert_eq!(parse_week_bits("0"), Vec::<u8>::new());
        assert_eq!(parse_week_bits("0101"), vec![1, 3]);
    }

    #[test]
    fn week_ranges_support_lists_and_odd_weeks() {
        assert_eq!(parse_week_ranges("1-3,5"), vec![1, 2, 3, 5]);
        assert_eq!(parse_week_ranges("2，4、6-7"), vec![2, 4, 6, 7]);
        assert_eq!(parse_week_ranges("1~3,5至6"), vec![1, 2, 3, 5, 6]);
        assert_eq!(parse_week_ranges("0-40"), Vec::<u8>::new());
    }

    #[test]
    fn merges_adjacent_task_activity_sections() {
        let html = r#"<script>
var table0 = new CourseTable();
table0.addCourse(new TaskActivity("1", "教师甲", "x", "高等数学(A123)", "", "1-101", "011111111111111111111"));
index = 0 * unitCount + 0;
index = 0 * unitCount + 1;
index = 1 * unitCount + 2;
</script>"#;
        let courses = parse_task_activity(html);
        assert_eq!(courses.len(), 2);
        let first = courses.iter().find(|course| course.day == 1).unwrap();
        assert_eq!((first.start, first.end), (1, 2));
        assert_eq!(first.name, "高等数学");
        assert_eq!(first.code, "A123");
        assert_eq!(first.weeks, vec![1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]);
        let second = courses.iter().find(|course| course.day == 2).unwrap();
        assert_eq!((second.start, second.end), (3, 3));
    }

    #[test]
    fn parses_practical_time_and_location() {
        let time = parse_time_text("1-16周 星期一 3-4节").unwrap();
        assert_eq!((time.day, time.start, time.end), (1, 3, 4));
        assert_eq!(time.weeks.len(), 16);
        assert_eq!(normalize_location("虎溪校区K302"), "笃行楼 K302");
        assert_eq!(normalize_location("虎溪校区LL402"), "砺志楼 L402");
        assert_eq!(normalize_location("待定"), "待定");
    }

    #[test]
    fn parses_practical_rows_with_codes_and_evening_correction() {
        let rows = vec![vec![
            "高等数学".to_string(),
            "实验一".to_string(),
            "3-4周 星期一 10-11节".to_string(),
            "虎溪校区K302".to_string(),
            "教师乙".to_string(),
            String::new(),
            String::new(),
            String::new(),
            String::new(),
            "20".to_string(),
        ]];
        let mut codes = HashMap::new();
        codes.insert("高等数学".to_string(), "A1".to_string());
        let items = parse_practical_rows(&rows, &codes);
        assert_eq!(items.len(), 1);
        assert_eq!(items[0].code, "A1");
        assert_eq!((items[0].start, items[0].end), (9, 10));
        assert_eq!(items[0].weeks, vec![3, 4]);
        assert_eq!(items[0].location, "笃行楼 K302");
        assert_eq!(items[0].project, "实验一");
    }

    #[test]
    fn reads_practical_semester_and_code_selects() {
        let html = r#"<select name="currYearterm"><option value="2024-2025-2">2024-2025-2</option><option value="2025-2026-1" selected>2025-2026-1</option></select>
<select name="currTeachCourseCode"><option value="%">全部</option><option value="A1">A1-高等数学</option></select>"#;
        let (current, list) = practical_semesters(html);
        assert_eq!(current.as_deref(), Some("2025-2026-1"));
        assert_eq!(list.len(), 2);
        let codes = practical_code_map(html);
        assert_eq!(codes.get("高等数学").map(String::as_str), Some("A1"));
    }

    #[test]
    fn js_string_args_handle_escapes() {
        let args = js_string_args(r#""教师\"甲", 'x', "b", "课程(A1)""#);
        assert_eq!(args, vec!["教师\"甲", "x", "b", "课程(A1)"]);
    }

    #[test]
    #[ignore = "explicit read-only school network test; set CQUST_TEST_STUDENT and CQUST_TEST_PASSWORD"]
    fn live_sync_reads_real_timetable() {
        let Ok(student) = std::env::var("CQUST_TEST_STUDENT") else { return };
        let Ok(password) = std::env::var("CQUST_TEST_PASSWORD") else { return };
        let password = Zeroizing::new(password);
        let store = fetch_store(None, &student, &password).expect("live schedule sync");
        assert!(!store.semester_id.is_empty());
        assert!(!store.slots.is_empty());
        assert!(valid_store(&store));
        println!(
            "profile={:?} semester={} label={} courses={} slots={}",
            store.profile.name,
            store.semester_id,
            store.semester_label,
            store.slots.iter().map(|slot| slot.name.clone()).collect::<std::collections::BTreeSet<_>>().len(),
            store.slots.len()
        );
        for slot in store.slots.iter().take(5) {
            println!(
                "  {} day={} {}-{} weeks={:?} place={} teacher={} type={}",
                slot.name, slot.day, slot.start, slot.end, slot.weeks, slot.location, slot.teacher, slot.course_type
            );
        }
        let practical = store.slots.iter().filter(|slot| slot.course_type == "practical").count();
        println!("types: practical={practical} theory={}", store.slots.len() - practical);
        if let Ok(path) = std::env::var("CQUST_TEST_VIEW_OUT") {
            std::fs::write(&path, serde_json::to_vec_pretty(&view(&store)).unwrap()).expect("write view");
            println!("view written to {path}");
        }
    }

    #[test]
    fn cache_roundtrip_keeps_slots_and_start_date() {
        let folder = std::env::temp_dir().join(format!("cqust-schedule-cache-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&folder);
        let schedule = Schedule::new(&folder);
        let mut store = Store {
            synced_at: 1.0,
            semester_id: "561".into(),
            semester_label: "2026-2027学年 第1学期".into(),
            ..Default::default()
        };
        store.slots.push(Slot {
            name: "高等数学".into(),
            day: 1,
            start: 1,
            end: 2,
            weeks: vec![1, 2],
            ..Default::default()
        });
        schedule.save(&store).expect("save");
        *schedule.cache.lock().unwrap() = store;
        assert!(set_start_date(&schedule, json!({"startDate": "2026-09-07"})).is_ok());
        let reloaded = Schedule::new(&folder);
        let cached = reloaded.cache.lock().unwrap();
        assert_eq!(cached.slots.len(), 1);
        assert_eq!(cached.start_date, "2026-09-07");
        assert_eq!(cached.semester_label, "2026-2027学年 第1学期");
        assert!(set_start_date(&reloaded, json!({"startDate": "2026-13-40"})).is_err());
        let _ = std::fs::remove_dir_all(&folder);
    }

    #[test]
    fn accepts_only_school_https_urls() {
        assert!(school_url(&Url::parse("https://web.cqust.edu.cn/http/token/eams").unwrap()));
        assert!(!school_url(&Url::parse("http://web.cqust.edu.cn/login").unwrap()));
        assert!(!school_url(&Url::parse("https://cqust.edu.cn.evil.test/login").unwrap()));
        assert!(!school_url(&Url::parse("https://evil.test/login").unwrap()));
        assert!(!school_url(&Url::parse("https://user@web.cqust.edu.cn/login").unwrap()));
    }

    #[test]
    fn encrypts_password_with_padding() {
        let salt = "0123456789abcdef";
        let iv = "abcdefghijklmnop";
        let encrypted = encrypt_password_with_iv("secret", salt, iv).unwrap();
        let bytes = base64::engine::general_purpose::STANDARD.decode(encrypted).unwrap();
        assert_eq!(bytes.len() % 16, 0);
        type Aes128CbcDec = cbc::Decryptor<aes::Aes128>;
        let mut buffer = bytes.clone();
        let cipher = Aes128CbcDec::new_from_slices(salt.as_bytes(), iv.as_bytes()).unwrap();
        let decrypted = cipher.decrypt_padded_mut::<NoPadding>(&mut buffer).unwrap();
        let text = String::from_utf8_lossy(&decrypted);
        assert!(text[64..].starts_with("secret"));
    }

    #[test]
    fn default_start_date_follows_semester() {
        assert_eq!(default_start_date("2025-2026", "1"), "2025-09-07");
        assert_eq!(default_start_date("2025-2026", "2"), "2026-02-22");
    }

    #[test]
    fn rejects_invalid_cache() {
        let mut store = Store::default();
        store.slots.push(Slot { name: "课程".into(), day: 1, start: 1, end: 2, weeks: vec![1], ..Default::default() });
        assert!(valid_store(&store));
        store.slots[0].weeks = vec![0];
        assert!(!valid_store(&store));
        store.slots[0].weeks = vec![1];
        store.slots[0].day = 8;
        assert!(!valid_store(&store));
    }

    #[test]
    fn builds_colored_slots_and_prefers_theory_metadata() {
        let theory = vec![RawEams {
            code: "A1".into(),
            name: "高等数学".into(),
            credit: "4".into(),
            category: "必修".into(),
            students: 50,
            teacher: "教师甲".into(),
            location: "A101".into(),
            day: 1,
            start: 1,
            end: 2,
            weeks: vec![1, 2],
        }];
        let practical = vec![RawPractical {
            code: String::new(),
            name: "高等数学".into(),
            project: "实验一".into(),
            day: 3,
            start: 3,
            end: 4,
            weeks: vec![3],
            location: "笃行楼 K302".into(),
            teacher: "教师乙".into(),
        }];
        let slots = build_slots(&theory, &practical);
        assert_eq!(slots.len(), 2);
        assert_eq!(slots[0].course_type, "theory");
        assert_eq!(slots[1].course_type, "practical");
        assert_eq!(slots[0].color, slots[1].color);
        assert_eq!(slots[1].code, "A1");
        assert_eq!(slots[1].credit, "4");
        assert_eq!(slots[1].category, "必修");
    }
}