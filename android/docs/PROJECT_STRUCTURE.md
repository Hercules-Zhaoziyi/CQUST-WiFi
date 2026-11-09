# 安卓模块导航

- `app/.../Ui.kt`：宿主统一登录、连接、网站、代理、设置与关于。
- `app/.../PortalActivity.kt`：门户独立认证、受限 WebView 与外部浏览器入口。
- `app/.../SchedulePage.kt`：完整课表模块接入及旧缓存迁移。
- `schedule/.../App.kt`：统一设置槽、课表导航与重复入栈保护。
- `schedule/.../data/api`：CAS、常规课、实践课、考试抓取和同步协调；已有 Cookie、来源及空结果保护保留。
- `schedule/.../data/model`：课程统一标识、非标准节次、课程/考试聚合单元。
- `schedule/.../data/db`：Room 数据库与新增考试表迁移。
- `schedule/.../ui/schedule/components`：课程/考试同格卡片、详情分页与调课冲突。
- `schedule/.../util/CourseLocationFormatter.kt`：校区、楼名与教室规范化。
- `schedule/.../service`、`widget`：日历、提醒、自动化和小组件。

模块仍保留原来源包名 `top.msfxp.schedule`，上游来源版本见 `INTEGRATION.md`。
