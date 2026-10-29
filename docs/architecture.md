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

仓库只读 Flow 在消费处收集；ViewModel 管理业务查询聚合、Paging、加载/失败及保存冲突基准。数据库查询首发前使用 `Loadable.Loading`，已加载空结果使用 `Ready(emptyList())`。搜索/筛选、编辑草稿及基准等自定义页面状态由所属 ViewModel 的 `MutableStateFlow` 持有，页面通过只读 `StateFlow` 和明确事件方法使用，不依赖自定义 Saver 恢复。状态随所属导航条目清理，StateFlow 本身不承担落盘或进程重启恢复。

滚动、焦点、动画等工具状态留在 Compose，使用官方 `rememberXxxState`；简单工具/组件状态确需恢复时允许 `rememberSaveable`，瞬时状态使用 `remember`。Navigation3 和官方组件内部的保存机制保留；主题通过 `rememberAppearance` 跟随组合生命周期。具体边界统一见 [开发规范](../AGENTS.md#ui-与生命周期)。

`AndroidAutomationSessions` 选择真实服务；应用生命周期的 `MatchingRuntime.coordinator` 统一自动规则、业务 HTTP 与单次诊断的输入通道。commonMain 的 `queryRules`、`matchRuleSelectors`、`ForegroundTracker`、`MatchInputReducer` 和规则运行状态承载共享决策；规则快照安装通过 `RuleReconciliation` 保留未受依赖变更影响的状态；Android 引擎保留事件接入、节点缓存和平台调用。服务交接会取消旧上下文，旧输入收尾完成后才能开始新输入。事件使用独立副本与有界队列，资源随所属 Android 组件释放。页面退出不销毁应用仓库；Desktop 场景重建不清空持久化数据。实现边界与操作说明见 [匹配引擎调试](matching-engine-debugging.md)，完整目标见 [重构方案](matching-engine-redesign.md)。

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

`RuleRuntime` 通过可选 `RuleStateObserver` 观察实际状态变化，系统录制器实现该接口；关闭录制时不构造状态快照。`MatchCoordinator` 持有诊断会话的协程生命周期，调试请求作为会话子任务运行，停止/过期取消请求，执行通道仍等待动作收尾；生产运行时不再回调 `MatchDebugRuns`。Android 查询的诊断对象由调用方显式传入，缓存上下文不自行查找全局诊断会话。

协调器与诊断、`queryRules` 与 `MatchWork` 仍有直接协作：前者负责记录真实调度顺序，后者提供统一的取消、提交和动作边界。它们属于同一执行核心，未为包图形式增加接口或转发层。前台判定与规则快照的原子更新、动作期间的规则依赖检查也保留原有一致性边界。

`AndroidAutomationSession` 是两种 Android 运行模式共用的会话接口，`AndroidMatchHost` 接入平台事件。`AndroidForegroundState` 在同一锁内更新前台身份和 `ForegroundRuleSnapshot`；系统应用信息与动作记录已从该状态对象所在文件分离。截图事件/音量触发归 `snapshot`，桌面事件触发订阅刷新归 `subscription`。已注册的服务、Activity、特权入口仍保留原组件名。

## 数据读写与兼容

订阅的坐标表达式在 commonMain 使用 `li.songe:expression:0.1.1` 编译求值。`Expression.compile` 直接解析原始表达式，随后 `validateBindings` 静态检查变量白名单并拒绝所有函数调用，不执行计算；不再保留外层语法解析或字符串改写。不绑定任何函数，也不启用库的标准函数或常量集。支持范围如下，名称区分大小写：

| 类型 | 支持范围 |
| --- | --- |
| 变量 | `left`、`top`、`right`、`bottom`、`width`、`height`、`screenWidth`、`screenHeight`、`random` |
| 数字 | 整数、完整小数（如 `0.5`）、科学计数法（如 `1e-3`）；不接受 `.5`、`2.` |
| 运算 | 二元 `+ - * / %`、单个前缀正负号、圆括号；正负号优先于乘除取余，最后加减，同级二元运算从左向右；`%` 是向零截断除法对应的余数 |
| 函数 | 不支持，包括 `min`、`max`、`clamp` 在内的所有函数调用均被拒绝 |
| 空白 | 词法单元之间允许 ASCII 空格、Tab、CR、LF，不接受其他 Unicode 空白 |

幂运算 `^`/`**`、`pow()`、数学常量、三角/对数等其他函数、隐式乘法、连续前缀符号、方/花括号、赋值和无括号函数调用均在规则表达式编译阶段拒绝。库支持的点分变量名也必须符合 GKD 的变量白名单，不能引入额外能力。当前 143 条本地订阅表达式均属于上述支持范围；此结论不代表未采集到的外部订阅。源码最长 16,384 个 UTF-16 单元，解析嵌套与 AST 深度上限各为 128，超限视为无效表达式。`Position` 懒加载不可变编译结果，每次求值单独绑定所需变量；同一表达式中的 `random` 使用同一次采样，不同求值重新采样。选择器仍由 `gkd-selector` 编译，不经过坐标表达式库。exp4j 只保留在 JVM 测试依赖中用于兼容性对照。

未知变量、所有函数调用及语法错误在规则表达式编译阶段拒绝；静态校验不受前面的除零等算术错误影响。`position` 和滑动起终点的所有已填写字段均参与校验，包括被更高优先级锚点遮盖的字段。有效坐标仍按 `left/right/x`、`top/bottom/y` 的顺序选择；缺少任一轴则无效。规则组缓存同一份校验结果，用于阻止无效规则参与匹配、卡片错误提示及手动编辑提交拦截；坐标错误携带字段路径、错误码、原始表达式和源码区间。无效规则仍可多选及保存启用设置，修复后遵循保存值。卡片以警告按钮替换开关并弹窗展示详情，两者共用固定尺寸容器；设置面板不显示静态错误，保留其他限制提示。

运行时除零/取余零保留 `DIVISION_BY_ZERO` 和源码区间；最终坐标转换成 Float 后拒绝 NaN、Infinity 和溢出。`PositionCalculationException` 在动作执行边界转为失败的 `ActionResult.coordinateError`，本次点击/长按/滑动在注入之前终止，不回退中心点或其他表达式，也不推进成功触发状态。只有未配置 `position` 时继续使用中心点，未配置滑动终点时继续使用起点。诊断开启时错误附带本次求值实际使用的变量（含同一次采样的 `random`），通过 `coordinate_calculation_failed` 事件及现有诊断导出链路输出；关闭诊断时不采集附加输入。

`RuleExpressionCompatibilityTest` 对照本地订阅提取的脱敏表达式夹具、支持范围内的边界语法和固定随机种子生成的算式，验证数值、编译/求值错误阶段及算术错误语义；另验证错误码/源码区间、函数调用拒绝、静态校验与已移除特性的拒绝边界，同时报告模板编译和隔离求值的 JVM 耗时/分配量。夹具采集工具为 `pnpm app-tools expression-corpus --input SUBSCRIPTIONS_DIR --output FILE`，仅读取 JSON 订阅中的 position 和 swipeArg 起终点表达式；报告不含应用标识或节点内容。Android 另以 API 26/33 的 Robolectric 测试验证实际依赖变体与坐标计算；宿主基准不代表真机耗时。

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
- remap、priv-ui 和 WebView2 使用发布版本，以版本目录为准。不同发布体系的 UI 版本号不直接等同。依赖调整需核对直接使用及运行时注册，验证 JVM 和 Android `gkd`；涉及产物或字节码转换时检查最终打包链路。
- npm 开发工具由根 [package.json](../package.json) 管理，私有子包仅声明 workspace 依赖；可发布 selector 包自行声明安装所需依赖。

## 字符串资源

共享文案位于 `gkd-app/src/commonMain/composeResources`，官方插件生成 `li.gkd.app.resources.Res`。资源键按用途命名，例如 `rule_enable_in_app`、`app_count`，不加 `gk_`；无障碍相关键使用 `a11y`。日志、诊断、协议字段、URL 和用户输入不属于固定 UI 文案。

```kotlin
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.stringResource

Text(stringResource(Res.string.app_count, apps.size.toString()))
```

Compose 使用 `stringResource` / `pluralStringResource`，协程使用 suspend `getString` / `getPluralString`。同步边界的低频调用才使用阻塞的 `StringResource.getSync()`；不在静态初始化或高频路径缓存解析文案。

参数仅使用 `%1$s`、`%2$s`，数字、日期、百分比及单位先由 Kotlin 按明确 Locale 和精度格式化。要展示百分号或占位符语法时将其作为参数传入。换行使用 `\n`，XML 的 `&`、`<` 用实体转义；Compose 资源不使用 Android 外层双引号保护空白，首尾空白直接保留。`${i}` 等持久化通知模板变量是普通文本，不随语言切换改写用户模板。

领域和存储错误保留语义、参数与原因，由共享 UI 映射资源；订阅/规则使用 `SubscriptionException`，仓库不接收文案适配器。默认订阅名称只用于创建缺失记录，已有用户名称优先。

普通 Android Kotlin 文案同样使用 Compose Resources；只有系统标签和构建变体等需要原生 ID 的内容留在 gkd-android 的 res 中。`validateStringResources` 只校验占位符，官方插件负责访问器、限定符和打包。翻译使用官方语言目录；当前只有默认中文资源。
