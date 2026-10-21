# CQUST Wi-Fi 4.0 · Electron

重写的 Electron GUI，保留 3.2 的 auth_adapter.py 认证协议，不把账号、密码、历史 queryString 或 userIndex 内置在程序中。

## 使用

安装 CQUST-WiFi-4.0.0-Setup.exe 后打开软件。不要同时运行旧版的自动重连功能，避免两个客户端互相干扰。
首次未保存密码时填写学号和密码。已有账号仍使用原来的 Windows 凭据管理器条目；密码留空可使用保存值。

- 设置中的开机自启默认关闭。开启并保存后，登录 Windows 时启动并隐藏到托盘；自动连接需另外开启“自动登录”。
- 关闭窗口默认隐藏到系统托盘，状态检测与自动重连继续运行。双击托盘图标恢复，右键菜单完全退出。
- 退出软件不会主动注销校园网，也不会继续保活或重连。下一次启动需重新识别当前网络会话。
- 状态检查为 5 秒、10 秒、2 分钟。旧版的 7 秒自动迁移为 10 秒；学校要求的保活独立于检测周期。
- 手动断开后不会自动重连。开启始终保持连接时掉线不弹提示；凭据错误和验证码需要人工处理，避免反复登录。
- 默认竖向窗口，可缩放到 560 像素宽，禁止最大化/全屏；关于页包含署名、网站、GitHub 图标链接与更新日志。
- GitHub 链接为作者指定用户名下的预留仓库，目前未创建或推送。没有自动更新功能。

## 架构与安全

本地 HTML/CSS/SVG + Electron 主进程 + PyInstaller 认证服务。前端没有 Node 权限，启用 contextIsolation 和 sandbox，CSP 不允许外部脚本、网络连接或内嵌对象。
主进程只暴露固定 IPC 操作，校验来源，只允许打开两个明确的网站地址。
认证通过子进程匿名管道通信，不使用命令行传密码，不启动本机 TCP 服务。密码仅在当前进程内存和 Windows 凭据管理器中存在；不写入配置或日志。
原认证模块只允许学校服务器，并拒绝 POST 重定向。学校 HTTP 协议的限制仍然存在，不声称是 HTTPS 认证。
页面参数探测失败仍允许手填当前网址；验证码需用户填写。已在线时必须确认下线才继续换号。

## 构建

需要 Node.js、Windows Python、requests/keyring/pyinstaller。Electron 45.0.0 与 electron-builder 26.15.3 已锁定到 package-lock.json。

```powershell
python -m pip install requests keyring pyinstaller
python -m unittest discover -s tests -v
python -m PyInstaller --noconfirm --onefile --console --name auth-service --distpath backend-dist --workpath backend-build --collect-all keyring backend/service.py
npm ci
npm run smoke
npm run build
```

GitHub 下载不可达时可对安装命令设置 ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/；下载校验必须保留。
认证服务的控制台由主进程 windowsHide 隐藏，不在用户桌面弹出命令行。
签名证书未配置，安装包未签名；不建议绕过组织安全策略。卸载时移除本应用开机启动项，保留用户配置与凭据。

## 验证边界

测试使用隔离配置和虚构凭据。smoke 模式不进行真实登录、不修改启动注册表，验证界面、主题、窄窗口、设置停留、托盘与进程隔离。
未为测试注销当前用户网络；另一个账号的真实登录、真实开机启动和其他设备挤下线仍需现场验证。

作者：@十七号旅客 · https://ziyis.cn · https://github.com/Hercules-Zhaoziyi/CQUST-WiFi
