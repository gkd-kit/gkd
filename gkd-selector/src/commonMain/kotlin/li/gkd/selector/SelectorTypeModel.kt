package li.gkd.selector

import kotlin.js.JsExport

public sealed class SelectorTypeKind(public val key: String) {
    internal open val displayName: String
        get() = key

    public data object BooleanType : SelectorTypeKind("boolean")
    public data object IntType : SelectorTypeKind("int")
    public data object StringType : SelectorTypeKind("string")
    public data class ObjectType(val name: String) : SelectorTypeKind("object") {
        override val displayName: String
            get() = name
    }
}

public data class SelectorMethod(
    val name: String,
    val returnType: SelectorType,
    val params: List<SelectorType> = emptyList(),
)

public data class SelectorProperty(
    val name: String,
    val type: SelectorType,
)

public class SelectorType internal constructor(
    public val type: SelectorTypeKind,
) {
    private var initialized: Boolean = false
    private var propList: List<SelectorProperty> = emptyList()
    private var methodList: List<SelectorMethod> = emptyList()

    public val props: List<SelectorProperty>
        get() {
            checkInitialized()
            return propList
        }

    public val methods: List<SelectorMethod>
        get() {
            checkInitialized()
            return methodList
        }

    public val displayName: String
        get() = type.displayName

    internal fun initialize(props: List<SelectorProperty>, methods: List<SelectorMethod>) {
        check(!initialized) { "Type $displayName is already initialized" }
        propList = props.toList()
        methodList = methods.toList()
        initialized = true
    }

    internal fun checkInitialized() {
        check(initialized) { "Type $displayName is not initialized" }
    }

    override fun toString(): String = displayName
}

public class SelectorTypeModelBuilder {
    private class Definition(
        val props: MutableList<SelectorProperty> = mutableListOf(),
        val methods: MutableList<SelectorMethod> = mutableListOf(),
    )

    private val definitions = mutableMapOf<SelectorType, Definition>()
    private var built = false

    public fun type(type: SelectorTypeKind): SelectorType {
        check(!built) { "Type model is already built" }
        return SelectorType(type).also { definitions[it] = Definition() }
    }

    public fun property(owner: SelectorType, name: String, type: SelectorType): SelectorTypeModelBuilder {
        requireOwned(type)
        definition(owner).props.add(SelectorProperty(name, type))
        return this
    }

    public fun method(
        owner: SelectorType,
        name: String,
        returnType: SelectorType,
        params: List<SelectorType> = emptyList(),
    ): SelectorTypeModelBuilder {
        requireOwned(returnType)
        params.forEach(::requireOwned)
        definition(owner).methods.add(SelectorMethod(name, returnType, params.toList()))
        return this
    }

    internal fun properties(owner: SelectorType, props: List<SelectorProperty>): SelectorTypeModelBuilder {
        definition(owner).props.addAll(props)
        return this
    }

    internal fun methods(owner: SelectorType, methods: List<SelectorMethod>): SelectorTypeModelBuilder {
        definition(owner).methods.addAll(methods)
        return this
    }

    public fun build(globalType: SelectorType): SelectorTypeModel {
        check(!built) { "Type model is already built" }
        check(definitions.containsKey(globalType)) { "Global type does not belong to this builder" }
        definitions.forEach { (type, definition) ->
            type.initialize(definition.props, definition.methods)
        }
        built = true
        return SelectorTypeModel(globalType)
    }

    private fun definition(type: SelectorType): Definition {
        check(!built) { "Type model is already built" }
        return checkNotNull(definitions[type]) { "Type does not belong to this builder" }
    }

    private fun requireOwned(type: SelectorType) {
        check(definitions.containsKey(type)) { "Type does not belong to this builder" }
    }
}

@JsExport
public class SelectorTypeModel internal constructor(
    internal val globalType: SelectorType,
)
