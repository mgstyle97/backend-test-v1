package im.bigs.pg.application.payment.service

import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.payment.port.out.PaymentPage
import im.bigs.pg.application.payment.port.out.PaymentQuery
import im.bigs.pg.application.payment.port.out.PaymentSummaryFilter
import im.bigs.pg.application.payment.port.out.PaymentSummaryProjection
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("QueryPaymentsService 커서 페이지네이션 검증")
class QueryPaymentsServiceCursorTest {

    private lateinit var paymentRepository: PaymentOutPort
    private lateinit var service: QueryPaymentsService

    @BeforeEach
    fun setUp() {
        paymentRepository = mockk()
        service = QueryPaymentsService(paymentRepository)
    }

    @Nested
    @DisplayName("커서 디코딩 예외 케이스")
    inner class CursorDecodingEdgeCases {

        @Test
        @DisplayName("null 커서는 null Pair를 반환한다")
        fun `null 커서는 null Pair를 반환한다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When
            service.query(QueryFilter(cursor = null, limit = 10))

            // Then
            val capturedQuery = querySlot.captured
            assertNull(capturedQuery.cursorCreatedAt)
            assertNull(capturedQuery.cursorId)
        }

        @Test
        @DisplayName("빈 문자열 커서는 null Pair를 반환한다")
        fun `빈 문자열 커서는 null Pair를 반환한다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When
            service.query(QueryFilter(cursor = "", limit = 10))

            // Then
            val capturedQuery = querySlot.captured
            assertNull(capturedQuery.cursorCreatedAt)
            assertNull(capturedQuery.cursorId)
        }

        @Test
        @DisplayName("공백만 있는 커서는 null Pair를 반환한다")
        fun `공백만 있는 커서는 null Pair를 반환한다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When
            service.query(QueryFilter(cursor = "   ", limit = 10))

            // Then
            val capturedQuery = querySlot.captured
            assertNull(capturedQuery.cursorCreatedAt)
            assertNull(capturedQuery.cursorId)
        }

        @Test
        @DisplayName("잘못된 Base64 커서는 null Pair를 반환한다")
        fun `잘못된 Base64 커서는 null Pair를 반환한다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When - 유효하지 않은 Base64 문자열
            service.query(QueryFilter(cursor = "invalid-base64@#$", limit = 10))

            // Then
            val capturedQuery = querySlot.captured
            assertNull(capturedQuery.cursorCreatedAt)
            assertNull(capturedQuery.cursorId)
        }

        @Test
        @DisplayName("콜론이 없는 커서는 null Pair를 반환한다")
        fun `콜론이 없는 커서는 null Pair를 반환한다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When - "123456789" (콜론 없음)
            val invalidCursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("123456789".toByteArray())

            service.query(QueryFilter(cursor = invalidCursor, limit = 10))

            // Then
            val capturedQuery = querySlot.captured
            assertNull(capturedQuery.cursorCreatedAt)
            assertNull(capturedQuery.cursorId)
        }

        @Test
        @DisplayName("숫자가 아닌 값이 포함된 커서는 null Pair를 반환한다")
        fun `숫자가 아닌 값이 포함된 커서는 null Pair를 반환한다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When - "abc:def"
            val invalidCursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("abc:def".toByteArray())

            service.query(QueryFilter(cursor = invalidCursor, limit = 10))

            // Then
            val capturedQuery = querySlot.captured
            assertNull(capturedQuery.cursorCreatedAt)
            assertNull(capturedQuery.cursorId)
        }

        @Test
        @DisplayName("parts가 1개만 있는 커서는 null Pair를 반환한다")
        fun `parts가 1개만 있는 커서는 null Pair를 반환한다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When - "123456789:" (id 부분 없음)
            val invalidCursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("123456789:".toByteArray())

            service.query(QueryFilter(cursor = invalidCursor, limit = 10))

            // Then
            val capturedQuery = querySlot.captured
            assertNull(capturedQuery.cursorCreatedAt)
            assertNull(capturedQuery.cursorId)
        }

        @Test
        @DisplayName("음수 타임스탬프를 가진 커서도 디코딩된다")
        fun `음수 타임스탬프를 가진 커서도 디코딩된다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When - "-1000:123" (과거 시간)
            val negativeCursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("-1000:123".toByteArray())

            service.query(QueryFilter(cursor = negativeCursor, limit = 10))

            // Then - 디코딩은 성공하지만 유효하지 않은 결과
            val capturedQuery = querySlot.captured
            assertNotNull(capturedQuery.cursorCreatedAt)
            assertEquals(123L, capturedQuery.cursorId)
        }

        @Test
        @DisplayName("음수 ID를 가진 커서도 디코딩된다")
        fun `음수 ID를 가진 커서도 디코딩된다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When - "1640000000000:-1" (음수 ID)
            val negativeCursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("1640000000000:-1".toByteArray())

            service.query(QueryFilter(cursor = negativeCursor, limit = 10))

            // Then
            val capturedQuery = querySlot.captured
            assertNotNull(capturedQuery.cursorCreatedAt)
            assertEquals(-1L, capturedQuery.cursorId)
        }

        @Test
        @DisplayName("매우 큰 숫자를 가진 커서도 디코딩된다")
        fun `매우 큰 숫자를 가진 커서도 디코딩된다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When - Long.MAX_VALUE 사용
            val maxCursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("${Long.MAX_VALUE}:${Long.MAX_VALUE}".toByteArray())

            service.query(QueryFilter(cursor = maxCursor, limit = 10))

            // Then
            val capturedQuery = querySlot.captured
            assertNotNull(capturedQuery.cursorCreatedAt)
            assertEquals(Long.MAX_VALUE, capturedQuery.cursorId)
        }

        @Test
        @DisplayName("콜론이 여러 개 있는 커서는 처음 두 값만 사용된다")
        fun `콜론이 여러 개 있는 커서는 처음 두 값만 사용된다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When - "1640000000000:123:456"
            val multiColonCursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("1640000000000:123:456".toByteArray())

            service.query(QueryFilter(cursor = multiColonCursor, limit = 10))

            // Then - split(":")은 ["1640000000000", "123", "456"]을 반환하고 parts[0], parts[1]만 사용
            val capturedQuery = querySlot.captured
            assertNotNull(capturedQuery.cursorCreatedAt) // "1640000000000"은 파싱 성공
            assertEquals(123L, capturedQuery.cursorId) // "123"은 파싱 성공
        }
    }

    @Nested
    @DisplayName("커서 인코딩/디코딩 왕복 테스트")
    inner class CursorRoundTripTest {

        @Test
        @DisplayName("정상적인 커서는 인코딩 후 디코딩 시 원래 값을 반환한다")
        fun `정상적인 커서는 인코딩 후 디코딩 시 원래 값을 반환한다`() {
            // Given
            val originalTime = LocalDateTime.of(2024, 1, 1, 10, 30, 0)
            val originalId = 123L

            val payment = createMockPayment(id = originalId, createdAt = originalTime)
            val page = PaymentPage(
                items = listOf(payment),
                hasNext = true,
                nextCursorCreatedAt = originalTime,
                nextCursorId = originalId,
            )

            every { paymentRepository.findBy(any()) } returns page
            every { paymentRepository.summary(any()) } returns createMockSummary()

            // When - 첫 조회에서 커서 생성
            val firstResult = service.query(QueryFilter(limit = 1))
            assertNotNull(firstResult.nextCursor)

            // Then - 생성된 커서로 다시 조회
            val querySlot = slot<PaymentQuery>()
            every { paymentRepository.findBy(capture(querySlot)) } returns PaymentPage(
                items = emptyList(),
                hasNext = false,
                nextCursorCreatedAt = null,
                nextCursorId = null,
            )

            service.query(QueryFilter(cursor = firstResult.nextCursor, limit = 1))

            val capturedQuery = querySlot.captured
            assertEquals(originalTime, capturedQuery.cursorCreatedAt)
            assertEquals(originalId, capturedQuery.cursorId)
        }

        @Test
        @DisplayName("nextCursor가 null인 경우 인코딩은 null을 반환한다")
        fun `nextCursor가 null인 경우 인코딩은 null을 반환한다`() {
            // Given
            val page = PaymentPage(
                items = emptyList(),
                hasNext = false,
                nextCursorCreatedAt = null,
                nextCursorId = null,
            )

            every { paymentRepository.findBy(any()) } returns page
            every { paymentRepository.summary(any()) } returns createMockSummary()

            // When
            val result = service.query(QueryFilter(limit = 10))

            // Then
            assertNull(result.nextCursor)
        }

        @Test
        @DisplayName("createdAt만 null인 경우 커서는 null이다")
        fun `createdAt만 null인 경우 커서는 null이다`() {
            // Given
            val page = PaymentPage(
                items = emptyList(),
                hasNext = false,
                nextCursorCreatedAt = null,
                nextCursorId = 123L,
            )

            every { paymentRepository.findBy(any()) } returns page
            every { paymentRepository.summary(any()) } returns createMockSummary()

            // When
            val result = service.query(QueryFilter(limit = 10))

            // Then
            assertNull(result.nextCursor)
        }

        @Test
        @DisplayName("id만 null인 경우 커서는 null이다")
        fun `id만 null인 경우 커서는 null이다`() {
            // Given
            val page = PaymentPage(
                items = emptyList(),
                hasNext = false,
                nextCursorCreatedAt = LocalDateTime.now(),
                nextCursorId = null,
            )

            every { paymentRepository.findBy(any()) } returns page
            every { paymentRepository.summary(any()) } returns createMockSummary()

            // When
            val result = service.query(QueryFilter(limit = 10))

            // Then
            assertNull(result.nextCursor)
        }
    }

    @Nested
    @DisplayName("페이지네이션 로직 검증")
    inner class PaginationLogicTest {

        @Test
        @DisplayName("첫 페이지 조회 시 커서 없이 조회된다")
        fun `첫 페이지 조회 시 커서 없이 조회된다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            mockRepositoryCalls(querySlot)

            // When
            service.query(QueryFilter(limit = 20))

            // Then
            val capturedQuery = querySlot.captured
            assertNull(capturedQuery.cursorCreatedAt)
            assertNull(capturedQuery.cursorId)
            assertEquals(20, capturedQuery.limit)
        }

        @Test
        @DisplayName("다음 페이지 존재 시 hasNext는 true이고 nextCursor가 존재한다")
        fun `다음 페이지 존재 시 hasNext는 true이고 nextCursor가 존재한다`() {
            // Given
            val payment = createMockPayment(
                id = 100L,
                createdAt = LocalDateTime.of(2024, 1, 1, 10, 0),
            )
            val page = PaymentPage(
                items = listOf(payment),
                hasNext = true,
                nextCursorCreatedAt = payment.createdAt,
                nextCursorId = payment.id,
            )

            every { paymentRepository.findBy(any()) } returns page
            every { paymentRepository.summary(any()) } returns createMockSummary()

            // When
            val result = service.query(QueryFilter(limit = 1))

            // Then
            assertTrue(result.hasNext)
            assertNotNull(result.nextCursor)
            assertEquals(1, result.items.size)
        }

        @Test
        @DisplayName("마지막 페이지는 hasNext가 false이고 nextCursor가 null이다")
        fun `마지막 페이지는 hasNext가 false이고 nextCursor가 null이다`() {
            // Given
            val payment = createMockPayment(id = 100L)
            val page = PaymentPage(
                items = listOf(payment),
                hasNext = false,
                nextCursorCreatedAt = null,
                nextCursorId = null,
            )

            every { paymentRepository.findBy(any()) } returns page
            every { paymentRepository.summary(any()) } returns createMockSummary()

            // When
            val result = service.query(QueryFilter(limit = 10))

            // Then
            assertTrue(!result.hasNext)
            assertNull(result.nextCursor)
        }

        @Test
        @DisplayName("결과가 없는 경우 빈 리스트와 hasNext false를 반환한다")
        fun `결과가 없는 경우 빈 리스트와 hasNext false를 반환한다`() {
            // Given
            val page = PaymentPage(
                items = emptyList(),
                hasNext = false,
                nextCursorCreatedAt = null,
                nextCursorId = null,
            )

            every { paymentRepository.findBy(any()) } returns page
            every { paymentRepository.summary(any()) } returns PaymentSummaryProjection(
                count = 0,
                totalAmount = BigDecimal.ZERO,
                totalNetAmount = BigDecimal.ZERO,
            )

            // When
            val result = service.query(QueryFilter(limit = 10))

            // Then
            assertTrue(result.items.isEmpty())
            assertTrue(!result.hasNext)
            assertNull(result.nextCursor)
            assertEquals(0, result.summary.count)
        }

        @Test
        @DisplayName("필터 조건이 올바르게 전달된다")
        fun `필터 조건이 올바르게 전달된다`() {
            // Given
            val querySlot = slot<PaymentQuery>()
            val summarySlot = slot<PaymentSummaryFilter>()

            every { paymentRepository.findBy(capture(querySlot)) } returns PaymentPage(
                items = emptyList(),
                hasNext = false,
                nextCursorCreatedAt = null,
                nextCursorId = null,
            )
            every { paymentRepository.summary(capture(summarySlot)) } returns createMockSummary()

            val from = LocalDateTime.of(2024, 1, 1, 0, 0)
            val to = LocalDateTime.of(2024, 12, 31, 23, 59)

            // When
            service.query(
                QueryFilter(
                    partnerId = 10L,
                    status = "APPROVED",
                    from = from,
                    to = to,
                    limit = 50,
                ),
            )

            // Then
            val capturedQuery = querySlot.captured
            assertEquals(10L, capturedQuery.partnerId)
            assertEquals(from, capturedQuery.from)
            assertEquals(to, capturedQuery.to)
            assertEquals(50, capturedQuery.limit)

            val capturedSummary = summarySlot.captured
            assertEquals(10L, capturedSummary.partnerId)
            assertEquals(from, capturedSummary.from)
            assertEquals(to, capturedSummary.to)
        }
    }

    // Helper methods
    private fun mockRepositoryCalls(querySlot: CapturingSlot<PaymentQuery>) {
        every { paymentRepository.findBy(capture(querySlot)) } returns PaymentPage(
            items = emptyList(),
            hasNext = false,
            nextCursorCreatedAt = null,
            nextCursorId = null,
        )
        every { paymentRepository.summary(any()) } returns createMockSummary()
    }

    private fun createMockPayment(
        id: Long = 1L,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): Payment {
        return Payment(
            id = id,
            partnerId = 1L,
            amount = BigDecimal("10000"),
            appliedFeeRate = BigDecimal("0.03"),
            feeAmount = BigDecimal("300"),
            netAmount = BigDecimal("9700"),
            cardLast4 = "1234",
            approvalCode = "APPROVAL-123",
            approvedAt = createdAt,
            status = PaymentStatus.APPROVED,
            createdAt = createdAt,
        )
    }

    private fun createMockSummary(): PaymentSummaryProjection {
        return PaymentSummaryProjection(
            count = 1,
            totalAmount = BigDecimal("10000"),
            totalNetAmount = BigDecimal("9700"),
        )
    }
}
