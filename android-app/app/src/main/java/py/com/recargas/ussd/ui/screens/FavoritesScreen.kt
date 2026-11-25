package py.com.recargas.ussd.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import py.com.recargas.ussd.history.FavoriteEntity
import py.com.recargas.ussd.history.FrequentNumberEntity
import py.com.recargas.ussd.ui.components.formatGuaranies

@Composable
fun FavoritesScreen(
    favorites: List<FavoriteEntity>,
    frequentNumbers: List<FrequentNumberEntity>,
    onRunFavorite: (FavoriteEntity) -> Unit,
    onDeleteFavorite: (Long) -> Unit,
    onSaveFrequentNumber: (String, String, String) -> Unit,
    onDeleteFrequentNumber: (String) -> Unit
) {
    var showAddNumberDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Favoritos rápidos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Toca cualquier favorito para cargar la operación en un toque.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (favorites.isEmpty()) {
            item { Text("No tienes favoritos guardados todavía.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(favorites, key = { it.id }) { fav ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onRunFavorite(fav) },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fav.label, fontWeight = FontWeight.Bold)
                            val sub = listOfNotNull(
                                fav.operatorId.uppercase(),
                                fav.amountGs?.let { formatGuaranies(it) },
                                fav.defaultPhoneNumber?.let { "Nro: $it" }
                            ).joinToString(" • ")
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRunFavorite(fav) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Ejecutar")
                        }
                        IconButton(onClick = { onDeleteFavorite(fav.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Números frecuentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { showAddNumberDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Agregar")
                }
            }
        }

        if (frequentNumbers.isEmpty()) {
            item { Text("Aún no hay números frecuentes guardados.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(frequentNumbers, key = { it.phoneNumber }) { item ->
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (item.alias.isNotBlank()) "${item.alias} • ${item.phoneNumber}" else item.phoneNumber,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${item.operatorId.uppercase()} • ${item.useCount} usos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteFrequentNumber(item.phoneNumber) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }
        }
    }

    if (showAddNumberDialog) {
        var num by remember { mutableStateOf("") }
        var alias by remember { mutableStateOf("") }
        var opId by remember { mutableStateOf("tigo") }
        AlertDialog(
            onDismissRequest = { showAddNumberDialog = false },
            title = { Text("Agregar número frecuente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = opId == "tigo", onClick = { opId = "tigo" }, label = { Text("Tigo") })
                        FilterChip(selected = opId == "claro", onClick = { opId = "claro" }, label = { Text("Claro") })
                    }
                    OutlinedTextField(
                        value = num,
                        onValueChange = { num = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Número") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text("Alias (opcional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (num.isNotBlank()) {
                            onSaveFrequentNumber(num, opId, alias)
                            showAddNumberDialog = false
                        }
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddNumberDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
