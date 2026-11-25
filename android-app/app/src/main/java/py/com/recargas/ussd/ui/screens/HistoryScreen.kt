package py.com.recargas.ussd.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import py.com.recargas.ussd.history.TransactionEntity
import py.com.recargas.ussd.ui.components.TransactionStatusBadge
import py.com.recargas.ussd.ui.components.formatGuaranies
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    transactions: List<TransactionEntity>,
    onRepeat: (TransactionEntity) -> Unit,
    onClearAll: () -> Unit
) {
    var opFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf("ALL") }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    val filtered = remember(transactions, opFilter, statusFilter) {
        transactions.filter { tx ->
            (opFilter == "ALL" || tx.operatorId == opFilter) &&
                (statusFilter == "ALL" || tx.status == statusFilter)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Historial local", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (transactions.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        Text("Limpiar")
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = opFilter == "ALL", onClick = { opFilter = "ALL" }, label = { Text("Todos") })
                FilterChip(selected = opFilter == "tigo", onClick = { opFilter = "tigo" }, label = { Text("Tigo") })
                FilterChip(selected = opFilter == "claro", onClick = { opFilter = "claro" }, label = { Text("Claro") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = statusFilter == "ALL", onClick = { statusFilter = "ALL" }, label = { Text("Todos estados") })
                FilterChip(selected = statusFilter == "COMPLETADA", onClick = { statusFilter = "COMPLETADA" }, label = { Text("Completada") })
                FilterChip(selected = statusFilter == "CANCELADA", onClick = { statusFilter = "CANCELADA" }, label = { Text("Cancelada") })
                FilterChip(selected = statusFilter == "FALLIDA", onClick = { statusFilter = "FALLIDA" }, label = { Text("Fallida") })
            }
        }

        if (filtered.isEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "No hay registros para los filtros seleccionados.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filtered, key = { it.id }) { tx ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${tx.operatorName} • ${tx.operationType}", fontWeight = FontWeight.Bold)
                            TransactionStatusBadge(statusName = tx.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        val details = listOfNotNull(
                            tx.productName,
                            tx.amountGs?.let { formatGuaranies(it) },
                            tx.phoneNumber?.let { "Dest: $it" }
                        ).joinToString(" • ")
                        Text(details, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${dateFmt.format(Date(tx.timestampMillis))} • Modo ${tx.executionMode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (tx.detailMessage.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(tx.detailMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { onRepeat(tx) }) {
                                Icon(Icons.Rounded.Replay, contentDescription = "Repetir")
                            }
                        }
                    }
                }
            }
        }
    }
}
