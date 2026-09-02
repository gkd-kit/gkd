package li.gkd.selector.engine

import li.gkd.selector.LogicalOperator
import li.gkd.selector.relation.RelationSelector
import li.gkd.selector.property.PropertySelector

internal sealed class SelectorExpression

internal class UnitSelectorExpression(
    val propertySelectors: List<PropertySelector>,
    val relations: List<RelationSelector>,
) : SelectorExpression()

internal class LogicalSelectorExpression(
    val left: SelectorExpression,
    val operator: LogicalOperator,
    val right: SelectorExpression,
) : SelectorExpression()

internal class NotSelectorExpression(
    val expression: SelectorExpression,
) : SelectorExpression()
