package py.com.recargas.ussd.ussd

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import py.com.recargas.ussd.catalog.OperatorDefinition

data class DetectedSim(
    val operatorId: String,
    val displayName: String,
    val slotIndex: Int,
    val subscriptionId: Int,
    val phoneAccountHandle: PhoneAccountHandle?
)

sealed class DialLaunchResult {
    data class LaunchedWithSim(val simDisplayName: String) : DialLaunchResult()
    data object LaunchedWithSystemPicker : DialLaunchResult()
    data object OpenedDialerOnly : DialLaunchResult()
    data class Error(val message: String) : DialLaunchResult()
}

object SimSelector {

    @SuppressLint("MissingPermission")
    fun detectSims(context: Context, operators: List<OperatorDefinition>): Map<String, DetectedSim> {
        if (!hasPermission(context, Manifest.permission.READ_PHONE_STATE)) {
            return emptyMap()
        }
        val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyMap()
        val telecomManager = context.getSystemService(TelecomManager::class.java)

        val activeSubs: List<SubscriptionInfo> = try {
            subManager.activeSubscriptionInfoList.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        val handles: List<PhoneAccountHandle> = if (telecomManager != null) {
            try {
                telecomManager.callCapablePhoneAccounts
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val result = mutableMapOf<String, DetectedSim>()
        for (op in operators) {
            val matchedSub = activeSubs.firstOrNull { sub ->
                matchesSubscription(sub, op)
            }
            if (matchedSub != null) {
                val handle = matchPhoneAccountHandle(telecomManager, handles, matchedSub, op)
                val label = matchedSub.displayName?.toString()?.takeIf { it.isNotBlank() }
                    ?: matchedSub.carrierName?.toString()
                    ?: op.shortName
                result[op.id] = DetectedSim(
                    operatorId = op.id,
                    displayName = label,
                    slotIndex = matchedSub.simSlotIndex,
                    subscriptionId = matchedSub.subscriptionId,
                    phoneAccountHandle = handle
                )
            }
        }
        return result
    }

    private fun matchesSubscription(sub: SubscriptionInfo, op: OperatorDefinition): Boolean {
        val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sub.mncString ?: ""
        } else {
            @Suppress("DEPRECATION")
            sub.mnc.toString().padStart(2, '0')
        }
        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sub.mccString ?: ""
        } else {
            @Suppress("DEPRECATION")
            sub.mcc.toString()
        }
        if (mcc == op.simMatch.mcc && mnc == op.simMatch.mnc) return true

        val names = listOfNotNull(sub.displayName?.toString(), sub.carrierName?.toString())
            .joinToString(" ")
            .uppercase()
        return op.simMatch.carrierKeywords.any { names.contains(it.uppercase()) }
    }

    private fun matchPhoneAccountHandle(
        telecomManager: TelecomManager?,
        handles: List<PhoneAccountHandle>,
        sub: SubscriptionInfo,
        op: OperatorDefinition
    ): PhoneAccountHandle? {
        if (telecomManager == null || handles.isEmpty()) return null
        val subIdStr = sub.subscriptionId.toString()
        val icc = sub.iccId.orEmpty()

        // Try direct ID / ICCID match first
        handles.firstOrNull { h ->
            h.id == subIdStr || (icc.isNotEmpty() && h.id.contains(icc, ignoreCase = true))
        }?.let { return it }

        // Try label match via PhoneAccount
        for (h in handles) {
            val account = try {
                telecomManager.getPhoneAccount(h)
            } catch (_: Exception) {
                null
            }
            val label = account?.label?.toString()?.uppercase().orEmpty()
            if (op.simMatch.carrierKeywords.any { label.contains(it.uppercase()) }) {
                return h
            }
        }
        return handles.getOrNull(sub.simSlotIndex)
    }

    @SuppressLint("MissingPermission")
    fun launchUssd(
        context: Context,
        operator: OperatorDefinition,
        ussdCode: String,
        detectedSim: DetectedSim?
    ): DialLaunchResult {
        val uri = Uri.fromParts("tel", ussdCode, null)
        val hasCallPhone = hasPermission(context, Manifest.permission.CALL_PHONE)

        if (!hasCallPhone) {
            return try {
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                DialLaunchResult.OpenedDialerOnly
            } catch (e: Exception) {
                DialLaunchResult.Error("No se pudo abrir el marcador: ${e.message}")
            }
        }

        val telecomManager = context.getSystemService(TelecomManager::class.java)
        val handle = detectedSim?.phoneAccountHandle
        if (telecomManager != null && handle != null) {
            return try {
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
                telecomManager.placeCall(uri, extras)
                DialLaunchResult.LaunchedWithSim(detectedSim.displayName)
            } catch (_: Exception) {
                fallbackActionCall(context, uri)
            }
        }

        return fallbackActionCall(context, uri)
    }

    private fun fallbackActionCall(context: Context, uri: Uri): DialLaunchResult {
        return try {
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DialLaunchResult.LaunchedWithSystemPicker
        } catch (e: Exception) {
            DialLaunchResult.Error("Error al iniciar llamada USSD: ${e.message}")
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
