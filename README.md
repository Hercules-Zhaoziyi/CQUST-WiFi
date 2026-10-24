# CQUST Wi-Fi

重庆科技大学校园网与课表客户端。当前 Windows 版本：**v5.3.2**；Android：**1.1.0-beta02**。Windows 使用 **Tauri 2 + Rust**；Android 使用 Kotlin / Jetpack Compose / Miuix。macOS 仍提供此前 v5.1.1 Apple Silicon 安装包，本次未重新构建或验证 Mac。

## v5.3.2 与 Android 1.1.0-beta02

- Windows 课表的账号、更换、同步和设置操作区移至课表最底部。
- 修复 Windows 特别鸣谢的图标与文字错行，保持统一图标列和校徽原尺寸。
- 安卓统一设置、账号和联名关于页；相同学校账号合并展示，不同账号明确标注，设置内不显示底部主导航。
- 安卓移除失效的内嵌认证网页，使用 Wi-Fi 原生探测、明确状态与官方网址校验。
- 安卓主题切换平滑过渡；课表按实际节次扩展，修复晚课与待安排课程重叠。
- 两个 GitHub 仓库与两个 QQ 群分列显示，QQ 官方接口唤起失败时可复制群号。
- 宣传页静态源码同步新版本下载链接与功能说明，不自动部署到任何站点。

## v5.3.1 更新

- 兼容学校已核实官方域名的 HTTP / HTTPS 与 CAS 跳转，不再将正常教务地址误判为未知来源；仍保留来源、端口及证书校验。
- 二维码入口改为连接信息上方的独立卡片，重设计连接二维码面板。

## Android 1.1.0-beta01 更新

- 使用 Miuix 原生 Compose 控件，统一卡片、按钮、表单、导航、分栏及对话框。
- 完整整合获授权的课表模块：周视图、课程总览、学期选择、课程与排课编辑、个性化、课前提醒、日历同步、上课自动化、权限管理和桌面小组件。
- 保留校园网、代理共享、SOCKS5 VPN、多账号、安全凭据及应用内更新；共用登录入口，两条认证链独立。
- 扫码入口固定在代理两分栏上方；全新全屏扫码页，支持相机、相册和手动配置。旧课表缓存保留并迁移。
- 使用与 1.0.0-beta02 相同的本机保管发布签名，可直接覆盖更新。Android 仍无校园网开机自启，默认跟随系统主题。

## v5.3.0 更新

- 共用一个账号密码入口，按需选择校园网、课表或两者；两条认证独立，未开通校园网上网服务也能通过可用网络同步课表。
- 课表支持教务理论课与实践课、离线缓存和周视图；保留原有 Cookie 安全边界与解析失败保护。
- Windows 与 Android 本机共享生成同一格式的代理二维码；Android 扫码确认后连接 SOCKS5 VPN。二维码含凭据，请勿公开传播。
- “关于”移到右上角，优化导航、账号列表和关于链接；桌面校徽尺寸保持不变。
- 自动发现新正式版本并弹窗；支持 Markdown 简介、应用内下载安装包、SHA-256 校验和 QQ 内测群入口。安装仍由用户确认。
- Android beta02：移除开机自启，默认跟随系统主题；权限生效后隐藏对应提示，权限丢失时重新出现。修正 VPN DNS 转发与断开清理，增加系统兼容性测试。

## v5.2.1 更新

- 代理账号默认使用当前校园网账号，可自定义。密码可选安全复用校园网密码或设置独立密码；校园网密码不传回界面。
- 自定义代理密码默认可见，支持小眼睛隐藏与随机推荐；开启代理后精简重复配置，提供服务器、端口、用户名和协议链接。
- “常用网站”之后新增默认关闭的代理页：HTTP / HTTPS CONNECT、SOCKS5 TCP / UDP，支持仅本机或局域网访问。
- 局域网访问选项默认打开，服务仍默认关闭；新增系统防火墙设置入口和授权指引，不自动更改放行规则。
- 不修改系统代理或防火墙，不提供透明代理、TUN / VPN 。使用说明与边界见 [代理说明](docs/PROXY.md)。

[软件介绍与下载](https://cqust-wifi.ziyis.cn/)

本项目为非官方工具，不代表学校。上网认证需要学校校园网络及本人有权限的账号；仅同步课表不要求开通校园网上网服务，但需能访问学校统一认证及教务系统。

## 下载与安装

- [Windows v5.3.2](https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases/tag/v5.3.2)
- [Android 1.1.0-beta02](https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases/tag/android-v1.1.0-beta02)
- [全部历史版本](https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases)
- [版本索引与架构演进](docs/HISTORY.md)

每个 Release 提供对应 Windows EXE、源码 ZIP 和 `SHA256SUMS.txt`。安装器未签名，Windows 可能提示未知发行者。升级前请在系统托盘菜单中完全退出旧版，再运行安装包。

历史版本仅供归档，建议日常使用最新版本。不要在公开 Issue 中粘贴密码、验证码、完整认证网址、Cookie 或 `userIndex`。

## 功能

- 手动登录、自动登录、内嵌账号管理与系统安全凭据存储（Windows 凭据管理器或 macOS 钥匙串）。
- 圆形连接按钮、连接状态检测、可选静默重连、IP 与单次连接时长。
- 自动探测认证入口：HTTP 跳转失败后使用隔离的系统 WebView 执行网页动态跳转；必要时保留手动填入引导。
- 同网络切换账号保留进程内入口提示，重新获取页面和公钥，不重用旧登录请求或会话。
- CAPTCHA 图片显示和会话内刷新，由用户手动填写；不绕过验证码。
- 亮暗主题、动效、开机自启、系统托盘、实时日志。
- 20 项本地化图标的校园常用网站。
- 课表查看：共用登录入口，经独立统一身份认证同步理论课与实践课；按周查看、离线缓存，凭据是否保存由用户选择。详见[课表说明](docs/SCHEDULE.md)。
- Windows 固定窄窗口宽度；macOS 可调整窗口宽度。底部拉柄可调整高度，导航固定，滚动条自动隐藏。
- Windows 可即时开关屏幕工作区边缘吸附，默认关闭；macOS 不启用吸附。
- 设置底部的 GitHub 正式版更新检测、安全 Markdown 更新简介与新安装包下载。

## v5.1.1 更新

更新检测移回设置底部，更新简介支持标题、列表、代码块、表格和链接；使用本地解析与净化，不执行脚本、不加载远程图片。窗口贴边自动收起，悬停边缘平滑滑出，拖离即可解除；关闭吸附和托盘恢复均可回到屏内。v5.1.0 保留不变。

## v5.1.0 更新

补全浏览器动态认证跳转，避免仅靠简单重定向解析；退出账号不再清除同网络入口探测提示。修复 WebView2 与应用重复显示密码眼睛的问题。新增窗口吸附、GitHub 更新检测和关于页官网下载按钮。

浏览器探测不接收密码，也不读取系统浏览器的已保存账号或 Cookie。认证网址仅存在内存，网络 IP 改变即失效，失败时仍可使用认证帮助。真实账号认证受当前校园网络、账号权限与验证码要求影响；隔离测试通过不等于已完成另一个真实账号登录。

## 界面预览

以下截图使用隔离测试账号与虚构网络信息，不包含实际校园网账号。

![连接界面](docs/screenshots/dashboard.png)

![认证引导](docs/screenshots/auth-help.png)

## 源码结构

```text
ui/                         HTML、CSS、JavaScript、SVG 与本地图片
src-tauri/                  Rust 认证、凭据、窗口和系统托盘后端
verification/               隔离原生界面验证与协议测试向量
legacy/python-v3.2.0/       最后一个 Python GUI 版本
legacy/electron-v4.1.0/     最后一个 Electron 版本
docs/                       版本、安全说明与验证记录
```

全部 10 个版本的准确源码快照另见 Git 标签 `v1.0.0` 至 `v5.0.2`，以及各 Release 的源码 ZIP。历史版本在本次发布时导入，Git 提交时间不是原始开发日期。初始未标号 GUI 文件使用 `v1.0.0` 作为归档标签。

## 从源码构建

### Windows

需要 Windows x64、Node.js/npm、Rust stable MSVC 工具链、Visual Studio C++ Build Tools 和 Windows SDK。运行时使用系统 WebView2；缺少时安装器可引导安装。

```powershell
npm ci
cargo test --release --manifest-path src-tauri/Cargo.toml
npm run build
```

安装器位于 `src-tauri/target/release/bundle/nsis/`。`build-local.ps1` 可使用已配置的本机工具链；仓库不包含工具链缓存、`node_modules` 或构建产物。

### macOS Apple Silicon

需要 Node.js/npm、Rust stable 的 `aarch64-apple-darwin` 目标及 Xcode Command Line Tools。在 Mac 上运行：

```sh
npm ci
cargo test --locked --manifest-path src-tauri/Cargo.toml
npm run build:macos
```

产物位于 `src-tauri/target/aarch64-apple-darwin/release/bundle/` 下的 `macos/` 和 `dmg/`。macOS 密码保存在钥匙串；登录启动项写入当前用户的 `~/Library/LaunchAgents`，应在将应用移入最终安装位置后开启。此本地构建未使用 Developer ID 签名或公证，分发前仍需完成签名、公证及真实校园网登录验证。

macOS 关闭窗口后仍在后台运行。点击菜单栏的 CQUST 图标可重新打开窗口，右键可打开“显示主窗口 / 退出软件”菜单；点击 Dock 中的应用图标也可恢复窗口。macOS 不显示或运行窗口贴边吸附设置。

仅用于隔离界面验证的 `verify-ui` 功能不应加入发布构建。正式发布使用默认 features；验证模式不会操作真实用户配置、启动项或校园账号。

## 安全与归档

密码由系统安全凭据存储保存，认证参数不进入公开源码。发布前对源码、安装器内层文件和 PyInstaller 解压成员做已知凭据及令牌模式检查，详见 [安全说明](docs/SECURITY.md) 和 [本次审计记录](docs/RELEASE_AUDIT.json)。检查不等于完整安全审计。

本次验证：10 项 Rust 测试与 45 项原生 Tauri 界面检查通过。没有为公开发布断开真实校园网或代用户提交账号密码。

## 作者与鸣谢

@ 十七号旅客 · [ziyis.cn](https://ziyis.cn) · [GitHub](https://github.com/Hercules-Zhaoziyi)

感谢 [AutoLogin](https://github.com/Gloridust/RuijieWIFI-AutoLogin) 项目提供的锐捷认证脚本。

校徽、校园门户图标和小红书标识用于界面识别，第三方权利与原有声明不因本仓库公开而改变。详见 [第三方说明](docs/THIRD_PARTY_NOTICES.md)。此批归档未额外添加统一开源许可证。
