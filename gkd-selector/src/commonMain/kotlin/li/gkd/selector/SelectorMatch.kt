package li.gkd.selector

import kotlin.js.JsExport

@JsExport
public enum class SelectorRelationKind {
    BeforeSibling,
    AfterSibling,
    Ancestor,
    Child,
    Descendant,
    Previous,
}

@JsExport
/** A successful selector match. Only the branch that produced the match is retained. */
public class SelectorMatch<T : Any> internal constructor(
    public val target: T,
    public val units: Array<SelectorMatchUnit<T>>,
)

@JsExport
/** The successful path of one unit selector. */
public class SelectorMatchUnit<T : Any> internal constructor(
    public val target: T,
    public val steps: Array<SelectorMatchStep<T>>,
    public val range: SourceRange?,
)

@JsExport
/** One relation followed from [source] to [target] in a successful unit path. */
public class SelectorMatchStep<T : Any> internal constructor(
    public val source: T,
    public val target: T,
    public val kind: SelectorRelationKind,
    public val offset: Int,
    public val formattedRelation: String,
    public val sourceRange: SourceRange?,
    public val relationRange: SourceRange?,
    public val targetRange: SourceRange?,
)
