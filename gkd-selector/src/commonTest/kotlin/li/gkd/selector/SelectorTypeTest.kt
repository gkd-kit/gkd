package li.gkd.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private data class TypeFailureCase(
    val source: String,
    val kind: SelectorTypeErrorKind,
    val expression: String,
    val expected: String? = null,
    val actual: String? = null,
)

class SelectorTypeTest {
    private val typeModel = createDefaultSelectorTypeModel()

    @Test
    fun documentedValueTypesAndMethodsValidate() {
        val sources = listOf(
            "View[text='x']",
            "View[text^='x']",
            "View[text~='x']",
            "View[width>1]",
            "View[clickable=true]",
            "View[parent=null]",
            "View[text.length=1]",
            "View[text.substring(0,1)='x']",
            "View[width.plus(1)=2]",
            "View[width.toString()='1']",
            "View[clickable.ifElse(text,desc)='x']",
            "View[parent.getChild(0)=null]",
            "View[equal(text,'x')=true]",
        )

        sources.forEach { source ->
            val selector = compileSelector(source)
            val result = assertIs<SelectorTypeResult.Success>(selector.validateType(typeModel))
            assertSame(selector, result.value)
        }
    }

    @Test
    fun invalidTypesReturnStructuredFailures() {
        val cases = listOf(
            TypeFailureCase(
                source = "View[true>1]",
                kind = SelectorTypeErrorKind.OperatorTypeMismatch,
                expression = "true>1",
                expected = "int operands",
                actual = "boolean and int",
            ),
            TypeFailureCase(
                source = "View[true^='x']",
                kind = SelectorTypeErrorKind.OperatorTypeMismatch,
                expression = "true^=\"x\"",
                expected = "string operands",
                actual = "boolean and string",
            ),
            TypeFailureCase(
                source = "View[true~='x']",
                kind = SelectorTypeErrorKind.OperatorTypeMismatch,
                expression = "true~=\"x\"",
                expected = "variable and regular expression string literal",
                actual = "boolean and string",
            ),
            TypeFailureCase(
                source = "View[text>1]",
                kind = SelectorTypeErrorKind.OperandTypeMismatch,
                expression = "text>1",
                expected = "string",
                actual = "int",
            ),
            TypeFailureCase(
                source = "View[unknown=1]",
                kind = SelectorTypeErrorKind.UnknownIdentifier,
                expression = "unknown",
            ),
            TypeFailureCase(
                source = "View[text.unknown=1]",
                kind = SelectorTypeErrorKind.UnknownMember,
                expression = "text.unknown",
            ),
            TypeFailureCase(
                source = "View[unknown()=1]",
                kind = SelectorTypeErrorKind.UnknownMethod,
                expression = "unknown",
            ),
            TypeFailureCase(
                source = "View[text.substring()='x']",
                kind = SelectorTypeErrorKind.ArgumentCountMismatch,
                expression = "text.substring()",
                expected = "1 or 2",
                actual = "0",
            ),
            TypeFailureCase(
                source = "View[text.substring(true)='x']",
                kind = SelectorTypeErrorKind.ArgumentTypeMismatch,
                expression = "true",
                expected = "int",
                actual = "boolean",
            ),
        )

        cases.forEach { case ->
            val result = compileSelector(case.source).validateType(typeModel)
            val error = assertIs<SelectorTypeResult.Failure>(result).error
            assertEquals(case.kind, error.kind, case.source)
            assertEquals(case.expression, error.expression, case.source)
            assertEquals(case.expected, error.expected, case.source)
            assertEquals(case.actual, error.actual, case.source)
            assertNull(error.range, case.source)
            assertNull(error.index, case.source)
        }
    }

    @Test
    fun parsedSelectorTypeFailuresIncludeExactSourceRange() {
        val source = "View[text.substring(true)='x']"
        val selector = Selector.parse(source).value
        val failure = assertIs<SelectorTypeResult.Failure>(selector.validateType(typeModel))
        val error = failure.error
        val start = source.indexOf("true")
        val range = assertNotNull(error.range)

        assertEquals(SourceRange(start, start + "true".length), range)
        assertEquals(start, error.index)
        assertEquals("true", source.substring(range.start, range.end))

        try {
            failure.value
            kotlin.test.fail("Failure.value must throw")
        } catch (thrown: SelectorTypeException) {
            assertSame(error, thrown)
        }
    }

    @Test
    fun selectorCollectsAllTypeErrorsInSourceOrder() {
        val source = "View[unknownA=1][text.substring(true,unknownB)='x'][unknownC=true]"
        val selector = Selector.parse(source).value
        val errors = selector.getTypeErrors(typeModel)

        assertEquals(
            listOf(
                SelectorTypeErrorKind.UnknownIdentifier,
                SelectorTypeErrorKind.ArgumentTypeMismatch,
                SelectorTypeErrorKind.UnknownIdentifier,
                SelectorTypeErrorKind.UnknownIdentifier,
            ),
            errors.map { it.kind },
        )
        assertEquals(
            listOf("unknownA", "true", "unknownB", "unknownC"),
            errors.map { it.expression },
        )
        assertEquals(
            errors.map { error -> error.expression },
            errors.map { error ->
                val range = assertNotNull(error.range)
                source.substring(range.start, range.end)
            },
        )
        assertEquals(errors.map { it.index }.sortedBy { it }, errors.map { it.index })

        val firstFailure = assertIs<SelectorTypeResult.Failure>(
            selector.validateType(typeModel),
        )
        assertEquals("unknownA", firstFailure.error.expression)
    }

    @Test
    fun allTypeErrorsSuppressDependentMemberFailures() {
        val source = "View[unknownA.member=unknownB]"
        val errors = Selector.parse(source).value.getTypeErrors(typeModel)

        assertEquals(
            listOf(
                SelectorTypeErrorKind.UnknownIdentifier,
                SelectorTypeErrorKind.UnknownIdentifier,
            ),
            errors.map { it.kind },
        )
        assertEquals(listOf("unknownA", "unknownB"), errors.map { it.expression })
    }

    @Test
    fun allTypeErrorsReportEveryMismatchedCallArgument() {
        val source = "View[text.substring(true,'x')='value']"
        val errors = Selector.parse(source).value.getTypeErrors(typeModel)

        assertEquals(
            listOf(
                SelectorTypeErrorKind.ArgumentTypeMismatch,
                SelectorTypeErrorKind.ArgumentTypeMismatch,
            ),
            errors.map { it.kind },
        )
        assertEquals(listOf("true", "\"x\""), errors.map { it.expression })
        assertEquals(
            listOf("true", "'x'"),
            errors.map { error ->
                val range = assertNotNull(error.range)
                source.substring(range.start, range.end)
            },
        )
    }

    @Test
    fun fullTypeCheckingIsIndependentOfParsing() {
        val source = "View[unknown=1]"
        val parsedErrors = Selector.parse(source).value.getTypeErrors(typeModel)
        val compiledErrors = Selector.compile(source).value.getTypeErrors(typeModel)

        assertEquals(parsedErrors.map { it.kind }, compiledErrors.map { it.kind })
        assertNotNull(parsedErrors.single().range)
        assertNull(compiledErrors.single().range)
    }

    @Test
    fun nullOperandsDoNotProduceTypeErrors() {
        val selector = Selector.parse("View[parent=null][text=null]").value

        assertTrue(selector.getTypeErrors(typeModel).isEmpty())
    }

    @Test
    fun allTypeErrorsHandleDeepInvalidMembersWithoutStackRecursion() {
        val source = "View[unknown.${"parent.".repeat(1_000)}text=1][another=2]"
        val errors = Selector.parse(source).value.getTypeErrors(typeModel)

        assertEquals(listOf("unknown", "another"), errors.map { it.expression })
    }

    @Test
    fun deeplyNestedMembersAndCallsTypeCheckWithoutStackRecursion() {
        val memberSource = "View[" + "parent.".repeat(3_000) + "parent=null]"
        assertIs<SelectorTypeResult.Success>(
            compileSelector(memberSource).validateType(typeModel),
        )

        val callSource = "View[width=" + "width.plus(".repeat(3_000) +
                "1" + ")".repeat(3_000) + "]"
        assertIs<SelectorTypeResult.Success>(
            compileSelector(callSource).validateType(typeModel),
        )
    }

    @Test
    fun recursiveTypeModelsFreezeAfterBuildWithoutRecursiveEquality() {
        val builder = SelectorTypeModelBuilder()
        val stringType = builder.type(SelectorTypeKind.StringType)
        val nodeType = builder.type(SelectorTypeKind.ObjectType("node"))
        val globalType = builder.type(SelectorTypeKind.ObjectType("global"))
        builder.property(nodeType, "parent", nodeType)
        builder.property(globalType, "title", stringType)
        builder.property(globalType, "current", nodeType)
        val model = builder.build(globalType)

        assertEquals("global", globalType.toString())
        assertEquals(globalType.hashCode(), globalType.hashCode())
        assertIs<SelectorTypeResult.Success>(
            compileSelector("View[title='x']").validateType(model),
        )
        assertFailsWith<IllegalStateException> {
            builder.property(globalType, "late", stringType)
        }
    }
}
