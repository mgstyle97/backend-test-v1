package im.bigs.pg.external.pg.dto

/**
 * TestPG API 에러 응답.
 *
 * 422 Unprocessable Entity 응답 형식.
 */
data class TestPgErrorResponse(
    val code: Int,
    val errorCode: String,
    val message: String,
    val referenceId: String,
)
