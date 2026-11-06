# 更新内容

- 重做特权服务，不强依赖 Shizuku，支持 Root、ADB 和手动启动，提供授权管理与断线恢复
- 新增「触摸优化」，支持通过特权服务执行点击和滑动，减少部分设备上的触摸卡顿
- 自动化模式新增「伴生无障碍」及按应用暂停功能，移除「局部无障碍」和「局部自动化」
- 重写选择器引擎，优化匹配调度与窗口识别，支持中断失效规则正在执行的滑动
- 新增匹配运行诊断，支持采集与分享诊断记录，完善崩溃记录管理和日志导出
- 新增持久化触发统计，支持按应用筛选触发记录及查看规则组最近触发时间
- 新增应用规则分类标签、显示方式切换与本地规则组拖动排序，完善全局规则应用范围设置和状态说明
- 调整全局订阅字段：`matchAnyApp`、`matchSystemApp`、`matchLauncher` 和 `apps[].enable` 仅在规则组层级生效；子规则 `apps` 改为按应用覆盖页面与版本限制，不再控制应用启用范围
- 重做快照浏览、设置与分享流程，支持分组浏览和批量操作，截图改用 WebP 压缩以减少存储占用
- 支持通过特权服务安装更新并自动重启，提供安装前备份入口
- 新增外部调用设置页与 HTTP 规则管理接口，支持快照命令及本地、内存订阅的规则读写
- 优化服务控制与保活引导，新增透明悬浮窗保活开关、通知栏快捷方式引导和事件悬浮窗紧凑模式
- 新增 Windows 预览版，仅供电脑体验界面
- 适配 Android 17 网络权限，修复部分 Android 16 系统的截图兼容问题
- 修复订阅备份数据不一致、追加规则组时 `scopeKeys` 引用错误及「最近使用」漏记等问题

## 更新方式

- GKD - 设置 - 关于 - 检测更新
- 下列方式之一

<a href="https://gkd.li/guide/"><img src="https://e.gkd.li/f23b704d-d781-494b-9719-393f95683b89" alt="Download from GKD.LI" width="32%" /></a><a href="https://play.google.com/store/apps/details?id=li.songe.gkd"><img src="https://e.gkd.li/f63fabeb-0342-4961-a46d-cac61b0f8856" alt="Download from Google Play" width="32%" /></a><a href="https://github.com/gkd-kit/gkd/releases"><img src="https://e.gkd.li/c1ef2bb9-7472-46d5-9806-81b4c37e5b4d" alt="Download from GitHub releases" width="32%" /></a>
