package ke.co.bingwa.companion.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import ke.co.bingwa.companion.data.CompanionRepository
import ke.co.bingwa.companion.model.JobStatus
import java.util.Locale

class BingwaAccessibilityService : AccessibilityService() {
    private lateinit var repository: CompanionRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = CompanionRepository(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = repository.getActiveSession() ?: return
        val job = repository.getJob(session.jobId) ?: run {
            repository.clearActiveSession()
            return
        }

        val source = event?.source
        val activeRoot = rootInActiveWindow
        val packageName = resolvePackageName(event, source, activeRoot) ?: return
        val root = activeRoot?.takeIf { it.packageName?.toString() == packageName } ?: source ?: return
        val texts = collectTexts(root)
            .map(::normalizeText)
            .distinct()
            .filter { it.isNotBlank() }
        if (texts.isEmpty()) return

        if (!shouldProcessPackage(packageName, texts)) return

        summarizeWindowText(texts)?.let { appendTranscript(job.id, it) }

        val lowered = texts.joinToString(" | ").lowercase(Locale.getDefault())
        if (containsSuccess(lowered)) {
            repository.updateJob(job.id) {
                it.copy(
                    status = JobStatus.SUCCESS,
                    updatedAt = System.currentTimeMillis(),
                    transcript = it.transcript + "USSD flow marked successful.",
                )
            }
            repository.clearActiveSession()
            return
        }

        if (containsFailure(lowered)) {
            repository.updateJob(job.id) {
                it.copy(
                    status = JobStatus.FAILED,
                    updatedAt = System.currentTimeMillis(),
                    transcript = it.transcript + "USSD flow marked failed.",
                )
            }
            repository.clearActiveSession()
            return
        }

        if (session.nextReplyIndex < session.replySequence.size) {
            val reply = session.replySequence[session.nextReplyIndex]
            val editable = findEditable(root)
            if (editable != null && sendReply(editable, reply)) {
                clickActionButton(root)
                repository.saveActiveSession(session.copy(nextReplyIndex = session.nextReplyIndex + 1))
                repository.updateJob(job.id) {
                    it.copy(
                        updatedAt = System.currentTimeMillis(),
                        transcript = it.transcript + "Auto-replied with $reply",
                    )
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun collectTexts(node: AccessibilityNodeInfo): List<String> {
        val values = mutableListOf<String>()
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(values::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(values::add)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            values += collectTexts(child)
        }
        return values
    }

    private fun resolvePackageName(
        event: AccessibilityEvent?,
        source: AccessibilityNodeInfo?,
        activeRoot: AccessibilityNodeInfo?,
    ): String? {
        return listOfNotNull(
            source?.packageName?.toString(),
            event?.packageName?.toString(),
            activeRoot?.packageName?.toString(),
        ).firstOrNull { it.isNotBlank() }
    }

    private fun shouldProcessPackage(packageName: String, texts: List<String>): Boolean {
        val loweredPackage = packageName.lowercase(Locale.getDefault())
        if (loweredPackage == this.packageName.lowercase(Locale.getDefault())) {
            return false
        }

        if (
            loweredPackage.contains("launcher") ||
            loweredPackage.contains("systemui") ||
            loweredPackage.contains("settings")
        ) {
            return false
        }

        if (
            loweredPackage.contains("phone") ||
            loweredPackage.contains("dialer") ||
            loweredPackage.contains("telecom") ||
            loweredPackage.contains("incall") ||
            loweredPackage.contains("callui")
        ) {
            return true
        }

        val combined = texts.joinToString(" ").lowercase(Locale.getDefault())
        return combined.contains("ussd") ||
            combined.contains("mmi") ||
            containsSuccess(combined) ||
            containsFailure(combined)
    }

    private fun summarizeWindowText(texts: List<String>): String? {
        val relevant = texts.filter(::isRelevantTranscriptText).take(4)
        if (relevant.isEmpty()) return null

        val summary = relevant.joinToString(" | ")
        return if (summary.length <= MAX_TRANSCRIPT_ENTRY_LENGTH) {
            summary
        } else {
            summary.take(MAX_TRANSCRIPT_ENTRY_LENGTH - 1).trimEnd() + "…"
        }
    }

    private fun isRelevantTranscriptText(text: String): Boolean {
        val lowered = text.lowercase(Locale.getDefault())
        return lowered.contains("ussd") ||
            lowered.contains("reply") ||
            lowered.contains("send") ||
            lowered.contains("cancel") ||
            lowered.contains("dismiss") ||
            lowered.contains("ok") ||
            lowered.contains("yes") ||
            lowered.contains("sawa") ||
            lowered.contains("confirm") ||
            lowered.contains("input") ||
            lowered.contains("enter") ||
            lowered.contains("bundle") ||
            containsSuccess(lowered) ||
            containsFailure(lowered)
    }

    private fun normalizeText(value: String): String {
        return value.replace(Regex("\\s+"), " ").trim()
    }

    private fun appendTranscript(jobId: String, line: String) {
        val current = repository.getJob(jobId) ?: return
        if (current.transcript.lastOrNull() == line) return

        repository.updateJob(jobId) {
            it.copy(
                updatedAt = System.currentTimeMillis(),
                transcript = it.transcript + line,
            )
        }
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findEditable(child)
            if (match != null) return match
        }
        return null
    }

    private fun sendReply(node: AccessibilityNodeInfo, reply: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun clickActionButton(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase(Locale.getDefault()).orEmpty()
        if (node.isClickable && text in setOf("send", "ok", "reply", "sawa", "yes", "submit")) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (clickActionButton(child)) return true
        }
        return false
    }

    private fun containsSuccess(message: String): Boolean {
        return listOf("successful", "success", "completed", "bundle activated", "you have successfully").any {
            message.contains(it)
        }
    }

    private fun containsFailure(message: String): Boolean {
        return listOf("failed", "error", "insufficient", "invalid", "try again later", "cancelled").any {
            message.contains(it)
        }
    }

    companion object {
        private const val MAX_TRANSCRIPT_ENTRY_LENGTH = 220
    }
}
