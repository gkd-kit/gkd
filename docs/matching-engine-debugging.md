# 匹配引擎调试

## 用户入口

在「高级设置 → 日志排查」打开「诊断采集」开关，切换到目标应用复现，通过通知栏「停止」或返回关闭开关结束。停止时自动保存当前现场，动作收尾后更新同一采集目录中的 `diagnostic.json`，从「高级设置 → 诊断记录」进入列表，点击记录查看摘要并分享或删除；长按进入多选，支持全选、反选、批量分享和删除。单条和多条分享统一命名为 `gkd-log-<导出时间yyyyMMddHHmmss>.zip`（本地时间，同秒重名自动追加序号）；分享统一通过日志包入口，包含现有常规日志与环境材料，只附加选中的诊断记录；多条分享为一个 ZIP，保留各自采集目录，其中每次采集保存到 `matching-diagnostics/<开始时间Z>-<短ID>/diagnostic.json`，例如 `20261028073012Z-a1b2c3d4/diagnostic.json`；时间取会话开始时间（UTC），短 ID 取会话 ID 前 8 位。页面和通知栏停止使用同一保存流程；保存失败会提示，分享选中的诊断记录时重试。已保存记录不再按次数或总容量自动清理，由用户主动删除；单次采集的内存、事件和明细上限不变。删除后迟到的 API 收尾不会重新创建该记录；未完成收尾的现场也可以分享，记录明确标注尚未完成。采集没有自动停止时限，由用户主动停止；进入或退出页面不会重建执行引擎。

Android 诊断由独立的 MatchDiagnosticService 前台服务承接，通知正文返回诊断记录页面；需要通知权限。页面与 HTTP/CLI 共用服务启动入口，服务进入前台成功后才开始采集。停止后先保存已有现场，首次保存完成或失败后退出服务，晚到的收尾仍由原保存队列更新同一文件。服务不自动重启采集，不关闭正常匹配；Desktop 直接使用同一协调器，不模拟 Android 通知。采集中仍只保存在内存，不持续落盘。保存只替换目录内的 `diagnostic.json`，保留其他附件；导出递归包含整个目录，用户删除时也按目录整体删除。记录列表只识别包含 `diagnostic.json` 的采集目录，不再兼容旧版单文件记录。

高级设置的「日志排查」分组提供「诊断采集」开关和「诊断记录」入口。记录页使用与规则卡片一致的圆角、背景和间距，按开始时间倒序显示已保存记录和短 ID；左侧图标与多选复选框共用固定占位，切换多选不移动文字，长按支持多选分享或删除。点击记录打开与快照操作一致的底部 Sheet，无顶部图标，显示短 ID、开始时间和采集目录总大小（含附件、未压缩）。列表在后台读取目录总大小，不读取诊断正文；列表与 Sheet 都将大小放在短 ID 后，以间隔号分隔；分享与删除先关闭 Sheet，删除再弹确认框，取消后返回列表。导出仍包含尚未完成的现场。运行记录、条件解释、引擎和前台状态只保留在诊断数据中，不在用户页面展示。

页面不再选择采集模式或规则。按现有订阅、应用、类别和规则开关运行；需要缩小范围时，在原配置页面调整。统一记录阶段、条件解释、节点访问、调度、规则状态及动作收尾。关闭诊断不会修改这些开关。开启开关先展示采集说明，取消不启动，点击“继续”才开始。说明提醒先启用目标规则、确认服务并准备复现步骤，复现后及时关闭，避免敏感输入和长时间采集。UI、HTTP 与 CLI 均由协调器从当前已加载的规则摘要保存轻量 `initialRules`，只包含订阅 ID、版本及已启用规则 ID，不再复制完整订阅、未启用规则或校验失败的定义。尚未加载完成时该值为 null，已加载但无规则时为空列表；它是开始时索引，不代表后续配置历史。

节点查询保留最近 32 次，达到上限后覆盖最早查询并累计 `droppedQueryRecordings`，避免只保留复现前的查询。事件与条件日志也有界保留；被覆盖或截断时，导出保留缺失计数，不能把未记录的阶段解释为没有发生。进行中的查询也有可导出的值快照：`completed=false` 表示尚未结束，已完成读取的参数、返回值和耗时立即保留，`result=null` 不能解释为未命中；查询结束后更新同一条记录。导出不会等待节点调用，也不会保留平台节点引用。停止后仍等待所属动作收尾，新采集需等收尾完成。

单规则过滤、时序参数及单次调试请求保留在 HTTP/CLI，普通用户页面不再展示这些输入。外部规则标识为 `订阅ID/类型/应用ID/分组key/规则key`；应用类型 `2`、全局类型 `3`，全局应用ID为 `*`，无规则 key 时用 `index:索引`。

诊断默认关闭。关闭时不创建记录、不读取诊断时钟、不生成逐选择器说明，也不额外读取节点。普通事件只有原因代码、规则标识和时间，不采集节点文本或截图。条件详情会记录实际读取的属性值，节点录制会保存实际访问的内容，两者都可能包含界面文本；单次调试的导出还包含用户提供的订阅、模拟树及请求结果，分享前检查这些内容。

### 属性条件解释

用户页面统一开启实际参与查询规则的条件详情；CLI 可使用 `--detailed --rule ID` 缩小范围，或使用系统录制加 `detailed: true` 解释全部参与查询的规则。Android 和模拟树都会在实际匹配过程中产生解释。导出按结构化字段保留条件、运算符、左右值及成立、不成立或未求值的结果。例如 `[text='跳过' && clickable=true]` 在文本为“关闭”的候选上，会显示文本条件不成立，短路后的 `clickable=true` 未求值；对应原始记录为：

```text
matches[0] node=2 unit=0 property=0 comparison: text='跳过' left="关闭" right="跳过" -> false
matches[0] node=2 unit=0 property=0 skipped: clickable=true -> not_evaluated
matches[0]:miss
```

事件的 `condition` 保存结构化信息：选择器项 `selectorPart`、完整选择器的限长文本 `selector`、本轮节点对象编号、逻辑单元/属性选择器索引、条件 `expression`、运算符、左右值及 `matched`。编号只按当前一次规则匹配中的对象引用关联，不是系统节点 ID；不同对象可能表示同一个系统节点，编号不能跨运行关联。编号不调用适配器、不读取 id/text/bounds。值分为 string、number、boolean、null、reference；reference 不调用节点的 `toString()`，null 只表示求值返回空，不能区分属性不存在、空值或空接收者。

- `comparison` 使用真实求值的左右操作数；类名匹配用 `name` 和 `class_suffix` 标明短类名后缀语义。函数/成员访问记录整个比较表达式的结果，不展开函数内部每个中间步骤。
- `logical`、`selector_not` 和 `selector_short_circuit` 保留逻辑组合结果；`skipped` 明确没有求值。单个条件 false 不代表规则失败，例如 `!(text='跳过')` 可以整体为 true。排除选择器命中会使规则被排除。
- `relation_exhausted` 表示关系候选没有形成完整匹配路径；`cached_failure` 表示复用本次搜索已确认的失败，不重读属性。
- `fast_query_scope` 表示只检查快速查询返回的候选；`no_candidates` 表示没有候选进入该次遍历。不会关闭 fastQuery 再扫描，也不会捏造被剪枝节点的失败属性。

记录在条件求值后立即送入同一诊断缓冲，界面、SSE、导出及 `matching-summary` 都能查看；不等整条查询结束才输出。异常/取消中断时保留此前记录，并标记该选择器 `incomplete`，不把没完成当作 miss。每次规则匹配最多展开 200 条属性/逻辑/关系记录，达到上限发出 `limit_reached` 后继续原匹配；字符串值最多 256 字符并带 `truncated`，选择器和表达式展示最多 2048 字符。单次会话的环形覆盖数量仍单独报告。摘要的 `recentConditions` 只展示最近 20 条，`omittedConditions` 表示摘要省略数量，原始导出仍保留缓冲内记录。

普通匹配与解释匹配复用比较运算、短路和关系搜索算法，通过内联的回调分离观测代码；普通入口不创建观察器、节点编号表或解释对象。关闭诊断和普通模式都不读取属性作解释、不读解释时钟、不格式化值；停止/切换会话后旧观察器不能向新会话写入属性。

### 从采集包开始排查

普通分享日志不包含历史诊断；从诊断记录页分享时，日志 ZIP 附带选中的记录。开发者从 `matching-diagnostics/` 下对应采集目录取出 `diagnostic.json`，无需保持手机连接。CLI 可离线生成摘要：

```powershell
pnpm app:tools matching-summary --input .local/diagnostics.json --output .local/diagnostics-summary.json
```

摘要包含单次请求的结果与错误、最慢的 20 次运行、最近 20 条失败/取消记录、常见原因及最后的引擎/前台观察；不会重复输出订阅定义与模拟节点树。原始文件仍是完整证据，摘要会显示省略的原因组数和失败/取消记录数。UI 和 HTTP 导出均可读取，专用性能/手势测试报告不属于此格式。

先看 `warnings` 和 `requests`，再按 `runId`、`firstSequence`/`lastSequence` 回查原始事件。缺少开始或终态时耗时为 `null`；`durationMillis` 包含查询、等待及动作，不是单独的选择器耗时。`cancellationMillis` 是记录中的取消请求到运行终态，不代表真实触点抬起延迟。`Completed` 只说明运行结束，是否匹配及动作是否成功要看请求结果或相应阶段记录。摘要按阶段终态、单次请求终态、调度 finish 三类证据合并运行结果，并在 `terminalSource` 标明来源；只有调度收尾证据时标为 `Finished`，不推断成功、取消或匹配结果，也不补造耗时。停止会话后任务仍可能是 `cancelling`，摘要会提示，不能据此断言输入已释放。`runtime` 是停止时快照，后续收尾证据已确认的运行不会再据此提示当前仍卡住。

界面的「高级调试」支持粘贴下文的单次请求 JSON。「仅匹配」不会发送点击或滑动；「匹配并执行真实动作」在 Android 使用真实节点和输入能力，在 Desktop 必须提供模拟树。两种模式都使用独立规则运行状态，不推进正常运行规则的次数或冷却。前置规则依赖不满足时会直接报告原因，不能用单次请求伪造前置规则已执行。

## Desktop 与 CLI

启动隔离宿主：

```powershell
./gradlew.bat :gkd-app:run --args=--test
pnpm app:tools matching-debug --operation start
pnpm app:tools matching-debug --operation readiness
# 将上一步的 id 作为下列 SESSION
pnpm app:tools matching-debug --operation status --session SESSION
pnpm app:tools matching-debug --operation events --session SESSION --after 0
pnpm app:tools matching-debug --operation watch --session SESSION --duration 10000
pnpm app:tools matching-debug --operation run --session SESSION --input .local/request.json
pnpm app:tools matching-debug --operation run-status --session SESSION --run example-1
pnpm app:tools matching-debug --operation cancel --session SESSION --run example-1
pnpm app:tools matching-debug --operation stop --session SESSION
pnpm app:tools matching-debug --operation export --session SESSION --output .local/diagnostics.json
# 或者一次完成有界采集、停止和导出
pnpm app:tools matching-debug --operation capture --duration 20000 --output .local/diagnostics.json
```

运行同一请求 ID 和相同内容会返回原任务；同一 ID 改变内容会被拒绝。每个会话最多 100 个单次请求。输入通道忙时，单次任务报告失败，不覆盖正在执行的动作，不自动重放。`start` 不设置采集时限；`capture --duration` 仅让命令行等待指定时长后主动调用停止，`watch --duration` 仅控制订阅时间。会话没有自动停止时限，默认环形缓冲保留 2000 条事件；分页的 `gap` 和会话的 `dropped` 表示记录已被覆盖。

单次请求示例：

```json
{
  "requestId": "example-1",
  "rule": "1/2/app.test/1/1",
  "subscription": "{\"id\":1,\"name\":\"Test\",\"version\":1,\"apps\":[{\"id\":\"app.test\",\"groups\":[{\"key\":1,\"name\":\"Test\",\"rules\":[{\"key\":1,\"matches\":\"[text=\\\"target\\\"]\"}]}]}]}",
  "execute": false,
  "simulation": {
    "app": {
      "id": "app.test", "name": "Test", "versionCode": 1, "versionName": "1",
      "isSystem": false, "mtime": 0, "hidden": false, "userId": 0
    },
    "root": {
      "name": "Root",
      "children": [{"name": "Button", "attributes": {"text": "target"}}]
    },
    "rootDelayMillis": 0,
    "actionMillis": 10000,
    "cleanupMillis": 500
  }
}
```

模拟操作明确输出 `simulation: true`。`actionMillis` 与 `cleanupMillis` 可以复现长动作和取消后的收尾；取消后处于 `cancelling`，收尾完成才进入 `cancelled`。Android 请求删除 `simulation`，替换成实际应用、订阅和规则标识。

时序策略通过下列命令读取或整份替换，仅在当前进程生效：

```powershell
pnpm app:tools matching-debug --operation policy
pnpm app:tools matching-debug --operation policy --input .local/timing.json
```

策略包括内容事件合并、前台确认重试及等待窗口、根节点等待、后续查询间隔与窗口、页面稳定期、服务交接等待。新查询读取新策略；已经启动的等待不会因为修改参数自动重放。当前是可调参数策略，不是自动学习或自动调参。

协议检查使用真实共享核心，覆盖仅匹配、重复请求、不同请求内容冲突、模拟动作取消收尾及详细选择器记录：

```powershell
pnpm app:tools check-matching-debug
```

## Debug 真机入口

### 保留运行现场

已安装带调试入口的 Debug 版并且应用进程正在运行时，使用 `--attach`：

```powershell
# 汇总当前条件，不重启、不切页面、不修改自动化或匹配开关
pnpm app:tools device-app-debug --serial DEVICE --attach --operation readiness
# 对当前现场进行 20 秒有界采集，退出时停止并导出本次会话
pnpm app:tools device-matching-debug --serial DEVICE --attach --duration 20000 --output .local/live-diagnostics.json
# 分步采集；重复命令连接同一进程和服务
pnpm app:tools device-matching-debug --serial DEVICE --attach --operation start
pnpm app:tools device-matching-debug --serial DEVICE --attach --operation events --session SESSION
pnpm app:tools device-matching-debug --serial DEVICE --attach --operation stop --session SESSION
pnpm app:tools device-matching-debug --serial DEVICE --attach --operation export --session SESSION --output .local/live-diagnostics.json
```

如果用户已经在界面开启诊断，`readiness.session.id` 就是可观察或导出的会话 ID；直接使用 `events`、`watch`、`status` 或 `export`，不要再 `start`。新建会话在已有活动会话时会被拒绝，不会覆盖用户采集。`stop`、`cancel`、`run`、修改 `policy` 仍是显式操作；连接或读取状态本身不会执行它们。

连接模式先确认主进程 PID，再幂等启动 Debug 服务、认证并建立本次 ADB 转发，操作后核对 PID。未运行时直接失败，不自动打开应用；期间进程退出或被系统重建会报错，不能把新进程的状态当成原现场。凭据可能残留时通过认证验证，不删除现有凭据、不输出 token。退出只移除本次转发，不停止共享 Debug 服务；服务不再定时退出，可以通过前台通知中的“停止”按钮结束。诊断会话仍按各自的采集期限停止，重复连接不会改变现有会话。

`--attach` 不允许与安装、打开测试页面、性能基准或手势测试组合；这些操作继续使用下面的受控测试流程。首次升级到包含新接口的 APK 仍需正常安装，安装可能重建进程；完成升级后再复现现场并连接。连接只增加 Debug 前台服务及 HTTP 诊断开销，不代表零性能影响。

### 统一就绪检查

`GET /debug/readiness` 同时返回应用开关、应用目录加载/刷新/错误、引擎、平台前台观察，以及当前或最后一次诊断会话。它不读取节点、不刷新仓库、不请求权限、不取消动作。各来源是一次请求中的依次观察，并非同一时刻的系统原子快照；执行时仍以生产通道的实际检查为准。

| `checks` 项 | `ready` 的含义 |
| --- | --- |
| `observe` | 接口已响应，可以读状态；服务未连接、锁屏或通道忙仍可观察 |
| `startCapture` | 当前没有活动诊断，可尝试新建采集；不要求节点服务可用 |
| `liveQuery` | 已观察到有效节点服务、亮屏、确认前台、已加载应用目录，且执行通道当前空闲 |
| `androidBenchmark` | 实时查询条件满足，并且未锁屏、普通规则匹配关闭、没有活动诊断 |

`reasons` 使用可检索代码：`service_not_effective`、`screen_not_interactive`、`foreground_pending`、`foreground_unknown`、`engine_not_attached`、`engine_busy`、`catalog_not_loaded`、`catalog_load_failed`、`screen_locked`、`rule_matching_enabled`、`diagnostic_session_active`。Desktop 的实时节点查询与 Android 基准返回 `live_nodes_unsupported`，不影响模拟查询或采集。已有目录在刷新失败后仍可使用，错误保留在 `catalog.error`；锁屏仅阻止基准，不直接阻止对已确认锁屏界面的查询；任务包与节点包不同不单独判错。

就绪不保证目标规则存在、节点能读到、权限允许注入或动作必定成功。`liveQuery`/`androidBenchmark` 不预留通道，下一刻变忙时仍可能被实际执行拒绝。Android 基准和手势检查工具已使用此接口等待共同前提，再检查各自需要的目标页面。

### 准备受控测试

先构建 Debug，再运行有界采集：

```powershell
./gradlew.bat :gkd-android:packageGkdDebug
pnpm app:tools device-matching-debug --serial DEVICE --install --duration 20000 --output .local/device-diagnostics.json
# 可选：采集期间提交给定规则；默认 execute=false
pnpm app:tools device-matching-debug --serial DEVICE --input .local/request.json --duration 20000
```

工具沿用自动化暂停与恢复流程：记录开关和模式、停止进程、关闭自动化并退出自动化模式、启动 Debug 服务、通过 ADB 转发连接、启动诊断、可选单次请求、有限时间读取 SSE、停止并导出，最后关闭服务和端口转发并恢复本次修改的两个字段。恢复失败时保留 `.local/device-recovery/` 下按设备标识保存的记录。工具不会为了观察正常自动化而静默重新启用开关；正常自动化问题可直接在应用内开始采集。

CLI 详细模式与界面使用同一入口：在 `start`、`capture` 或 `device-matching-debug` 后添加 `--detailed --rule '订阅ID/类型/应用ID/分组key/规则key'`，记录各条选择器命中情况；缺少规则标识时在建立会话前报错。对已经创建会话的有界采集，Ctrl+C 或 SSE 失败仍会尝试停止并导出；停止失败也会继续尝试导出，同时保留原错误并以非零状态退出。成功写入后在标准错误输出打印 `Diagnostic export: 路径`。进程被强杀、服务断连或启动前失败无法保证导出；设备恢复记录仍按原流程保留。

`li.gkd.app.service.DebugService` 统一承载浏览器接口 `/api/*` 和应用诊断 `/debug/*`，只监听用户配置的 `httpServerPort`（默认 8888）。Android 支持局域网连接；诊断接口和 `/health` 必须提供随机 Bearer token，旧浏览器接口保持原协议。诊断接入实现位于 `gkd-app/src/androidDebug`，由最终应用 Debug 源集编译并通过 Debug Manifest 元数据装配；Release 不包含该接入实现或注册诊断路由。Debug 构建允许 ADB 通过 `android.permission.INTERACT_ACROSS_USERS_FULL` 保护的同一服务入口启动；Release 服务不导出。每次服务启动生成 token，和实际端口一起写入私有目录的 `app-debug.json`，工具通过 `run-as` 读取，token 不输出到日志和诊断包。重复启动沿用现有监听与 token；修改端口会重启监听并更新凭据文件，工具需重新连接，采集会话继续。通知“停止”关闭统一监听、删除凭据、取消服务自有诊断会话，并按设置清理内存订阅；动作通过执行引擎完成取消收尾。

此服务已由匹配专用入口扩展为应用级 Debug 入口，凭据文件为 `files/private-store/app-debug.json`。停止服务只结束它创建且仍为当前会话的匹配诊断，不结束用户之后在界面开启的会话。应用状态和应用列表直接读取生产仓库，不导出 Cookie 或完整设置。

```shell
pnpm app:tools device-app-debug --serial DEVICE --operation app
pnpm app:tools device-app-debug --serial DEVICE --operation catalog --output .local/app-catalog.json
pnpm app:tools device-app-debug --serial DEVICE --operation foreground
pnpm app:tools device-app-debug --serial DEVICE --operation capabilities
```

这些命令也会暂停并最终恢复自动化；首次启动尚未写入设置文件时，按生产存储契约使用默认设置。`foreground` 返回引擎已收到的观察，不发起额外 Binder 查询：`task` 是最近任务栈观察，`nodeAppId` 是最近节点观察，`confirmed` 是已确认交互界面，`pending` 是待确认候选，`reason` 是最近决定原因。它们不是同一时刻的系统原子快照；无特权任务栈来源时 `task` 可为空。`interactive`、`locked` 与 `serviceMode` 帮助区分熄屏、锁屏和服务不可用。

所有 HTTP 路径以 `/debug` 开始：

| 操作 | 方法与路径 |
| --- | --- |
| 应用状态 / 已加载应用列表 / 统一就绪 | `GET /debug/app` / `GET /debug/app/catalog` / `GET /debug/readiness`，Android Debug 与 Desktop 共用 |
| Android 前台来源 / 可用入口 | `GET /debug/android/foreground` / `GET /debug/capabilities` |
| 创建会话 | `POST /debug/sessions` |
| 状态 / 增量记录 | `GET /debug/sessions/{id}` / `GET .../{id}/events?after=0&limit=200` |
| 单次任务 / 查询 / 取消 | `POST .../{id}/runs` / `GET .../{id}/runs/{runId}` / `POST .../{id}/runs/{runId}/cancel` |
| 停止 / 导出 | `POST .../{id}/stop` / `POST .../{id}/export` |
| SSE | `GET .../{id}/stream`，支持 `Last-Event-ID`；`trackingComplete=true` 且 `hasMore=false` 才结束，停止后继续发送迟到完成及心跳 |
| 时序 | `GET` / `POST /debug/policy`；写入仅限活动诊断会话，结束时恢复 |
| 引擎快照 | `GET /debug/engine`；连接、运行、待查询、下次唤醒、规则延迟数、当前策略及平台前台观察 |

SSE 从同一有界缓冲读取，不直接订阅执行线程；客户端慢不会阻塞输入。停止诊断同时请求取消它创建的单次任务，但返回 `active=false` 不表示物理输入已经结束，应检查任务是否仍是 `cancelling`。

### 目前仍需手工判断的部分

- 就绪检查汇总当前条件，但不检查实际节点、规则兼容性或输入权限，不能替代执行结果。
- 属性解释仅覆盖真实到达的条件和候选，受短路、快速查询和记录限额影响；显式录制保存实际节点访问、耗时和异常，不包含未读取的节点，也不执行离线重放。
- 未使用 `--attach` 的受控测试仍会重启应用并暂停自动化；正常运行现场使用连接模式，或从应用内采集后离线分析。
- SSE 适合实时观察，但不能代替持久化采集包。环形记录覆盖、采集结束早于收尾等情况仍需结合摘要警告与原始事件解释。

## 时序调整与现场快照

共享诊断页现在展示服务模式、屏幕/锁屏、Task 与节点来源、确认/待确认界面、当前运行和下一次唤醒。可编辑时序 JSON 并应用到活动诊断会话；停止或拥有该会话的服务退出时恢复会话开始前的参数。运行中的查询保留其策略快照，后续查询使用新参数；引擎补查从原调度起点重算，规则 matchDelay/actionDelay 不被改写。

`query_started` 事件携带该次查询的 policy，参数变化记录为 policy_updated；session.policy 始终是会话开始时的参数。导出 runtime 是导出时快照，不是所有事件同一时刻的系统状态。Desktop 的 foreground=null 明确表示没有 Android 前台观察。

```shell
pnpm app:tools matching-debug --operation engine
pnpm app:tools matching-debug --operation policy --input .local/timing.json
pnpm app:tools device-app-debug --serial DEVICE --operation engine
```

参数写入前先开始诊断。页面不展示运行记录，完整证据从诊断文件或 HTTP 获取；停止采集后，已有单次任务处于 cancelling 时页面继续刷新到终态。设备采集命令收到 SIGINT/SIGTERM 后会取消流读取，随后停止服务、移除转发、恢复自动化字段；强制杀死进程或设备断开后可使用 `device-recovery` 恢复；新采集会先检查同一设备是否存在未完成恢复。
本轮补充验证（2026-10-21）：75 项规则 JVM 测试通过；Desktop 协议覆盖策略快照、恢复和可中断 SSE；共享页面实测参数编辑/筛选/停止恢复，以及停止诊断后仍刷新取消收尾。最终 Debug 包已重新安装 Android 13，/debug/engine 返回服务未连接、无运行任务及实际屏幕/锁屏信息，认证检查通过，自动化字段已恢复。本轮没有重新启用真实匹配或执行手势。

## 输入现场

诊断导出中的 `session.inputCheckpoint` 保留第一条捕获输入之前的状态；事件的 `input` 与 `inputDecision` 保存公共输入和实际判断。输入包括 system_surface、foreground、accessibility、service_changed、screen，带序号、相对单调接收时间、来源时间和当时策略。

使用 `matching-summary` 查看 `inputs`，结合原因、前台状态和取消要求分析；不重新计算判断。事件缺口和覆盖数量保留在导出中。

## 入口诊断、规则快照与恢复

导出中的 `entries` 按入口和原因累计整个会话的次数，不随事件环形缓冲覆盖丢失。入口覆盖事件初筛/消费、查询请求/启动、前台确认、后续轮询及协调器接收。常见原因包括 `matching_disabled`、`screen_not_interactive`、`foreground_pending`、`foreground_wait_expired`、`service_not_effective`、`event_foreground_mismatch`、`service_start_settling` 和 `follow_up_window_expired`。`rules_loading`、`rules_load_failed` 与 `no_rules_for_activity` 分别表示加载中、加载失败和加载完成但当前界面无可用规则；不能混作同一原因。

普通运行在诊断开始、规则快照更新和查询绑定规则时记录 `rule_snapshot`，通过 `ruleSnapshotId` 关联导出的 `ruleSnapshots`。快照包含当前页面、加载状态、阻止匹配状态、候选及选中规则 ID。`definitions` 引用独立的 `ruleDefinitions` 字典，仅保存当前选中的规则和递归执行依赖；定义包含订阅版本、组级继承字段（不含整组规则）、单条规则、实际生效的时序参数及用户排除配置，不包含示例链接。相同定义跨快照去重，规则或配置变更会获得新的引用，同一订阅版本内的本地修改也可区分。仅保留最近 16 份页面快照及其引用的定义，淘汰数量通过 `droppedRuleSnapshots` 明示，避免去重表随采集无限增长。候选但未选中规则只保留 ID，不能据此还原其完整筛选条件。它不包含全部已停用订阅、节点树或物理动作重放资料，也不提供冷却计数的完整运行状态回放。

每个会话保留最近 16 份规则快照，超出后增加 `droppedRuleSnapshots`；事件引用不到的旧快照明确属于采集缺口。事件缺口继续由 `session.dropped` 和分页 `gap` 表示。`scope` 标明导出范围。规则定义不受逐条规则的日志筛选器裁剪，以保留依赖上下文；关闭诊断不构造快照和原因计数。

恢复记录统一保存在 `.local/device-recovery/`，按 ADB 实际设备标识区分，并保存原采集目录与这次修改的字段。`capture-profile` 和设备诊断共用同一恢复流程：开始前发现同设备待恢复记录便停止，不会用当前暂停状态覆盖原值。

```shell
pnpm app:tools device-recovery --serial DEVICE --operation status
pnpm app:tools device-recovery --serial DEVICE --operation restore
```

设备重连后执行 `restore`：校验设备身份，停止应用，读取最新设置，仅合并 `enableAutomator`/`automatorMode`（原字段不存在时恢复为不存在），暂存传输成功后原子替换设备设置，读回核对并重启应用，最后删除恢复记录。中途断连或核对失败会保留记录，可再次执行；没有记录时返回 `restored=false`。它不覆盖整份旧配置。历史手工测试生成的旧 `automation-recovery.json` 仍是当时证据，不由新格式推测设备归属。

## 实际节点访问、耗时与关联

诊断页默认采集节点访问。CLI 的 `--record-queries --rule ID` 仅记录指定规则；`--record-system` 包含全部参与查询。关闭采集时不创建节点访问记录，不增加节点读取。

`queryRecordings` 保留选择器计划、源节点、实际调用参数、返回值、异常与最终结果。每份查询独立保留 `startedAt`、`endedAt`、`durationNanos` 和 `outcome`（matched/not_matched/failed/cancelled），未完成时结束时间、耗时和终态为空；摘要不依赖事件环形缓冲。这里的耗时是选择器查询范围，不包含整个运行的其他等待或动作。每条调用的 `startedNanos` 相对该查询记录开始，`durationNanos` 测量实际委托调用，不包含结果序列化；同时记录线程、异常类型及消息。

查询的 `operationId` 关联 `SelectorQuery` 操作；关键节点调用的 `operationId` 关联 `Selector.*` 操作，等待记录的 `parentOperationId` 可关联到该调用。查询内成功的 Selector 调用不再重复写事件；等待与实际读取的完成详情保存在查询的 `operations` 中，开始/完成不再重复写事件。异常和停止后收尾仍进入事件流。调用及物理读取详情共用原有字符容量预算，截断会标记 `incomplete`，摘要仍会收尾。未返回的关键调用在 `session.openOperations` 中保留参数、运行、规则、线程和开始时间，即使完成记录尚未产生也能查看。

平台读取记录为 `NodeRead:Root/Child/Parent/FindText/FindId/Refresh/Source` 等，等待记录为对应的 `*Wait`。两者保留同一 runId、规则和页面标识，实际读取的 parentOperationId 指向等待操作，等待操作再指向触发它的 Selector 调用（包含惰性迭代时的 hasNext/next）。底层读取保留实际节点引用、子节点索引或搜索文本/ID；录制内节点引用与选择器调用共用编号，未开启节点录制时使用进程内 node_identity 标识。等待可能已超时或取消，而底层调用仍未返回；不能把等待耗时当成 Binder 实际执行耗时，也不能把重叠耗时相加。缓存命中和本地属性读取不是 Binder 调用。

运行级 `Query`、`Action` 操作记录完整查询与动作函数耗时、结果和失败。动作耗时包含它的异步等待与收尾，不等于触摸注入 API 本身的耗时，也不证明物理触点已释放。节点点击/长按、全局返回、dispatchGesture 提交与 GestureCallbackWait 分别计时；特权 tap/swipe/keyevent 路径记录调用方等待耗时，不宣称是远端服务内部的单次注入耗时。动作协程携带父操作关联，切到 IO 线程或不可取消收尾时仍关联原 runId 和规则。

普通查询采集最多 8 次；系统诊断滚动保留最近 32 次。每次最多 10000 个调用、约 100 万字符，单个文本最多 16384 字符。超限记录 `incomplete` 和丢弃计数，实际匹配继续。数据只覆盖实际访问过的节点，不是完整 UI 树。

离线分析无需启动 Desktop 或连接手机：

```shell
pnpm app:tools matching-summary --input .local/capture.json --output .local/summary.json
```

摘要提供 `inputs`、包含调用详情与独立摘要的 `queryRecordings`、按查询完整耗时排序的 `slowestQueries`、合并查询内记录及异常事件并按 ID 去重的 `operations`、`slowestNodeCalls`、`operationsAtStop`、`openOperations`、`completedAfterStop` 和 `systemRecording.timeline`。`runDetails` 按 runId 关联事件序号、查询索引、系统步骤和操作 ID；请求通过 engineRunId 对应同一运行。仅剩请求终态的运行也保留关联条目，缺失的事件、查询、系统步骤和操作列表为空，不补造证据。规则定义通过原始包的 ruleSnapshots、ruleDefinitions 和 initialRules 查看。摘要不重新执行任何业务逻辑。

## 采集收尾与历史验收

关闭诊断时，无障碍事件直接进入同一过滤逻辑，不构造 `MatchEventContext`、`MatchInput.Accessibility`、`MatchInputRecord` 或该事件的 `MatchInputDecision`；队列仍保留实际需要的 `MatchEventKey` 和接受后的事件副本。其他输入也只在采集开启时构造 `MatchInputRecord`。序号、时间和过滤状态始终连续更新，开启诊断后仍记录完整上下文、接受/拒绝原因及采集前状态；未引入对象池，采集中不持续落盘。

匹配输入只接受窗口内容变化（`Content`）和窗口状态变化（`WindowState`），其他事件类型及组合类型在入口过滤，不再导出 `Other`。排队事件的应用和类名统一来自 `MatchEventKey`。动作取消由所属运行的 `cancelling` 表示，不再另存动作级 `cancellationRequested`；动作内容仍保留至平台返回，取消请求不等于动作完成。

输入处理器和调度器仅从正常初始状态启动，不支持从诊断检查点恢复执行。`inputCheckpoint`、前台检查点及系统录制的初始/最终状态仍作为只读诊断快照导出，用于解释采集开始前的待确认前台、事件合并时间和调度现场。输入序号与接收时间由串行输入入口维护，不再按外部重放记录校验连续性；无障碍来源时间的过期事件过滤仍按生产逻辑执行。

保留期内，历史会话的请求列表和单请求状态从诊断会话读取，与整包导出的 `runs` 使用同一数据源；新会话复用 requestId 不覆盖旧请求。诊断请求属于会话协程作用域，新会话要等旧请求发布终态并完成收尾后才能启动，因此历史请求只读，不跨会话补录终态。取消操作仍只作用于当前请求缓存中的任务。物理 API 可以比请求任务更晚返回，仍保留跨会话的 API 完成和查询结果补录。旧查询在新会话启动后发生的超时或容量不足，不会向新会话追加压力事件；停止后不恢复普通事件采集。此行为不增加采集中持续落盘。

CLI capture 在停止所属会话后，只轮询会话的 `trackingComplete`；该状态已经包含已跟踪的 API、查询、请求和系统收尾，无需另查请求列表。最多等待 30 秒，完成后导出。超时或读取失败仍尝试保存采集包，然后返回错误，不把超时当成物理输入释放。已停止的会话不再追加普通阶段事件，因此请求终态和事件时间线可能有不同截止点；摘要会使用请求终态及调度收尾补齐判断，仅在这些证据也缺失时提示未知。普通自动化不是诊断会话拥有的任务，不会因停止采集被取消。UI 导出保持即时快照语义，可在取消结束后再次导出。

watch 现在将 `--after SEQUENCE` 作为 Last-Event-ID 发送，重连可以接着读取；服务返回的 gap 仍表示事件已被覆盖，不会由重连补造。

此前离线重放的历史验收见 matching-engine-redesign.md；当前入口只保留诊断采集与摘要。

证据保存在 `.local/refactor-remaining-*`：final-validation.log、tools-final.log、protocol-final.log、export.json、replay.json、capture.json 和 ui-check.json。隔离 Desktop 进程已清理，未操作任何真机设置。

## 系统调度、规则状态与动作现场

诊断页统一采集；HTTP 使用 `recordSystem: true`，CLI 使用 `--record-system`。系统步骤保留实际调度命令及效果、规则状态前后值、共享计数/冷却关联、动作开始、取消、返回/异常和执行结束。初始与最终状态包含服务代次、任务、延迟和策略，用来解释采集边界。数据不包含平台可执行对象。

系统步骤最多 20000 条、约 800 万序列化字符，超限标记 incomplete。查询记录独立保留最近 32 次。诊断不能证明未采集的历史，也不会按旧记录再次执行动作。

停止时先保存不可变现场，包含未返回操作和 `sealed=false` 的系统记录；收尾完成后更新同一会话文件。分享日志会等待已排队的文件写入，不等待动作结束。为保留旧会话收尾和避免串会话，新采集仍需等待当前会话收尾；这不妨碍保存、分享当前现场。普通自动匹配不会因为停止诊断而被取消；诊断拥有的请求按原生命周期取消。

`systemRecording.sealed` 只表示停止时所属运行的收尾已被记录，不表示动作成功或触点一定释放。时间线使用单调毫秒；节点与操作耗时使用单调纳秒。停止后不新增普通阶段事件；session.operationsAtStop 保留停止瞬间未返回的操作，session.openOperations 则随已开始的操作实际返回而更新。迟到完成以 operation_finished_after_stop 保留原操作 ID、完整耗时和结果，并更新同一诊断文件；系统步骤仍可继续记录收尾。新会话创建后，旧会话仍按自身 ID 和原单调时钟接收迟到完成、更新原文件，旧查询后续读取也不会串入新会话。尚未完成跟踪的历史会话保留在内存中；已完成会话保留最近更新的 5 份供状态、分页和导出查询。`trackingComplete` 表示已跟踪的 API、查询、请求及系统收尾均结束，不代表动作成功或未记录的物理操作已结束。

动作终态保留原 ActionResult 或异常类型/消息。坐标错误保留 coordinateError 的字段、表达式、错误码、范围及实际输入；失败结果不累计成功次数。最早一条动作可能已在采集开始前启动，初始状态保留该动作，不补造开始事件。

HTTP 导出、自动保存和日志压缩包统一使用 MatchDiagnosticCapture，包含单次请求 runs、运行环境 runtime、runtimeCapturedAt、应用标识/版本及平台；JSON（包括 SSE 数据页）保留默认值和空字段，不依赖读取方代码恢复当时的默认配置。活动会话导出时刷新运行环境；停止时在恢复临时时序策略及取消请求前冻结运行环境，后续完成更新不覆盖这一快照。它是缓存观察组合，不声称是原子系统快照。TaskStack 的 ActivityManager.getTasks 单独记录调用耗时与异常，调用返回空列表仍可与异常区分。

采集中只更新内存，不持续落盘；停止时保存现场，停止后的完成事件更新原文件。未停止前崩溃或进程被杀仍可能丢失本次采集，进程退出后也无法继续补录旧调用。

匹配引擎的四个离线重放 HTTP/CLI 入口及 Android 能力声明已移除；`matching-summary` 直接分析导出的 JSON。生产状态机、选择器和业务测试继续保留。

调试服务通知停止与动作收尾可单独验证：

```shell
pnpm app:tools device-service-stop-check --serial DEVICE --install --output .local/debug-service-stop.json
```

此命令复用统一安装和设置恢复流程，关闭规则匹配后启用测试所需服务。默认检查无障碍输入；设备已连接特权服务时使用 `--backend privilege`，自动化模式使用 `--backend automation`，均先用短滑动确认实际输入后端。先识别「调试服务已开启」通知并点击其「停止」按钮，检查服务退出、HTTP 关闭、凭据删除和应用进程保留；再在同一进程重启调试服务，验证滑动进行中停止服务会取消诊断请求、释放触点、恢复时序策略并封口录制。动作阶段通过 ADB 停止同一服务，避免下拉通知栏改变前台界面而提前取消动作。最后导出诊断，执行新会话短滑动确认动作通道可复用，输出主报告、动作报告和 `*-service-stop-capture.json`。

## 诊断会话生命周期真机复验（2026-10-21）

`device-session-check` 复用受控设备准备与恢复流程，在消费触摸的 Debug 页面上提交 6 秒无障碍滑动。分别在开始约 350 ms 后停止会话、让 2 秒会话自动过期，检查实际 UP、请求取消、通道释放、时序参数恢复和诊断导出。每种场景之后创建新会话，重复停止旧会话，再执行 250 ms 短滑动，检查旧会话不能取消新请求。输出主报告及两份 `*-capture.json`，可随后用 `matching-summary` 离线分析。

当前 Android 13 / M2012K11AC、当前 Debug APK 验证结果：

| 场景 | 停止边界 | 录制中取消到动作收尾 | 实际触摸 | 后续请求 |
| --- | --- | --- | --- | --- |
| 手动停止 | 请求 cancelling，通道仍占用，触点仍按下 | 34 ms | 一次 DOWN / UP，无 CANCEL；总触摸 414 ms；停止前采样到 UP 为 39 ms | 新会话短滑动 completed，shell=false |
| 自动过期 | 请求 cancelling，通道仍占用，触点仍按下 | 100 ms | 一次 DOWN / UP，无 CANCEL；总触摸 2076 ms | 新会话短滑动 completed，shell=false |

两份导出均为 v2、sealed=true、无 incomplete；每份包含 1 次查询、1 次取消动作、5 次规则状态观察。历史回放验收（该功能现已移除）中，真机和电脑端均 verified=true，回放不增加触摸事件或改变在线引擎。手动停止这份短采集未捕获公共输入事件，离线报告如实显示 inputs/not_captured；过期采集的公共输入也已验证通过，不把缺失输入冒充回放成功。

本轮仅验证无障碍动作路径，不扩展此前特权、厂商矩阵结论。首次测试脚本对 JSON null 的断言已修正并完整重跑，应用代码未改。类型检查和 16 项工具测试通过；测试结束恢复 enableAutomator=false、automatorMode=2、enableMatch=true、原三个无障碍服务与全局开关 1。无残留 ADB 转发或设备恢复记录，崩溃缓冲区未发现 GKD 异常。

证据位于 `.local/diagnostic-session-device/`：`final.json`、`final-stop-capture.json`、`final-expiry-capture.json`、`offline-stop.json`、`offline-expiry.json`；执行日志为 `.local/diagnostic-session-device-final.log`。电脑端隔离测试进程已关闭。

## 查询不完整与节点拥塞诊断（2026-10-23）

查询触及子节点/后代预算，或声明存在的子节点、查询所需根节点无法取得时，终止对应规则的本次动作，不执行输入、不提交成功计数，也不永久禁用规则。日志原因分别为 `query_child_limit`、`query_descendant_limit`、`query_child_unavailable`、`query_root_unavailable`，不会归为 `not_matched`。这尤其避免 `excludeMatches` / `excludeAllMatches` 将未完成的查询误判为不存在。正常短路命中、关系范围内查询和恰好完成预算内遍历不需要扫描整树；后续事件可重新查询。

诊断开启时，现有事件流、导出 JSON、应用诊断页及 `summarize-matching` 包含：

- `event_queue_overflow` / `eventDrop`：被丢弃的事件键、队列容量和已等待毫秒数。队列清理仍释放平台引用，不计作溢出；累计次数保存在 `entries`。
- `node_read_timeout`、`node_read_capacity` / `nodePressure`：当时占用的读取线程、容量、本会话跟踪的读取数及其中最长未返回耗时。`NodeRead` 在物理读取真正结束时收尾，取消协程等待不会提前移除。采集开始前已进入的调用没有起始时间，因此最长耗时只覆盖观察到的调用；停止时的列表保留在 operationsAtStop，openOperations 继续移除已确认返回的调用。
- 查询预算中断保留 failure 和原始访问记录，属于查询失败；它与诊断采集本身的截断、覆盖分别标记。

引擎内部冷却、延迟、前台页面年龄、服务启动等待和节点缓存寿命使用同一 `System.nanoTime` 单调时间域。日志日期、访问记录和会话展示时间仍为日历时间；Android 事件自带的 uptime 仅作为该输入源的排序依据，不与引擎接收时间相减。关闭诊断时，新增诊断路径不创建记录、读取诊断时钟、检查线程池统计或增加节点访问；查询预算保护始终生效。

### 诊断页面与真机只读采集验证

`pnpm app:tools device-diagnostic-page-check --serial DEVICE --apps com.ss.android.ugc.aweme --output .local/tests/diagnostic-page/douyin-records` 可验证开关、通知返回/停止、记录列表、文本预览和单条系统分享，并对指定应用执行三次 `execute: false` 的节点遍历。脚本不接受应用协议；目标应用应提前处于需要验证的页面。测试会保存并恢复自动化、匹配和无障碍配置。

本轮真机证据位于 `.local/tests/diagnostic-page/douyin-records-verified/`。抖音处于首次隐私协议弹窗，三次查询分别约 71.6、59.4、45.8 ms，每次记录 49 次节点接口调用，均完成且无查询异常，不能代表正常信息流的大节点场景。即时现场没有事件覆盖；返回页面并操作通知后的最终文件保留 2000 条事件、覆盖 793 条旧事件，但三份查询调用记录仍完整。最终 JSON 约 10.4 MB，其中开始时配置约 8.5 MB。大文本预览有界读取，分享包仍含完整原始记录。

### 查询摘要与调用去重验证

`pnpm app:tools device-diagnostic-performance --serial DEVICE --apps com.ss.android.ugc.aweme --output .local/tests/diagnostic-performance/douyin` 使用同一页面、同一只读完整遍历规则，以 Cold 应用缓存模式预热后交替执行 off/flow/detailed 各三次。flow 和 detailed 都启用查询记录，区别是条件解释；三档都使用生产节点读取线程。多轮 Debug benchmark 总预算为 120 秒，与用户采集无自动停止无关。节点接口计数不等于 Binder 事务数；活跃视频场景并非固定树快照。

验证覆盖事件覆盖后的独立查询摘要、取消终态、停止/替换会话后物理读取晚到，以及离线摘要合并。事件容量、配置快照和保存队列不变，仍不在采集中持续落盘。`slowestRuns` 仍只报告有完整运行边界证据的总运行耗时，不用选择器耗时冒充运行总耗时。`attributeNodeCount` 从现有属性调用的节点引用去重得到，记录截断时不能当作完整树节点数。

最终真机证据：`.local/tests/diagnostic-performance/douyin-final/`。抖音推荐页每轮属性访问覆盖 846 个节点；三档各预热一次、交替测量三次，off/flow/detailed 中位数分别为 2586.8/3488.5/3597.6 ms（本轮最大值分别 3114.8/3661.4/3800.4 ms，三次样本的 P95 即最大值，不能代表稳定分位数）。另一次详细现场的三条查询各保留 2539 条调用、1690 条等待/物理读取记录，独立摘要约 4268.2/4011.7/4293.4 ms，均正常结束且没有详情截断。最终事件覆盖 630 条；相比先前同规则三轮现场约 2.7 万条覆盖明显减少，但条件解释等事件仍可能覆盖。即使如此，查询耗时与结果仍可从独立摘要读取。对照包含诊断开销，不能据此宣称普通匹配性能同比提升；设备自动化、匹配和无障碍设置已恢复。

诊断记录页不再包含采集开关、介绍卡片或重复的列表标题；空列表水平居中并留顶部间距，显示“暂无诊断记录”，采集中显示“正在采集，停止后生成记录”。通知点击仍跳转到诊断记录页。

本轮记录管理验证位于 `.local/tests/desktop/b6fbbd58-4d67-404b-9c97-7f97eb2b2639/reports/`：隔离宿主实际操作开关、单条删除、长按多选、全选、批量删除及两种空提示。Desktop 系统分享仍返回平台不支持，单条/多条 ZIP 内容通过文件存储测试验证；本轮未执行真机安装或 Android 分享面板测试。

真机记录管理验证：`.local/tests/diagnostic-page/record-management-device-retry/`。验证了设置开关、通知返回/停止、详情单条分享与删除、长按选择两条记录后分享与删除，导出 ZIP 中的会话 ID 与选择完全一致，测试前已有记录保持不变。采集超过 60 秒未自动停止，原自动化开关与模式已恢复。真机发现详情使用普通 GkDialog 铺满会重复顶部留白，已改用项目 GkFullscreenDialog。

全屏组件修正后的安装复查位于 `.local/tests/diagnostic-page/fullscreen-device/`：真机截图确认系统栏背景与内容一致、顶部额外留白消失；分享面板、删除取消后返回详情、系统返回列表均通过，现有记录及原自动化设置保持不变。Android gkd Debug 构建与 JVM 编译通过。

停止采集并成功保存后提示“已保存诊断记录”；开关停止与通知停止共用服务完成流程，每次采集仅提示一次，后续收尾补写不重复提示。

### 连续操作时的统计与覆盖范围

`operationStatistics` 独立于事件和查询详情缓冲，累计本会话已完成调用的次数、总耗时，并保留各组最慢调用的 ID、runId、规则和结果。Query/SelectorQuery 按规则分组，平台 API 等按名称分组；最多 128 组，超过后仍累计已有组，未纳入分组的完成调用数记入 `omitted`。尚未返回的调用仍见 `openOperations`；并发跟踪上限造成的遗漏见 `session.omittedOperations`。停止后返回的调用计入原会话，不计入后来启动的会话。统计不持有查询证据或节点参数，也不改变原有详细采集上限和停止落盘时机。

分析摘要中的 `entryTotals` 使用原始累计入口计数，`operationStatistics` 给出累计与平均耗时；`coverage` 显示事件、查询、系统步骤各自保留下来的时间范围和遗漏情况。区间不是连续完整记录的保证。旧记录没有全程统计时不会从剩余片段补造，摘要会明确警告。不同 API 层级和并发调用可能重叠，不能将各组耗时相加作为运行时长。
