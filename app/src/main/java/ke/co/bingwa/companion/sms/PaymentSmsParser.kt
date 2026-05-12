package ke.co.bingwa.companion.sms

import ke.co.bingwa.companion.model.AppSettings
import ke.co.bingwa.companion.model.BundleOffer
import java.util.Locale
import kotlin.math.roundToInt

data class ParsedPayment(
    val sender: String,
    val amountKes: Int,
    val recipientPhone: String,
    val offer: BundleOffer,
    val rawMessage: String,
)

object PaymentSmsParser {
    fun parse(
        sender: String,
        body: String,
        settings: AppSettings,
        offers: List<BundleOffer>,
    ): ParsedPayment? {
        if (body.isBlank()) return null

        val normalizedSender = sender.trim()
        val senderFilter = settings.senderFilter
            .split(",")
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.isNotBlank() }
        if (senderFilter.isNotEmpty() &&
            senderFilter.none { normalizedSender.lowercase(Locale.getDefault()).contains(it) }
        ) {
            return null
        }

        val loweredBody = body.lowercase(Locale.getDefault())
        val keywords = settings.successKeywords
            .split(",")
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.isNotBlank() }
        if (keywords.isNotEmpty() && keywords.none { loweredBody.contains(it) }) {
            return null
        }

        val amountMatch = Regex(settings.amountRegex, RegexOption.IGNORE_CASE).find(body) ?: return null
        val amount = amountMatch.groupValues.getOrNull(1)?.toDoubleOrNull()?.roundToInt() ?: return null

        val phoneMatch = Regex(settings.phoneRegex).findAll(body).firstOrNull() ?: return null
        val recipient = normalizePhone(phoneMatch.value)

        val matchingOffers = offers.filter { it.active && it.priceKes == amount && it.ussdCode.isNotBlank() }
        val offer = matchingOffers.singleOrNull() ?: return null

        return ParsedPayment(
            sender = normalizedSender,
            amountKes = amount,
            recipientPhone = recipient,
            offer = offer,
            rawMessage = body,
        )
    }

    fun renderUssd(ussdCode: String, recipientPhone: String): String {
        return ussdCode.replace("pppp", normalizePhone(recipientPhone))
    }

    private fun normalizePhone(value: String): String {
        val digits = value.filter(Char::isDigit)
        return when {
            digits.startsWith("254") -> "0${digits.drop(3)}"
            digits.startsWith("7") || digits.startsWith("1") -> "0$digits"
            else -> digits
        }.take(10)
    }
}
