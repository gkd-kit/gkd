# 本地开发产物

所有自定义本地产物统一位于仓库根目录 `.local/`，路径不依赖启动工作目录。工具标准目录（`.gradle`、`.kotlin`、`node_modules`、模块 `build`）保持默认。

Windows 绿色版脱离仓库运行时，以 `GKD.exe` 所在目录为根，沿用相同的 `.local/` 数据布局；不会访问打包机器的仓库路径。

| 相对 `.local/` 的目录 | 用途 |
| --- | --- |
| `snapshots/` | 调试、分析使用的快照 JSON 与关联图片 |
| `desktop/data/` | 日常应用数据 |
| `desktop/profile/` | 设备资料、字体、应用清单和样本 |
| `desktop/simulator.json` | 日常模拟配置 |
| `desktop-packages/` | Windows 绿色版 ZIP |
| `desktop/*-imported` | 日常数据对应的导入标记 |
| `cache/` | 可重新获取的依赖及工具缓存 |
| `tests/desktop/<会话ID>/` | 隔离 data、profile、模拟配置、导入标记及 reports |
| `tests/android/<会话ID>/` | Android 验证截图、日志和恢复记录 |
| `backups/` | 主动保留的备份 |
| `tmp/<任务名>/` | 临时脚本、中间文件和一次性输出 |

## 应用存储布局

Android 与 Desktop 共用 `AppStorageLayout`，宿主注入 files/cache/privateFiles 根目录。Desktop 的 `data/` 对应 Android 外部应用目录：

| 相对 `data/` 的路径 | 消费者 |
| --- | --- |
| `files/db/gkd.db` | Room 数据库 |
| `files/store/` | 生产设置 |
| `files/subscription/` | 订阅文件 |
| `files/snapshot/` | 应用自身快照 |
| `files/crash/temp/` | 临时崩溃文件 |
| `files/private-store/` | 私有配置 |
| `cache/coil/` | 图片缓存 |
| `cache/webview/` | 网页用户数据 |
| `cache/shared/` | 备份、日志等共享输出 |

Desktop 整个 data 目录视为私有，privateFiles 与 files 共用根目录；Android 私有存储仍使用系统内部目录。设备资料、导入标记与模拟配置位于 data 外。WebView2 内核使用系统安装目录，原生 DLL 由依赖自身的临时加载机制处理。

## 隔离与清理

Desktop `--test` 创建随机会话，交互写入前核对 `/state` 的 `isolated` 标记；截图与报告放在当前会话 `reports/`，不得写入日常目录。启动与导入操作见 [开发文档](../gkd-app/README.md)。

Gradle clean 不删除日常数据。缓存和临时文件可按需清理；日常数据、备份以及包含未处理自动化恢复记录的会话必须保留。历史报告保留原名称，不推测所属会话。可复用工具提交到源码目录，不长期放在忽略目录；不增加旧目录回退或双写。
