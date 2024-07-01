package li.songe.selector

import kotlin.js.JsExport

@JsExport
sealed class PrimitiveType(val key: String) {
    data object BooleanType : PrimitiveType("boolean")
    data object IntType : PrimitiveType("int")
    data object StringType : PrimitiveType("string")
    data class ObjectType(val name: String) : PrimitiveType("object")
}

@JsExport
data class MethodInfo(
    val name: String,
    val returnType: TypeInfo,
    val params: Array<TypeInfo> = emptyArray(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MethodInfo

        if (name != other.name) return false
        if (returnType != other.returnType) return false
        if (!params.contentEquals(other.params)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + params.contentHashCode()
        return result
    }
}

@JsExport
data class PropInfo(
    val name: String,
    val type: TypeInfo,
)

@JsExport
data class TypeInfo(
    val type: PrimitiveType,
    var props: Array<PropInfo> = arrayOf(),
    var methods: Array<MethodInfo> = arrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TypeInfo

        if (type != other.type) return false
        if (!props.contentEquals(other.props)) return false
        if (!methods.contentEquals(other.methods)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + props.contentHashCode()
        result = 31 * result + methods.contentHashCode()
        return result
    }
}