# Repository Instructions

- 除 `app_icon`、`service` 等 Android 平台必须使用 XML 的场景外，禁止新增 XML 文件。
- UI、图标及其他能够使用 Kotlin 表达的实现必须使用 `.kt` 文件，不得为其新增 drawable、layout 等 XML 资源。
- 无法确定是否属于 XML 例外场景时，必须先向用户确认。

## Kotlin 可见性

- `app` 模块内禁止使用 `internal` 关键字；由于没有其他模块会引用 `app` 模块，对外可见的声明应省略可见性修饰符（使用 Kotlin 默认的 `public`），仅在需要收窄作用域时使用 `private`。

## 构建与测试

- 常规测试只编译 `gkd` 渠道；若用户没有明确指令，禁止运行任何 `play` 渠道的编译任务。

## 测试策略

- 新增测试必须验证可观察行为，明确被测输入、预期输出和要防止的具体回归。优先覆盖纯函数、边界条件、异常路径、平台或版本兼容差异，以及已修复缺陷的回归场景。
- 禁止新增仅复述生产代码静态声明的测试，包括枚举成员、常量取值或集合、连续编号、由同一注册表推导出的成员关系，以及 Kotlin 类型系统已经保证的约束。
- 只有当常量或标识属于外部协议、持久化格式或跨版本兼容契约时，才允许为其新增稳定性测试，并在测试名称或注释中说明要保护的兼容行为。

## Android API 调研

- 涉及 Android framework Java/AIDL API 的源码定位、跨版本签名或可用性比较、API 缺失原因分析，以及 Java hidden-API 访问代码生成时，必须使用项目内的 `android-api-diff` skill：`.agents/skills/android-api-diff/SKILL.md`。
- 按该 skill 的路由使用 `android-api-diff` CLI，并保留默认 JSON 输出；不得自行实现或模拟 Android API 版本检查。
- 安装或更新项目级 skill 时，在项目根目录运行 `android-api-diff skill install`。
