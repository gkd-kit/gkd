# v1.9.0-beta.1

以下是本次更新的主要内容

## 优化和修复

- 订阅字段新增 [priorityTime](https://gkd.li/api/interfaces/RawCommonProps#prioritytime) 和 [priorityActionMaximum](https://gkd.li/api/interfaces/RawCommonProps#priorityactionmaximum), 用于解决开屏类规则被其他规则阻塞, 无法及时执行的问题
- 优化运行一段时间后出现系统通知提示的问题
- 修复某些机型 java 方法 removeLast 报错不存在的问题
- 其它优化和错误修复

## 旧版本日志

## v1.9.0-beta.2

- 新增写入安全设置权限操作, 便捷控制无障碍开关以及无感保活
- 新增订阅字段 Postion 中 random 变量
- 新增订阅字段 excludeSnapshotUrls
- 新增应用列表权限未授权的明显提示
- 新增可修改已添加订阅链接
- 新增通知快捷开关图标(无障碍开关/HTTP服务开关/悬浮按钮开关)
- 新增快照成功通知, 点击前往快照记录
- 新增点击通知栏对应服务前往对应页面
- 新增列表筛选项状态保存
- 修复 preKeys 非空时 actionCd 等限制条件失效的问题
- 修复通知栏原生图标过小的问题
- 修复图片预览界面闪烁的问题
- 优化无障碍事件识别逻辑以及匹配速度
- 重新添加音量快照操作
- 其它优化和错误修复

## 通过以下任意方式更新

- 打开 APP - 设置 - 检测更新
- 前往首页 <https://gkd.li/guide/>
- 通过 github [releases](https://github.com/gkd-kit/gkd/releases)
