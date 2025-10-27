package im.bigs.pg.external.pg

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.external.pg.dto.TestPgErrorResponse
import im.bigs.pg.external.pg.exception.TestPgAuthenticationException
import im.bigs.pg.external.pg.exception.TestPgUnexpectedException
import im.bigs.pg.external.pg.exception.TestPgValidationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * TestPG API 클라이언트.
 *
 * https://api-test-pg.bigs.im 와 통신하여 결제 승인을 처리합니다.
 *
 * @property restClient HTTP 클라이언트 (common 모듈에서 제공)
 * @property properties TestPG API 설정
 */
@Component
class TestPgClient(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    @Value("\${pg.crypto.api-key}")
    private val apiKey: String,
) : PgClientOutPort {

    /**
     * 짝수 파트너 ID를 지원합니다.
     * (MockPgClient는 홀수 파트너를 담당)
     */
    override fun supports(partnerId: Long): Boolean {
        return partnerId % 2L == 0L
    }

    /**
     * TestPG API를 호출하여 결제를 승인합니다.
     *
     * - API 문서: https://api-test-pg.bigs.im/docs/index.html
     * - 요청 암호화 (AES-256-GCM)
     * - 응답 파싱
     *
     * @throws TestPgAuthenticationException API-KEY 헤더 오류 (401)
     * @throws TestPgValidationException 검증 실패 (422)
     * @throws TestPgUnexpectedException 예상치 못한 오류
     */
    override fun approve(request: PgApproveRequest): PgApproveResult {
        return restClient.post()
            .uri("/api/v1/pay/credit-card")
            .header("API-KEY", apiKey)
            .body(mapOf("enc" to request.enc))
            .retrieve()
            .onStatus({ status -> status == HttpStatusCode.valueOf(401) }) { _, _ ->
                throw TestPgAuthenticationException()
            }
            .onStatus({ status -> status == HttpStatusCode.valueOf(422) }) { _, response ->
                try {
                    val errorBody = objectMapper.readValue(
                        response.body,
                        TestPgErrorResponse::class.java,
                    )
                    throw TestPgValidationException(
                        code = errorBody.code,
                        errorCode = errorBody.errorCode,
                        message = errorBody.message,
                        referenceId = errorBody.referenceId,
                    )
                } catch (e: Exception) {
                    if (e is TestPgValidationException) throw e
                    throw TestPgUnexpectedException(422, "Failed to parse error response: ${e.message}", e)
                }
            }
            .onStatus({ status -> status.isError }) { _, response ->
                throw TestPgUnexpectedException(
                    response.statusCode.value(),
                    "Unexpected error from TestPG API",
                )
            }
            .body(PgApproveResult::class.java)
            ?: throw TestPgUnexpectedException(200, "Response body is null")
    }
}
