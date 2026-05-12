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

        val root = rootInActiveWindow ?: event?.source ?: return
        val texts = collectTexts(root).distinct().filter { it.isNotBlank() }
        if (texts.isEmpty()) return

        val flattened = texts.joinToString(" | ")
        if (job.transcript.lastOrNull() != flattened) {
            repository.updateJob(job.id) {
                it.copy(
                    updatedAt = System.currentTimeMillis(),
                    transcript = it.transcript + flattened,
                )
            }
        }

        val lowered = flattened.lowercase(Locale.getDefault())
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
}
