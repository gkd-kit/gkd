# 本地开发产物

自定义本地产物按用途分别放在仓库根目录 `.local/` 与 `.temp/`，路径不依赖启动工作目录，禁止混合使用：

- `.local/`：与项目代码及其运行、构建、测试直接相关的结构化数据，包括应用数据、设备资料、模拟配置、依赖缓存、构建包和正式测试会话产物。
- `.temp/<任务名>/`：临时脚本、排查日志、中间文件和一次性杂项输出。不得在 `.local/` 堆放这些文件或继续使用 `.local/tmp/`，也不得将应用数据放入 `.temp/`。

工具标准目录（`.gradle`、`.kotlin`、`node_modules`、模块 `build`）保持默认。应用自身存储布局中的缓存、日志与临时文件仍由应用管理，不属于开发任务的临时杂项。

设备工具的临时工作目录为 `.temp/device-install/update-*/`、`.temp/test-app-cleanup/round-*/`、`.temp/matching-debug/capture-*/`；安装弹窗识别日志位于 `.temp/install-confirmation/attempt-*/`。设备设置传输中间文件位于 `.temp/device-automation/store-*/` 与 `scope-*/`，selector 下载中间文件位于 `.temp/selector-dist/download-*/`，操作结束后清理各自会话目录。设备设置恢复记录仍保存在 `.local/device-recovery/`，测试应用安装记录仍保存在 `.local/device-test-apps/`。

Windows 绿色版脱离仓库运行时，以 `GKD.exe` 所在目录为根，沿用相同的 `.local/` 数据布局；不会访问打包机器的仓库路径。

| 相对 `.local/` 的目录 | 用途 |
| --- | --- |
| `snapshots/` | 调试、分析使用的快照 JSON 与关联图片 |
| `desktop/data/` | 日常应用数据 |
| `desktop/profile/` | 设备资料、字体、应用清单和样本 |
| `desktop/simulator.json` | 日常模拟配置 |
| `desktop/debug-server.json` | 运行中的调试服务端口，停止时删除；隔离会话在自身目录保存 |
| `desktop-packages/` | Windows 绿色版 ZIP |
| `desktop/*-imported` | 日常数据对应的导入标记 |
| `cache/` | 可重新获取的依赖及工具缓存 |
| `tests/desktop/<会话ID>/` | 隔离 data、profile、模拟配置、导入标记及 reports |
| `tests/android/<会话ID>/` | Android 验证截图、日志和恢复记录 |
| `device-test-apps/` | 按设备保存的测试轮次安装记录，整轮清理成功后移除对应记录 |
| `backups/` | 主动保留的备份 |

## 应用存储布局

Android 与 Desktop 共用 `AppStorageLayout`，宿主注入 files/cache/privateFiles 根目录。Desktop 的 `data/` 对应 Android 外部应用目录：

| 相对 `data/` 的路径 | 消费者 |
| --- | --- |
| `files/db/gkd.db` | Room 数据库 |
| `files/store/` | 生产设置 |
| `files/subscription/` | 订阅文件 |
| `files/snapshot/` | 应用自身快照 |
| `files/log/` | 普通运行日志；按日期和 2 MiB 滚动，总量 16 MiB，保留 7 天 |
| `files/matching-diagnostics/<记录ID>/` | 每次采集的 `diagnostic.json` 及附件；记录 ID 为开始时间的毫秒时间戳，摘要保存在数据库 `match_diagnostic` 表。停止时保存未完成现场，收尾后更新，诊断页分享附带选中的目录，关于页导出日志包含所有已保存诊断 |
| `files/crash/temp/` | 临时崩溃文件 |
| `files/private-store/` | 私有配置 |
| `cache/coil/` | 图片缓存 |
| `cache/webview/` | 网页用户数据 |
| `cache/shared/` | 备份、日志等共享输出 |

Desktop 整个 data 目录视为私有，privateFiles 与 files 共用根目录；Android 私有存储仍使用系统内部目录。设备资料、导入标记与模拟配置位于 data 外。WebView2 内核使用系统安装目录，原生 DLL 由依赖自身的临时加载机制处理。

## 隔离与清理

Desktop `--test` 创建随机会话，交互写入前核对 `/desktop/state` 的 `isolated` 标记；截图与报告放在当前会话 `reports/`，不得写入日常目录。启动与导入操作见 [开发文档](../gkd-app/README.md)。

Gradle clean 不删除日常数据。缓存和临时文件可按需清理；日常数据、备份以及包含未处理自动化恢复记录的会话必须保留。历史报告保留原名称，不推测所属会话。可复用工具提交到源码目录，不长期放在忽略目录；不增加旧目录回退或双写。

日志写入队列最多 1024 条、2 MiB，单条最多 64 KiB；超限会标记截断或累计丢弃数量，队列拥塞时优先保留较高级别。Release 文件保留 INFO/WARN/ERROR，DEBUG 仅调试构建采集。公用日志分享先复制稳定的日志文件集，`metadata/log-writer.txt` 包含当前进程的丢弃、截断及写入失败状态。
