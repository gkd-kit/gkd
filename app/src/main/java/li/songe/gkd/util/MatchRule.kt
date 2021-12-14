package li.songe.gkd.util

data class MatchRule(val matchUnit: MatchUnit, val rawText: String) {
    companion object {
        fun parse(text: String): MatchRule {
            val unitStrList = text.split("\u0020").filter { it.isNotEmpty() }.reversed()
            assert(unitStrList.isNotEmpty())
            val matchUnitList = mutableListOf<MatchUnit>()
            val operatorList = mutableListOf<RelationUnit.Operator>()
            unitStrList.forEachIndexed { index, s ->
                when (index % 2) {
                    0 -> {
                        matchUnitList.add(MatchUnit.parse(s))
                    }
                    1 -> {
                        operatorList.add(RelationUnit.parse(s))
                    }
                }
            }
            matchUnitList.forEachIndexed { index, unit ->
                if (index < matchUnitList.size - 1) {
                    unit.relationUnit = RelationUnit(matchUnitList[index + 1], operatorList[index])
                }
            }
            return MatchRule(matchUnitList.first(), text)
        }

        fun stringify(selector: MatchRule): String {
            return selector.rawText
        }
    }
}

/**
// example
root >> LinearLayout -4 WebView > TextView +3 ImageView[text*=x]
 */
