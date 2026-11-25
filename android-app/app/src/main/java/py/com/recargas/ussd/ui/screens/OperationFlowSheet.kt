package py.com.recargas.ussd.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import py.com.recargas.ussd.catalog.AmountInputMode
import py.com.recargas.ussd.catalog.CategoryId
import py.com.recargas.ussd.catalog.PackageOption
import py.com.recargas.ussd.catalog.QueryOption
import py.com.recargas.ussd.history.FrequentNumberEntity
import py.com.recargas.ussd.history.TransactionStatus
import py.com.recargas.ussd.preferences.AppPreferences
import py.com.recargas.ussd.ui.ActiveOperationDraft
import py.com.recargas.ussd.ui.components.VerificationBadge
import py.com.recargas.ussd.ui.components.buttonHeightDp
import py.com.recargas.ussd.ui.components.formatGuaranies
import py.com.recargas.ussd.ui.components.parseHexColor
import py.com.recargas.ussd.ussd.ExecutionMode
import py.com.recargas.ussd.ussd.PhoneFormatter
import py.com.recargas.ussd.ussd.SimSelector
import py.com.recargas.ussd.ussd.UssdExecutionPlan
import py.com.recargas.ussd.ussd.UssdSessionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OperationFlowScreen(
    draft: ActiveOperationDraft,
    preferences: AppPreferences,
    frequentNumbers: List<FrequentNumberEntity>,
    onBack: () -> Unit,
    onPrepareRecharge: (String, Int) -> String?,
    onPreparePackage: (PackageOption, String) -> String?,
    onPrepareInteractive: (String) -> String?,
    onPrepareQuery: (QueryOption, String) -> String?,
    onSaveFavorite: (String, String?, String, Int?, String, String?) -> Unit
) {
    val context = LocalContext.current
    val op = draft.operator
    val cat = draft.category
    val brandColor = parseHexColor(op.brandColorHex)

    var phoneInput by remember(draft) { mutableStateOf(draft.initialPhone) }
    var selectedAmountGs by remember(draft) { mutableStateOf(draft.initialAmountGs) }
    var customAmountText by remember(draft) {
        mutableStateOf(draft.initialAmountGs?.toString().orEmpty())
    }
    var selectedPackage by remember(draft) {
        mutableStateOf(cat.packages.firstOrNull { it.id == draft.initialPackageId } ?: cat.packages.firstOrNull())
    }
    var selectedQuery by remember(draft) { mutableStateOf(cat.queries.firstOrNull()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var favSavedNotice by remember { mutableStateOf(false) }

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!SimSelector.hasPermission(context, Manifest.permission.READ_CONTACTS)) return@rememberLauncherForActivityResult
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(uri.lastPathSegment.orEmpty()),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val raw = it.getString(0).orEmpty()
                val res = PhoneFormatter.normalizeAndValidate(raw, op.phoneFormat)
                phoneInput = res.normalized.ifBlank { raw.filter { ch -> ch.isDigit() } }
                errorMessage = null
            }
        }
    }

    val contactPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) contactPicker.launch(null)
    }

    val needsDestination = when (cat.id) {
        CategoryId.QUERIES -> selectedQuery?.requiresDestination == true
        else -> cat.requiresDestination
    }

    val opFrequents = remember(frequentNumbers, op.id) {
        frequentNumbers.filter { it.operatorId == op.id }.take(6)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${op.shortName} • ${cat.title}", fontWeight = FontWeight.Bold)
                        Text("Código ${op.ussdCode}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (cat.id != CategoryId.QUERIES && cat.id != CategoryId.PACKS) {
                        IconButton(
                            onClick = {
                                val (prodId, prodName, amt, valStr) = when (cat.id) {
                                    CategoryId.RECHARGE -> {
                                        val a = selectedAmountGs ?: customAmountText.toIntOrNull()
                                        listOf(null, "Recarga ${formatGuaranies(a)}", a, "Según operador")
                                    }
                                    else -> {
                                        val p = selectedPackage
                                        listOf(p?.id, p?.name ?: cat.title, p?.priceGs, p?.validity.orEmpty())
                                    }
                                }
                                val amtInt = amt as? Int
                                val label = "${op.shortName} ${prodName as String}"
                                onSaveFavorite(
                                    label,
                                    prodId as? String,
                                    prodName,
                                    amtInt,
                                    valStr as String,
                                    phoneInput
                                )
                                favSavedNotice = true
                            }
                        ) {
                            Icon(Icons.Rounded.StarOutline, contentDescription = "Guardar favorito")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (favSavedNotice) {
                item {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Guardado en Favoritos.",
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (cat.note.isNotBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            VerificationBadge(status = cat.status)
                            Spacer(Modifier.width(8.dp))
                            Text(cat.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Queries selector
            if (cat.id == CategoryId.QUERIES) {
                items(cat.queries, key = { it.id }) { q ->
                    val isSel = selectedQuery?.id == q.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedQuery = q
                            errorMessage = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) brandColor else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(q.name, fontWeight = FontWeight.Bold)
                                if (q.note.isNotBlank()) {
                                    Text(q.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            VerificationBadge(status = q.status)
                        }
                    }
                }
            }

            // Destination phone input
            if (needsDestination) {
                item {
                    val hintText = if (op.id == "claro" && cat.id == CategoryId.ROAMING) {
                        "Número destino internacional (sin validación local)"
                    } else {
                        op.phoneFormat.hint
                    }
                    val validation = if (phoneInput.isNotBlank() && !(op.id == "claro" && cat.id == CategoryId.ROAMING)) {
                        PhoneFormatter.normalizeAndValidate(phoneInput, op.phoneFormat)
                    } else null

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it.filter { ch -> ch.isDigit() }
                            errorMessage = null
                        },
                        label = { Text("Número destinatario") },
                        placeholder = { Text(op.phoneFormat.example) },
                        supportingText = {
                            Text(validation?.errorMessage ?: hintText)
                        },
                        isError = validation?.isValid == false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (SimSelector.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
                                        contactPicker.launch(null)
                                    } else {
                                        contactPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Contacts, contentDescription = "Elegir contacto")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (opFrequents.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Frecuentes", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            opFrequents.forEach { freq ->
                                FilterChip(
                                    selected = phoneInput == freq.phoneNumber,
                                    onClick = { phoneInput = freq.phoneNumber },
                                    label = {
                                        Text(
                                            if (freq.alias.isNotBlank()) "${freq.alias} (${freq.phoneNumber})"
                                            else freq.phoneNumber
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Recharge Amounts
            if (cat.id == CategoryId.RECHARGE) {
                item {
                    Text("Selecciona el importe", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val amounts = if (cat.amountInputMode == AmountInputMode.MENU_OPTION) {
                        cat.amounts.sortedBy { it.amountGs }.map { it.amountGs }
                    } else {
                        cat.suggestedAmountsGs
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        amounts.forEach { amt ->
                            val isSel = selectedAmountGs == amt
                            OutlinedButton(
                                onClick = {
                                    selectedAmountGs = amt
                                    customAmountText = amt.toString()
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) brandColor else MaterialTheme.colorScheme.outlineVariant),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) brandColor.copy(alpha = 0.12f) else Color.Transparent
                                ),
                                modifier = Modifier.height(preferences.buttonSize.buttonHeightDp())
                            ) {
                                Text(
                                    text = formatGuaranies(amt),
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) brandColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (cat.amountInputMode == AmountInputMode.FREE_STEP) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customAmountText,
                            onValueChange = { raw ->
                                val clean = raw.filter { it.isDigit() }
                                customAmountText = clean
                                selectedAmountGs = clean.toIntOrNull()
                                errorMessage = null
                            },
                            label = { Text("Monto personalizado (múltiplos de Gs. 1.000)") },
                            supportingText = { Text("Rango verificado: Gs. ${cat.minAmountGs ?: 1000} a Gs. ${cat.maxAmountGs ?: 124000}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Packages list
            if (cat.packages.isNotEmpty()) {
                item {
                    Text("Selecciona un paquete", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                items(cat.packages, key = { it.id }) { pkg ->
                    val isSel = selectedPackage?.id == pkg.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedPackage = pkg
                            errorMessage = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) brandColor else MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) brandColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pkg.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${formatGuaranies(pkg.priceGs)} • Vigencia: ${pkg.validity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            VerificationBadge(status = pkg.status)
                        }
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        errorMessage = when (cat.id) {
                            CategoryId.RECHARGE -> {
                                val amt = selectedAmountGs ?: customAmountText.toIntOrNull()
                                if (amt == null) "Selecciona o ingresa un monto."
                                else onPrepareRecharge(phoneInput, amt)
                            }
                            CategoryId.QUERIES -> {
                                val q = selectedQuery
                                if (q == null) "Selecciona una consulta."
                                else onPrepareQuery(q, phoneInput)
                            }
                            CategoryId.PACKS -> onPrepareInteractive(phoneInput)
                            else -> {
                                val pkg = selectedPackage
                                if (pkg == null) "Selecciona un paquete."
                                else onPreparePackage(pkg, phoneInput)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(preferences.buttonSize.buttonHeightDp())
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Revisar resumen y ejecutar USSD", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PreConfirmationDialog(
    plan: UssdExecutionPlan,
    autoPinEnabled: Boolean,
    hasStoredPin: Boolean,
    accessibilityEnabled: Boolean,
    onConfirmLaunch: () -> Unit,
    onCancel: () -> Unit
) {
    val s = plan.summary
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Resumen previo (${s.operatorName})", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Operación: ${s.operationType}", fontWeight = FontWeight.SemiBold)
                s.destinationNumber?.let { Text("Destinatario: $it") }
                Text("Producto: ${s.productName}")
                s.amountGs?.let { Text("Importe: ${formatGuaranies(it)}") }
                Text("Vigencia: ${s.validity}")
                VerificationBadge(status = s.verificationStatus)
                if (plan.modeFallbackReason != null) {
                    Text(plan.modeFallbackReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    text = if (accessibilityEnabled) {
                        val pinNote = if (autoPinEnabled && hasStoredPin) "Auto-PIN cifrado activo." else "El PIN se pedirá manualmente."
                        "Modo asistido automático: $pinNote Se detendrá en la pantalla de confirmación del operador."
                    } else {
                        "Modo asistido manual: la app abrirá el USSD y te mostrará los pasos sin confirmar cargos."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirmLaunch) {
                Text("Iniciar USSD")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun UssdLiveMonitorDialog(
    state: UssdSessionState,
    onDismiss: () -> Unit,
    onRetry: (UssdExecutionPlan) -> Unit,
    onMarkOutcome: (TransactionStatus, String) -> Unit
) {
    val context = LocalContext.current
    when (state) {
        UssdSessionState.Idle -> Unit
        is UssdSessionState.Running -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Ejecutando ${state.plan.summary.operatorName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.simInfoText.isNotBlank()) {
                            Text(state.simInfoText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(state.lastDialogMessage, style = MaterialTheme.typography.bodySmall)
                        Text("Secuencia de pasos:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        state.plan.steps.forEachIndexed { index, step ->
                            val marker = when {
                                index < state.currentStepIndex -> "✓"
                                index == state.currentStepIndex -> "→"
                                else -> "•"
                            }
                            Text(
                                text = "$marker ${step.label}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (index == state.currentStepIndex) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        if (!state.accessibilityEnabled) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            ) {
                                Text("Activar Asistente USSD (Accesibilidad)")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onMarkOutcome(TransactionStatus.CANCELADA, "Detenido por usuario desde monitor.") }) {
                        Text("Marcar Cancelada")
                    }
                },
                dismissButton = {
                    if (!state.accessibilityEnabled) {
                        TextButton(onClick = { onMarkOutcome(TransactionStatus.DESCONOCIDA, "Ejecutado manualmente sin lectura automática.") }) {
                            Text("Cerrar (Desconocida)")
                        }
                    }
                }
            )
        }
        is UssdSessionState.AwaitingManualPin -> {
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
                title = { Text("Ingreso manual de PIN requerido") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.reason)
                        Text("Mensaje USSD: ${state.promptMessage}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onMarkOutcome(TransactionStatus.CANCELADA, "Cancelado en solicitud manual de PIN.") }) {
                        Text("Cerrar como Cancelada")
                    }
                }
            )
        }
        is UssdSessionState.AwaitingUserConfirmation -> {
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Detenido antes de confirmar") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("El asistente se detuvo de forma segura en la ventana final del operador:")
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                            Text(state.confirmationText, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Responde directamente en el diálogo USSD (1 para confirmar / 2 para cancelar).", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onMarkOutcome(TransactionStatus.CANCELADA, "Usuario cerró/canceló en confirmación final.") }) {
                        Text("Registrar Cancelada")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onMarkOutcome(TransactionStatus.DESCONOCIDA, "Usuario cerró monitor sin confirmación registrada.") }) {
                        Text("Cerrar")
                    }
                }
            )
        }
        is UssdSessionState.Completed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A)) },
                title = { Text("Operación completada") },
                text = { Text(state.resultMessage) },
                confirmButton = { Button(onClick = onDismiss) { Text("Aceptar") } }
            )
        }
        is UssdSessionState.Cancelled -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Operación cancelada") },
                text = { Text(state.reason) },
                confirmButton = { Button(onClick = onDismiss) { Text("Aceptar") } }
            )
        }
        is UssdSessionState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Error o sesión expirada") },
                text = { Text(state.errorMessage) },
                confirmButton = {
                    Button(onClick = { onRetry(state.plan) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reintentar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
            )
        }
        is UssdSessionState.Unknown -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Resultado no confirmado") },
                text = { Text(state.detail) },
                confirmButton = { Button(onClick = onDismiss) { Text("Aceptar") } }
            )
        }
    }
}
