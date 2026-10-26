                        ActionPerformer.getAction(rule.parameters.action
                            ?: rule.parameters.position?.let { ActionPerformer.ClickCenter.action }
                            ?: rule.parameters.swipeArg?.let { ActionPerformer.Swipe.action })
                            .perform(node, rule.parameters, coordinator.diagnostics.enabled)
