# Hidden API and R8 Failure Patterns

Use these patterns to form hypotheses, then verify them against the current project's dependency graph, R8 inputs, and optimized artifacts. They are not universal diagnoses or prescribed fixes.

## Hidden callbacks removed after module migration

Possible symptoms: Debug works while minified Release fails with `AbstractMethodError`, and the usage report lists a system callback implementation as removed.

Check whether compilation stubs remain available to a library or KMP module but are absent from the application module that runs final R8 optimization. Compare the relevant compilation classpaths with actual R8 analysis inputs. Broad `-dontwarn` rules may conceal missing-type diagnostics.

If analysis dependencies are missing, restore the appropriate dependency path. Depending on the build configuration, the application may need its own `compileOnly` dependency on the stub module. Verify that the required callbacks survive optimization and that compilation stubs are not packaged. Then validate the affected runtime path.

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

## References

- [Android: optimization for library authors and consumer rules](https://developer.android.com/topic/performance/app-optimization/library-optimization)
- [AOSP: Dalvik bytecode instructions](https://source.android.com/docs/core/runtime/dalvik-bytecode)
