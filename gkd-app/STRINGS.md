# UI Strings

User-visible titles, buttons, subtitles, Toasts, notifications, accessibility descriptions, and validation prompts are uniformly maintained in
`src/main/res/values/strings.xml`. Logs, internal diagnostics, protocol fields, URLs, animation debug tags, and user input are not considered fixed UI strings.

Resource keys are named by purpose, e.g. `rule_enable_in_app`, `subscription_disabled`, without `gk_`; accessibility-related keys use `a11y`.
Before reusing an existing key, confirm that the semantics are the same; strings with the same display value but different purposes can be named separately.

Currently only one set of strings is supported; runtime language switching is not performed. `generateUiStrings` generates
`li.gkd.app.text.UiStrings` from this XML, shared by Compose, notifications, ViewModels, and pure Kotlin rule logic,
without requiring Android Context. The generated file is located at `build/generated/source/uiStrings` and should not be manually modified.

```xml
<string name="rule_enable_in_app">Enable in this app</string>
<string name="app_count">%1$s apps</string>
```

```kotlin
Text(UiStrings.rule_enable_in_app)
Text(UiStrings.app_count(apps.size))
```

Parameterless strings generate constants; parameterized strings generate functions, with parameters using sequentially numbered `%1$s`, `%2$s`.
Normal strings do not need to declare `translatable` or `formatted` attributes. Literal percent signs in parameterized strings are written as `%%`.
Normal strings do not need outer double quotes; quotes are only needed when preserving leading/trailing spaces or consecutive whitespace.
Newlines are written as `\n`, double quotes as `\"`, backslashes as `\\`, and `&`, `<` in XML use entity escaping.
Custom notification template variables like `${i}` are retained as plain text without additional attributes.
`formatted="false"` is only declared when the format specifier itself (e.g., `%1$s`) needs to be displayed as plain text.

Platform tags with `debug_suffix` continue to be obtained via `R.string` to preserve build variant suffixes and do not generate accessors.
If multiple languages are added in the future, string parsing should be switched to the Android resource mechanism; adding only `values-xx` directories is not sufficient.
