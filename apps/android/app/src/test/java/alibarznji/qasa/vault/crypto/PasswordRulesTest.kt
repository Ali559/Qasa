package alibarznji.qasa.vault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Apple `passwordrules` parsing and the R2 spec (docs/autofill-hardening.md). */
class PasswordRulesTest {
    @Test
    fun parsesAppleSyntax() {
        val rules = PasswordRules.parse("required: upper; required: digit; required: [-().&@?'#,/\"+]; allowed: lower; max-consecutive: 2; minlength: 8; maxlength: 20;")!!
        assertEquals(3, rules.required.size)
        assertEquals(PasswordRules.UPPER, rules.required[0])
        assertEquals(PasswordRules.DIGIT, rules.required[1])
        assertEquals("-().&@?'#,/\"+".toSet(), rules.required[2])
        assertEquals(PasswordRules.LOWER, rules.allowed)
        assertEquals(2, rules.maxConsecutive)
        assertEquals(8, rules.minLength)
        assertEquals(20, rules.maxLength)
    }

    @Test
    fun requiredListIsOneSet() {
        val rules = PasswordRules.parse("required: upper, lower")!!
        assertEquals(1, rules.required.size)
        assertEquals(PasswordRules.UPPER + PasswordRules.LOWER, rules.required[0])
    }

    @Test
    fun bracketSetsKeepSemicolonsAndClosingBrackets() {
        val rules = PasswordRules.parse("allowed: [;:]; required: [abc]]")!!
        assertEquals(";:".toSet(), rules.allowed)
        assertEquals("abc]".toSet(), rules.required.single())
    }

    @Test
    fun emptyAndUnknownRulesAreNoRules() {
        assertNull(PasswordRules.parse(null))
        assertNull(PasswordRules.parse("   "))
        assertNull(PasswordRules.parse("foo: bar; nonsense"))
        assertNull(PasswordRules.parse("minlength: abc"))
    }

    @Test
    fun asciiPrintableAndUnicodeAllowEverything() {
        assertEquals(PasswordRules.ASCII_PRINTABLE, PasswordRules.parse("allowed: ascii-printable")!!.allowed)
        assertEquals(PasswordRules.ASCII_PRINTABLE, PasswordRules.parse("allowed: unicode")!!.allowed)
        assertEquals(PasswordGenerator.DEFAULT_CLASSES, PasswordSpec.forFields(emptyList(), rules = PasswordRules.parse("allowed: ascii-printable")).classes)
    }

    @Test
    fun lengthFollowsMaxAndMin() {
        assertEquals(16, PasswordSpec.forFields(emptyList()).length)
        assertEquals(16, PasswordSpec.forFields(listOf(64, null)).length)
        assertEquals(12, PasswordSpec.forFields(listOf(20, 12)).length)
        assertEquals(6, PasswordSpec.forFields(listOf(6)).length)
        // Below 4 the field's limit is ignored.
        assertEquals(16, PasswordSpec.forFields(listOf(3)).length)
        assertEquals(24, PasswordSpec.forFields(listOf(null), minLengths = listOf(24)).length)
        // A minimum above the maximum: the field's maximum wins (it would cut the value).
        assertEquals(20, PasswordSpec.forFields(listOf(20), minLengths = listOf(30)).length)
        assertEquals(10, PasswordSpec.forFields(emptyList(), rules = PasswordRules.parse("maxlength: 10")).length)
    }

    @Test
    fun noSymbolsWhenTheRulesDoNotAllowThem() {
        val spec = PasswordSpec.forFields(emptyList(), rules = PasswordRules.parse("required: upper; required: lower; required: digit"))
        repeat(50) {
            val password = PasswordGenerator.generate(spec)
            assertTrue(password, password.all { it.isLetterOrDigit() })
            assertTrue(password.any { it.isUpperCase() } && password.any { it.isLowerCase() } && password.any { it.isDigit() })
        }
    }

    @Test
    fun symbolsOnlyFromTheGeneratorSet() {
        val spec = PasswordSpec.forFields(emptyList(), rules = PasswordRules.parse("required: special; allowed: upper, lower, digit"))
        val generatorSymbols = PasswordGenerator.DEFAULT_CLASSES[3]
        repeat(50) {
            val password = PasswordGenerator.generate(spec)
            assertTrue(password, password.filterNot { it.isLetterOrDigit() }.all { it in generatorSymbols })
            assertTrue(password.any { it in generatorSymbols })
        }
        // A symbol outside our set that the site requires is used.
        val tilde = PasswordSpec.forFields(emptyList(), rules = PasswordRules.parse("required: [~]; allowed: lower"))
        repeat(20) { assertTrue(PasswordGenerator.generate(tilde).contains('~')) }
    }

    @Test
    fun maxConsecutiveIsHonoured() {
        val spec = PasswordSpec(length = 64, classes = listOf("ab".toSet()), maxConsecutive = 1)
        repeat(50) {
            val password = PasswordGenerator.generate(spec)
            assertEquals(64, password.length)
            assertEquals(1, PasswordGenerator.longestRun(password.toCharArray()))
        }
        val two = PasswordSpec(length = 40, classes = listOf(PasswordRules.DIGIT, "x".toSet()), maxConsecutive = 2)
        repeat(50) {
            val password = PasswordGenerator.generate(two)
            assertTrue(PasswordGenerator.longestRun(password.toCharArray()) <= 2)
            assertTrue(password.contains('x') && password.any { it.isDigit() })
        }
    }

    @Test
    fun everyClassGetsACharacter() {
        repeat(100) {
            val password = PasswordGenerator.generate(PasswordSpec(length = 4))
            assertTrue(password, PasswordGenerator.DEFAULT_CLASSES.all { cls -> password.any { it in cls } })
        }
    }
}
