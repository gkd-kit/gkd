package li.gkd.selector

import kotlin.js.JsExport

@JsExport
/** Result of [Selector.compile]. Reading [Failure.value] throws [Failure.error]. */
public sealed class SelectorCompileResult {
    public abstract val value: Selector

    public class Success internal constructor(
        override val value: Selector,
    ) : SelectorCompileResult()

    public class Failure internal constructor(
        public val error: SelectorSyntaxException,
    ) : SelectorCompileResult() {
        override val value: Selector
            get() = throw error
    }
}

@JsExport
/** Result of [Selector.parse]. Tokens remain available when semantic parsing fails. */
public sealed class SelectorParseResult {
    public abstract val value: Selector
    public abstract val tokens: Array<out SelectorToken>
    public abstract val positions: Array<out SelectorPosition>

    public class Success internal constructor(
        override val value: Selector,
        override val tokens: Array<out SelectorToken>,
        override val positions: Array<out SelectorPosition>,
    ) : SelectorParseResult()

    public class Failure internal constructor(
        public val error: SelectorSyntaxException,
        override val tokens: Array<out SelectorToken>,
        override val positions: Array<out SelectorPosition>,
    ) : SelectorParseResult() {
        override val value: Selector
            get() = throw error
    }
}

@JsExport
/** Result of [Selector.validateType]. Reading [Failure.value] throws [Failure.error]. */
public sealed class SelectorTypeResult {
    public abstract val value: Selector

    public class Success internal constructor(
        override val value: Selector,
    ) : SelectorTypeResult()

    public class Failure internal constructor(
        public val error: SelectorTypeException,
    ) : SelectorTypeResult() {
        override val value: Selector
            get() = throw error
    }
}
