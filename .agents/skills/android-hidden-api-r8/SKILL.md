---
name: android-hidden-api-r8
description: Check compatibility between Android hidden APIs and R8 when adding or changing hidden interface or superclass implementations, system callbacks, bytecode remapping, or related module dependencies. Also use for Debug-only success with minified Release failures involving AbstractMethodError, removed or renamed methods, or changed signatures. Excludes ordinary UI, documentation, and unrelated code changes.
---

# Android Hidden APIs and R8

Ensure R8 receives the necessary type information and preserves the method contracts required when Android calls project code. Inspect affected paths within the task's scope. Expand to a broader audit only when requested or supported by evidence.

## Identify the call contract

- Distinguish calls into system hidden APIs, direct calls to project implementations, and system callbacks through hidden interfaces or superclasses. These do not all imply missing keep rules.
- Identify the runtime caller, dispatch type, implementation, and required method names, parameter types, and return descriptors.
- R8 analyzes bytecode and input types, not Kotlin's `override` keyword. Successful compilation does not prove that the final R8 task sees the same inheritance relationships.

## Inspect actual R8 inputs

- Locate the application module and build variant that run R8. Trace both compilation classpaths and actual R8 analysis inputs. Do not infer consumer visibility from a library's `compileOnly` or `androidMainCompileOnly` declaration.
- For remapping, check that transformed type names, method descriptors, and inheritance relationships match the declarations supplied for analysis. Resolving a remap index, applying an ASM transformation, and supplying types to final R8 analysis are separate concerns.
- Check that stubs declare the required classes and members. A separate `*Hidden` type does not automatically supply hidden members missing from a public SDK type. Do not assume R8 merges duplicate types or remapped declarations into `android.jar`.
- Inspect existing annotations, consumer rules, merged rules, and missing-type diagnostics. `-dontwarn` suppresses warnings; it does not restore missing type relationships or preserve methods.
- For migration regressions, compare inputs and artifacts from known working and failing versions before attributing every difference to the migration.

## Choose the fix

- When analysis dependencies were lost, first restore a dependency path visible to final R8 analysis while keeping compilation stubs out of the APK. Use the project's actual AGP version, variants, and task inputs; do not treat an internal task property as a stable universal API.
- If the contract remains invisible, apply targeted `@Keep` or consumer keep rules to the actual entry points. Preserve required method existence, names, and signatures; preserving names alone must not allow required methods to be removed.
- Ship a library's required preservation rules with the library and validate them in a consumer with R8 enabled. Do not rely on a particular host accidentally supplying stubs or keep rules.
- Choose preservation scope from evidence. Public SDK callbacks, bundled project-owned AIDL, and entry points already covered by rules do not all need Keep merely because they use Binder. Keeping entire packages or disabling R8 is not the default fix.
- Do not infer runtime dispatch solely from the referenced class name in decompiled output. For instructions such as `invoke-super`, verify the applicable ART/Dalvik semantics or use a minimal runtime reproduction before changing stubs.

## Validate and report

- Build the affected variant with R8 actually enabled. Before building, check whether associated tasks upload or publish artifacts. Prefer a validation path without external publication side effects and stay within the user's authorization.
- Use mapping, usage, seeds, and merged rules as supporting evidence. Inspect the final DEX for critical methods, complete descriptors, and required inheritance relationships; no single report establishes every contract.
- Confirm that compilation stubs are absent from the APK. Validate library changes through a consumer or minimal integration fixture, rather than only inspecting an AAR before final optimization.
- Use runtime validation as needed for system dispatch, exception handling, or API-version compatibility. Contract checks should assert required methods and behavior, not fixed referenced class names or complete decompiled text that R8 may legitimately change.
- Report root-cause evidence, fix scope, build/artifact results, and runtime validation separately. State any unverified parts when building or device testing is unavailable. Debug success or method presence alone does not establish that a runtime failure is fully resolved.

Read [failure patterns](references/failure-cases.md) as needed for callbacks lost after module migration, hidden members missing from public types, or uncertainty about `invoke-super`. Verify each hypothesis against the current project rather than applying a pattern as a universal diagnosis.

## Update this skill

Upstream: [lisonge/remap](https://github.com/lisonge/remap/tree/main/skills/android-hidden-api-r8).

When asked to update this skill, use either command from the consuming project's root if it was installed with the skills CLI:

```shell
npx skills update android-hidden-api-r8
```

Or with pnpm:

```shell
pnpm dlx skills update android-hidden-api-r8
```

For a manually copied installation, compare the installed directory with upstream and merge updates to `SKILL.md` and `references/`. Preserve project-specific customizations. Review the resulting diff and reconcile any affected guidance in the project's `AGENTS.md` without replacing unrelated instructions. Updating the skill does not automatically update that file. Do not update the skill as part of an ordinary R8 investigation unless requested.
