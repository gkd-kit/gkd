import li.gkd.app.model.ScreenSize
        assertEquals(59.0, expression.evaluate(bounds, ScreenSize(1080, 2400)), 0.0)
        assertEquals(-4.0, compileRuleExpression("-2*2")!!.evaluate(bounds, ScreenSize(1080, 2400)), 0.0)
        assertEquals(60f to 170f, position.calc(bounds, ScreenSize(1080, 2400)))
                RawSubscription.Position(null, null, null, null, source, "10").calc(bounds, ScreenSize(1080, 2400))
            val error = assertThrows(PositionCalculationException::class.java) { position.calc(bounds, ScreenSize(100, 200)) }.error
        assertEquals(0.0, compileRuleExpression("random-random")!!.evaluate(bounds, ScreenSize(100, 200)), 0.0)
