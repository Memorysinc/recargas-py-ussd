package py.com.recargas.ussd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import py.com.recargas.ussd.catalog.CatalogValidator
import py.com.recargas.ussd.catalog.CategoryId
import py.com.recargas.ussd.ussd.ExecutionMode
import py.com.recargas.ussd.ussd.StepKind
import py.com.recargas.ussd.ussd.UssdPlanBuilder
import java.io.File

class UssdPlanBuilderTest {

    private val catalog by lazy {
        val raw = listOf(File("../../catalog/catalog_v1.json"), File("../catalog/catalog_v1.json"), File("catalog/catalog_v1.json")).first { it.exists() }.readText(Charsets.UTF_8)
        CatalogValidator.parseAndValidate(raw).catalog!!
    }

    @Test
    fun `tigo recharge stops at confirmation and falls back from unverified direct mode`() {
        val tigo = catalog.operators.first { it.id == "tigo" }
        val recharge = tigo.categories.first { it.id == CategoryId.RECHARGE }
        val plan = UssdPlanBuilder.buildRechargePlan(tigo, recharge, "0981000000", 5000, ExecutionMode.DIRECT).getOrThrow()

        assertEquals(ExecutionMode.ASSISTED, plan.effectiveMode)
        assertNotNull(plan.modeFallbackReason)
        assertEquals("*888#", plan.initialCode)
        assertEquals(StepKind.PIN, plan.steps.first().kind)
        assertEquals(StepKind.STOP_AT_CONFIRMATION, plan.steps.last().kind)
    }

    @Test
    fun `claro recharge validates step 1000 and puts pin before confirmation`() {
        val claro = catalog.operators.first { it.id == "claro" }
        val recharge = claro.categories.first { it.id == CategoryId.RECHARGE }

        val bad = UssdPlanBuilder.buildRechargePlan(claro, recharge, "991000000", 1500, ExecutionMode.ASSISTED)
        assertTrue(bad.isFailure)

        val ok = UssdPlanBuilder.buildRechargePlan(claro, recharge, "991000000", 2000, ExecutionMode.ASSISTED).getOrThrow()
        assertEquals("*321#", ok.initialCode)
        assertEquals(StepKind.MENU_OPTION, ok.steps[0].kind)
        assertEquals(StepKind.DESTINATION, ok.steps[1].kind)
        assertEquals(StepKind.AMOUNT, ok.steps[2].kind)
        assertEquals(StepKind.PIN, ok.steps[3].kind)
        assertEquals(StepKind.STOP_AT_CONFIRMATION, ok.steps[4].kind)
    }
}
