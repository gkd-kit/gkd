                    action = action ?: requireNotNull(rule).let { GkdAction("", action = it.parameters.action, position = it.parameters.position, swipeArg = it.parameters.swipeArg) })
