# Repository Instructions

设计与依赖见 [应用架构](docs/architecture.md)，启动和 HTTP 调试见 [Desktop 开发](gkd-app/README.md)，固定目录见 [本地产物](docs/local-development.md)。本文件只维护开发约束。

## 模块与平台

- 模块统一使用 `gkd-` 前缀，依赖方向为 `gkd-android` → `gkd-app` → `gkd-db` / `gkd-selector`，禁止反向依赖。gkd-app 承载完整应用：commonMain 放业务、UI、ViewModel、导航；androidMain/jvmMain 放平台实现。gkd-android 仅负责 Manifest、原生 res、渠道、签名、打包和设备测试；JVM 启动/打包位于 gkd-app，TypeScript 工具位于根 `scripts/desktop`。
- Desktop 仅为开发宿主，不另建产品后端、复制页面或仅为它拆共享模块/空壳 Repository；数据库职责留在 gkd-db。领域逻辑不得依赖 Compose、Android Context 或宿主。Android/JVM 共用实现可直接在 commonMain 使用两端支持的 Java API，出现实际差异或非 JVM 目标时再隔离，不增中间源码集。
- 公共根包及 gkd-app Android namespace 为 `li.gkd.app`，UI/导航/ViewModel 位于 `li.gkd.app.ui.*`；Desktop 同样使用该包层级，以 jvmMain 和职责文件名区分，不设 desktop 包。源码目录与包名一致。gkd-android namespace 为 `li.gkd.android`，Manifest 使用完整组件名。
- androidMain 普通工具、适配、UI 接入及基类按职责归入 `li.gkd.app.*`；已注册 Application、Activity、Service、磁贴、特权入口及紧密关联实现保留原身份。`com.google.android.accessibility.selecttospeak.SelectToSpeakService` 的包名、路径和注册不变。包名整理不得改变组件、路由序列化名称/参数、AIDL 或持久化兼容标识。
- Android 权限、Activity Result、通知、悬浮窗、Service 和特权进程位于 gkd-app/src/androidMain，由真实组件管理生命周期。gkd-aidl 只编译 Binder 协议；gkd-hidden-api 只提供 stub。引用 stub 的模块必须独立完成对应字节码的 remap。
- ViewModel、Repository、业务状态和流程在 commonMain 保持唯一实现，ViewModel 使用 `XxxViewModel` 命名。禁止同名宿主子类、空壳继承、旧路径 typealias 或纯转发包装；共享类默认不可继承，宿主在原 ViewModelStoreOwner 直接创建，复杂/复用构造可用明确工厂。
- 平台差异优先使用对应功能包的函数级 `expect/actual`，实现文件为 `.android.kt` / `.jvm.kt`；禁止整份 ViewModel、Repository 或业务流程 actual 化。能力签名只用公共类型，不导航、弹 Toast 或读取 UI ViewModel；仅 UI 组装可用 Composable expect/actual，显式接收窗口会话。无生命周期工具只持有 Application Context，Activity/窗口引用限于生命周期绑定的 UI 会话。
- 不支持的平台动作统一返回 `PlatformResult<T>`，无数据成功为 `Unit`；不得另造 Unsupported 类型或用 Boolean/null 暗示不支持。契约明确成功含义，保留异常/取消传播，不替代业务结果、用户取消或权限状态。
- 应用仓库由应用生命周期唯一持有，禁止重复创建、可替换全局实例和无意义转发。无独立会话需求时优先使用 object；需要独立生命周期或明确依赖时允许普通类及构造注入。平台依赖先于消费者初始化，所属生命周期结束时清理协程和资源，不为测试增加生产重置接口。

## UI 与生命周期

- 两端共用 commonMain 的生产页面、组件、业务和事件链，模拟输入也经过生产 Repository/ViewModel；共享代码不得读取 Android Application/MainViewModel/Store/Service 或伪造全局实例。Desktop 默认四标签，组件目录仅显式诊断。新增入口同时覆盖双端及下游弹窗、面板、返回路径，不用整页 Toast/占位掩盖缺失；交付需业务、读写、生命周期和验证证据。
- 两端共用 `GkAppNavigation` 注册及 `GkNavigation` 保存状态/条目生命周期；禁止 Desktop 字符串分支注册生产路由、复制平台路由或另写保存/导航规则。普通/编辑页面过渡统一声明，公共页面只注册一次；HTTP 标签等状态读取生产来源。
- ViewModel 只属于根页面 MainViewModel 或具体导航条目；没有业务状态/协程时不创建。根不持有其他 ViewModel 或子 ViewModelStore；子路由用条目标准 API 获取，多次入栈实例独立、出栈清理，不预建、缓存或跨路由借用。导航不重建仓库，宿主退出清理 ViewModel/协程；Desktop 场景重载只重置页面/草稿，不清空数据或模拟配置。
- 路由及私有 Composable 可获取页面 ViewModel、处理平台 UI；主题、通用布局、可复用组件和工具仅接收状态/事件，不通过默认参数、工厂、CompositionLocal 或全局入口隐藏获取。Service、悬浮窗、后台任务使用自身作用域和应用仓库，不借用或补建页面 ViewModel。
- 平台 UI 操作优先显式接收所属窗口会话或事件回调。现有 MainViewModel.requireCurrent() 仅限已初始化的 Android 单主界面接入，不向新通用能力扩散；commonMain、复用组件和后台不得获取，也不引入 LocalMainViewModel。引用绑定所属生命周期，不静态缓存或创建替代实例。
- 仅影响当前展示的搜索输入、过滤和草稿，以及展开、弹窗、滚动、焦点、菜单、动画、拖拽和多选留在 Compose；驱动业务查询、分页、保存冲突或恢复需求的状态由所属 ViewModel 管理。rememberXxxState 不访问 ViewModel、数据库、Store、Service 或导航。组合工作使用 rememberCoroutineScope，稳定且绑定同一组合的 scope 不作冗余 remember key。主题状态、时序及深色优先级双端共用，宿主只给系统主题/色板。
- `XxxUiState/Actions` 仅用于确有需要的复用、预览或复杂契约；UiState 是不可变快照，不含 Flow、Paging 或高频状态；多个构造路径重复映射时才提取私有构建函数。业务状态不通过 CompositionLocal 传递。
- 应用/仓库只读 Flow 在实际消费处用 `collectAsStateWithLifecycle` 收集，Paging 用专用 API，高频状态放最小子树；不复制进 UiState/ViewModel 或纯转发；公共页面接收不可变值和明确事件。ViewModel 可管理业务查询聚合、Paging 缓存、加载/失败和保存冲突基准，不因 map/combine 一律迁出。
- ViewModel 可变状态为 private，只暴露只读状态及明确业务方法；只读 StateFlow 使用 Explicit Backing Fields，禁止 `_xxxFlow` 双属性及 `.asStateFlow()`。Composable 条件输出可用条件区块或清晰的提前返回，优先沿用邻近风格，不为形式增加嵌套或无关重构。
- 共享页面用 `GkScaffold` 接收宿主 Insets（Android 原生，Desktop 模拟），不重复加系统留白。滚动内容末尾用 `GkPageBottomSpace()` / LazyColumn 的 `gkPageBottomSpace()`，高度由 GkPageBottomSpaceDefaults 统一；已有末尾 item 可内嵌但不重复添加，不替代系统栏或 IME Insets。
- 动画不阻塞交互：禁止因过渡、图标变形、普通开关短暂保存或假设快速点击而禁用、节流、延时解锁或吞点击。禁用仅对应数据、输入、权限等业务前提，写入一致性交业务层处理。退场重复内容可隐藏于无障碍导航，但不改变颜色/增加等待；检查覆盖过渡中点击和切换。

## 最小实现与防御边界

- 按明确需求和已确认的问题实现最小改动。不得为假设中的异常、竞态或未来扩展，新增状态、缓存、刷新机制、抽象层或生命周期联动。
- 添加校验、重试、兜底或异常捕获前，先核对依赖 API 和现有调用链的契约；已有保证不得重复实现。不确定契约时先查源码，不用防御代码代替确认。
- 操作只检查自身必需的前提，不因单项操作刷新全部能力，不在 UI、ViewModel、服务层重复执行同一检查。UI 提示与执行前校验确有不同职责时可分别保留。
- 不把未知、加载中或查询失败等同于权限拒绝；不得据此扩大为停服、断连、切换模式或修改用户设置，除非需求明确要求。
- 异常在能实际处理它的边界捕获；禁止仅为返回默认值、重复记录日志或捕获后原样抛出而增加包装。
- 抽象应消除已有重复或隔离真实差异。简单逻辑优先直接调用，不引入仅转发、预留扩展或另建同义状态的封装。
- 保留外部输入校验、权限边界、事务一致性和资源释放等必要保护；新增保护应能说明具体失败场景，以及现有机制为何不足。

## 状态、存储与副作用

- Room 查询保持冷 Flow，在所属 ViewModel 按一致性边界聚合后转为 `StateFlow<Loadable<XxxUiState>>`；Loading 表示首发未完整，Ready(emptyList()) 表示已加载为空，禁止空集合初值、计数器或 attachLoad 推断加载完成。
- 数据库/文件/网络写入和 Service 启停由明确用户、系统事件或领域方法触发，在 Repository/Store 完成一致性处理；禁止用派生 map/combine/stateIn 的 collect/onEach/watch 或 Composable 状态监听驱动。允许单一权威状态同步到幂等外部投影，但回调不再读取其他状态拼装写入。
- 一致读取字段由事实源提供同一不可变快照；debounce、conflate、collectLatest 等调度机制不保证一致性；锁或事务必须覆盖全部相关读写，禁止仅锁消费端便宣称多个独立状态源原子一致。Repository 不读取资源/弹 Toast，错误保留原因交 UI 显示。生产设置文件名、字段、默认值是契约，迁移验证旧数据加载、失败重试、并发写入和恢复回滚。
- 两端复用 AppStorageLayout 与真实 Repository/DAO；文件事务、归档、Cookie、网络配置和维护放共享层，宿主仅适配根目录、编解码、文件选择及系统动作。Desktop profile 只给平台输入，不另存统计、记录、排序或备份恢复事实源。
- 宿主只注入 files/cache/privateFiles 根目录，布局遵循 [本地产物](docs/local-development.md)。图片缓存、网页用户数据、归档、临时文件须实际接入，不能仅创建目录；引擎安装可用工具缓存。Desktop privateFiles 与 files 同根，Android 保留系统私有目录，profile/导入标记/模拟配置在 data 外。
- 自定义产物仅放根 `.local/`，工具标准目录不变。日常数据固定 `.local/desktop/`，禁止数据路径环境变量或 build 目录；`--test` 使用 `.local/tests/desktop/<随机标识>/`，不访问日常、真机或生产数据。写入检查先核对隔离标记，报告/截图在会话内；可复用工具提交源码目录。不得随缓存清理日常数据、备份或未处理恢复记录；不增旧目录回退/双写。

## 资源与 Kotlin 规范

- 禁止新增 XML，例外仅 Compose strings/plurals、Android 原生字符串及语言限定资源、app_icon/service 等平台配置。可用 Kotlin 表达的 UI/图标用 `.kt`；页面图标必须 ImageVector，drawable XML 仅限 Manifest 等平台配置及其依赖，即使同图标双用，页面仍用 ImageVector。不确定例外时先问用户。
- 原生 res 归 gkd-android，gkd-app 不引用其 R；通知小图标通过 Application Manifest `notificationSmallIcon` 的 `android:resource` 注入，运行时校验非零，不持久化 ID。共享颜色用 Kotlin，Compose Resources 位于 gkd-app。
- 共享及普通 Android Kotlin 文案统一官方 Compose Resources（`Res.string/plurals`），不建自定义 UiString/Desktop 文案表或原生副本；只有 Manifest/平台配置/变体标签保留原生 ID，Kotlin 可按需读取平台标签。语义/参数与持久化数据分离，文案不在枚举、单例或静态初始化解析缓存，语言切换不改写用户模板。
- 参数只用 `%1$s`、`%2$s` 等位置文本占位符；禁止数值/精度/补零格式指令，Kotlin 按明确 Locale/精度先格式化。Compose 用官方 Composable API，协程用 suspend getString/getPluralString；getSync 仅限低频同步边界，不用于静态初始化、高频或新增 Compose 路径，不用 runBlocking(Dispatchers.Main)。新增翻译/复数/限定符验证双端选择替换，校验任务不生成平台副本，访问器/打包交官方插件。
- Kotlin 可见性按实际访问需求收窄，优先使用 private；gkd-app 模块禁止使用 internal 关键字，需要跨文件或类访问的声明使用默认 public，其他模块允许 internal。仅为公开属性收窄可见性/可变性的 `_xxx` 字段改用 Explicit Backing Fields；普通私有缓存、生成命名及 Lambda `_` 不受限。避免循环静态初始化。
- 工具按职责组织并优先沿用邻近代码风格；无状态工具可用顶级函数，有共享状态或明确命名空间需求时使用 object，不为形式新增包装。XxxExt.kt 只放扩展，普通工具放职责明确的文件；不借规范调整进行无关重构。
- 跨文件/页面复用 UI 统一 `Gk` + PascalCase，名称描述用途，禁用 Perf/Custom 泛化前缀。单组件文件同名，组件族及配套声明可同文件，文件以 Gk 开头并描述组件族；配置用 GkAbcDefaults/Colors，图标用 GkIcon/GkIcons。页面、私有 Composable、Preview、工具、Modifier 扩展、独立状态及其 Render() 不强制此前缀。

## Desktop 运行时

- 模拟配置、开发 HTTP 和工具不承担跨版本兼容，模型变更同步消费者，可重置，不增迁移/旧格式适配；生产数据兼容不受影响。开发工具统一 TypeScript、根 scripts/desktop 与 `pnpm app:tools`，复用公共 HTTP/控件逻辑，不另建同类 PowerShell/Python 脚本。
- 预览/控制窗口同进程，关闭控制窗口不退出 App 且可重新打开（F12）。模拟器为单一状态源，控件和 HTTP 同命令路径。系统栏由 jvmMain 窗口模拟层实时绘制，时间取当前时钟，系统栏/挖孔统一计算 Insets；不以静态或整页截图替代 UI，不把模拟网络/电量冒充真机。未实现内容如实标识，不宣称模拟成功。
- 不支持仅针对具体动作，以非模态 Toast 显示“当前平台不支持”，不替代整页或伪造成功；编辑/列表/文件业务继续真实执行。用户明确要求的 priv-ui 模拟可修改 Desktop 授权/启动/停止状态，日志标记 simulation，不执行真实特权命令。
- priv-ui 只用公开 PrivilegeScreen、状态/actions，禁用内部 renderer/模拟器或 INVISIBLE_MEMBER；页面及系统提示 Insets 分别注入，宿主模拟状态机与控制窗口特权可用状态同步。使用版本目录的远程稳定版，升级验证双端编译和实际页面。
- HTTP 仅在 jvmMain、绑定 127.0.0.1，不进入 Android 或混用业务服务；状态修改在 UI 线程。语义树/操作/截图明确 app|controls，各窗口节点快照独立；操作执行真实控件，不用直接改状态冒充点击。模拟返回先收 IME，再处理弹窗/编辑/页面。
- 截图区分实时捕获和重新组合渲染，后者不证明当前焦点/滚动/弹窗。Desktop 不替代 Android 像素验收；对照须同页面状态、dp、fontScale、Insets、字体和颜色。设备导出仅 UI 所需输入，设备资料/字体不提交。

## 验证与 Git

- 按改动做最小必要验证：共享逻辑/UI、Desktop 只执行对应 JVM 编译及相关业务测试，不因 commonMain 或文档含双端命令追加 Android。仅 Android 实现、资源、Manifest、AIDL、隐藏 API、依赖/构建链路、专项规则或用户要求才做 Android 验证；默认仅 gkd 渠道，未经明确要求禁止 play。
- UI 开发用 `./gradlew.bat :gkd-app:run`，构建前停止本任务启动的 Desktop JVM，避免覆盖运行中 JAR。网页使用版本目录 WebView2 和系统 Runtime。UI 默认使用对应编译及人工/HTTP 检查，不要求自动 UI 套件；仅为已发生、难以稳定人工复查的重要回归增加小范围自动测试，禁止仅复述静态布局或实现细节。业务状态机、持久化、事务恢复、协议/兼容测试保留，不按 ui 包名删除。
- 新测试说明输入、预期及回归，优先边界/异常/兼容行为；存储测试验证实际读写恢复，不仅路径。测试遵循生产生命周期，可用独立会话和构造依赖实现隔离；全局单例的集成测试复用一次真实应用生命周期及隔离存储。禁止为测试拆散生产逻辑、扩大可见性、增加专用 API、ViewModel、嵌套 Store 或生产重置/替换接口；不以逐类工厂、fork 或 ClassLoader 掩盖生命周期缺陷。
- 不测试枚举/常量集合、连续编号、同注册表推导关系或类型系统保证的静态声明；外部协议/持久化/跨版本标识例外，名称或注释说明保护的契约。
- Android 权限、IME、系统栏、预测返回、无障碍/Service 按需 adb 验证。真机/模拟器交互前记录自动化开关与运行模式，关闭 enableAutomator 并退出自动化模式，核对后再检查；finally 在成功/失败/中断后仅恢复本次字段并核对，不用旧整份配置覆盖。仅 Desktop 检查不改真机。
- 用户仅要求提交/推送时，只做轻量核对及 Git 操作；未经明确要求，不扩展为深度审查、临时 worktree 验证、全量构建/测试、Release Gate 或发布检查。

## 依赖与 Android 专项

- 外部 npm 依赖/版本集中根 package.json，private 子包所有 dependencies/devDependencies/optionalDependencies/peerDependencies 仅用 workspace:。可发布子包自行声明独立安装所需运行时/optional/peer 依赖，公共开发工具仍在根；变更同步清单和 pnpm 锁文件。
- Android framework Java/AIDL 源码定位、跨版本签名/可用性、缺失分析及 Java hidden-API 生成必须使用 [android-api-diff skill](.agents/skills/android-api-diff/SKILL.md) 与其 CLI，保留默认 JSON，不自行模拟版本检查。安装/更新 skill 在根执行 `android-api-diff skill install`。
- 修改隐藏接口/父类实现、系统回调、remap 或相关模块依赖/R8 配置，以及排查相关混淆 Release 故障时，必须使用 [android-hidden-api-r8 skill](.agents/skills/android-hidden-api-r8/SKILL.md)。检查最终应用模块 `gkd-android` 的实际 R8 输入及优化后的运行时契约，不以源码 override、编译成功或 Debug 正常代替；`gkd-app` 可见的 `gkd-hidden-api` stub 不代表最终 R8 可见。默认仅验证受影响的 gkd Release 路径，未经用户要求或证据支持不扩大审计范围。
- 特权进程 UserService 的可能失败 Binder 方法在最外层末端 `catch (e: Throwable)` 捕获可恢复错误，通过 Binder 可传输异常/结果保留原类型、消息、堆栈；不只在主进程捕获，不让 NoSuchMethodError 等 LinkageError 逃逸崩溃。VirtualMachineError/ThreadDeath 等终止错误可原样抛出，不伪装普通失败。
