# Repository Instructions

- 除 `app_icon`、`service` 等 Android 平台必须使用 XML 的场景外，禁止新增 XML 文件。
- UI、图标及其他能够使用 Kotlin 表达的实现必须使用 `.kt` 文件，不得为其新增 drawable、layout 等 XML 资源。
- 无法确定是否属于 XML 例外场景时，必须先向用户确认。

## Git 提交与推送

- 用户明确要求提交或推送代码时，只执行必要的轻量核对与对应的 Git 操作；不得自动扩展为深度代码审查、临时 worktree 隔离验证、全量构建或测试、Release Gate、发布检查等重量级流程。只有用户明确要求对应检查时才允许执行。

## Kotlin 可见性

- `gkd-app` 模块内禁止使用 `internal` 关键字；由于没有其他模块会引用 `gkd-app` 模块，对外可见的声明应省略可见性修饰符（使用 Kotlin 默认的 `public`），仅在需要收窄作用域时使用 `private`。
- 与公开属性直接一一对应、仅用于收窄可见性或可变性的 `_xxx` backing property，必须改用 Explicit Backing Fields；不禁止不存在这种直接对应关系的普通私有字段、缓存或生成代码风格命名。未使用的 Lambda 参数占位符 `_` 不受此限制。

## Compose 与状态边界

- 除悬浮窗 Compose 外，应用 Compose 树中的 Composable 都可以通过 `LocalMainViewModel` 获取 `mainVm`，无需逐层转发导航、全局弹窗、打开 URL 等应用级操作。
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

## 构建与测试

- 常规测试只编译 `gkd` 渠道；若用户没有明确指令，禁止运行任何 `play` 渠道的编译任务。

## 测试策略

- 新增测试必须验证可观察行为，明确被测输入、预期输出和要防止的具体回归。优先覆盖纯函数、边界条件、异常路径、平台或版本兼容差异，以及已修复缺陷的回归场景。
- 禁止仅为增加测试而拆散本应聚合的生产逻辑、扩大声明可见性或暴露测试专用 API；测试必须适配合理的生产设计，而不是反向塑造生产代码。
- 禁止新增仅复述生产代码静态声明的测试，包括枚举成员、常量取值或集合、连续编号、由同一注册表推导出的成员关系，以及 Kotlin 类型系统已经保证的约束。
- 只有当常量或标识属于外部协议、持久化格式或跨版本兼容契约时，才允许为其新增稳定性测试，并在测试名称或注释中说明要保护的兼容行为。

## Android API 调研

- 涉及 Android framework Java/AIDL API 的源码定位、跨版本签名或可用性比较、API 缺失原因分析，以及 Java hidden-API 访问代码生成时，必须使用项目内的 `android-api-diff` skill：`.agents/skills/android-api-diff/SKILL.md`。
- 按该 skill 的路由使用 `android-api-diff` CLI，并保留默认 JSON 输出；不得自行实现或模拟 Android API 版本检查。
- 安装或更新项目级 skill 时，在项目根目录运行 `android-api-diff skill install`。
