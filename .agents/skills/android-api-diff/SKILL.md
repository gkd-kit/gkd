---
name: android-api-diff
description: Inspect Android framework Java and AIDL APIs across versions with the android-api-diff CLI. Use when resolving an Android API to frameworks/base source, fetching the complete content of a tagged source file, comparing signatures or availability by Android version, explaining missing APIs, generating Java hidden-API access code, or explicitly prewarming the local query cache.
---

# Android API Diff

Use the `android-api-diff` CLI as the only tool interface. Keep its default JSON
output so results remain machine-readable.

## Route the request

- Run `generate` when the user wants Java hidden-API access code. It performs the
  version query internally, so never call `query` first.
- Run `query` when the user wants signatures, version ranges, availability,
  missing reasons, or source metadata.
- Run `resolve` when the user only needs the matching `frameworks/base` file and
  target kind.
- Run `source` when the user wants the complete content of a known source path
  at an exact Android release tag. If only an API name is known, run `resolve`
  first and use its `source.path`.
- Run `preload` only when the user explicitly asks to preload several APIs.

Always quote API names because constructors and member references can contain
shell-sensitive characters.

```sh
android-api-diff resolve "ContentObserver()"
android-api-diff source android-17.0.0_r1 core/java/android/accessibilityservice/AccessibilityButtonController.java
android-api-diff query "IActivityManager.getTasks" --min-sdk 28
android-api-diff generate "ActivityThread.currentApplication" --min-sdk 28
android-api-diff preload "ContentObserver()" "IActivityManager.getTasks" --min-sdk 28
```

Omit `--min-sdk` unless the user provides a minimum Android API level. The CLI
then uses its built-in default.

## Interpret results

Treat exit code `0` together with top-level `ok: true` as success. Read the
operation result from the top-level `result` field.

For `query`, describe range endpoints as the first or last tag checked in the
current snapshot. A `last-checked` endpoint is not a promise that no later
revision exists.

For member queries, treat `result.overloads` as the source of truth. Each item
has a stable `overloadId`, its latest checked `signature` and `member`, and
independent `ranges`. A range with `missingReason: "overload-not-found"` means
the member name exists in that source revision but that specific parameter
signature does not. Use top-level `result.ranges[].overloadIds` only when
describing which overloads were available together.

On a nonzero exit code, read the structured JSON error from stderr and report its
`error.code` and `error.message`. Do not add an automatic retry: the CLI network
layer already retries transient fetches.

## Handle a missing CLI

If `android-api-diff` is not installed, stop and tell the user to run:

```sh
npm install --global android-api-diff@latest
android-api-diff skill install
```

Run the Skill command from the root of the current Git or Gradle project so it
remains project-scoped. Do not implement or simulate version checks. The
package manager owns CLI update detection and upgrades.
