### 真机查询边界与拥塞检查

`pnpm app:tools device-query-safety --serial DEVICE --install --output .local/query-safety/report.json` 使用当前 Debug APK 和临时 `li.gkd.a11y.fixture` 测试应用。工具先通过 `:gkd-a11y-fixture:assembleDebug` 构建测试 APK，与 GKD Debug 共用 buildSrc 中的签名配置逻辑及密钥，再复用自动化暂停/无障碍准备/恢复流程；测试应用与 GKD 分进程，只延迟测试应用自己的无障碍节点提供，不向生产引擎注入假异常或假诊断。已识别的 MIUI USB 安装提示可自动确认，其他界面仍需人工处理。

测试包括正常命中/排除、513 个同级节点、超过 4096 个后代、内容事件突发与有界的节点响应延迟，以及恢复后的动作。断言同时检查单次请求结果和测试页面收到的真实 DOWN/UP；事件队列溢出和读取拥塞从生产诊断读取。各场景停止会话后导出原始 JSON，并生成同目录离线摘要。报告记录设备、GKD APK SHA-256 及测试应用的安装结果（installed / unchanged / updated）；已安装 GKD 与当前本地构建不一致时需使用 `--install` 更新。

## 统一调试服务

`li.gkd.app.service.DebugService` 统一承载浏览器接口 `/api/*` 和应用诊断 `/debug/*`，只监听用户配置的 `httpServerPort`（默认 8888）。Android 支持局域网连接；诊断接口和 `/health` 必须提供随机 Bearer token，旧浏览器接口保持原协议。诊断接入实现位于 `gkd-app/src/androidDebug`，由最终应用 Debug 源集编译并通过 Debug Manifest 元数据装配；Release 不包含该接入实现或注册诊断路由。Debug 构建允许 ADB 通过 `android.permission.INTERACT_ACROSS_USERS_FULL` 保护的同一服务入口启动；Release 服务不导出。每次服务启动生成 token，和实际端口一起写入私有目录的 `app-debug.json`，工具通过 `run-as` 读取，token 不输出到日志和诊断包。重复启动沿用现有监听与 token；修改端口会重启监听并更新凭据文件，工具需重新连接，采集会话继续。通知“停止”关闭统一监听、删除凭据、取消服务自有诊断会话，并按设置清理内存订阅；动作通过执行引擎完成取消收尾。
