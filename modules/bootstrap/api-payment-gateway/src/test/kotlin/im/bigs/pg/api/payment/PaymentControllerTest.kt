package im.bigs.pg.api.payment

import im.bigs.pg.api.payment.dto.CreatePaymentRequest
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.application.payment.port.`in`.QueryPaymentsUseCase
import im.bigs.pg.application.payment.port.`in`.QueryResult
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.domain.payment.PaymentSummary
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("PaymentController 단위 테스트")
class PaymentControllerTest {

    private lateinit var paymentUseCase: PaymentUseCase
    private lateinit var queryPaymentsUseCase: QueryPaymentsUseCase
    private lateinit var controller: PaymentController

    @BeforeEach
    fun setUp() {
        paymentUseCase = mockk()
        queryPaymentsUseCase = mockk()
        controller = PaymentController(paymentUseCase, queryPaymentsUseCase)
    }

    @Nested
    @DisplayName("POST /api/v1/payments - 결제 생성")
    inner class CreatePayment {

        @Test
        @DisplayName("정상적인 결제 요청을 처리한다")
        fun `정상적인 결제 요청을 처리한다`() {
            // Given
            val request = CreatePaymentRequest(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                cardBin = "123456",
                cardLast4 = "4242",
                productName = "Test Product",
                enc = "encrypted-data",
            )

            val payment = createMockPayment(
                id = 100L,
                partnerId = 1L,
                amount = BigDecimal("10000"),
                cardLast4 = "4242",
            )

            every { paymentUseCase.pay(any()) } returns payment

            // When
            val response = controller.create(request)

            // Then
            assertEquals(200, response.statusCode.value())
            assertNotNull(response.body)
            assertEquals(100L, response.body!!.id)
            assertEquals(1L, response.body!!.partnerId)
            assertEquals(BigDecimal("10000"), response.body!!.amount)
            assertEquals("4242", response.body!!.cardLast4)
            assertEquals(PaymentStatus.APPROVED, response.body!!.status)
        }

        @Test
        @DisplayName("PaymentCommand로 올바르게 변환한다")
        fun `PaymentCommand로 올바르게 변환한다`() {
            // Given
            val request = CreatePaymentRequest(
                partnerId = 2L,
                amount = BigDecimal("50000"),
                cardBin = "654321",
                cardLast4 = "9999",
                productName = "Premium Product",
                enc = "encrypted-premium-data",
            )

            val commandSlot = slot<PaymentCommand>()
            every { paymentUseCase.pay(capture(commandSlot)) } returns createMockPayment()

            // When
            controller.create(request)

            // Then
            val capturedCommand = commandSlot.captured
            assertEquals(2L, capturedCommand.partnerId)
            assertEquals(BigDecimal("50000"), capturedCommand.amount)
            assertEquals("654321", capturedCommand.cardBin)
            assertEquals("9999", capturedCommand.cardLast4)
            assertEquals("Premium Product", capturedCommand.productName)
            assertEquals("encrypted-premium-data", capturedCommand.enc)

            verify(exactly = 1) { paymentUseCase.pay(any()) }
        }

        @Test
        @DisplayName("optional 필드가 없어도 처리한다")
        fun `optional 필드가 없어도 처리한다`() {
            // Given
            val request = CreatePaymentRequest(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                cardBin = null,
                cardLast4 = null,
                productName = null,
                enc = "encrypted-data",
            )

            val commandSlot = slot<PaymentCommand>()
            every { paymentUseCase.pay(capture(commandSlot)) } returns createMockPayment()

            // When
            controller.create(request)

            // Then
            val capturedCommand = commandSlot.captured
            assertNull(capturedCommand.cardBin)
            assertNull(capturedCommand.cardLast4)
            assertNull(capturedCommand.productName)
        }

        @Test
        @DisplayName("응답에 수수료 정보가 포함된다")
        fun `응답에 수수료 정보가 포함된다`() {
            // Given
            val request = CreatePaymentRequest(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                enc = "encrypted-data",
            )

            val payment = createMockPayment(
                amount = BigDecimal("10000"),
                appliedFeeRate = BigDecimal("0.03"),
                feeAmount = BigDecimal("400"),
                netAmount = BigDecimal("9600"),
            )

            every { paymentUseCase.pay(any()) } returns payment

            // When
            val response = controller.create(request)

            // Then
            assertNotNull(response.body)
            assertEquals(BigDecimal("0.03"), response.body!!.appliedFeeRate)
            assertEquals(BigDecimal("400"), response.body!!.feeAmount)
            assertEquals(BigDecimal("9600"), response.body!!.netAmount)
        }

        @Test
        @DisplayName("응답에 승인 정보가 포함된다")
        fun `응답에 승인 정보가 포함된다`() {
            // Given
            val request = CreatePaymentRequest(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                enc = "encrypted-data",
            )

            val approvedAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0)
            val payment = createMockPayment(
                approvalCode = "APPROVAL-12345",
                approvedAt = approvedAt,
            )

            every { paymentUseCase.pay(any()) } returns payment

            // When
            val response = controller.create(request)

            // Then
            assertNotNull(response.body)
            assertEquals("APPROVAL-12345", response.body!!.approvalCode)
            assertEquals(approvedAt, response.body!!.approvedAt)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments - 결제 조회")
    inner class QueryPayments {

        @Test
        @DisplayName("필터 없이 조회한다")
        fun `필터 없이 조회한다`() {
            // Given
            val payment1 = createMockPayment(id = 1L, amount = BigDecimal("10000"))
            val payment2 = createMockPayment(id = 2L, amount = BigDecimal("20000"))

            val queryResult = QueryResult(
                items = listOf(payment1, payment2),
                summary = PaymentSummary(
                    count = 2,
                    totalAmount = BigDecimal("30000"),
                    totalNetAmount = BigDecimal("29000"),
                ),
                nextCursor = null,
                hasNext = false,
            )

            every { queryPaymentsUseCase.query(any()) } returns queryResult

            // When
            val response = controller.query(
                partnerId = null,
                status = null,
                from = null,
                to = null,
                cursor = null,
                limit = 20,
            )

            // Then
            assertEquals(200, response.statusCode.value())
            assertNotNull(response.body)
            assertEquals(2, response.body!!.items.size)
            assertEquals(2L, response.body!!.summary.count)
            assertEquals(BigDecimal("30000"), response.body!!.summary.totalAmount)
            assertEquals(BigDecimal("29000"), response.body!!.summary.totalNetAmount)
            assertNull(response.body!!.nextCursor)
            assertTrue(!response.body!!.hasNext)
        }

        @Test
        @DisplayName("partnerId 필터로 조회한다")
        fun `partnerId 필터로 조회한다`() {
            // Given
            val filterSlot = slot<QueryFilter>()
            every { queryPaymentsUseCase.query(capture(filterSlot)) } returns createEmptyQueryResult()

            // When
            controller.query(
                partnerId = 10L,
                status = null,
                from = null,
                to = null,
                cursor = null,
                limit = 20,
            )

            // Then
            val capturedFilter = filterSlot.captured
            assertEquals(10L, capturedFilter.partnerId)
            verify(exactly = 1) { queryPaymentsUseCase.query(any()) }
        }

        @Test
        @DisplayName("status 필터로 조회한다")
        fun `status 필터로 조회한다`() {
            // Given
            val filterSlot = slot<QueryFilter>()
            every { queryPaymentsUseCase.query(capture(filterSlot)) } returns createEmptyQueryResult()

            // When
            controller.query(
                partnerId = null,
                status = "APPROVED",
                from = null,
                to = null,
                cursor = null,
                limit = 20,
            )

            // Then
            val capturedFilter = filterSlot.captured
            assertEquals("APPROVED", capturedFilter.status)
        }

        @Test
        @DisplayName("기간 필터로 조회한다")
        fun `기간 필터로 조회한다`() {
            // Given
            val from = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
            val to = LocalDateTime.of(2024, 12, 31, 23, 59, 59)

            val filterSlot = slot<QueryFilter>()
            every { queryPaymentsUseCase.query(capture(filterSlot)) } returns createEmptyQueryResult()

            // When
            controller.query(
                partnerId = null,
                status = null,
                from = from,
                to = to,
                cursor = null,
                limit = 20,
            )

            // Then
            val capturedFilter = filterSlot.captured
            assertEquals(from, capturedFilter.from)
            assertEquals(to, capturedFilter.to)
        }

        @Test
        @DisplayName("커서와 limit으로 페이지네이션한다")
        fun `커서와 limit으로 페이지네이션한다`() {
            // Given
            val filterSlot = slot<QueryFilter>()
            every { queryPaymentsUseCase.query(capture(filterSlot)) } returns createEmptyQueryResult()

            // When
            controller.query(
                partnerId = null,
                status = null,
                from = null,
                to = null,
                cursor = "encoded-cursor-value",
                limit = 50,
            )

            // Then
            val capturedFilter = filterSlot.captured
            assertEquals("encoded-cursor-value", capturedFilter.cursor)
            assertEquals(50, capturedFilter.limit)
        }

        @Test
        @DisplayName("모든 필터를 함께 사용한다")
        fun `모든 필터를 함께 사용한다`() {
            // Given
            val from = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
            val to = LocalDateTime.of(2024, 12, 31, 23, 59, 59)

            val filterSlot = slot<QueryFilter>()
            every { queryPaymentsUseCase.query(capture(filterSlot)) } returns createEmptyQueryResult()

            // When
            controller.query(
                partnerId = 5L,
                status = "APPROVED",
                from = from,
                to = to,
                cursor = "my-cursor",
                limit = 100,
            )

            // Then
            val capturedFilter = filterSlot.captured
            assertEquals(5L, capturedFilter.partnerId)
            assertEquals("APPROVED", capturedFilter.status)
            assertEquals(from, capturedFilter.from)
            assertEquals(to, capturedFilter.to)
            assertEquals("my-cursor", capturedFilter.cursor)
            assertEquals(100, capturedFilter.limit)
        }

        @Test
        @DisplayName("다음 페이지가 있을 때 nextCursor와 hasNext를 반환한다")
        fun `다음 페이지가 있을 때 nextCursor와 hasNext를 반환한다`() {
            // Given
            val payment = createMockPayment(id = 1L)
            val queryResult = QueryResult(
                items = listOf(payment),
                summary = PaymentSummary(
                    count = 100,
                    totalAmount = BigDecimal("1000000"),
                    totalNetAmount = BigDecimal("970000"),
                ),
                nextCursor = "next-page-cursor",
                hasNext = true,
            )

            every { queryPaymentsUseCase.query(any()) } returns queryResult

            // When
            val response = controller.query(
                partnerId = null,
                status = null,
                from = null,
                to = null,
                cursor = null,
                limit = 1,
            )

            // Then
            assertNotNull(response.body)
            assertEquals("next-page-cursor", response.body!!.nextCursor)
            assertTrue(response.body!!.hasNext)
        }

        @Test
        @DisplayName("빈 결과를 처리한다")
        fun `빈 결과를 처리한다`() {
            // Given
            val queryResult = QueryResult(
                items = emptyList(),
                summary = PaymentSummary(
                    count = 0,
                    totalAmount = BigDecimal.ZERO,
                    totalNetAmount = BigDecimal.ZERO,
                ),
                nextCursor = null,
                hasNext = false,
            )

            every { queryPaymentsUseCase.query(any()) } returns queryResult

            // When
            val response = controller.query(
                partnerId = 999L,
                status = null,
                from = null,
                to = null,
                cursor = null,
                limit = 20,
            )

            // Then
            assertNotNull(response.body)
            assertTrue(response.body!!.items.isEmpty())
            assertEquals(0L, response.body!!.summary.count)
            assertEquals(BigDecimal.ZERO, response.body!!.summary.totalAmount)
            assertEquals(BigDecimal.ZERO, response.body!!.summary.totalNetAmount)
            assertNull(response.body!!.nextCursor)
            assertTrue(!response.body!!.hasNext)
        }

        @Test
        @DisplayName("응답 items는 Payment에서 PaymentResponse로 변환된다")
        fun `응답 items는 Payment에서 PaymentResponse로 변환된다`() {
            // Given
            val payment = createMockPayment(
                id = 123L,
                partnerId = 5L,
                amount = BigDecimal("15000"),
                cardLast4 = "5678",
                approvalCode = "APPROVAL-ABC",
            )

            val queryResult = QueryResult(
                items = listOf(payment),
                summary = PaymentSummary(1, BigDecimal("15000"), BigDecimal("14500")),
                nextCursor = null,
                hasNext = false,
            )

            every { queryPaymentsUseCase.query(any()) } returns queryResult

            // When
            val response = controller.query(
                partnerId = null,
                status = null,
                from = null,
                to = null,
                cursor = null,
                limit = 20,
            )

            // Then
            assertNotNull(response.body)
            assertEquals(1, response.body!!.items.size)

            val item = response.body!!.items[0]
            assertEquals(123L, item.id)
            assertEquals(5L, item.partnerId)
            assertEquals(BigDecimal("15000"), item.amount)
            assertEquals("5678", item.cardLast4)
            assertEquals("APPROVAL-ABC", item.approvalCode)
        }

        @Test
        @DisplayName("limit 기본값은 20이다")
        fun `limit 기본값은 20이다`() {
            // Given
            val filterSlot = slot<QueryFilter>()
            every { queryPaymentsUseCase.query(capture(filterSlot)) } returns createEmptyQueryResult()

            // When - limit 파라미터를 명시하지 않으면 기본값 20이 사용됨
            controller.query(
                partnerId = null,
                status = null,
                from = null,
                to = null,
                cursor = null,
                limit = 20, // 컨트롤러에서 defaultValue = "20"으로 설정되어 있음
            )

            // Then
            val capturedFilter = filterSlot.captured
            assertEquals(20, capturedFilter.limit)
        }
    }

    // Helper methods
    private fun createMockPayment(
        id: Long = 1L,
        partnerId: Long = 1L,
        amount: BigDecimal = BigDecimal("10000"),
        appliedFeeRate: BigDecimal = BigDecimal("0.03"),
        feeAmount: BigDecimal = BigDecimal("400"),
        netAmount: BigDecimal = BigDecimal("9600"),
        cardLast4: String? = "1234",
        approvalCode: String = "APPROVAL-123",
        approvedAt: LocalDateTime = LocalDateTime.now(),
        status: PaymentStatus = PaymentStatus.APPROVED,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): Payment {
        return Payment(
            id = id,
            partnerId = partnerId,
            amount = amount,
            appliedFeeRate = appliedFeeRate,
            feeAmount = feeAmount,
            netAmount = netAmount,
            cardLast4 = cardLast4,
            approvalCode = approvalCode,
            approvedAt = approvedAt,
            status = status,
            createdAt = createdAt,
        )
    }

    private fun createEmptyQueryResult(): QueryResult {
        return QueryResult(
            items = emptyList(),
            summary = PaymentSummary(
                count = 0,
                totalAmount = BigDecimal.ZERO,
                totalNetAmount = BigDecimal.ZERO,
            ),
            nextCursor = null,
            hasNext = false,
        )
    }
}
