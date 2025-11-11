package im.bigs.pg.external.pg.exception

/**
 * TestPG API 관련 예외.
 */
sealed class TestPgException(code: TestPgErrorCode, message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * TestPG API 인증 실패 (401 Unauthorized).
 */
class TestPgAuthenticationException(message: String = "API-KEY 헤더 오류") :
    TestPgException(
        message = message,
        code = TestPgErrorCode.UNAUTHORIZED
    )

/**
 * TestPG API 검증 실패 (422 Unprocessable Entity).
 *
 * @property code 에러 코드(정수)
 * @property errorCode 실패 사유 코드
 * @property referenceId 참조 ID
 */
class TestPgValidationException(
    val code: Int,
    val errorCode: String,
    message: String,
    val referenceId: String,
) : TestPgException(
    message = "$message (code=$code, errorCode=$errorCode, referenceId=$referenceId)",
    code = TestPgErrorCode.INVALID_CARD
)

/**
 * TestPG API 예상치 못한 오류.
 */
class TestPgUnexpectedException(message: String, cause: Throwable? = null) :
    TestPgException(
        message = message,
        code = TestPgErrorCode.UNEXPECTED_ERROR,
        cause = cause
    )

enum class TestPgErrorCode {
    UNAUTHORIZED,
    INVALID_CARD,
    UNEXPECTED_ERROR,
}
