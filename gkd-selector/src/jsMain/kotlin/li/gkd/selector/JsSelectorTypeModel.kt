package li.gkd.selector

import kotlin.js.collections.JsArray
import kotlin.js.collections.toList

@JsExport
public enum class JsSelectorTypeKind {
    Boolean,
    Int,
    String,
    Object,
}

@JsExport
public class JsSelectorType internal constructor(
    internal val core: SelectorType,
) {
    public val name: String
        get() = core.displayName
}

@JsExport
public class JsSelectorTypeModelBuilder {
    private val core = SelectorTypeModelBuilder()

    public fun type(kind: JsSelectorTypeKind, objectName: String? = null): JsSelectorType {
        val selectorTypeKind = when (kind) {
            JsSelectorTypeKind.Boolean -> SelectorTypeKind.BooleanType
            JsSelectorTypeKind.Int -> SelectorTypeKind.IntType
            JsSelectorTypeKind.String -> SelectorTypeKind.StringType
            JsSelectorTypeKind.Object -> SelectorTypeKind.ObjectType(
                checkNotNull(objectName?.takeIf { it.isNotBlank() }) {
                    "Object type requires a non-empty name"
                },
            )
        }
        return JsSelectorType(core.type(selectorTypeKind))
    }

    public fun property(
        owner: JsSelectorType,
        name: String,
        type: JsSelectorType,
    ): JsSelectorTypeModelBuilder {
        core.property(owner.core, name, type.core)
        return this
    }

    public fun method(
        owner: JsSelectorType,
        name: String,
        returnType: JsSelectorType,
        params: JsArray<JsSelectorType>,
    ): JsSelectorTypeModelBuilder {
        core.method(owner.core, name, returnType.core, params.toList().map { it.core })
        return this
    }

    public fun build(globalType: JsSelectorType): SelectorTypeModel =
        core.build(globalType.core)
}
