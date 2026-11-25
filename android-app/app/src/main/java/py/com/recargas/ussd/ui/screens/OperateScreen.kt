package py.com.recargas.ussd.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import py.com.recargas.ussd.catalog.CategoryDefinition
import py.com.recargas.ussd.catalog.CategoryId
import py.com.recargas.ussd.catalog.OperatorDefinition
import py.com.recargas.ussd.history.FavoriteEntity
import py.com.recargas.ussd.preferences.AppPreferences
import py.com.recargas.ussd.updates.CatalogStateSnapshot
import py.com.recargas.ussd.ui.components.VerificationBadge
import py.com.recargas.ussd.ui.components.cardPaddingDp
import py.com.recargas.ussd.ui.components.formatGuaranies
import py.com.recargas.ussd.ui.components.parseHexColor
import py.com.recargas.ussd.ussd.DetectedSim

@Composable
fun OperateScreen(
    catalogSnapshot: CatalogStateSnapshot,
    preferences: AppPreferences,
    selectedOperatorId: String,
    detectedSims: Map<String, DetectedSim>,
    favorites: List<FavoriteEntity>,
    accessibilityEnabled: Boolean,
    onSelectOperator: (String) -> Unit,
    onOpenCategory: (OperatorDefinition, CategoryDefinition) -> Unit,
    onOpenFavorite: (FavoriteEntity) -> Unit,
    onDismissDiffAlert: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val catalog = catalogSnapshot.activeCatalog
    val orderedOperators = remember(catalog.operators, preferences.operatorOrder) {
        val byId = catalog.operators.associateBy { it.id }
        val ordered = preferences.operatorOrder.mapNotNull { byId[it] }
        val rest = catalog.operators.filter { it.id !in preferences.operatorOrder }
        ordered + rest
    }
    val activeOperator = orderedOperators.firstOrNull { it.id == selectedOperatorId } ?: orderedOperators.firstOrNull()

    var showDiffDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Recargas PY",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Catálogo v${catalog.catalogVersion} • ${catalog.operators.size} operadores",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                VerificationBadge(status = catalog.verificationLevel)
            }
        }

        if (preferences.showUpdateAlerts && catalogSnapshot.lastDiffReport?.hasChanges == true) {
            item {
                val diff = catalogSnapshot.lastDiffReport
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showDiffDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Catálogo actualizado a v${diff.newVersion}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${diff.changes.size} cambios detectados. Toca para ver resumen.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = onDismissDiffAlert) { Text("Ocultar") }
                    }
                }
            }
        }

        if (!accessibilityEnabled) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenSettings() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Asistente USSD inactivo (modo guía manual). Toca para configurar.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Big Operator Selector Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                orderedOperators.forEach { op ->
                    val isSelected = op.id == activeOperator?.id
                    val brandColor = parseHexColor(op.brandColorHex)
                    val sim = detectedSims[op.id]
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectOperator(op.id) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) brandColor else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = op.shortName.uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color.White else brandColor
                                )
                                Text(
                                    text = op.ussdCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.SimCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = sim?.let { "SIM ${it.displayName} (S${it.slotIndex + 1})" } ?: "SIM no detectada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Favorites Carousel
        if (favorites.isNotEmpty()) {
            item {
                Text(
                    text = "Accesos rápidos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favorites, key = { it.id }) { fav ->
                        val favOp = catalog.operators.firstOrNull { it.id == fav.operatorId }
                        val favColor = favOp?.let { parseHexColor(it.brandColorHex) } ?: MaterialTheme.colorScheme.primary
                        Card(
                            modifier = Modifier.width(175.dp).clickable { onOpenFavorite(fav) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, favColor.copy(alpha = 0.35f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Star, contentDescription = null, tint = favColor, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = favOp?.shortName ?: fav.operatorId.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = favColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = fav.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                fav.amountGs?.let { amt ->
                                    Text(
                                        text = formatGuaranies(amt),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Operator-specific Categories Grid
        if (activeOperator != null) {
            val visibleCategories = activeOperator.categories
                .filter { it.id.name !in preferences.hiddenCategories }
                .sortedBy { cat ->
                    val idx = preferences.categoryOrder.indexOf(cat.id.name)
                    if (idx == -1) Int.MAX_VALUE else idx
                }

            item {
                Text(
                    text = "Operaciones de ${activeOperator.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val rows = visibleCategories.chunked(2)
            items(rows) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { cat ->
                        CategoryActionCard(
                            operator = activeOperator,
                            category = cat,
                            paddingDp = preferences.buttonSize.cardPaddingDp(),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenCategory(activeOperator, cat) }
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showDiffDialog && catalogSnapshot.lastDiffReport != null) {
        val diff = catalogSnapshot.lastDiffReport
        AlertDialog(
            onDismissRequest = { showDiffDialog = false },
            title = { Text("Cambios v${diff.previousVersion} → v${diff.newVersion}") },
            text = { Text(diff.humanSummary()) },
            confirmButton = {
                TextButton(onClick = { showDiffDialog = false }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun CategoryActionCard(
    operator: OperatorDefinition,
    category: CategoryDefinition,
    paddingDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val brandColor = parseHexColor(operator.brandColorHex)
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(paddingDp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = categoryIcon(category.id),
                    contentDescription = null,
                    tint = brandColor,
                    modifier = Modifier.size(28.dp)
                )
                VerificationBadge(status = category.status)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                category.amounts.isNotEmpty() -> "${category.amounts.size} importes disponibles"
                category.packages.isNotEmpty() -> "${category.packages.size} opciones"
                category.queries.isNotEmpty() -> "${category.queries.size} consultas"
                category.minAmountGs != null -> "Gs. ${category.minAmountGs}–${category.maxAmountGs}"
                else -> "Navegación asistida"
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun categoryIcon(id: CategoryId): ImageVector = when (id) {
    CategoryId.RECHARGE -> Icons.Rounded.AccountBalanceWallet
    CategoryId.COMBOS -> Icons.Rounded.Inventory2
    CategoryId.INTERNET -> Icons.Rounded.AllInclusive
    CategoryId.CALLS_SMS -> Icons.Rounded.Call
    CategoryId.ROAMING -> Icons.Rounded.Flight
    CategoryId.PACKS -> Icons.Rounded.Wifi
    CategoryId.QUERIES -> Icons.Rounded.QueryStats
}
