# CQUST Wi-Fi 5.0 · Tauri 2 / Rust

前端沿用现有 HTML/CSS/SVG，桌面运行时改为 Tauri 2，认证、凭据、偏好与在线检测全部由 Rust 实现。
发布包不包含 Electron、Chromium、Python 或认证子进程。使用系统 WebView2；缺少时安装器可安装微软运行时。

## 本版变化

- 融合标题栏，可调整窗口大小，保持竖向布局并禁止最大化/全屏；关闭到托盘。
- 顶部品牌区和导航固定，内容独立滚动，预留稳定滚动条空间；窄窗口下校徽和检测间隔保持同排。
- 设置即时生效，无保存按钮。开机自启使用当前用户 Run 启动项，写入后回读验证。
- 显示认证接口返回的姓名；没有姓名时显示“校园网用户”。账号和局域网 IP 居中。
- 电源按钮负责连接/断开；退出登录先注销网络会话，再清除当前进程中的密码，返回登录页。
- 问候语按早上、中午、下午、晚上和深夜切换。
- 页面禁止复制与图片拖拽；仍可在输入框中输入和粘贴账号密码。
- 关于页署名为“@ 十七号旅客”，最底部居中展示学校标识，并感谢 Gloridust 的锐捷认证脚本项目。

GitHub 主页：https://github.com/Hercules-Zhaoziyi
开源项目：https://github.com/Hercules-Zhaoziyi/CQUST-WiFi
特别鸣谢：https://github.com/Gloridust/RuijieWIFI-AutoLogin

## 认证与凭据

Rust 实现保留已验证的学校协议：当前登录页参数、同一 Cookie 会话、页面加密开关、公钥、ASCII 无填充 RSA、密码拼接 MAC 后反转与小端分块，学号/密码/服务/queryString 各进行两次 encodeURIComponent 兼容编码。
不使用历史 queryString、密文或 userIndex；普通登录只接受新返回的会话标识，并再次查询在线状态。
学校服务使用 HTTP，此协议限制没有因更换 GUI 而改变。

继承本机原有非敏感账号列表及偏好。密码通过 Win32 Credential Manager 读取，兼容旧版 python-keyring 的服务目标名与 user@service 目标名；不会把已保存密码发送回前端。
密码不写入日志或配置；进程内密码使用 zeroize 管理。真实凭据不在源码包和 EXE 中。

安装新版本前，请从托盘退出旧版，避免两版同时重连。新版不会为测试主动注销当前校园网。

## 构建

需要 Rust MSVC 工具链、Microsoft C++ 编译器/Windows SDK、Node.js 和 WebView2。

```powershell
npm ci
cargo test --manifest-path src-tauri/Cargo.toml
npm run build
```

输出位于 src-tauri/target/release/bundle/nsis。
Rust 单元测试包含学校原始 security.js 生成的七组 RSA 对照向量、编码规则、学校地址限制、真实姓名字段、配置迁移和启动项回读。
启动项测试会暂时读写本应用自己的 Run 条目，并以清理保护恢复测试前的值，不会开启用户的开机自启偏好。
verification 目录的开发用浏览器测试不进入发布包，也不表示应用依赖 Electron。

## 验证边界

浏览器模拟测试使用虚构账号，仅验证布局和交互。真实认证与 Windows 启动结果需要分别验证；构建通过不代表另一个真实账号的校园网登录或重启电脑后的自启已经现场完成。
安装包没有数字签名。未自动发布到 GitHub。

2026-10-21 验证：8 项 Rust 测试全部通过，包含启动项实际读写并恢复原值；真实 Tauri/WebView2 窗口的 20 项隔离检查全部通过。
发布安装包 3,196,751 字节（约 3.05 MiB），相比 Electron 4.1 安装包约 120 MiB 大幅缩小。
未为测试中断当前校园网，未现场测试另一个真实账号登录，也未实际重启 Windows 测试自启。
