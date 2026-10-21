# CQUST Wi-Fi Windows GUI

重庆科技大学校园网 Windows 客户端。界面使用现代圆角卡片设计，并保留原
`login.py` / `config.py` 的认证接口与 `success` / `wait` 状态判断。

## 功能

- 手动登录与启动自动登录
- 内嵌账号切换和账号管理，不打开独立管理窗口
- 密码显示/隐藏按钮
- 登录后使用圆形电源按钮连接或断开
- 每 5、7 或 10 秒检查在线状态
- 检测到账号被其他设备挤下线时显示断开并提示
- “始终保持连接”启用时静默自动重连
- 默认隐藏、在主界面内打开的实时日志
- 跟随 Windows、亮色、暗色三种主题

## 凭据安全

账号列表和界面设置保存在 `%APPDATA%\CQUST Wifi`。密码通过 `keyring` 写入
Windows 凭据管理器，不会写入 JSON、源码或日志。`config.example.py` 只保留
旧版字段结构，所有敏感值为空。

本机原 `config.py` 中已有的密码保持原样，程序不改写它。留空密码时可使用原配置
对应账号的加密密码；输入新密码会替换本次认证。原配置密码不会显示在输入框。
不要分享含真实凭据、queryString 或 userIndex 的原配置。

## 运行与测试

```bat
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
.venv\Scripts\python -m unittest discover -s tests -v
.venv\Scripts\python gui.py
```

程序优先读取 EXE 旁的 `config.py`，其次读取桌面 `CQUST Wifi\config.py`
或 `CQUST Wifi\CQUST_Wifi_Python\config.py`。只解析字面量，不执行配置中的代码。
保留原服务、认证参数和加密密码。没有配置参数时尝试自动发现本机认证页面。
原 queryString 可能在换设备或网络后失效，届时需要更新本机配置。

校园认证请求绕过系统代理，只向固定学校认证服务器发送凭据。
手动输入密码时读取服务器公钥，遵循原配置的 RSA 加密模式；不能取得公钥则不降级发送明文。
未配置加密且服务器明确返回关闭加密时，按该服务器的原协议登录（学校接口使用 HTTP）。
登录成功后额外查询在线状态，未确认在线不会显示连接成功。
只有服务器明确说明其他设备登录时才显示被挤下线；普通网络故障不冒充该原因。

设置和日志均内嵌在主窗口，关闭后保留当前输入；列表不再使用系统原生弹出菜单。
Wi-Fi、眼睛、下拉箭头和电源图标由程序绘制，不依赖 emoji 字体。
校徽使用用户提供的透明 PNG。开关使用约 180 毫秒滑动动画。

## 构建 EXE

运行 `build_windows.bat`，或执行：

```bat
.venv\Scripts\python -m PyInstaller --noconfirm --clean --onefile --windowed ^
  --name CQUST_Wifi_v2 --icon assets\wifi.ico --add-data "assets;assets" ^
  --collect-all customtkinter --collect-all keyring gui.py
```

输出为 `dist\CQUST_Wifi_v2.exe`。首次构建前运行 `python make_icon.py`。

## 验证范围（2026-10-21）

使用本机原配置实际调用学校登录接口，返回成功；后续在线检查通过。
模拟回归覆盖参数保留、密码加密、压缩响应、错误脱敏、离线分类、内嵌设置/日志、
保存不丢输入、图标切换及开关动画。未人为制造其他设备挤下线。
电脑控制未授权测试用 Python 窗口，因此本轮未完成截图视觉验收。
