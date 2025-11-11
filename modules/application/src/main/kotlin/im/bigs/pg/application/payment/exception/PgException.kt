package im.bigs.pg.application.payment.exception

sealed class PgException(
    override val message: String,
    code: PgErrorCode,
) : RuntimeException(message)

class PgAuthenticationException(message: String = "PG사 인증 오류") :
    PgException(
        message = message,
        code = PgErrorCode.UNAUTHORIZED
    )

class PgValidationException(code: PgErrorCode = PgErrorCode.INVALID_CARD, message: String = "카드 결제에 실패했습니다.") :
    PgException(code = code, message = message)

class PgUnexpectedException(message: String = "PG사 결제에 오류가 발생했습니다. 관리자를 통해 확인해주세요.") :
    PgException(
        message = message,
        code = PgErrorCode.UNEXPECTED_ERROR
    )

enum class PgErrorCode {
    UNAUTHORIZED,
    INVALID_CARD,
    STOLEN_OR_LOST,
    INSUFFICIENT_LIMIT,
    EXPIRED_OR_BLOCKED,
    TAMPERED_CARD,
    UNEXPECTED_ERROR,
}
