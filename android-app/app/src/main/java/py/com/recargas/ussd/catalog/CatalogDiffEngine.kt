package py.com.recargas.ussd.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class ChangeType {
    PRICE_CHANGED,
    PACKAGE_ADDED,
    PACKAGE_REMOVED,
    VALIDITY_CHANGED,
    OPTION_ADDED,
    OPTION_REMOVED,
    CODE_BROKEN,
    OPERATOR_ADDED,
    OPERATOR_REMOVED
}

@Serializable
data class CatalogChangeItem(
    val type: ChangeType,
    val operatorName: String,
    val categoryTitle: String,
    val summary: String
)

@Serializable
data class CatalogDiffReport(
    val previousVersion: String,
    val newVersion: String,
    val generatedAt: String,
    val changes: List<CatalogChangeItem> = emptyList()
) {
    val hasChanges: Boolean get() = changes.isNotEmpty()

    fun humanSummary(): String {
        if (changes.isEmpty()) return "Sin cambios funcionales entre v$previousVersion y v$newVersion."
        val header = "Cambios detectados ($previousVersion → $newVersion): ${changes.size}"
        return (listOf(header) + changes.map { "• [${it.operatorName}] ${it.summary}" }).joinToString("\n")
    }
}

object CatalogDiffEngine {
    fun compare(
        oldCatalog: OperatorCatalog,
        newCatalog: OperatorCatalog,
        timestamp: String = newCatalog.updatedAt
    ): CatalogDiffReport {
        val changes = mutableListOf<CatalogChangeItem>()
        val oldOps = oldCatalog.operators.associateBy { it.id }
        val newOps = newCatalog.operators.associateBy { it.id }

        for ((id, op) in newOps) {
            if (id !in oldOps) {
                changes += CatalogChangeItem(ChangeType.OPERATOR_ADDED, op.shortName, "Operador", "Nuevo operador agregado: ${op.name} (${op.ussdCode}).")
            }
        }
        for ((id, op) in oldOps) {
            if (id !in newOps) {
                changes += CatalogChangeItem(ChangeType.OPERATOR_REMOVED, op.shortName, "Operador", "Operador eliminado: ${op.name}.")
            }
        }

        for ((opId, newOp) in newOps) {
            val oldOp = oldOps[opId] ?: continue

            val oldCodes = oldOp.directCodes.associateBy { it.code }
            for (dc in newOp.directCodes) {
                val prev = oldCodes[dc.code]
                if (dc.status == VerificationStatus.BROKEN && prev?.status != VerificationStatus.BROKEN) {
                    changes += CatalogChangeItem(ChangeType.CODE_BROKEN, newOp.shortName, "Códigos", "Código marcado como no operativo: ${dc.code}.")
                }
            }
            if (oldOp.ussdCode != newOp.ussdCode) {
                changes += CatalogChangeItem(ChangeType.CODE_BROKEN, newOp.shortName, "Menú raíz", "Código USSD cambió de ${oldOp.ussdCode} a ${newOp.ussdCode}.")
            }

            val oldCats = oldOp.categories.associateBy { it.id }
            val newCats = newOp.categories.associateBy { it.id }

            for ((catId, newCat) in newCats) {
                val oldCat = oldCats[catId]
                if (oldCat == null) {
                    changes += CatalogChangeItem(ChangeType.OPTION_ADDED, newOp.shortName, newCat.title, "Nueva categoría disponible: ${newCat.title}.")
                    continue
                }

                // Compare recharge menu amounts
                val oldAmts = oldCat.amounts.associateBy { it.amountGs }
                val newAmts = newCat.amounts.associateBy { it.amountGs }
                for ((amt, opt) in newAmts) {
                    if (amt !in oldAmts) {
                        changes += CatalogChangeItem(ChangeType.OPTION_ADDED, newOp.shortName, newCat.title, "Nuevo monto agregado: Gs. ${formatGs(amt)} (opción ${opt.ussdOption}).")
                    }
                }
                for ((amt, _) in oldAmts) {
                    if (amt !in newAmts) {
                        changes += CatalogChangeItem(ChangeType.OPTION_REMOVED, newOp.shortName, newCat.title, "Monto eliminado: Gs. ${formatGs(amt)}.")
                    }
                }

                // Compare packages by id (fallback to name if id changed)
                val oldPkgs = oldCat.packages.associateBy { it.id }
                val newPkgs = newCat.packages.associateBy { it.id }
                for ((pkgId, newPkg) in newPkgs) {
                    val oldPkg = oldPkgs[pkgId]
                    if (oldPkg == null) {
                        val priceText = newPkg.priceGs?.let { "Gs. ${formatGs(it)}" } ?: "sin precio"
                        changes += CatalogChangeItem(ChangeType.PACKAGE_ADDED, newOp.shortName, newCat.title, "Paquete nuevo: ${newPkg.name} ($priceText, ${newPkg.validity}).")
                    } else {
                        if (oldPkg.priceGs != newPkg.priceGs) {
                            val oldP = oldPkg.priceGs?.let { "Gs. ${formatGs(it)}" } ?: "N/D"
                            val newP = newPkg.priceGs?.let { "Gs. ${formatGs(it)}" } ?: "N/D"
                            changes += CatalogChangeItem(ChangeType.PRICE_CHANGED, newOp.shortName, newCat.title, "Precio modificado en ${newPkg.name}: $oldP → $newP.")
                        }
                        if (oldPkg.validity != newPkg.validity) {
                            changes += CatalogChangeItem(ChangeType.VALIDITY_CHANGED, newOp.shortName, newCat.title, "Vigencia modificada en ${newPkg.name}: ${oldPkg.validity} → ${newPkg.validity}.")
                        }
                        if (oldPkg.status != VerificationStatus.BROKEN && newPkg.status == VerificationStatus.BROKEN) {
                            changes += CatalogChangeItem(ChangeType.CODE_BROKEN, newOp.shortName, newCat.title, "Paquete marcado sin servicio: ${newPkg.name}.")
                        }
                    }
                }
                for ((pkgId, oldPkg) in oldPkgs) {
                    if (pkgId !in newPkgs) {
                        changes += CatalogChangeItem(ChangeType.PACKAGE_REMOVED, newOp.shortName, newCat.title, "Paquete eliminado: ${oldPkg.name}.")
                    }
                }

                // Compare queries
                val oldQueries = oldCat.queries.associateBy { it.id }
                val newQueries = newCat.queries.associateBy { it.id }
                for ((qId, newQ) in newQueries) {
                    val oldQ = oldQueries[qId]
                    if (oldQ == null) {
                        changes += CatalogChangeItem(ChangeType.OPTION_ADDED, newOp.shortName, newCat.title, "Nueva consulta agregada: ${newQ.name}.")
                    } else if (oldQ.status != VerificationStatus.BROKEN && newQ.status == VerificationStatus.BROKEN) {
                        changes += CatalogChangeItem(ChangeType.CODE_BROKEN, newOp.shortName, newCat.title, "Consulta dejó de funcionar: ${newQ.name}.")
                    }
                }
                for ((qId, oldQ) in oldQueries) {
                    if (qId !in newQueries) {
                        changes += CatalogChangeItem(ChangeType.OPTION_REMOVED, newOp.shortName, newCat.title, "Consulta eliminada: ${oldQ.name}.")
                    }
                }
            }
            for ((catId, oldCat) in oldCats) {
                if (catId !in newCats) {
                    changes += CatalogChangeItem(ChangeType.OPTION_REMOVED, newOp.shortName, oldCat.title, "Categoría eliminada: ${oldCat.title}.")
                }
            }
        }

        return CatalogDiffReport(
            previousVersion = oldCatalog.catalogVersion,
            newVersion = newCatalog.catalogVersion,
            generatedAt = timestamp,
            changes = changes
        )
    }

    private fun formatGs(value: Int): String = "%,d".format(value).replace(',', '.')
}
