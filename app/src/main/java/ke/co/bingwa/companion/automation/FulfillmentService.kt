package ke.co.bingwa.companion.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import ke.co.bingwa.companion.MainActivity
import ke.co.bingwa.companion.R
import ke.co.bingwa.companion.data.CompanionRepository
import ke.co.bingwa.companion.model.ActiveSession
import ke.co.bingwa.companion.model.JobStatus
import kotlin.concurrent.thread

class FulfillmentService : Service() {
    private lateinit var repository: CompanionRepository

    override fun onCreate() {
        super.onCreate()
        repository = CompanionRepository(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pendingIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Processing Bingwa fulfillment queue")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        val jobId = intent?.getStringExtra(EXTRA_JOB_ID)
        val testUssd = intent?.getStringExtra(EXTRA_TEST_USSD)
        val testSimSlot = intent?.getIntExtra(EXTRA_TEST_SIM_SLOT, 0) ?: 0
        thread(name = "bingwa-fulfillment-$startId") {
            if (!testUssd.isNullOrBlank()) {
                processTest(testUssd, testSimSlot)
            } else {
                process(jobId)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun process(jobId: String?) {
        val job = jobId?.let(repository::getJob) ?: repository.nextPendingJob() ?: return
        val offer = repository.getOffers().firstOrNull { it.id == job.matchedOfferId } ?: return
        val settings = repository.getSettings()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            repository.updateJob(job.id) {
                it.copy(
                    status = JobStatus.REQUIRES_REVIEW,
                    updatedAt = System.currentTimeMillis(),
                    transcript = it.transcript + "CALL_PHONE permission is missing.",
                )
            }
            return
        }

        val delayMs = settings.fulfillmentDelaySeconds.coerceAtLeast(0) * 1000L
        if (delayMs > 0) {
            repository.updateJob(job.id) {
                it.copy(
                    updatedAt = System.currentTimeMillis(),
                    transcript = it.transcript + "Waiting ${settings.fulfillmentDelaySeconds}s after payment SMS to avoid interfering with STK push.",
                )
            }
            Thread.sleep(delayMs)
        }

        repository.updateJob(job.id) {
            it.copy(
                status = JobStatus.PROCESSING,
                updatedAt = System.currentTimeMillis(),
                transcript = it.transcript + "Launching USSD ${job.renderedUssd}",
            )
        }

        repository.saveActiveSession(
            ActiveSession(
                jobId = job.id,
                replySequence = offer.replySequence,
            ),
        )

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${encodeUssd(job.renderedUssd)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applyPreferredSimHint(settings.preferredSimSlot)
        }
        startActivity(intent)
    }

    private fun processTest(ussdCode: String, simSlot: Int) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${encodeUssd(ussdCode)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applyPreferredSimHint(simSlot)
        }
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bingwa Fulfillment",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun encodeUssd(ussd: String): String {
        return ussd.replace("#", Uri.encode("#"))
    }

    private fun Intent.applyPreferredSimHint(slotPreference: Int) {
        if (slotPreference <= 0) {
            return
        }

        val slotIndex = slotPreference - 1
        putExtra("com.android.phone.extra.slot", slotIndex)
        putExtra("com.android.phone.extra.SIM_SLOT", slotIndex)
        putExtra("com.android.phone.force.slot", true)
        putExtra("slot", slotIndex)
        putExtra("slotId", slotIndex)
        putExtra("simSlot", slotIndex)
        putExtra("simSlotIndex", slotIndex)
        putExtra("slot_id", slotIndex)
        putExtra("phone", slotIndex + 1)

        when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> putExtra("miui.slotid", slotIndex)
            "samsung" -> putExtra("simnum", slotIndex + 1)
            "oppo", "realme", "oneplus", "vivo" -> putExtra("slotIndex", slotIndex)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 &&
            ActivityCompat.checkSelfPermission(this@FulfillmentService, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        ) {
            val subscriptionManager = getSystemService(SubscriptionManager::class.java)
            val subscriptionInfo = subscriptionManager?.activeSubscriptionInfoList
                ?.firstOrNull { it.simSlotIndex == slotIndex }
            if (subscriptionInfo != null) {
                putExtra("subscription", subscriptionInfo.subscriptionId)
                putExtra("sub_id", subscriptionInfo.subscriptionId)
                putExtra("subscription_id", subscriptionInfo.subscriptionId)
                putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", subscriptionInfo.subscriptionId)
            }
        }
    }

    companion object {
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_TEST_USSD = "test_ussd"
        const val EXTRA_TEST_SIM_SLOT = "test_sim_slot"
        private const val CHANNEL_ID = "bingwa_fulfillment"
        private const val NOTIFICATION_ID = 1201
    }
}
