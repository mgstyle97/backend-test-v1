package im.bigs.pg.external.pg

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.external.pg.dto.TestPgErrorResponse
import im.bigs.pg.external.pg.exception.TestPgAuthenticationException
import im.bigs.pg.external.pg.exception.TestPgException
import im.bigs.pg.external.pg.exception.TestPgUnexpectedException
import im.bigs.pg.external.pg.exception.TestPgValidationException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("TestPgClient 에러 처리")
class TestPgClientErrorHandlingTest {

    private val objectMapper = ObjectMapper().apply {
        findAndRegisterModules()
    }

    @Nested
    @DisplayName("예외 클래스")
    inner class ExceptionClasses {

        @Test
        @DisplayName("TestPgAuthenticationException은 TestPgException을 상속한다")
        fun `TestPgAuthenticationException은 TestPgException을 상속한다`() {
            // Given & When
            val exception = TestPgAuthenticationException()

            // Then
            assertTrue(exception is TestPgException)
            assertTrue(exception is RuntimeException)
            assertEquals("API-KEY 헤더 오류", exception.message)
        }

        @Test
        @DisplayName("TestPgAuthenticationException은 커스텀 메시지를 받을 수 있다")
        fun `TestPgAuthenticationException은 커스텀 메시지를 받을 수 있다`() {
            // Given & When
            val exception = TestPgAuthenticationException("Custom error message")

            // Then
            assertEquals("Custom error message", exception.message)
        }
    }

    @Nested
    @DisplayName("422 Unprocessable Entity 에러")
    inner class ValidationError {

        @Test
        @DisplayName("TestPgValidationException은 필요한 모든 정보를 포함한다")
        fun `TestPgValidationException은 필요한 모든 정보를 포함한다`() {
            // Given & When
            val exception = TestPgValidationException(
                code = 4001,
                errorCode = "INVALID_CARD_NUMBER",
                message = "카드 번호가 유효하지 않습니다",
                referenceId = "ref-123456",
            )

            // Then
            assertEquals(4001, exception.code)
            assertEquals("INVALID_CARD_NUMBER", exception.errorCode)
            assertEquals("ref-123456", exception.referenceId)
            assertTrue(exception.message!!.contains("카드 번호가 유효하지 않습니다"))
            assertTrue(exception.message!!.contains("code=4001"))
            assertTrue(exception.message!!.contains("errorCode=INVALID_CARD_NUMBER"))
            assertTrue(exception.message!!.contains("referenceId=ref-123456"))
            assertTrue(exception is TestPgException)
        }

        @Test
        @DisplayName("TestPgErrorResponse를 JSON으로 파싱할 수 있다")
        fun `TestPgErrorResponse를 JSON으로 파싱할 수 있다`() {
            // Given
            val errorJson = """
                {
                    "code": 4001,
                    "errorCode": "INVALID_CARD_NUMBER",
                    "message": "카드 번호가 유효하지 않습니다",
                    "referenceId": "ref-123456"
                }
            """.trimIndent()

            // When
            val errorResponse = objectMapper.readValue(errorJson, TestPgErrorResponse::class.java)

            // Then
            assertNotNull(errorResponse)
            assertEquals(4001, errorResponse.code)
            assertEquals("INVALID_CARD_NUMBER", errorResponse.errorCode)
            assertEquals("카드 번호가 유효하지 않습니다", errorResponse.message)
            assertEquals("ref-123456", errorResponse.referenceId)
        }
    }

    @Nested
    @DisplayName("기타 에러")
    inner class OtherErrors {

        @Test
        @DisplayName("TestPgUnexpectedException은 메시지를 포함한다")
        fun `TestPgUnexpectedException은 메시지를 포함한다`() {
            // Given & When
            val exception = TestPgUnexpectedException(
                message = "Unexpected error from TestPG API"
            )

            // Then
            assertTrue(exception.message!!.contains("Unexpected error from TestPG API"))
            assertTrue(exception is TestPgException)
        }

        @Test
        @DisplayName("TestPgUnexpectedException은 cause를 포함할 수 있다")
        fun `TestPgUnexpectedException은 cause를 포함할 수 있다`() {
            // Given
            val cause = IllegalArgumentException("Original cause")

            // When
            val exception = TestPgUnexpectedException(
                message = "Failed to parse error response",
                cause = cause
            )

            // Then
            assertTrue(exception.message!!.contains("Failed to parse error response"))
            assertEquals(cause, exception.cause)
        }
    }

    @Nested
    @DisplayName("파트너 지원")
    inner class PartnerSupport {

        @Test
        @DisplayName("TestPgClient는 짝수 파트너 ID를 지원한다")
        fun `TestPgClient는 짝수 파트너 ID를 지원한다`() {
            // Given
            val client = TestPgClient(
                RestClient.create("http://localhost"),
                objectMapper,
                "test-api-key",
            )

            // When & Then
            assertTrue(client.supports(2L))
            assertTrue(client.supports(4L))
            assertTrue(client.supports(100L))
        }

        @Test
        @DisplayName("TestPgClient는 홀수 파트너 ID를 지원하지 않는다")
        fun `TestPgClient는 홀수 파트너 ID를 지원하지 않는다`() {
            // Given
            val client = TestPgClient(
                RestClient.create("http://localhost"),
                objectMapper,
                "test-api-key",
            )

            // When & Then
            assertTrue(!client.supports(1L))
            assertTrue(!client.supports(3L))
            assertTrue(!client.supports(99L))
        }
    }
}
