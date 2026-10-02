# GKD Desktop 开发

`gkd-app` 内置 Desktop 开发宿主，默认运行与 Android 共用的首页、订阅、应用、设置四标签及生产路由。平台不支持的具体动作显示 Toast；页面编辑与数据读写使用真实业务实现。模块、依赖和资源说明见 [应用架构](../docs/architecture.md)，目录布局见 [本地开发产物](../docs/local-development.md)，开发约束见 [AGENTS.md](../AGENTS.md)。

## 启动与验证

在仓库根目录运行：

```powershell
./gradlew.bat :gkd-app:run
# 会写入数据的交互检查使用隔离宿主：
./gradlew.bat :gkd-app:run --args=--test
```

日常启动使用已有开发数据。`--test` 自动创建隔离会话，不读取日常 profile；无测试输入时使用最小模拟清单。测试窗口位于可视区域之外且不获取系统焦点。控制窗口通过标题栏齿轮或 F12 打开，关闭它不会退出 App。

HTTP 默认监听 `127.0.0.1:17322`，可用 `GKD_DESKTOP_PORT` 修改端口，关闭 App 时停止。修改 Kotlin 后先关闭本任务启动的 Desktop，再编译、重启，避免覆盖运行中的 JAR。

按改动范围选择命令，不必每次全部执行：

```powershell
./gradlew.bat :gkd-app:compileKotlinJvm
./gradlew.bat :gkd-app:jvmTest
pnpm type-check
pnpm test
pnpm app:tools --help
```

共享逻辑、共享 UI 和 Desktop 修改只做相关 JVM 验证；涉及 Android 实现、依赖或构建链路时才执行相关 `gkd` 渠道验证。业务、持久化、协议和恢复行为保留自动测试；UI 默认使用人工或 HTTP 实际交互检查；仅为已发生、难以稳定人工复查的重要回归增加小范围自动测试，具体边界见 AGENTS.md。

## Windows 绿色版打包

在 Windows 上使用包含 `jpackage` 的 JDK 17 或更新版本，在仓库根目录执行：

```powershell
./gradlew.bat :gkd-app:packageWindowsPortable
```

ZIP 输出到 `.local/desktop-packages/gkd.win-<架构>.zip`。解压完整的 `GKD/` 目录后运行 `GKD.exe`，无需安装 Java，也不生成 MSI 或安装器。架构跟随构建所用 JDK，`amd64` / `x86_64` 统一命名为 `x86_64`；当前 WebView2 集成使用 Windows x64，建议使用 x64 JDK，网页功能仍需系统 WebView2 Runtime。

设置非空环境变量或 Gradle 属性 `GKD_RENAME_PACKAGE_FLAG` 后，APK 命名为 `gkd-v<版本号><提交后缀>.apk`，ZIP 命名为 `gkd-v<版本号><提交后缀>.win-<架构>.zip`。HEAD 正好位于 Git tag 时无提交后缀，否则追加 `-<7位提交ID>`。例如：

```powershell
./gradlew.bat :gkd-app:packageWindowsPortable -PGKD_RENAME_PACKAGE_FLAG=1
```

打包复用 Compose 的 `createReleaseDistributable`，未压缩镜像位于 `gkd-app/build/compose/binaries/main-release/app/GKD/`。重新打包前关闭从该镜像启动的应用。ZIP 仅包含应用和运行时，不携带本机开发数据或设备资料。

绿色版仅打包所需 Java 模块，并启用 ProGuard 删除未使用的应用代码、依赖和图标；关闭混淆及字节码优化，使用 Compose 默认裁剪规则，不整包保留应用代码。`collectDesktopConsumerRules` 自动读取 `jvmRuntimeClasspath` 中 JAR 的 `META-INF/proguard/*.pro`，按来源合并到 `build/generated/proguard/desktop-consumer-rules.pro`，作为 ProGuard 的任务输入。同名规则文件全部保留；依赖变化会重新收集，不读取 R8 专用目录。`desktop.pro` 只补充依赖未提供的 JNA 反射、ServiceLoader 入口及可选依赖声明。JVM 数据库显式使用生成的 `AppDbConstructor`，避免反射查找实现类。升级依赖后可运行 `:gkd-app:suggestModules` 复查静态模块需求，并用打包后的 `GKD.exe --test` 检查隔离启动、数据库、网络和网页；静态分析不能覆盖动态加载。

绿色版数据、profile 和模拟配置保存在 `GKD.exe` 同目录的 `.local/desktop/`；`--test` 使用同目录的 `.local/tests/desktop/<会话ID>/`。可以整体移动目录，启动不依赖工作目录或源码仓库。请解压到可写目录；升级时保留已有 `.local/`。Gradle `run` 仍使用仓库根目录的 `.local/`。

## 导入设备资料

连接并解锁真机，构建带导出能力的调试包，再运行工具：

```powershell
./gradlew.bat :gkd-android:assembleGkdDebug
pnpm app:tools capture-profile --install --screens
./gradlew.bat :gkd-app:run
```

工具位于根目录 `scripts/desktop`，支持 `--serial`、`--apps`、`--font`、`--install`、`--screens`、`--output`，不会自行构建 APK。省略 `--apps` 时使用设备导出的应用清单；默认字体路径为 `/system/fonts/MiSansVF.ttf`，其他设备通过 `--font` 指定。

工具先记录自动化开关和运行模式，关闭自动化并确认后导出，最后仅恢复这两个字段并核对。中断或断连后如保留 `automation-recovery.json`，必须先按记录恢复再重试。资料校验、暂存及真机恢复成功后才替换目标 profile，失败保留原资料。

默认输出为日常 profile，包含设备尺寸、Insets、字体缩放、颜色、应用图标、订阅和初始设置。修改设备元数据后重启宿主，应用清单可在页面刷新。已有应用数据优先，重新导出不会清空数据库；profile 不复制数据库、规则统计或排序结果。字体和设备资料仅本地使用，不提交。

如需直接调试导出协议，仅 debuggable App 接受：

```powershell
adb shell am start -n li.songe.gkd.debug/li.gkd.app.MainActivity -a li.gkd.action.EXPORT_DESKTOP_PROFILE --es requestId profile-example-1
```

每次使用新的 1–64 位字母、数字或连字符 requestId。外部 files 下的 `development/desktop-profile/<requestId>/status.json` 返回 running/success/failed 及失败原因，成功后拉取 `profile.zip`。导出等待实际窗口布局，串行执行；Activity 销毁会取消，相同 ID 不重复执行，超过一天的请求在下次导出时清理。ZIP 包含 `profile.json`、`apps.json`、`settings.json`、`seed.json`、`subscriptions/*.json` 和 `icons/*.png`；字体由工具单独拉取。资料格式变化后重新导出。

## 模拟环境与数据

控制窗口和 HTTP 操作同一个模拟器，配置自动保存；保存失败显示原因并提供重试，退出前须保存成功。模拟配置损坏或模型变更时可删除当前会话的 `simulator.json` 重置，不影响应用数据。路由、弹窗、IME 和进行中的任务不跨进程恢复。

`GET /simulator` 读取配置，`POST /simulator` 替换配置，`POST /simulator/patch` 仅合并提供的字段。分组为 `device`、`permissions`、`services`、`prompts`、`privilege`。配置更新保留页面草稿；`POST /scenario` 重建页面及其 ViewModel/草稿，但保留持久化数据与模拟配置。

`device.width/height` 是内容视口 dp，`density` 为相对系统 DPI 的倍率，`fontScale` 单独控制字体。`locale` 影响格式化，当前资源只有默认中文。系统栏实时绘制：时间取本机时钟，电量取 Windows 系统，网络和充电为模拟值。隐藏状态栏仍保留挖孔安全高度；隐藏导航栏移除底部系统留白。

样本可放在当前会话 profile 的 `fixtures.json`，包含 `actionLogs`、`activityLogs`、`eventLogs`、`snapshots` 数组，字段对应 gkd-db 实体。快照图片放在同级 `snapshot/<id>/<id>.png` 或 `.webp`。使用稳定 id，首次成功导入后不重复导入；更换测试样本使用新的隔离会话。

控制窗口“本地归档”提供备份导出、路径导入和日志导出，执行共享文件流程。文件选择及另存使用本机选择器；Android 系统分享等具体动作仍不支持。

## HTTP 调试

所有写入检查先核对 `GET /state` 的 `isolated` 为 true。操作串行执行；节点失效时重新读取目标窗口控件树，只重试尚未执行的操作。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /health`、`GET /state` | 启动状态、场景与隔离标记 |
| `GET /scenarios`、`POST /scenario` | 查询可用场景；打开页面或指定路由 |
| `GET /simulator`、`POST /simulator`、`POST /simulator/patch` | 读取、替换或合并模拟配置 |
| `GET /semantics`、`POST /action` | 读取实际控件树、执行实际控件操作 |
| `GET /screenshot`、`GET /window` | 截图、窗口位置和尺寸 |
| `GET /input-status` | 激活次数、可聚焦及已聚焦窗口数 |
| `GET /app-catalog` | 生产仓库应用清单、刷新状态、错误及版本 |
| `POST /runtime-records/replay` | 隔离宿主注入合成运行记录 |
| `POST /snapshot-capture/replay` | 隔离宿主回放快照采集输入 |

PowerShell 示例（`-NoProxy` 避免环回请求经过代理）：

```powershell
$api = 'http://127.0.0.1:17322'
$state = Invoke-RestMethod -NoProxy "$api/state"
if (-not $state.isolated) { throw '请使用 --test 隔离宿主' }
Invoke-RestMethod -NoProxy "$api/scenario" -Method Post -ContentType 'application/json' `
    -Body '{"page":"settings","variant":"normal"}'
Invoke-RestMethod -NoProxy "$api/simulator/patch" -Method Post -ContentType 'application/json' `
    -Body '{"device":{"width":412,"height":820,"density":1,"fontScale":1.5,"dark":true}}'
Invoke-RestMethod -NoProxy "$api/semantics?window=app"
```

将截图保存到当前隔离会话的 `reports/`，请求为 `GET /screenshot?window=app`。控件树、截图和窗口查询显式指定 `window=app|controls`；操作请求在 JSON 中指定窗口：

```json
{"window":"app","type":"invoke","node":"从控件树获取的节点 id","action":0}
{"window":"app","type":"text","node":"可编辑节点 id","text":"新内容"}
{"window":"app","type":"click","x":120,"y":200}
{"window":"app","type":"scroll","x":120,"y":500,"amount":3}
{"window":"app","type":"key","key":"Escape"}
```

`invoke/text` 调用真实无障碍操作；坐标事件由 Compose 命中检测与手势处理器执行，不移动系统鼠标、不发送系统键盘事件、不激活窗口。按键支持 Tab、Enter、Escape、Space、Backspace、F5、F12。F5 刷新当前订阅或应用列表，菜单和弹窗打开时不触发。坐标及控件树 bounds 使用 AWT 逻辑像素，相对当前输入弹窗内容区，无弹窗时相对宿主内容区。节点路径仅属于该窗口最近一次控件树；失效返回 `Stale node path`。后台操作只支持 `area=content`，拒绝 `nativeKey`。

截图默认 `mode=compose`，读取当前 Skia 绘制记录并合成弹窗和最近网页帧，响应头为 `X-GKD-Capture: compose-recording`。它不重新创建页面，可在窗口被遮挡或位于屏幕外时使用，但不含系统菜单等原生窗口。`mode=screen` 捕获屏幕像素，响应头为 `X-GKD-Capture: screen`，要求窗口可见且可能包含遮挡。两者均禁止 `activate=true`。`area=frame` 包含 Compose 标题栏，不含原生外边框。

`POST /scenario` 默认页面为 `dashboard`，完整场景以 `/scenarios` 为准。支持带持久化序列化名称的 `route`：

```json
{"page":"rule-editor","route":{"type":"li.gkd.app.feature.subscription.UpsertRuleGroupRoute","subsId":-2,"forward":true}}
```

共享提示可用 `{"page":"dashboard","overlay":"terms"}` 预览，overlay 支持 terms、restricted、occupied；条款预览不写接受状态。更新从关于页检查，上传从日志或快照的生成链接操作进入真实流程。

回放接口仅注入业务输入，不能代替控件点击验证：

- `/runtime-records/replay` 接收 `activities`（appId/activityId/time）、`visits`（oldAppId/newAppId/time）、`actions`（appId/activityId/subsId/subsVersion/groupKey/groupType/ruleIndex/ruleKey/time）。空请求提交 Activity 缓冲并查询数据库；不执行真实动作、不增加累计动作次数。
- `/snapshot-capture/replay` 接收 `source`（ComplexSnapshot，忽略原 ID）、与屏幕尺寸一致的 `screenshotBase64` 及可选 `autoExport`。共享流程生成并保存新快照，返回 `snapshot` 和 `exportResult`；系统下载不支持时仍保留已保存快照。

## 故障处理与验收边界

Windows x64 网页使用版本目录声明的 webview2-compose 和系统 WebView2 Runtime。缺少 Runtime 时按错误页提供的微软安装入口处理，再重试；不需要 Maven Local 或单独安装 CEF。关闭 App 时等待原生浏览器释放，超时保留窗口供再次关闭。

Desktop 只能证明对应宿主的共享交互与业务结果，以下需按改动范围单独验收，不能视为已由后台 HTTP 或 JVM 测试覆盖：

- Android 权限、IME、系统栏、预测返回、无障碍/Service、安装、相册和分享。真机检查前按 AGENTS.md 暂停自动化，结束、失败或中断后都恢复并核对。
- Windows 网页键盘、IME、前台焦点、标题栏拖动、边缘缩放、系统菜单、Snap Layouts 及跨显示器 DPI。
- 更新弹窗、忽略版本、取消下载、真实更新渠道及 GitHub 上传。本机 HTTP 客户端测试只证明解码、下载内容和进度。

Desktop 与 Android 视觉对照须统一页面状态、dp、字体、fontScale、实际 Insets 和颜色。Compose 截图不证明系统焦点、真实遮挡或 Android 像素一致性。
