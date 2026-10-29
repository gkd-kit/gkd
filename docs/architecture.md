# 应用架构

本文描述当前设计、依赖和资源用法；强制开发约束见 [AGENTS.md](../AGENTS.md)，启动与调试见 [Desktop 开发文档](../gkd-app/README.md)，本地目录见 [开发产物](local-development.md)。

## 模块与源码归属

依赖方向为 `gkd-android` → `gkd-app` → `gkd-db` / `gkd-selector`。

| 位置 | 职责 |
| --- | --- |
| `gkd-app/src/commonMain` | 业务、Repository、页面、组件、ViewModel、导航、主题和 Compose Resources |
| `gkd-app/src/androidMain` | Application、Activity、Service、权限、Activity Result、通知、无障碍、特权进程与原生能力 |
| `gkd-app/src/jvmMain` | Desktop 入口、窗口、模拟控制器、开发 HTTP、设备资料输入与原生能力 |
| `gkd-android` | Android Manifest、原生 res、渠道、签名、打包及设备测试入口 |
| `gkd-app/build.gradle.kts` / `scripts/desktop` | JVM 启动与打包配置 / TypeScript 开发工具 |
| `gkd-db` | Room 实体、DAO、分页查询和数据库事务 |
| `gkd-selector` | 选择器解析、匹配与多平台发布，见其 [README](../gkd-selector/README.md) |
| `gkd-aidl` / `gkd-hidden-api` | Binder 协议编译 / Android 隐藏 API stub |

应用公共包及 Android namespace 为 `li.gkd.app`，页面和 ViewModel 位于 `li.gkd.app.ui.*`。Desktop 使用相同包层级，入口为 `li.gkd.app.DesktopMainKt`。Android 打包模块 namespace 为 `li.gkd.android`，Manifest 使用完整组件类名；已注册组件（含 SelectToSpeakService）保留原身份。当前应用目标为 Android/JVM，两端可共用的 Java API 实现直接放在 commonMain。

平台差异通过函数级 `expect/actual`、事件回调或原生内容槽接入；业务与领域实现保留在共享层。Desktop 只替换平台输入和能力，不执行真实 Android 自动化；不支持的具体动作返回 `PlatformResult.Unsupported`，页面、编辑和可移植文件流程仍正常工作。

## 状态与生命周期

当前应用仓库使用 `object`，平台启动先初始化真实依赖，再启动消费者；退出先提交待保存数据，再关闭应用协程、网络、数据库和日志。

Android/Desktop 采用单主界面设计，根 `MainViewModel` 由主界面的 ViewModelStore 持有，生命周期覆盖子页面。生产页面、私有 Composable 和子页面 ViewModel 通过 `MainViewModel.requireCurrent()` 访问已注册的根实例；根不持有子 ViewModel。页面 ViewModel 组织保存、确认和后续导航等事件流程，Compose 负责展示和 IME、焦点等界面操作。

`GkAppNavigation` 是双端生产路由注册，`GkNavigation` 管理导航条目、框架保存状态及 ViewModel 生命周期。每次入栈由 `AppNavEntry` 区分，即使路由相同也不共享条目状态。HomeRoute 的 HomeViewModel 管理四标签状态与业务，子路由持有各自需要的 ViewModel；纯展示和组件瞬时状态不单独创建 ViewModel。

页面 ViewModel 持有查询聚合、Paging、加载状态与编辑草稿；状态随导航条目清理，不提供进程重启恢复。数据库首发以 `Loadable.Loading` 区分已加载的空结果。滚动、焦点和动画留在 Compose；状态归属与收集方式见 [开发规范](../AGENTS.md#ui-与生命周期)。

`AndroidAutomationSessions` 选择真实服务；应用生命周期的 `MatchingRuntime.coordinator` 统一自动规则、业务 HTTP 与单次诊断的输入通道。commonMain 的 `queryRules`、`matchRuleSelectors`、`ForegroundTracker`、`MatchInputReducer` 和规则运行状态承载共享决策；规则快照安装通过 `RuleReconciliation` 保留未受依赖变更影响的状态；Android 引擎保留事件接入、节点缓存和平台调用。服务交接会取消旧上下文，旧输入收尾完成后才能开始新输入。事件使用独立副本与有界队列，资源随所属 Android 组件释放。页面退出不销毁应用仓库；Desktop 场景重建不清空持久化数据。实现边界与操作说明见 [匹配引擎调试](matching-engine-debugging.md)。

匹配相关代码按职责组织在 `li.gkd.app` 下，Android/JVM 实现在各自源码集的对应包中：

| 包 | 当前职责 |
| --- | --- |
| `rule` | 规则定义、解析、分组、配置、身份、运行状态、限制与状态判定；声明可选的状态观察接口 |
| `matching` | 应用运行时组装、协程协调、调度状态机、规则状态复用与前台规则快照 |
| `matching.input` | 事件输入、前台判定、时序策略；Android 前台状态、系统应用信息及屏幕/规则观察 |
| `matching.query` | 查询流程、选择器组合、根节点读取；Android `A11yQueryContext` 与节点缓存 |
| `matching.action` | 连续手势、Android 动作执行、手动查询动作与全局返回 |
| `matching.diagnostics` | 诊断会话、节点指标、属性解释、查询与系统录制、统一诊断包及停止现场保存 |
| `development.matching` | 调试 HTTP、单次运行、模拟节点与性能基准 |

这些是职责包，不是独立 Gradle 模块，也不构成严格的逐包分层。平台宿主和调试入口组装同一个协调器；`rule` 不依赖 `matching` 或调试实现。选择器组合算法只依赖规则与 `gkd-selector`，诊断从外侧观察。`MatchScheduleMachine` 只接收生产调度命令，不引用诊断记录；采集器保存输入、实际节点访问、调度效果和规则状态供分析，不再提供离线重放。

## 数据读写与兼容

订阅坐标表达式由 `li.songe:expression` 编译、静态校验并在动作注入前求值；选择器由 `gkd-selector` 独立处理。语法、错误处理和验证工具见 [规则坐标表达式](rule-expressions.md)。

| 数据 | 读取 | 写入与一致性 |
| --- | --- | --- |
| 设置与名单 | `SettingsRepository` 只读状态 | 明确事件更新/保存，`awaitPersistence` 确认落盘 |
| 订阅 | `SubscriptionRepository.snapshotFlow`、订阅 DAO | 仓库协调解析、文件原子写入与数据库补偿 |
| 规则配置 | DAO、共享 `ruleGroupState` | `RuleGroupConfigService` / `SubscriptionConfigStore` 事务校验与更新 |
| 运行记录 | 日志和访问记录 DAO | `RuntimeRecordRepository` 写入、批次提交与裁剪 |
| 快照 | 快照 DAO、`SnapshotStore` | `SnapshotCaptureRepository` 编排采集，Store 提交、替换与删除 |
| 备份与日志归档 | `BackupManager`、`LogArchive` | 共享文件流程，宿主处理选择、分享与保存目的地 |
| Service 与权限 | 平台运行状态 | 真实组件和权限请求宿主管理生命周期 |

业务副作用由明确事件触发，不通过派生展示 Flow 驱动。设置更新先发布内存状态并提交持久化请求；转换函数保持无副作用，恢复时可能应用于当前状态和回滚状态。备份提交持有订阅写锁，数据库回滚和文件补偿完成后释放；设置回滚保留恢复期间的新命令。

路由序列化名称、组件类名、AIDL、数据库结构、生产设置文件名/字段/默认值属于兼容契约。开发模拟配置和 HTTP 协议随模型同步更新，不保留旧格式适配。

两端通过 `AppStorageLayout` 使用真实文件、Repository/DAO 和共享算法。Desktop profile 提供设备、应用及首次导入输入，不维护规则统计、排序等派生事实源。具体目录及缓存消费者见 [开发产物](local-development.md)。

## 依赖管理

- 外部 Gradle 坐标和版本统一放在 [版本目录](../gradle/libs.versions.toml)，固定组合使用 bundle。公共依赖放 commonMain，Activity、Service、权限、特权进程及 GIF 解码放 androidMain，桌面窗口、WebView2 和开发 HTTP 放 jvmMain；调试与测试依赖保留对应作用域。
- gkd-android 通过 `implementation(project(":gkd-app"))` 获取应用实现，不重复声明已由 gkd-app 明确 `api` 导出的库。gkd-app 仅导出消费者需要的 API，Atomicfu、表达式和拖拽等内部依赖保留 `implementation`；模块直接使用且未被导出的库仍显式声明，包括 Android 设备测试使用的 Serialization JSON。
- gkd-app/androidMain 依赖 gkd-aidl 的 Binder 产物。gkd-hidden-api 通过顶层 `remapApi` 提供编译期 stub 与 remap 索引，不作为运行时依赖；remap 插件转换 gkd-app 的 KMP Android 产物。
- remap、priv-ui 和 WebView2 使用发布版本，以版本目录为准。不同发布体系的 UI 版本号不直接等同。验证范围见 [开发规范](../AGENTS.md#验证与-git)。
- npm 开发工具由根 [package.json](../package.json) 管理，私有子包仅声明 workspace 依赖；可发布 selector 包自行声明安装所需依赖。

## 字符串资源

共享文案位于 `gkd-app/src/commonMain/composeResources`，官方插件生成 `li.gkd.app.resources.Res`。资源键按用途命名，例如 `rule_enable_in_app`、`app_count`，不加 `gk_`；无障碍相关键使用 `a11y`。日志、诊断、协议字段、URL 和用户输入不属于固定 UI 文案。

```kotlin
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.stringResource

Text(stringResource(Res.string.app_count, apps.size.toString()))
```

资源解析、占位符与平台归属约束见 [开发规范](../AGENTS.md#资源与-kotlin-规范)。Compose 资源 XML 的 `&`、`<` 使用实体转义，首尾空白直接保留；`${i}` 等持久化通知模板变量是普通文本。

领域和存储错误保留语义、参数与原因，由共享 UI 映射资源；订阅/规则使用 `SubscriptionException`，仓库不接收文案适配器。默认订阅名称只用于创建缺失记录，已有用户名称优先。

普通 Android Kotlin 文案同样使用 Compose Resources；只有系统标签和构建变体等需要原生 ID 的内容留在 gkd-android 的 res 中。`validateStringResources` 只校验占位符，官方插件负责访问器、限定符和打包。翻译使用官方语言目录；当前只有默认中文资源。
