# 更新内容

- 重做特权服务，不强依赖 Shizuku。支持 Root、ADB 和手动启动
- 新增特权服务管理页面，支持授权管理与受限原因查看
- 重写选择器引擎
- 重做快照浏览与分享流程，支持批量分享和取消准备
- 新增独立快照设置页，集中管理触发方式、截图处理与导出
- 快照支持按应用或时间分组，支持批量保存到下载目录或相册
- 快照截图改用 WebP 压缩，减少存储占用
- 新增应用规则分类标签，支持切换显示方式
- 完善全局规则应用范围设置，支持单独开启或关闭应用
- 完善规则状态说明，展示配置来源与生效限制
- 调整全局订阅字段：`matchAnyApp`、`matchSystemApp`、`matchLauncher` 和 `apps[].enable` 仅在规则组层级生效
- 子规则 `apps` 改为按应用覆盖页面与版本限制，不再控制应用启用范围
- 订阅解析错误显示具体字段路径
- 新增触发记录按应用多选筛选
- 新增事件悬浮窗紧凑模式
- 新增 HTTP 规则管理接口，支持本地与内存订阅的规则读写及开关设置
- 调整首页服务控制与常驻通知，支持开机启动状态服务
- 完善崩溃记录管理、启动诊断与日志导出
- 新增 Windows 预览版，仅供电脑体验界面
- 修复部分 Android 16 系统的截图兼容问题
- 完善本地网络与存储权限处理
- 修复部分场景下订阅自动更新未触发的问题
- 修复三态开关的无障碍操作问题
- 修复更新下载失败处理、订阅备份数据不一致等问题

## 更新方式

- GKD - 设置 - 关于 - 检测更新
- 下列方式之一

<a href="https://gkd.li/guide/"><img src="https://e.gkd.li/f23b704d-d781-494b-9719-393f95683b89" alt="Download from GKD.LI" width="32%" /></a><a href="https://play.google.com/store/apps/details?id=li.songe.gkd"><img src="https://e.gkd.li/f63fabeb-0342-4961-a46d-cac61b0f8856" alt="Download from Google Play" width="32%" /></a><a href="https://github.com/gkd-kit/gkd/releases"><img src="https://e.gkd.li/c1ef2bb9-7472-46d5-9806-81b4c37e5b4d" alt="Download from GitHub releases" width="32%" /></a>
