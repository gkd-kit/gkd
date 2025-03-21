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
    val params: List<TypeInfo> = emptyList(),
) : Stringify {
    override fun stringify(): String {
        return "$name(${params.joinToString(", ") { it.stringify() }}): ${returnType.stringify()}"
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
    var props: List<PropInfo> = emptyList(),
    var methods: List<MethodInfo> = emptyList(),
) : Stringify {
    override fun stringify(): String {
        return if (type is PrimitiveType.ObjectType) {
            type.name
        } else {
            type.key
        }
    }
}