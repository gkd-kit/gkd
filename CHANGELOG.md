# v1.11.0-beta.1

以下是本次更新的主要内容

## 优化和修复

- 优化多个页面的显示和使用体验
- 优化规则编辑弹窗为页面并支持代码高亮
- 重构应用列表移除部分排序筛选，增加按最近使用排序，显示冻结应用，隐藏无界面应用，支持下拉刷新
- 新增多个通知栏开关
- 新增使用协议和隐私政策
- 新增通知文案支持主标题和副标题
- 优化任意悬浮窗支持保存上次位置
- 新增无障碍事件悬浮窗及日志页面
- 新增截屏快照的应用ID和特征事件选择器
- 新增应用白名单内暂停匹配
- 新增局部关闭，在无障碍白名单内关闭无障碍
- 新增界面服务悬浮窗显示 Activity
- 重构 Shizuku 授权为单个 `启用优化` 开关，所有功能内部自动判断开启
- 优化连接 Shizuku 后自动给 GKD 授权
- 优化通知管理，服务类常驻通知均增加关闭按钮
- 优化关于页面反馈提示
- 优化空白截图增加文字提示
- 优化所有列表页面点击标题返回顶部
- 优化规则执行逻辑
- 新增订阅字段 `versionCode` 和 `versionName`
- 新增订阅字段值 `action:'none'`
- 修复在设备重启时启动常驻通知报错
- 修复 resetMatch=app 且 activityIds 有值时匹配异常
- 修复 com.android.systemui 系统界面识别异常
- 其它多个优化和修复

## 更新方式

- GKD - 设置 - 关于 - 检测更新
- 下列方式之一

<a href="https://gkd.li/guide/"><img src="https://e.gkd.li/f23b704d-d781-494b-9719-393f95683b89" alt="Download from GKD.LI" width="32%" /></a><a href="https://play.google.com/store/apps/details?id=li.songe.gkd"><img src="https://e.gkd.li/f63fabeb-0342-4961-a46d-cac61b0f8856" alt="Download from Google Play" width="32%" /></a><a href="https://github.com/gkd-kit/gkd/releases"><img src="https://e.gkd.li/c1ef2bb9-7472-46d5-9806-81b4c37e5b4d" alt="Download from GitHub releases" width="32%" /></a>
