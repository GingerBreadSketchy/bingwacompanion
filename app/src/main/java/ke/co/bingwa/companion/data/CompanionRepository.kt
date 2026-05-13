package ke.co.bingwa.companion.data

import android.content.Context
import android.content.SharedPreferences
import ke.co.bingwa.companion.model.ActiveSession
import ke.co.bingwa.companion.model.AppSettings
import ke.co.bingwa.companion.model.BundleOffer
import ke.co.bingwa.companion.model.DefaultCatalog
import ke.co.bingwa.companion.model.FulfillmentJob
import ke.co.bingwa.companion.model.JobStatus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class CompanionRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("bingwa_companion", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun getSettings(): AppSettings {
        return decode("settings", AppSettings.serializer(), AppSettings())
    }

    fun saveSettings(settings: AppSettings) {
        encode("settings", AppSettings.serializer(), settings)
    }

    fun getOffers(): List<BundleOffer> {
        val stored = decode("offers", ListSerializer(BundleOffer.serializer()), DefaultCatalog.offers)
        val normalized = normalizeOffers(stored)
        if (normalized != stored) {
            saveOffers(normalized)
        }
        return normalized
    }

    fun saveOffers(offers: List<BundleOffer>) {
        encode("offers", ListSerializer(BundleOffer.serializer()), normalizeOffers(offers))
    }

    fun exportOffersJson(): String {
        return json.encodeToString(ListSerializer(BundleOffer.serializer()), getOffers())
    }

    fun importOffersJson(raw: String): Boolean {
        val decoded = runCatching {
            json.decodeFromString(ListSerializer(BundleOffer.serializer()), raw)
        }.getOrNull() ?: return false

        saveOffers(decoded)
        return true
    }

    fun resetOffers() {
        saveOffers(DefaultCatalog.offers)
    }

    fun getJobs(): List<FulfillmentJob> {
        return decode("jobs", ListSerializer(FulfillmentJob.serializer()), emptyList())
    }

    fun saveJobs(jobs: List<FulfillmentJob>) {
        encode("jobs", ListSerializer(FulfillmentJob.serializer()), jobs.sortedByDescending { it.createdAt })
    }

    fun clearJobs() {
        saveJobs(emptyList())
    }

    fun enqueueJob(job: FulfillmentJob) {
        val jobs = getJobs().toMutableList()
        jobs.removeAll { it.id == job.id }
        jobs.add(0, job)
        saveJobs(jobs)
    }

    fun updateJob(jobId: String, transform: (FulfillmentJob) -> FulfillmentJob) {
        val jobs = getJobs().map { current ->
            if (current.id == jobId) transform(current) else current
        }
        saveJobs(jobs)
    }

    fun getJob(jobId: String): FulfillmentJob? {
        return getJobs().firstOrNull { it.id == jobId }
    }

    fun nextPendingJob(): FulfillmentJob? {
        return getJobs().firstOrNull { it.status == JobStatus.QUEUED }
    }

    fun getActiveSession(): ActiveSession? {
        val raw = prefs.getString("active_session", null) ?: return null
        return runCatching { json.decodeFromString(ActiveSession.serializer(), raw) }.getOrNull()
    }

    fun saveActiveSession(session: ActiveSession) {
        encode("active_session", ActiveSession.serializer(), session)
    }

    fun clearActiveSession() {
        prefs.edit().remove("active_session").apply()
    }

    private fun <T> decode(key: String, serializer: KSerializer<T>, fallback: T): T {
        val raw = prefs.getString(key, null) ?: return fallback
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(fallback)
    }

    private fun <T> encode(key: String, serializer: KSerializer<T>, value: T) {
        prefs.edit().putString(key, json.encodeToString(serializer, value)).apply()
    }

    private fun normalizeOffers(offers: List<BundleOffer>): List<BundleOffer> {
        val storedById = offers.associateBy { it.id }
        val mergedSeeded = DefaultCatalog.offers.map { seeded ->
            val existing = storedById[seeded.id] ?: return@map seeded
            existing.copy(
                ussdCode = existing.ussdCode.ifBlank { seeded.ussdCode },
                replySequence = if (existing.replySequence.isEmpty()) seeded.replySequence else existing.replySequence,
            )
        }
        val customOrUnknown = offers.filter { offer -> DefaultCatalog.offers.none { it.id == offer.id } }
        return (mergedSeeded + customOrUnknown)
            .distinctBy { it.id }
            .sortedWith(compareBy<BundleOffer> { it.type }.thenBy { it.priceKes })
    }
}
