package py.com.recargas.ussd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import py.com.recargas.ussd.catalog.PhoneFormatRule
import py.com.recargas.ussd.ussd.PhoneFormatter

class PhoneFormatterTest {

    private val tigoRule = PhoneFormatRule(true, 10, 10, "0981000000", "hint")
    private val claroRule = PhoneFormatRule(false, 9, 9, "991000000", "hint")

    @Test
    fun `normalizes tigo number with and without leading zero`() {
        val r1 = PhoneFormatter.normalizeAndValidate("0981000000", tigoRule)
        assertTrue(r1.isValid)
        assertEquals("0981000000", r1.normalized)

        val r2 = PhoneFormatter.normalizeAndValidate("981000000", tigoRule)
        assertTrue(r2.isValid)
        assertEquals("0981000000", r2.normalized)

        val r3 = PhoneFormatter.normalizeAndValidate("+595 981 000 000", tigoRule)
        assertTrue(r3.isValid)
        assertEquals("0981000000", r3.normalized)
    }

    @Test
    fun `normalizes claro number without leading zero`() {
        val r1 = PhoneFormatter.normalizeAndValidate("991000000", claroRule)
        assertTrue(r1.isValid)
        assertEquals("991000000", r1.normalized)

        val r2 = PhoneFormatter.normalizeAndValidate("0991000000", claroRule)
        assertTrue(r2.isValid)
        assertEquals("991000000", r2.normalized)
    }

    @Test
    fun `rejects invalid short numbers`() {
        val bad = PhoneFormatter.normalizeAndValidate("0983", tigoRule)
        assertFalse(bad.isValid)
    }
}
