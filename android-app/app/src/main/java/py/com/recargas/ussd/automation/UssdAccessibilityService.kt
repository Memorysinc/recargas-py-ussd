package py.com.recargas.ussd.automation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import py.com.recargas.ussd.RecargasApp
import py.com.recargas.ussd.ussd.StepKind
import py.com.recargas.ussd.ussd.UssdSessionState

class UssdAccessibilityService : AccessibilityService() {

    private var lastDispatchedSessionId: Long = -1L
    private var lastDispatchedMessage: String = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg !in SUPPORTED_PACKAGES) return

        val app = applicationContext as? RecargasApp ?: return
        val coordinator = app.sessionCoordinator
        val plan = coordinator.activePlan ?: return
        val state = coordinator.state.value

        // Handle Dialer SIM picker if it appears
        if (pkg.contains("dialer")) {
            val simTitle = findNodeById(root, "com.android.dialer:id/alertTitle")?.text?.toString().orEmpty()
            if (simTitle.contains("SIM", ignoreCase = true)) {
                val labels = findNodesById(root, "com.android.dialer:id/label")
                val match = labels.firstOrNull { node ->
                    val label = node.text?.toString()?.uppercase().orEmpty()
                    plan.operator.simMatch.carrierKeywords.any { label.contains(it.uppercase()) }
                }
                if (match != null && clickClickableParent(match)) {
                    return
                }
            }
            return
        }

        // Handle com.android.phone USSD dialog
        val messageNode = findNodeById(root, "android:id/message") ?: return
        val messageText = messageNode.text?.toString()?.trim().orEmpty()
        if (messageText.isEmpty()) return

        val inputNode = findNodeById(root, "com.android.phone:id/input_field")
        val sendBtn = findButtonByText(root, "ENVIAR") ?: findNodeById(root, "android:id/button1")?.takeIf { inputNode != null }
        val okBtn = findButtonByText(root, "ACEPTAR") ?: findButtonByText(root, "OK") ?: findNodeById(root, "android:id/button1")?.takeIf { inputNode == null }
        val cancelBtn = findButtonByText(root, "CANCELAR")

        val sessionId = coordinator.sessionId
        if (sessionId == lastDispatchedSessionId && messageText == lastDispatchedMessage) {
            return
        }

        val rules = plan.operator.ussdRules
        val lower = messageText.lowercase()

        // First let coordinator check cancel / error / confirmation / read-only result
        if (inputNode == null && okBtn != null) {
            lastDispatchedSessionId = sessionId
            lastDispatchedMessage = messageText
            coordinator.onDialogMessageObserved(messageText)
            return
        }
        if (rules.confirmKeywords.any { lower.contains(it.lowercase()) } ||
            rules.errorKeywords.any { lower.contains(it.lowercase()) } ||
            rules.cancelResultKeywords.any { lower.contains(it.lowercase()) }
        ) {
            // NEVER auto-confirm; stop safely and alert user
            lastDispatchedSessionId = sessionId
            lastDispatchedMessage = messageText
            coordinator.onDialogMessageObserved(messageText)
            return
        }

        if (state !is UssdSessionState.Running) return
        val step = coordinator.getCurrentStep() ?: return
        if (inputNode == null || sendBtn == null) return

        when (step.kind) {
            StepKind.STOP_AT_CONFIRMATION -> {
                lastDispatchedSessionId = sessionId
                lastDispatchedMessage = messageText
                coordinator.onDialogMessageObserved(messageText)
            }
            StepKind.READ_RESULT -> {
                lastDispatchedSessionId = sessionId
                lastDispatchedMessage = messageText
                coordinator.onDialogMessageObserved(messageText)
            }
            StepKind.PIN -> {
                val isPinPrompt = rules.pinPromptKeywords.any { lower.contains(it.lowercase()) }
                val isExcluded = rules.pinExcludeKeywords.any { lower.contains(it.lowercase()) }
                if (!isPinPrompt || isExcluded) {
                    lastDispatchedSessionId = sessionId
                    lastDispatchedMessage = messageText
                    coordinator.onManualPinRequired(
                        messageText,
                        "La pantalla actual no coincide con la solicitud de PIN del operador."
                    )
                    return
                }
                val pin = coordinator.shouldAutoInjectPin()
                if (pin.isNullOrBlank()) {
                    lastDispatchedSessionId = sessionId
                    lastDispatchedMessage = messageText
                    coordinator.onManualPinRequired(
                        messageText,
                        "Ingreso manual requerido: auto-PIN desactivado o PIN no guardado."
                    )
                    return
                }
                if (fillAndSend(inputNode, sendBtn, pin)) {
                    lastDispatchedSessionId = sessionId
                    lastDispatchedMessage = messageText
                    coordinator.onPinInjected()
                } else {
                    lastDispatchedSessionId = sessionId
                    lastDispatchedMessage = messageText
                    coordinator.onManualPinRequired(
                        messageText,
                        "Android bloqueó la inserción automática; ingresa tu PIN manualmente."
                    )
                }
            }
            StepKind.MENU_OPTION, StepKind.DESTINATION, StepKind.AMOUNT, StepKind.DATE -> {
                if (step.value.isBlank()) {
                    lastDispatchedSessionId = sessionId
                    lastDispatchedMessage = messageText
                    coordinator.onManualPinRequired(messageText, "Ingresa el valor requerido manualmente.")
                    return
                }
                if (fillAndSend(inputNode, sendBtn, step.value)) {
                    lastDispatchedSessionId = sessionId
                    lastDispatchedMessage = messageText
                    coordinator.advanceStep(messageText)
                }
            }
        }

        // Attach no-op reference to cancelBtn to suppress unused warning if needed
        cancelBtn?.let { }
    }

    private fun fillAndSend(
        inputNode: AccessibilityNodeInfo,
        sendBtn: AccessibilityNodeInfo,
        value: String
    ): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val setOk = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!setOk) return false
        return sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val list = root.findAccessibilityNodeInfosByViewId(id)
        return list.firstOrNull()
    }

    private fun findNodesById(root: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo> =
        root.findAccessibilityNodeInfosByViewId(id) ?: emptyList()

    private fun findButtonByText(root: AccessibilityNodeInfo, targetUpper: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className?.toString()?.contains("Button") == true &&
                node.text?.toString()?.trim()?.uppercase() == targetUpper &&
                node.isClickable
            ) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun clickClickableParent(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        while (cur != null) {
            if (cur.isClickable) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            cur = cur.parent
        }
        return false
    }

    override fun onInterrupt() {}

    companion object {
        private val SUPPORTED_PACKAGES = setOf(
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer"
        )
    }
}
