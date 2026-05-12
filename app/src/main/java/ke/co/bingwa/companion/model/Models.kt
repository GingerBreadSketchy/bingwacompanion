package ke.co.bingwa.companion.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class BundleOffer(
    val id: String,
    val name: String,
    val type: String,
    val priceKes: Int,
    val ussdCode: String,
    val active: Boolean = true,
    val replySequence: List<String> = emptyList(),
)

@Immutable
@Serializable
data class AppSettings(
    val senderFilter: String = "",
    val successKeywords: String = "confirmed,received,successful,credited,paid",
    val phoneRegex: String = "(?:254|0)[17]\\d{8}",
    val amountRegex: String = "(?:KSH|KES)\\s*([0-9]+(?:\\.[0-9]{1,2})?)",
    val autoStartFulfillment: Boolean = true,
    val fulfillmentDelaySeconds: Int = 25,
    val preferredSimSlot: Int = 0,
)

@Immutable
@Serializable
data class FulfillmentJob(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sender: String,
    val rawMessage: String,
    val amountKes: Int,
    val recipientPhone: String,
    val matchedOfferId: String,
    val matchedOfferName: String,
    val renderedUssd: String,
    val status: JobStatus,
    val transcript: List<String> = emptyList(),
)

@Immutable
@Serializable
data class ActiveSession(
    val jobId: String,
    val replySequence: List<String>,
    val nextReplyIndex: Int = 0,
)

@Serializable
enum class JobStatus {
    QUEUED,
    PROCESSING,
    SUCCESS,
    FAILED,
    REQUIRES_REVIEW,
}

object DefaultCatalog {
    val offers = listOf(
        BundleOffer("b1", "1GB Data", "data", 19, "*180*5*2*pppp*5*1#"),
        BundleOffer("b2", "250MB Data", "data", 20, "*180*5*2*pppp*5*2#"),
        BundleOffer("b3", "350MB Data", "data", 49, ""),
        BundleOffer("b4", "1.5GB Data", "data", 50, ""),
        BundleOffer("b5", "1.25GB Data", "data", 55, "*180*5*2*pppp*5*4#"),
        BundleOffer("b6", "1GB Daily Data", "data", 99, ""),
        BundleOffer("b7", "2.5GB Weekly Data", "data", 300, ""),
        BundleOffer("b8", "6GB Weekly Data", "data", 700, ""),
        BundleOffer("m1", "45 Minutes", "minutes", 22, ""),
        BundleOffer("m2", "50 Minutes", "minutes", 51, "*180*5*2*pppp*5*5#"),
        BundleOffer("m3", "100 Minutes", "minutes", 97, ""),
        BundleOffer("m4", "250 Minutes", "minutes", 235, ""),
        BundleOffer("s1", "20 SMS", "sms", 5, "*180*5*2*pppp*5*6#"),
        BundleOffer("s2", "200 SMS", "sms", 10, "*180*5*2*pppp*5*7#"),
        BundleOffer("s3", "1000 SMS", "sms", 30, "*180*5*2*pppp*5*8#"),
        BundleOffer("s4", "1500 SMS", "sms", 101, ""),
    )
}
