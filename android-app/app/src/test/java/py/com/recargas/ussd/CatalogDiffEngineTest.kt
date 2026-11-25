package py.com.recargas.ussd

import org.junit.Assert.assertTrue
import org.junit.Test
import py.com.recargas.ussd.catalog.CatalogDiffEngine
import py.com.recargas.ussd.catalog.CatalogValidator
import py.com.recargas.ussd.catalog.ChangeType
import java.io.File

class CatalogDiffEngineTest {

    @Test
    fun `detects price, validity, added, removed and broken code differences`() {
        val v1Raw = listOf(File("../../catalog/catalog_v1.json"), File("../catalog/catalog_v1.json"), File("catalog/catalog_v1.json")).first { it.exists() }.readText(Charsets.UTF_8)
        val v2Raw = listOf(File("../../catalog/catalog_v2_sample.json"), File("../catalog/catalog_v2_sample.json"), File("catalog/catalog_v2_sample.json")).first { it.exists() }.readText(Charsets.UTF_8)

        val v1 = CatalogValidator.parseAndValidate(v1Raw).catalog!!
        val v2 = CatalogValidator.parseAndValidate(v2Raw).catalog!!

        val report = CatalogDiffEngine.compare(v1, v2)
        assertTrue(report.hasChanges)
        val types = report.changes.map { it.type }.toSet()
        assertTrue(ChangeType.PRICE_CHANGED in types)
        assertTrue(ChangeType.VALIDITY_CHANGED in types)
        assertTrue(ChangeType.PACKAGE_ADDED in types)
        assertTrue(ChangeType.PACKAGE_REMOVED in types)
        assertTrue(ChangeType.CODE_BROKEN in types)
    }
}
