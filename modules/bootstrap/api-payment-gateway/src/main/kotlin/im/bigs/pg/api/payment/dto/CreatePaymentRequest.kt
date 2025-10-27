package im.bigs.pg.api.payment.dto

import im.bigs.pg.api.config.validator.PaymentEnc
import jakarta.validation.constraints.Min
import java.math.BigDecimal

data class CreatePaymentRequest(
    val partnerId: Long,
    @field:Min(1)
    val amount: BigDecimal,
    val cardBin: String? = null,
    val cardLast4: String? = null,
    val productName: String? = null,
    @field:PaymentEnc
    val enc: String,
)
