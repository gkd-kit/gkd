# Repository Instructions

- 除 `app_icon`、`service` 等 Android 平台必须使用 XML 的场景外，禁止新增 XML 文件。
- UI、图标及其他能够使用 Kotlin 表达的实现必须使用 `.kt` 文件，不得为其新增 drawable、layout 等 XML 资源。
- 新增图标资源时，页面及页面内组件使用的图标必须以 Kotlin `ImageVector` 定义，不得使用 drawable XML。
- 只有 `AndroidManifest.xml` 等 Android 平台 XML 配置需要引用的图标及其依赖资源，才允许使用 drawable XML；同一图标同时用于平台配置和页面时，页面仍必须使用 Kotlin `ImageVector`。
- 无法确定是否属于 XML 例外场景时，必须先向用户确认。

## Git 提交与推送

- 用户明确要求提交或推送代码时，只执行必要的轻量核对与对应的 Git 操作；不得自动扩展为深度代码审查、临时 worktree 隔离验证、全量构建或测试、Release Gate、发布检查等重量级流程。只有用户明确要求对应检查时才允许执行。

## Kotlin 可见性

- `gkd-app` 模块内禁止使用 `internal` 关键字；由于没有其他模块会引用 `gkd-app` 模块，对外可见的声明应省略可见性修饰符（使用 Kotlin 默认的 `public`），仅在需要收窄作用域时使用 `private`。
- 与公开属性直接一一对应、仅用于收窄可见性或可变性的 `_xxx` backing property，必须改用 Explicit Backing Fields；不禁止不存在这种直接对应关系的普通私有字段、缓存或生成代码风格命名。未使用的 Lambda 参数占位符 `_` 不受此限制。

## Kotlin 静态初始化

- `companion object` 或 `object` 中由 `object`/`data object` 单例组成的列表、集合、映射及其排序结果必须使用 `by lazy` 初始化；禁止在静态初始化阶段直接构造这类集合，以避免 JVM、JS 和 Wasm 上的循环初始化。

## Kotlin 工具声明

- `gkd-app` 的 `util` 包中，新增或修改的跨文件工具函数和共享工具属性必须声明为与文件名同名的 `object` 成员；扩展函数、类型声明以及仅供文件内部使用的 `private` 实现可以保留为顶级声明。
- `XxxExt.kt` 文件只允许放置扩展声明；普通工具函数和共享工具属性必须移入对应的 `XxxUtils.kt` 或职责明确的同名 `object`。
- Compose 页面、组件及其私有 Composable 不适用上述工具声明规则。

## 通用 UI 组件命名

- 项目自定义、供跨页面或跨文件复用的 UI 组件统一使用 `Gk` 前缀和 PascalCase，命名为 `GkAbc`，包括基础控件和共享业务组件；该规则不受组件所在包限制。
- 名称必须描述组件用途，不再使用 `Perf`、`Custom` 等泛化前缀；具有实际语义的 `App`、`AppBar`、`Rule`、`Subs` 等词保留，例如 `GkAppIcon`、`GkAppBarTextField`、`GkRuleGroupCard`。
- 单组件文件与组件同名；同一组件族的重载、私有实现和配套声明允许放在同一文件，文件以 `Gk` 开头并描述该组件族。
- 组件专属配置类型使用 `GkAbcDefaults`、`GkAbcColors` 等名称；图标组件使用 `GkIcon`，共享图标集合使用 `GkIcons`。
- 页面、页面私有 Composable、Preview、普通工具函数、Modifier 扩展及独立状态管理类型不强制添加 `Gk`；状态对象的 `Render()` 成员不属于独立组件入口。

## Compose 与状态边界

- 主界面的 Composable 和页面 ViewModel 统一通过 `MainViewModel.requireCurrent()` 获取当前 `mainVm`，无需逐层转发导航、全局弹窗、打开 URL 等应用级操作；不再使用 `LocalMainViewModel`。实例由 `MainActivity` 在权限及 Activity Result 宿主绑定后、创建 Compose 界面前注册，ViewModel 清理时按实例身份清除引用。
- `MainViewModel.requireCurrent()` 仅用于已初始化的主界面调用链，不得用于 Service、后台任务或悬浮窗。一次操作获取一次实例并贯穿整个操作，不得在权限等待前后重新获取，也不得在静态字段中缓存；该方法不得自行创建替代实例。
- 路由页面及其私有 Composable 可以直接获取页面 ViewModel，并处理权限和 Activity Result 等平台 UI 行为。可复用组件不得获取页面 ViewModel，只接收所需的状态和事件回调。
- 应用级只读 Flow 由实际消费它的 Composable 直接收集，不要复制进页面 `UiState` 或 ViewModel。普通 Flow 使用 `collectAsStateWithLifecycle`，Paging 使用专用 API，高频状态放在最小消费子树。
- Service 启停、持久化和其他业务副作用必须由明确事件触发，并交给 ViewModel、Repository 或 Store 完成；Composable 不得通过状态监听执行写入。
- `XxxUiState` 和 `XxxUiActions` 只在复用、独立预览或复杂页面契约确有需要时使用。`UiState` 只能表示不可变页面快照，不得包含 Flow、Paging 或高频状态；相同映射存在多个构造路径时再提取私有构建函数。
- ViewModel 的可变状态必须为 `private`，只暴露不可变状态和明确的业务方法。只读 `StateFlow` 使用 Explicit Backing Fields，禁止 `_xxxFlow`/`xxxFlow` 双属性和 `.asStateFlow()`。
- 滚动、焦点、菜单、动画、拖拽和多选等纯 UI 状态留在 Compose；可复用交互逻辑可以封装为 `rememberXxxState`，但不得访问 ViewModel、数据库、Store、Service 或导航。需要原子一致性的多个字段必须由事实源提供同一个不可变快照，业务状态不得通过 `CompositionLocal` 传递。
- Composable 需要根据条件决定是否输出后续 UI 时，禁止使用提前 `return`，必须将 UI 包裹在对应的条件区块中；事件或协程 Lambda 的标记返回不受此限制。

## 状态与副作用

- Room 可观察查询应保持为冷 `Flow`，先在 ViewModel 内按页面一致性边界完成聚合，再将最终页面快照转换为 `StateFlow<Loadable<XxxUiState>>`；`Loading` 表示尚未收到完整首发，`Ready(emptyList())` 表示已加载但结果为空。禁止用空集合伪装初始值，也禁止用计数器、`attachLoad` 等旁路状态推断多个查询是否加载完成。
- `combine`、`map`、`stateIn` 等产生的派生展示状态只能用于渲染和临时 UI 同步，禁止通过 `collect`、`onEach` 或状态 watch 驱动数据库、文件、网络写入以及 Service 启停。
- 持久化和业务副作用必须由明确的用户事件、系统事件或领域方法触发，并在 Repository/Store 中按业务一致性边界完成。允许将单一权威状态同步到幂等外部投影，但同步回调不得再读取其他状态拼装写入。
- `debounce`、`conflate`、`collectLatest` 和互斥锁只能控制调度或并发，不能替代多状态源的原子更新；需要一致读取的状态应聚合为同一个不可变状态对象。

## UI 交互与过渡动画

- 可滚动页面必须在内容末尾提供统一的额外底部留白：普通 `Column` 使用 `GkPageBottomSpace()`，`LazyColumn` 使用 `gkPageBottomSpace()` 添加末尾 item；已有末尾 item 包含空状态等内容时，可在该 item 内使用 `GkPageBottomSpace()`，不得重复添加。高度统一由 `GkPageBottomSpaceDefaults` 管理，不再手写页面底部 Spacer 高度。
- 底部留白必须位于滚动内容内部，让最后一项可以继续向上滚动；它不替代 Scaffold、系统导航栏或 IME inset 处理，也不得在统一组件中重复叠加已由宿主处理的 inset。

- 动画只负责视觉过渡，交互按当前业务或 UI 状态立即响应。禁止因动画未结束、图标变形或旧内容正在退场，给按钮、图标、开关、标题等添加临时禁用态、等待动画完成、延时解锁或额外点击节流；也不得改成在点击回调中吞掉操作。
- 禁用交互必须对应明确的业务前提，例如没有可操作数据、输入无效或权限不足。普通开关的短暂保存、模式切换或对快速点击的假设，不得成为临时锁定控件、扩大禁用范围的理由；写入一致性在 ViewModel、Repository 或 Store 中处理。
- 退场重复内容可以从无障碍导航中隐藏，但不得因此改变控件颜色或增加点击等待。相关测试应验证过渡期间的正常点击和状态切换，不得将这类临时禁用作为正确行为固化。

## 构建与测试

- 常规测试只编译 `gkd` 渠道；若用户没有明确指令，禁止运行任何 `play` 渠道的编译任务。
- 执行界面测试（包括真机、模拟器上的 Compose UI / Instrumentation 测试）前，必须先记录应用原有的自动化开关与运行模式，临时关闭自动化功能（关闭 `enableAutomator` 并退出自动化模式），确认设置已生效后再初始化测试，防止应用自动化干扰测试初始化。
- 测试结束后必须恢复并核对原有自动化设置；测试失败或中断时也必须执行恢复，脚本应通过 `finally` 等清理机制保证这一点。恢复时只还原本次临时修改的字段，不得用整份旧配置覆盖其他设置。

## 测试策略

- 新增测试必须验证可观察行为，明确被测输入、预期输出和要防止的具体回归。优先覆盖纯函数、边界条件、异常路径、平台或版本兼容差异，以及已修复缺陷的回归场景。
- 禁止仅为增加测试而拆散本应聚合的生产逻辑、扩大声明可见性或暴露测试专用 API；测试必须适配合理的生产设计，而不是反向塑造生产代码。
- 禁止新增仅复述生产代码静态声明的测试，包括枚举成员、常量取值或集合、连续编号、由同一注册表推导出的成员关系，以及 Kotlin 类型系统已经保证的约束。
- 只有当常量或标识属于外部协议、持久化格式或跨版本兼容契约时，才允许为其新增稳定性测试，并在测试名称或注释中说明要保护的兼容行为。

## Android API 调研

- 涉及 Android framework Java/AIDL API 的源码定位、跨版本签名或可用性比较、API 缺失原因分析，以及 Java hidden-API 访问代码生成时，必须使用项目内的 `android-api-diff` skill：`.agents/skills/android-api-diff/SKILL.md`。
- 按该 skill 的路由使用 `android-api-diff` CLI，并保留默认 JSON 输出；不得自行实现或模拟 Android API 版本检查。
- 安装或更新项目级 skill 时，在项目根目录运行 `android-api-diff skill install`。

## 嵌入式 UserService 异常边界

- 嵌入式 `UserService` 运行在特权进程中。调用隐藏 API 等可能失败的 Binder 方法，必须在方法最外层以末端 `catch (e: Throwable)` 兜住可恢复错误，并通过 Binder 可传输的异常或失败结果将原始类型、消息和堆栈交给主进程；不得只在主进程捕获，也不得让 `NoSuchMethodError` 等 `LinkageError` 逃逸导致特权进程崩溃。
- `VirtualMachineError` 和 `ThreadDeath` 等无法可靠恢复的终止错误可以原样抛出；不要把它们伪装成普通业务失败。
