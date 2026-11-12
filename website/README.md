# CQUST Wi-Fi 宣传下载页源码

独立静态网站，无需 Node.js、依赖安装或构建。直接部署 `dist/` 内的全部文件。本次仅交付源码和静态文件，没有更新任何 Sites 或自托管站点。

## 本次内容

- Windows v5.4.2：3.85 MiB，主下载来自 Gitee，完整 SHA-256 可复制。
- Android 1.2.0-beta03：64.42 MiB，提供真实 APK 下载，标明 Android 10+、测试版与旧签名迁移注意事项。
- macOS 保留已经发布的 v5.1.1 Apple Silicon arm64 DMG，不冒称已包含本次新功能。
- Windows 与安卓主下载链接改为已核验的 Gitee 同版附件；macOS 旧包按不迁移旧版本的要求保留原 GitHub 地址。
- 两端更新检测优先 Gitee 清单，GitHub 保留备用；不会把校方验证码或二次认证宣传为可绕过。
- Mac 访客优先看到 Mac 下载，Android 访客优先看到 Android 下载；触屏 iPad 不误判成 Mac。
- 底部下载区只展开当前平台，另外两个平台收为一排紧凑按钮；下载与导航按钮不再带向下箭头，Android 使用机器人头像图标。
- 网站头部、页脚和下载区使用 Wi-Fi × 课程表联合标识。
- 新增独立课表认证、通用代理二维码、应用内更新介绍；桌面自启与安卓后台运行的差异明确说明。
- 安卓 1.2.0-beta03：合并课表 v1.0.3 的考试、非标准节次、地点格式化和导航修复；新增应用内 WebView 门户与外部浏览器入口。
- Windows 5.4.2：新增门户和全部登录选项，通过校方返回的登录跳转交给默认浏览器。保留课表操作区下置和已有功能。
- 门户会话仍受校方服务、账号权限、验证码和二次认证约束；真实校园账号的会话交接未作自动化实测，不承诺绕过认证。
- 保留代理共享、多账号、静默重连、常用网站、亮暗主题、较大字号与对齐的展示箭头。
- QQ 内测群入口与备案号保留；不收集账号、密码或代理二维码。
- 界面展示使用隔离验证环境的虚构数据，不包含真实学生账号。

## 上传方式

将本包 `dist/` 里的 `index.html`、`style.css`、`app.js`、`assets/` 完整上传到域名实际站点根目录；不要多嵌套一层 dist。

例如：

```text
/www/wwwroot/cqust-wifi/
  index.html
  app.js
  style.css
  assets/
```

上传前备份旧站点，覆盖本次对应文件即可，不要删除其他站点内容。若 Nginx root 本就指向 dist，以实际 root 为准。

HTML 构建标记：`20261112-w542-m511-a120beta03-joint`。
CSS/JS 查询版本：`542-android-120beta03-joint`。
页脚应显示“Windows v5.4.2 · macOS v5.1.1 · Android 1.2.0-beta03”。

如果页面仍旧，先核对域名对应的站点根目录和实际返回的 index.html，再清理该域名首页的 CDN／反代缓存。查询参数不能替代正确的服务端缓存策略。`deploy/nginx-cache.example.conf` 仅为可合并的缓存参考，不是完整站点配置。

## 本地预览与检查

```text
python serve.py
node --check dist/app.js
node verify-site.cjs
```

预览地址：http://127.0.0.1:4173/ 。

## 文件与来源

- `dist/`：可直接部署的静态网站。
- `verify-site.cjs`：版本、真实下载地址、校验值、QQ/ICP备案、静态资源与平台排序检查。
- `serve.py`：仅本地静态预览。
- `deploy/`：缓存配置参考。
- 现有校名、校徽及应用素材用于说明项目；保留其原有权利。本项目不是学校官方客户端。
- 不包含服务器密码、签名私钥、客户端账号配置、真实二维码或后台遥测。

GitHub 仓库：https://github.com/Hercules-Zhaoziyi/CQUST-WiFi
宣传域名：https://cqust-wifi.ziyis.cn/
QQ 内测群：https://qm.qq.com/q/Rn7B5NBbiK
