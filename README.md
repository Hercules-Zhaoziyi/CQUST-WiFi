# CQUST Wi-Fi Windows GUI

重庆科技大学校园网 Windows 客户端。界面使用现代圆角卡片设计，并保留原
`login.py` / `config.py` 的认证接口与 `success` / `wait` 状态判断。

## 功能

- 手动登录与启动自动登录
- 内嵌账号切换和账号管理，不打开独立管理窗口
- 密码显示/隐藏按钮
- 登录后使用圆形闪电按钮连接或断开
- 每 5、7 或 10 秒检查在线状态
- 检测到账号被其他设备挤下线时显示断开并提示
- “始终保持连接”启用时静默自动重连
- 默认隐藏、在主界面内打开的实时日志
- 跟随 Windows、亮色、暗色三种主题

## 凭据安全

账号列表和界面设置保存在 `%APPDATA%\CQUST Wifi`。密码通过 `keyring` 写入
Windows 凭据管理器，不会写入 JSON、源码或日志。`config.example.py` 只保留
旧版字段结构，所有敏感值为空。

## 运行与测试

```bat
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
.venv\Scripts\python -m unittest discover -s tests -v
.venv\Scripts\python gui.py
```

程序会在连接校园 Wi-Fi 后自动从认证跳转页提取本机 `queryString`，不再依赖
旧 `config.py` 中与设备绑定的静态参数。

## 构建 EXE

运行 `build_windows.bat`，或执行：

```bat
.venv\Scripts\python -m PyInstaller --noconfirm --clean --onefile --windowed ^
  --name CQUST_Wifi --collect-all customtkinter --collect-all keyring gui.py
```

输出为 `dist\CQUST_Wifi.exe`。
