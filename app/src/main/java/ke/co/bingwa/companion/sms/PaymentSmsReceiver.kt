package ke.co.bingwa.companion.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat
import ke.co.bingwa.companion.automation.FulfillmentService
import ke.co.bingwa.companion.data.CompanionRepository
import ke.co.bingwa.companion.model.FulfillmentJob
import ke.co.bingwa.companion.model.JobStatus

class PaymentSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()

        val repository = CompanionRepository(context)
        val settings = repository.getSettings()
        val parsed = PaymentSmsParser.parse(
            sender = sender,
            body = body,
            settings = settings,
            offers = repository.getOffers(),
        ) ?: return

        val now = System.currentTimeMillis()
        val renderedUssd = PaymentSmsParser.renderUssd(parsed.offer.ussdCode, parsed.recipientPhone)
        val transcript = mutableListOf(
            "SMS matched sender=$sender amount=${parsed.amountKes} recipient=${parsed.recipientPhone}",
        )
        if (settings.autoStartFulfillment) {
            transcript += "Job queued. Waiting ${settings.fulfillmentDelaySeconds}s before launching USSD."
        } else {
            transcript += "Manual approval mode enabled. Waiting for Run Now."
        }
        val job = FulfillmentJob(
            id = "job_$now",
            createdAt = now,
            updatedAt = now,
            sender = parsed.sender,
            rawMessage = parsed.rawMessage,
            amountKes = parsed.amountKes,
            recipientPhone = parsed.recipientPhone,
            matchedOfferId = parsed.offer.id,
            matchedOfferName = parsed.offer.name,
            renderedUssd = renderedUssd,
            status = JobStatus.QUEUED,
            transcript = transcript,
        )

        repository.enqueueJob(job)

        if (settings.autoStartFulfillment) {
            val serviceIntent = Intent(context, FulfillmentService::class.java).apply {
                putExtra(FulfillmentService.EXTRA_JOB_ID, job.id)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
