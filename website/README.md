# CQUST Wi-Fi 宣传下载页源码

独立静态网站，无需 Node.js、依赖安装或构建。直接部署 `dist/` 内的全部文件。本次仅交付源码和静态文件，没有更新任何 Sites 或自托管站点。

## 本次内容

- Windows v5.3.2：3.84 MiB，连接 GitHub 安装包，完整 SHA-256 可复制。
- Android 1.1.0-beta02：64.13 MiB，提供真实 APK 下载，标明 Android 10+、测试版与旧签名迁移注意事项。
- macOS 保留已经发布的 v5.1.1 Apple Silicon arm64 DMG，不冒称已包含本次新功能。
- Mac 访客优先看到 Mac 下载，Android 访客优先看到 Android 下载；触屏 iPad 不误判成 Mac。
- 底部下载区只展开当前平台，另外两个平台收为一排紧凑按钮；下载与导航按钮不再带向下箭头，Android 使用机器人头像图标。
- 网站头部、页脚和下载区使用 Wi-Fi × 课程表联合标识。
- 新增独立课表认证、通用代理二维码、应用内更新介绍；桌面自启与安卓后台运行的差异明确说明。
- 安卓 1.1.0-beta02：统一设置与账号、联名关于、两个交流群，移除失效内嵌认证页，使用原生 Wi-Fi 探测。
- Windows 5.3.2：课表账号与操作移至课表底部。补充 Android 安装包校验值和准确的同签名升级提示。
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

HTML 构建标记：`20261024-w532-m511-a110beta02-joint`。
CSS/JS 查询版本：`532-android-110beta02-joint`。
页脚应显示“Windows v5.3.2 · macOS v5.1.1 · Android 1.1.0-beta02”。

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
