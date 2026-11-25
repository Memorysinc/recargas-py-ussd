package py.com.recargas.ussd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import py.com.recargas.ussd.catalog.CatalogValidator
import py.com.recargas.ussd.catalog.VerificationStatus
import java.io.File

class CatalogValidatorTest {

    private fun loadCatalogText(name: String): String {
        val candidates = listOf(
            File("../../catalog/$name"),
            File("../catalog/$name"),
            File("catalog/$name"),
            File("src/main/assets/catalog_default.json")
        )
        return candidates.first { it.exists() }.readText(Charsets.UTF_8)
    }

    @Test
    fun `validates bundled catalog v1 cleanly`() {
        val raw = loadCatalogText("catalog_v1.json")
        val res = CatalogValidator.parseAndValidate(raw)
        assertTrue("Errores inesperados: ${res.errors}", res.isValid)
        assertTrue(res.catalog!!.operators.size == 2)
        assertTrue(res.catalog!!.verificationLevel == VerificationStatus.VERIFIED_USSD)
    }

    @Test
    fun `rejects catalog containing plaintext pin`() {
        val raw = loadCatalogText("catalog_v1.json")
            .replace("\"shortName\": \"Tigo\"", "\"shortName\": \"Tigo\", \"pin\": \"9999\"")
        val res = CatalogValidator.parseAndValidate(raw)
        assertFalse(res.isValid)
        assertTrue(res.errors.any { it.contains("PIN", ignoreCase = true) })
    }

    @Test
    fun `rejects corrupt json`() {
        val res = CatalogValidator.parseAndValidate("{ invalid json ")
        assertFalse(res.isValid)
    }
}
