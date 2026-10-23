# 第三方与归档说明

- 感谢 [AutoLogin](https://github.com/Gloridust/RuijieWIFI-AutoLogin) 项目提供的锐捷认证脚本。历史认证逻辑及说明保留来源；本仓库没有替上游指定新许可证。
- 重庆科技大学校名、校徽与校园服务图标来自用户提供的学校素材和门户保存文件，用于识别学校及校园服务。本项目不是学校官方客户端。
- 小红书 SVG 标识由用户提供，用于链接作者个人主页；不是本项目原创商标。
- Tauri、Rust crates、Electron、Node/npm 和 Python 依赖的许可证由对应项目提供。锁文件与历史依赖清单随源码保留；本仓库不重新授权其内容。
- 二维码编码使用 [qrcode-rust 0.14.1](https://github.com/kennytm/qrcode-rust/tree/v0.14.1)，MIT / Apache-2.0；SHA-256 使用 RustCrypto sha2（MIT / Apache-2.0）。版本锁定见 Cargo.lock。
- 课程表认证与解析参考并获授权整合自 [yuexps/cqust-schedule-miuix](https://github.com/yuexps/cqust-schedule-miuix)；源项目未标示通用许可证，此项授权不代表为其重新指定开源许可证。网络探测参考 campus-autologin，Android 转发核心为 HevSocks5Tunnel，详见 android 目录的许可与来源说明。
- 历史源码按原版本导入 Git 标签，构建缓存、真实个人配置、重复打包的 EXE 与无关临时网络诊断文件不进入源码仓库。
- v1.0.0 是为初始未标号 GUI 分配的归档标签。导入提交及 Release 的创建时间是本次整理时间，不是原始开发时间。
