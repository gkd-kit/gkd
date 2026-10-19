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
  first and use the chosen `result.candidates[].source.path`.
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

`resolve` returns `result.candidates` in recommended order, or an empty array
when no source matches. `query` and `generate` include the same candidate list
and inspect the recommended source by default. When the user specifies another
matching source, pass `--source-path <path>` to `query` or `generate`; retain the
original API reference to preserve its member or constructor target. Candidate
metadata alone does not prove that a member exists in a particular version.

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

## Choose an interface constant access strategy

Four strategies are supported and have already been validated in the companion
remap repository. Choose based on the user's build and value requirements;
do not require another feasibility experiment unless the user requests one.

| Strategy                | When to use it                                                                                                 | Declaration or access                                                                      |
| ----------------------- | -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Reflection              | Read the device's value without a remapped field access                                                        | Read the original interface field reflectively.                                            |
| Interface stub          | Preserve runtime field access in a compatible remap build                                                      | Initialize the interface field with `RemapStub.value()`.                                   |
| Consistent constant     | Embed a supported primitive expression that agrees across checked versions                                     | Keep the original initializer in the generated interface.                                  |
| Implementing-class stub | Read the device's value through a known platform class implementing the interface, without `RemapStub.value()` | Declare a `public static`, non-final field with no initializer in that class's remap stub. |

The current `generate` command automatically chooses the consistent-constant
strategy for supported primitive initializers whose token sequences match across
checked definitions, ignoring whitespace and comments. It does not evaluate
expressions: `0x1` and `(1 << 0)` differ. Constant references, unsupported
expressions, missing initializers, differing definitions, and non-primitive
types fall back to `RemapStub.value()`. Agreement covers only checked versions;
do not download all versions to strengthen this conclusion.

Reflection and implementing-class stubs are adaptations of the generated code,
not CLI flags or automatic generator fallbacks. Honor an explicitly selected
strategy. For an interface/R8 incompatibility, use a consistent constant when
the generated result supports it; otherwise prefer a known suitable platform
implementing class, or reflection when no suitable class is known. Do not
present the `RemapStub.value()` fallback as resolving that incompatibility.

For example, access `IBinder.SHELL_COMMAND_TRANSACTION` through a stub mapped
to the platform's `Binder` implementation:

```java
@li.songe.remap.RemapType(android.os.Binder.class)
public class BinderHidden {
    public static int SHELL_COMMAND_TRANSACTION;
}
```

Read `BinderHidden.SHELL_COMMAND_TRANSACTION` at the call site. Keep the field
name and type matching the original interface declaration; omit both `final`
and an initializer. This strategy also applies to reference types and values
that differ between versions. Map to the real platform implementing class;
merely declaring a field in an unmapped application subclass creates a separate
field. Use these declarations for reads, since the original interface field is
final.

## Handle a missing CLI

If `android-api-diff` is not installed, stop and tell the user to run:

```sh
npm install --global android-api-diff@latest
android-api-diff skill install
```

Run the Skill command from the root of the current Git or Gradle project so it
remains project-scoped. Do not implement or simulate version checks. The
package manager owns CLI update detection and upgrades.
