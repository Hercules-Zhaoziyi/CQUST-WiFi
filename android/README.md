# CQUST Wi-Fi Android

原生 Kotlin / Jetpack Compose 安卓客户端，当前为 **1.0.0-beta02 开发测试版**。不修改桌面端版本号。

## 功能

- 校园网自动探测、动态认证参数/RSA、验证码、手动网址补充。
- 登录/下线、状态检测、可选自动登录和掉线重连、多账号切换。
- Android Keystore AES-GCM 加密保存凭据，关闭系统备份。
- 20 个校园常用网站、本地图标、深浅色主题、动效、日志。
- GitHub Android 更新检测与 Markdown 更新说明、软件介绍和内测群入口。
- 用户授权的 SOCKS5 VPN 客户端：连接电脑端局域网代理，转发公网 TCP/UDP；校内/私有网络直连。
- 默认关闭的 HTTP / HTTPS CONNECT / SOCKS5 局域网代理服务，要求账号密码认证。
- 前台通知服务，不注册开机自启。主题默认跟随系统。
- 共用一个账号密码入口，校园网／课表认证独立调度；课表不依赖校园网付费开通状态。
- 课表支持理论与实践课程同步、离线缓存、周视图、课程详情和校历日期设置。认证及解析经所有者授权整合自 yuexps/cqust-schedule-miuix，未移植该项目的桌面小组件、日历导出和勿扰自动化。
- 代理配置支持扫描通用二维码、确认后连接；安卓本机共享和 Windows 端都可生成相同格式的二维码。

## 开发与测试

需要 JDK 17、Android SDK 35、Gradle 8.9；Android 10（API 29）及以上。

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest
```

仓库 `.github/workflows/android-build.yml` 可手动构建。CI 生成无签名 release APK，再由维护者在本机使用固定密钥签名；CI 不持有签名私钥或密码。测试 APK 使用 debug key，仅用于模拟器测试，不作为发布安装包。仍需真实校园网与不同品牌手机测试，不能承诺后台永久驻留。启用 VPN 必须由用户在系统授权页同意；启动时不会自动申请权限。

Windows 使用 `gradlew.bat`；Android Studio 可直接打开本目录。Gradle 发行包与官方 wrapper JAR 均校验 SHA-256；wrapper JAR 为 `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`。

从 beta02 起采用本机保管的固定发布签名。beta01 为旧 debug 签名，无法覆盖升级到 beta02，需要用户确认后自行卸载重装（会清除旧账号缓存）；本应用不会自动卸载或清除数据。beta02 以后的同签名版本支持应用内下载安装更新。不要将密钥或密码提交到 GitHub；保管好签名备份。

## 安全与使用边界

- 认证凭据只发送给经过校验的学校认证主机；探测不使用系统代理，绑定 Wi-Fi 的 DNS 和 socket。
- 持久凭据加密；VPN 运行期间原生库需要 app 私有、禁止备份目录内的临时明文配置，停用后删除。不得导出该目录或日志中的敏感数据。
- SOCKS5/HTTP 代理不是加密隧道，只用于可信局域网；请优先使用独立代理密码，不要复用校园网密码。遵守校园网络使用规则。
- VPN 与本机代理共享互斥，避免流量回环；VPN 排除本应用以维持认证与上游连接。系统 Wi-Fi HTTP 代理应关闭。
- 原生 VPN 依赖 Hev Socks5 Tunnel 2.19.1，锁定 AAR SHA-256：`107958f7a07051386248ba8dba778b63d8dcefdfb2c3665b0ee9a8c93de5f6ca`。完整许可见 `app/src/main/assets/licenses/`。

## 借鉴与依赖

参考 [Zelm05/campus-autologin](https://github.com/Zelm05/campus-autologin) 的 Wi-Fi 定向探测、网络事件驱动和移动端信息分层思路，保留本项目品牌和动态认证实现，不照搬其固定认证参数。

原生转发库：[heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel/tree/2.19.1)（MIT），包含 hev-task-system、hev-socks5-core、yaml（MIT）、lwIP（BSD）。Compose/AndroidX、OkHttp、Jsoup、Coil、Markwon 的许可证由各上游项目提供。
