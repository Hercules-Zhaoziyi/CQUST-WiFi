# v5.1.0 发布验证

验证日期：2026-10-22（Asia/Shanghai）。

- 15 项 Rust 离线单元测试通过：加密对照、编码、可信地址、验证码、账号退出后的入口提示、配置迁移、吸附坐标、版本比较和更新地址限制等。
- 1 项 GitHub 正式版接口只读网络测试通过。
- 56 项真实 Tauri/WebView2 隔离窗口检查通过，包括动态 JS 拼接跳转地址、实际窗口贴边及禁用、默认吸附、设置页更新卡片置顶、更新说明展示、官网下载入口、密码显示按钮和既有登录/验证码流程。
- 正式安装包成功生成。源码、构建程序和解包后的实际安装程序均通过已知校园凭据和验证构建标记扫描。
- 解包程序与原始构建程序只存在 Tauri 官方文档源码所描述的包类型标记差异（`__TAURI_BUNDLE_TYPE_VAR_UNK` → `__TAURI_BUNDLE_TYPE_VAR_NSS`）；未接受任何其他差异。

测试使用虚构账号，动态跳转夹具在提交合成目的地址前被拦截，不发出合成登录请求。没有中断当前真实校园网会话，没有尝试另一个真实账号登录。上述结果不应解释为所有真实账号和所有网络环境均已验证。

常规测试：`cargo test --release --manifest-path src-tauri/Cargo.toml`。
可选只读网络测试：`cargo test --release --manifest-path src-tauri/Cargo.toml github_latest_readonly -- --ignored`。
隔离窗口检查：`build-local.ps1 -Mode verify` 后，以 `--verify` 运行验证构建，输出目录由 `CQUST_VERIFY_DIR` 指定。
正式发布必须重新构建不含 `verify-ui` 特性的安装包，不得发布验证构建。
