# Repository Instructions

- 除 `app_icon`、`service` 等 Android 平台必须使用 XML 的场景外，禁止新增 XML 文件。
- UI、图标及其他能够使用 Kotlin 表达的实现必须使用 `.kt` 文件，不得为其新增 drawable、layout 等 XML 资源。
- 无法确定是否属于 XML 例外场景时，必须先向用户确认。

## Android API 调研

- 涉及 Android framework Java/AIDL API 的源码定位、跨版本签名或可用性比较、API 缺失原因分析，以及 Java hidden-API 访问代码生成时，必须使用项目内的 `android-api-diff` skill：`.agents/skills/android-api-diff/SKILL.md`。
- 按该 skill 的路由使用 `android-api-diff` CLI，并保留默认 JSON 输出；不得自行实现或模拟 Android API 版本检查。
- 安装或更新项目级 skill 时，在项目根目录运行 `android-api-diff skill install`。
