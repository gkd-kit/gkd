# Hidden API and R8 Failure Patterns

Use these patterns to form hypotheses, then verify them against the current project's dependency graph, R8 inputs, and optimized artifacts. They are not universal diagnoses or prescribed fixes.

## Hidden callbacks removed after module migration

Possible symptoms: Debug works while minified Release fails with `AbstractMethodError`, and the usage report lists a system callback implementation as removed.

Check whether compilation stubs remain available to a library or KMP module but are absent from the application module that runs final R8 optimization. Compare the relevant compilation classpaths with actual R8 analysis inputs. Broad `-dontwarn` rules may conceal missing-type diagnostics.

If analysis dependencies are missing, inspect generated callback protection or restore the appropriate dependency path. Depending on the build configuration, the application may need its own `compileOnly` dependency on the stub module. Verify that the required callbacks survive optimization and that compilation stubs are not packaged. Then validate the affected runtime path.

A removed method is evidence of the optimization outcome, not proof that the correct fix is to keep its entire class.

## Hidden members missing from a public SDK type

A public SDK class or interface may omit a hidden method that exists at runtime. A project implementation matching that hidden method can therefore appear unrelated to any system contract during R8 analysis.

Direct calls to the concrete implementation may still work because R8 can rewrite both the implementation and its call sites. System dispatch through the hidden contract can fail if the required method is removed, renamed, or given a different descriptor.

Check the exact declarations available to R8. A separate remapped stub type does not automatically add missing members to the SDK type. If the contract remains invisible, apply targeted preservation for the required method's existence, name, and complete signature. Validate the optimized consumer artifact and the system dispatch path separately.

This does not imply that public SDK callbacks or every method in the same class also require explicit keep rules.

## Misinterpreting invoke-super references

An optimized `invoke-super` instruction may reference an ancestor type. That reference alone does not establish that runtime dispatch bypasses an intermediate superclass implementation.

Compare with a known working artifact when available, and verify the applicable ART/Dalvik method resolution and dispatch semantics using the actual class hierarchy. Use a minimal runtime reproduction when necessary. Do not add stub declarations merely to restore a preferred class name in decompiled output.

Validate the required dispatch behavior rather than fixing the text of an instruction that R8 may legitimately rewrite.

## Interface hidden field crashes in R8 9.4.24

A reproduced AGP 9.4.1 / R8 9.4.24 case reads `IBinder.SHELL_COMMAND_TRANSACTION` through an interface alias initialized with `RemapStub.value()`. The helper prevents Java constant inlining; it is not inherently incompatible with R8.

The input-only consumer crashes in `FieldNameMinifier`: a missing `frontierStatesForInterfaces` entry is dereferenced with `Set.forEach`. Adding application-side `compileOnly(hidden-api)` was confirmed in actual R8 inputs but did not fix it. Exact `dontwarn` and field keep rules also failed.

Both full sample Release variants passed and retained the interface field read. A controlled minimal consumer passed after adding a reachable Binder subclass, even without hidden-api analysis dependencies. An unused subclass that was removed did not help. A reachable subclass with no fields also passed. The full sample's retained Binder implementations therefore explain why its successful build did not expose the minimal consumer failure.

Do not generate dummy implementations or keep unrelated classes as a library workaround. For this field, an explicitly chosen `Binder.SHELL_COMMAND_TRANSACTION` reference resolves the inherited system field and passed full R8 with no extra implementation. Preserve runtime field access and verify resolution before choosing such an owner manually; Remap should not automatically change field owners. If the interface owner must remain, reproduce and fix the R8 issue or validate a fixed R8 version.

These results apply to the reproduced case. When comparing consumers, record tool versions, actual analysis inputs, retained interface implementations, and final field reads.

## References

- [Android: optimization for library authors and consumer rules](https://developer.android.com/topic/performance/app-optimization/library-optimization)
- [AOSP: Dalvik bytecode instructions](https://source.android.com/docs/core/runtime/dalvik-bytecode)

## GKD callbacks removed without application analysis stubs

Issue #1474 reported `AbstractMethodError` for `IUiAutomationConnection.connect` and
`ITaskStackListener.onTaskDescriptionChanged`. A controlled Release comparison
with the application `compileOnly(hidden-api)` removed found that R8 deleted
`ProxyUiAutomationConnection.connect/disconnect/shutdown` and
`CompatTaskStackListener.onTaskStackChanged`, both `onTaskMovedToFront` overloads,
and `onTransact`. The system superclass references kept their names. The latter
class never implemented `onTaskDescriptionChanged`; its removed `onTransact`
handler had caught such version-dependent errors.

`-keep,allowshrinking` on these program classes and all their members still allowed
all seven callbacks to disappear. Exact `-keepclassmembers` without
`allowshrinking` preserved their names and descriptors without requiring stable
program class names. Keep rules targeting absent system stub definitions cannot
protect these program implementations. A generator must inspect the implementation
hierarchy, including public SDK ancestors behind hidden intermediate types.

The original mapping archive was unavailable; the deletion evidence comes from
the controlled APK comparison, not a reconstruction of the original obfuscated
class names. This callback failure is separate from the interface-field R8 crash.
