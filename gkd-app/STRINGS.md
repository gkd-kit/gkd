# UI 文案

用户可见的标题、按钮、副文案、Toast、通知、无障碍描述和校验提示统一维护在
`src/main/res/values/strings.xml`。日志、内部诊断、协议字段、URL、动画调试标签和用户输入不属于固定 UI 文案。

资源键按用途命名，例如 `rule_enable_in_app`、`subscription_disabled`，不加 `gk_`；无障碍相关键使用 `a11y`。
复用已有键前应确认语义相同；显示值相同但用途不同的文案可以分别命名。

默认文案在 `values/strings.xml`，日语翻译在 `values-ja/strings.xml`。`generateUiStrings`
从默认 XML 生成 `li.gkd.app.text.UiStrings`，供 Compose、通知、ViewModel 和纯 Kotlin
规则逻辑共同使用。运行时通过 Android 资源按设备语言读取文案；未初始化 Application 的
本地单元测试回退到默认文案。生成文件位于 `build/generated/source/uiStrings`，不要手动修改。

```xml
<string name="rule_enable_in_app">在此应用启用</string>
<string name="app_count">%1$s 个应用</string>
```

```kotlin
Text(UiStrings.rule_enable_in_app)
Text(UiStrings.app_count(apps.size))
```

无参数文案生成属性；带参数的文案生成函数，参数使用连续编号的 `%1$s`、`%2$s`。
普通文案无需声明 `translatable` 或 `formatted` 属性。带参数文案中的字面百分号写成 `%%`。
普通文案不加外层双引号，仅需保留首尾空格、连续空白时使用引号包裹。
换行写成 `\n`，双引号写成 `\"`，反斜杠写成 `\\`，XML 中的 `&`、`<` 使用实体转义。
`${i}` 等自定义通知模板变量作为普通文本保留，无需额外属性。
只有需要将 `%1$s` 等格式符本身作为普通文本显示时，才声明 `formatted="false"`。

带 `debug_suffix` 的平台标签继续通过 `R.string` 获取，以保留构建变体后缀，不生成访问器。
增加语言时，需要同时维护对应的 `values-xx/strings.xml`，并将语言加入
`gkd-app/build.gradle.kts` 的 `localeFilters`。
