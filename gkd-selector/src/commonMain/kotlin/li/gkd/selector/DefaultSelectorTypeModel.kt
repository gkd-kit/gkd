package li.gkd.selector

import li.gkd.selector.property.BuiltinScope
import li.gkd.selector.property.BuiltinTypeSet
import li.gkd.selector.property.BuiltinMembers

private object DefaultSelectorTypeModels {
    val standard: SelectorTypeModel by lazy { buildDefaultSelectorTypeModel(webField = false) }
    val web: SelectorTypeModel by lazy { buildDefaultSelectorTypeModel(webField = true) }
}

@kotlin.js.JsExport
public fun createDefaultSelectorTypeModel(webField: Boolean = false): SelectorTypeModel =
    if (webField) DefaultSelectorTypeModels.web else DefaultSelectorTypeModels.standard

private fun buildDefaultSelectorTypeModel(webField: Boolean): SelectorTypeModel {
    val builder = SelectorTypeModelBuilder()
    val booleanType = builder.type(SelectorTypeKind.BooleanType)
    val intType = builder.type(SelectorTypeKind.IntType)
    val stringType = builder.type(SelectorTypeKind.StringType)
    val nodeType = builder.type(SelectorTypeKind.ObjectType("node"))
    val contextType = builder.type(SelectorTypeKind.ObjectType("context"))
    val globalType = builder.type(SelectorTypeKind.ObjectType("global"))

    val builtinTypes = BuiltinTypeSet(
        booleanType = booleanType,
        intType = intType,
        stringType = stringType,
        contextType = contextType,
        nodeType = nodeType,
    )
    builder.methods(booleanType, BuiltinMembers.methodInfos(BuiltinScope.Boolean, builtinTypes))
    builder.methods(intType, BuiltinMembers.methodInfos(BuiltinScope.Int, builtinTypes))
    builder.properties(stringType, listOf(
        SelectorProperty("length", intType),
    ))
    builder.methods(stringType, BuiltinMembers.methodInfos(BuiltinScope.String, builtinTypes))
    val nodeProps = (if (webField) {
        listOf(
            SelectorProperty("_id", intType),
            SelectorProperty("_pid", intType),
        )
    } else {
        emptyList()
    }) + listOf(
        SelectorProperty("id", stringType),
        SelectorProperty("vid", stringType),
        SelectorProperty("name", stringType),
        SelectorProperty("text", stringType),
        SelectorProperty("desc", stringType),

        SelectorProperty("clickable", booleanType),
        SelectorProperty("focusable", booleanType),
        SelectorProperty("checkable", booleanType),
        SelectorProperty("checked", booleanType),
        SelectorProperty("editable", booleanType),
        SelectorProperty("longClickable", booleanType),
        SelectorProperty("visibleToUser", booleanType),

        SelectorProperty("left", intType),
        SelectorProperty("top", intType),
        SelectorProperty("right", intType),
        SelectorProperty("bottom", intType),
        SelectorProperty("width", intType),
        SelectorProperty("height", intType),

        SelectorProperty("childCount", intType),
        SelectorProperty("index", intType),
        SelectorProperty("depth", intType),

        SelectorProperty("parent", nodeType),
    )
    val nodeMethods = listOf(
        SelectorMethod("getChild", nodeType, listOf(intType)),
    )
    builder.properties(nodeType, nodeProps)
    builder.methods(nodeType, nodeMethods)
    builder.methods(
        contextType,
        nodeMethods +
                BuiltinMembers.methodInfos(BuiltinScope.Context, builtinTypes),
    )
    val contextProps = nodeProps + listOf(
        SelectorProperty("prev", contextType),
        SelectorProperty("current", nodeType),
    )
    builder.properties(contextType, contextProps)
    builder.methods(
        globalType,
        nodeMethods +
                BuiltinMembers.methodInfos(BuiltinScope.Context, builtinTypes) +
                BuiltinMembers.methodInfos(BuiltinScope.Global, builtinTypes),
    )
    builder.properties(globalType, contextProps)
    return builder.build(globalType)
}
