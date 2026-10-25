### 真机查询边界与拥塞检查

`pnpm app:tools device-query-safety --serial DEVICE --install --output .local/query-safety/report.json` 使用当前 Debug APK 和临时 `li.gkd.a11y.fixture` 测试应用。工具先通过 `:gkd-a11y-fixture:assembleDebug` 构建测试 APK，与 GKD Debug 共用 buildSrc 中的签名配置逻辑及密钥，再复用自动化暂停/无障碍准备/恢复流程；测试应用与 GKD 分进程，只延迟测试应用自己的无障碍节点提供，不向生产引擎注入假异常或假诊断。已识别的 MIUI USB 安装提示可自动确认，其他界面仍需人工处理。

测试包括正常命中/排除、513 个同级节点、超过 4096 个后代、内容事件突发与有界的节点响应延迟，以及恢复后的动作。断言同时检查单次请求结果和测试页面收到的真实 DOWN/UP；事件队列溢出和读取拥塞从生产诊断读取。各场景停止会话后导出原始 JSON，并生成同目录离线摘要。报告记录设备、GKD APK SHA-256 及测试应用的安装结果（installed / unchanged / updated）；已安装 GKD 与当前本地构建不一致时需使用 `--install` 更新。
