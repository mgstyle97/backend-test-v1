package im.bigs.pg.application.payment.exception

/**
 * TestPG API 관련 예외.
 */
sealed class PaymentException(
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class PaymentInvalidQueryException(message: String) : PaymentException(message)
