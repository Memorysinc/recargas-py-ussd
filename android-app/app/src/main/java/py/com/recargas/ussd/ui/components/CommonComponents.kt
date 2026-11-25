package py.com.recargas.ussd.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import py.com.recargas.ussd.catalog.VerificationStatus
import py.com.recargas.ussd.history.TransactionStatus
import py.com.recargas.ussd.preferences.ButtonSizeOption

fun formatGuaranies(amount: Int?): String {
    if (amount == null) return "Según menú"
    return "Gs. " + "%,d".format(amount).replace(',', '.')
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF00377B)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

fun ButtonSizeOption.buttonHeightDp(): Dp = when (this) {
    ButtonSizeOption.NORMAL -> 48.dp
    ButtonSizeOption.LARGE -> 56.dp
    ButtonSizeOption.XLARGE -> 66.dp
}

fun ButtonSizeOption.cardPaddingDp(): Dp = when (this) {
    ButtonSizeOption.NORMAL -> 12.dp
    ButtonSizeOption.LARGE -> 16.dp
    ButtonSizeOption.XLARGE -> 20.dp
}

@Composable
fun VerificationBadge(status: VerificationStatus, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (status) {
        VerificationStatus.VERIFIED_USSD -> Triple("Verificado USSD", Color(0xFFDCFCE7), Color(0xFF166534))
        VerificationStatus.PUBLISHED_UNVERIFIED -> Triple("Publicado s/verificar", Color(0xFFFEF3C7), Color(0xFF92400E))
        VerificationStatus.PENDING -> Triple("Pendiente", Color(0xFFE0E7FF), Color(0xFF3730A3))
        VerificationStatus.BROKEN -> Triple("No operativo", Color(0xFFFEE2E2), Color(0xFF991B1B))
        VerificationStatus.UNAVAILABLE_UNVERIFIED -> Triple("No disponible", Color(0xFFF3F4F6), Color(0xFF4B5563))
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun TransactionStatusBadge(statusName: String, modifier: Modifier = Modifier) {
    val status = runCatching { TransactionStatus.valueOf(statusName) }.getOrDefault(TransactionStatus.DESCONOCIDA)
    val (label, bg, fg) = when (status) {
        TransactionStatus.COMPLETADA -> Triple("Completada", Color(0xFFDCFCE7), Color(0xFF166534))
        TransactionStatus.CANCELADA -> Triple("Cancelada", Color(0xFFFEF3C7), Color(0xFF92400E))
        TransactionStatus.FALLIDA -> Triple("Fallida", Color(0xFFFEE2E2), Color(0xFF991B1B))
        TransactionStatus.DESCONOCIDA -> Triple("Desconocida", Color(0xFFE5E7EB), Color(0xFF374151))
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
