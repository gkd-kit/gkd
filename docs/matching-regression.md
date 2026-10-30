# 匹配引擎场景回归

## 运行与报告

在仓库根目录执行，Linux/macOS 将 `./gradlew.bat` 替换为 `./gradlew`：

```powershell
./gradlew.bat :gkd-app:matchingRegression
# 只运行一个场景族
./gradlew.bat :gkd-app:matchingRegression --tests '*RepeatedWindowEventScenarioTest*'
```

入口运行 `li.gkd.app.matching.*` 下的 JVM 测试，使用项目的 JDK、Gradle 和构建依赖，无需 Android 设备或模拟器。报告包含测试类、方法及失败原因：

- HTML：`gkd-app/build/reports/tests/matchingRegression/index.html`
- JUnit XML：`gkd-app/build/test-results/matchingRegression/`

现有 build 工作流的 Windows 作业运行同一入口，并在失败时也上传报告。

## 场景组织与字段

按场景族保存 JSON：共享基础输入和问题背景的 case 放在同一个文件的 `cases` 中；不同问题背景使用独立文件。文件名和测试名称描述行为，包名及节点数据使用脱敏或合成内容。

当前场景族为 [repeated-window-event.json](../gkd-app/src/jvmTest/resources/matching-scenarios/repeated-window-event.json)，由 [RepeatedWindowEventScenarioTest](../gkd-app/src/jvmTest/kotlin/li/gkd/app/matching/scenarios/RepeatedWindowEventScenarioTest.kt) 执行。具体描述、输入变化、预期和来源以 JSON 为准，不在文档中重复列举。

| 位置 | 内容 |
| --- | --- |
| 顶层 | 场景族标题、说明、诊断链接、引擎版本、公共时间线和节点数据 |
| `cases[].id/title/description` | 场景编号、标题和描述 |
| `cases[].source` | `recorded` 为真实问题的缩减场景，`derived` 为派生反例 |
| `cases[].inputChange` | 输入变化类型、说明及窗口、Activity 参数 |
| `cases[].expected` | 预期说明、完整规则是否匹配、动作目标列表和提交次数 |

测试按编号读取 case，执行输入变化并断言其预期；失败提示包含该 case 的描述。原始诊断只保存链接，测试不下载日志，因此链接失效不影响运行。

派生 case 聚焦原问题的直接反例。通用执行条件和排除条件在查询专项测试中覆盖，不重复套用场景族的公共时间线。

## 既有测试入口

统一入口也运行下列既有测试，各测试方法描述具体行为：

| 职责 | 入口 |
| --- | --- |
| 前台确认、窗口关联和事件输入 | [input](../gkd-app/src/jvmTest/kotlin/li/gkd/app/matching/input/) |
| 调度、动作取消和服务交接 | [MatchCoordinatorTest](../gkd-app/src/jvmTest/kotlin/li/gkd/app/matching/MatchCoordinatorTest.kt) |
| 延迟、定时任务与清理 | [MatchDeadlinesTest](../gkd-app/src/jvmTest/kotlin/li/gkd/app/matching/MatchDeadlinesTest.kt) |
| 规则更新与运行状态保留 | [RuleReconciliationTest](../gkd-app/src/jvmTest/kotlin/li/gkd/app/matching/RuleReconciliationTest.kt) |
| 查询、排除条件和动作前校验 | [query](../gkd-app/src/jvmTest/kotlin/li/gkd/app/matching/query/) |
| 诊断、录制和导出 | [diagnostics](../gkd-app/src/jvmTest/kotlin/li/gkd/app/matching/diagnostics/) |

## 验证边界

场景调用生产的前台/窗口跟踪、`MatchExecutionState` 执行判定、选择器、规则状态和调度器，以虚拟时间和动作记录替代平台输入及物理点击。当前重复窗口事件样本的旧窗口、节点树和规则经过合成或缩减；测试在选择器返回后暂缓交付结果，以插入窗口事件，验证查询完成与事件交错，不回放原日志的全部细节或测量真实耗时。

测试驱动器负责将窗口变化转为页面失效，没有运行完整 `AndroidMatchHost`。Android 事件采集、宿主接线、节点缓存、Binder、手势和生命周期仍需专项验证；Android host tests 和 selector 模块专项测试不包含在此入口中。

## 添加与维护

1. 在对应场景族的 `cases` 中添加编号、描述、来源、输入变化和预期；没有共同基础输入时新建 JSON。
2. 添加按编号读取该 case 的独立测试方法；需要新输入类型时扩展驱动器，并继续调用生产逻辑。添加 JSON case 本身不会自动注册测试。
3. 先确认场景能暴露问题，再修改生产代码，最后运行完整 `matchingRegression`。

已有 Kotlin 测试无需为统一格式迁移成 JSON。架构升级时保留场景输入和可观察预期，调整生产实现的接入方式；确需改变产品行为时说明预期变化。断言围绕动作目标、次数、提交、取消和清理结果，避免依赖私有方法或内部调用次数。
