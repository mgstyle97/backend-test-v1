package im.bigs.pg.api.payment

import im.bigs.pg.api.payment.dto.QueryResponse
import im.bigs.pg.infra.persistence.payment.entity.PaymentEntity
import im.bigs.pg.infra.persistence.payment.repository.PaymentJpaRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("거래 내역 조회 API 통합 테스트 - 쿼리 파라미터 필터링 검증")
class QueryPaymentsIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var paymentRepository: PaymentJpaRepository

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @BeforeEach
    fun setUp() {
        // 테스트 데이터 정리
        paymentRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        // 테스트 데이터 정리
        paymentRepository.deleteAll()
    }

    @Nested
    @DisplayName("partnerId 필터링 테스트")
    inner class PartnerIdFilterTest {

        @Test
        @DisplayName("partnerId로 필터링 시 해당 partner의 결제만 반환된다")
        fun `partnerId로 필터링 시 해당 partner의 결제만 반환된다`() {
            // Given - Partner 1에 5건, Partner 2에 3건 생성
            val baseTime = Instant.parse("2024-01-01T10:00:00Z")
            repeat(5) { i ->
                createPayment(partnerId = 1L, amount = 1000 + i, createdAt = baseTime.plusSeconds(i.toLong()))
            }
            repeat(3) { i ->
                createPayment(partnerId = 2L, amount = 2000 + i, createdAt = baseTime.plusSeconds(10 + i.toLong()))
            }

            // When - Partner 1로 필터링
            val url = "/api/v1/payments?partnerId=1&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(5, body.items.size)
            assertTrue(body.items.all { it.partnerId == 1L })
            assertEquals(5L, body.summary.count)
            assertEquals(BigDecimal("5010"), body.summary.totalAmount) // 1000+1001+1002+1003+1004
        }

        @Test
        @DisplayName("존재하지 않는 partnerId로 필터링 시 빈 결과를 반환한다")
        fun `존재하지 않는 partnerId로 필터링 시 빈 결과를 반환한다`() {
            // Given
            val baseTime = Instant.parse("2024-01-01T10:00:00Z")
            createPayment(partnerId = 1L, amount = 1000, createdAt = baseTime)

            // When - 존재하지 않는 Partner 999
            val url = "/api/v1/payments?partnerId=999&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertTrue(body.items.isEmpty())
            assertEquals(0L, body.summary.count)
            assertEquals(BigDecimal.ZERO, body.summary.totalAmount)
        }

        @Test
        @DisplayName("partnerId 필터 없이 조회 시 모든 partner의 결제를 반환한다")
        fun `partnerId 필터 없이 조회 시 모든 partner의 결제를 반환한다`() {
            // Given
            val baseTime = Instant.parse("2024-01-01T10:00:00Z")
            createPayment(partnerId = 1L, amount = 1000, createdAt = baseTime)
            createPayment(partnerId = 2L, amount = 2000, createdAt = baseTime.plusSeconds(1))
            createPayment(partnerId = 3L, amount = 3000, createdAt = baseTime.plusSeconds(2))

            // When
            val url = "/api/v1/payments?limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(3, body.items.size)
            assertEquals(3L, body.summary.count)
            assertEquals(BigDecimal("6000"), body.summary.totalAmount) // 1000+2000+3000
        }
    }

    @Nested
    @DisplayName("status 필터링 테스트")
    inner class StatusFilterTest {

        @Test
        @DisplayName("status=APPROVED로 필터링 시 승인된 결제만 반환된다")
        fun `status=APPROVED로 필터링 시 승인된 결제만 반환된다`() {
            // Given
            val baseTime = Instant.parse("2024-01-01T10:00:00Z")
            repeat(4) { i ->
                createPayment(
                    partnerId = 1L,
                    amount = 1000 + i,
                    status = "APPROVED",
                    createdAt = baseTime.plusSeconds(i.toLong()),
                )
            }
            repeat(2) { i ->
                createPayment(
                    partnerId = 1L,
                    amount = 2000 + i,
                    status = "CANCELED",
                    createdAt = baseTime.plusSeconds(10 + i.toLong()),
                )
            }

            // When
            val url = "/api/v1/payments?status=APPROVED&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(4, body.items.size)
            assertTrue(body.items.all { it.status.name == "APPROVED" })
            assertEquals(4L, body.summary.count)
            assertEquals(BigDecimal("4006"), body.summary.totalAmount) // 1000+1001+1002+1003
        }

        @Test
        @DisplayName("status=CANCELED로 필터링 시 취소된 결제만 반환된다")
        fun `status=CANCELED로 필터링 시 취소된 결제만 반환된다`() {
            // Given
            val baseTime = Instant.parse("2024-01-01T10:00:00Z")
            createPayment(partnerId = 1L, amount = 1000, status = "APPROVED", createdAt = baseTime)
            createPayment(partnerId = 1L, amount = 2000, status = "CANCELED", createdAt = baseTime.plusSeconds(1))
            createPayment(partnerId = 1L, amount = 3000, status = "CANCELED", createdAt = baseTime.plusSeconds(2))

            // When
            val url = "/api/v1/payments?status=CANCELED&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(2, body.items.size)
            assertTrue(body.items.all { it.status.name == "CANCELED" })
            assertEquals(2L, body.summary.count)
            assertEquals(BigDecimal("5000"), body.summary.totalAmount) // 2000+3000
        }
    }

    @Nested
    @DisplayName("기간 필터링 테스트 (from/to)")
    inner class DateRangeFilterTest {

        @Test
        @DisplayName("from 파라미터로 시작 시간 이후의 결제만 조회된다")
        fun `from 파라미터로 시작 시간 이후의 결제만 조회된다`() {
            // Given
            val baseTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0)
            createPayment(
                partnerId = 1L,
                amount = 1000,
                createdAt = baseTime.minusHours(1).toInstant(ZoneOffset.UTC),
            ) // 09:00 - 제외됨
            createPayment(
                partnerId = 1L,
                amount = 2000,
                createdAt = baseTime.toInstant(ZoneOffset.UTC),
            ) // 10:00 - 포함됨
            createPayment(
                partnerId = 1L,
                amount = 3000,
                createdAt = baseTime.plusHours(1).toInstant(ZoneOffset.UTC),
            ) // 11:00 - 포함됨

            // When - from=2024-01-01 10:00:00
            val fromStr = baseTime.format(dateTimeFormatter)
            val url = "/api/v1/payments?from=$fromStr&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(2, body.items.size)
            assertEquals(2L, body.summary.count)
            assertEquals(BigDecimal("5000"), body.summary.totalAmount) // 2000+3000
        }

        @Test
        @DisplayName("to 파라미터로 종료 시간 미만의 결제만 조회된다")
        fun `to 파라미터로 종료 시간 미만의 결제만 조회된다`() {
            // Given
            val baseTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0)
            createPayment(
                partnerId = 1L,
                amount = 1000,
                createdAt = baseTime.minusHours(1).toInstant(ZoneOffset.UTC),
            ) // 09:00 - 포함됨 (< 10:00)
            createPayment(
                partnerId = 1L,
                amount = 2000,
                createdAt = baseTime.toInstant(ZoneOffset.UTC),
            ) // 10:00 - 제외됨 (to는 exclusive)
            createPayment(
                partnerId = 1L,
                amount = 3000,
                createdAt = baseTime.plusHours(1).toInstant(ZoneOffset.UTC),
            ) // 11:00 - 제외됨

            // When - to=2024-01-01 10:00:00 (exclusive)
            val toStr = baseTime.format(dateTimeFormatter)
            val url = "/api/v1/payments?to=$toStr&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(1, body.items.size)
            assertEquals(1L, body.summary.count)
            assertEquals(BigDecimal("1000"), body.summary.totalAmount) // 09:00 데이터만
        }

        @Test
        @DisplayName("from과 to 파라미터로 특정 기간의 결제만 조회된다")
        fun `from과 to 파라미터로 특정 기간의 결제만 조회된다`() {
            // Given
            val baseTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
            createPayment(
                partnerId = 1L,
                amount = 1000,
                createdAt = baseTime.minusHours(3).toInstant(ZoneOffset.UTC),
            ) // 09:00 - 제외 (< from)
            createPayment(
                partnerId = 1L,
                amount = 2000,
                createdAt = baseTime.minusHours(2).toInstant(ZoneOffset.UTC),
            ) // 10:00 - 포함 (>= from, < to)
            createPayment(
                partnerId = 1L,
                amount = 3000,
                createdAt = baseTime.minusHours(1).toInstant(ZoneOffset.UTC),
            ) // 11:00 - 포함 (>= from, < to)
            createPayment(
                partnerId = 1L,
                amount = 4000,
                createdAt = baseTime.toInstant(ZoneOffset.UTC),
            ) // 12:00 - 제외 (to는 exclusive)
            createPayment(
                partnerId = 1L,
                amount = 5000,
                createdAt = baseTime.plusHours(1).toInstant(ZoneOffset.UTC),
            ) // 13:00 - 제외

            // When - from=2024-01-01 10:00:00 (inclusive), to=2024-01-01 12:00:00 (exclusive)
            val fromStr = baseTime.minusHours(2).format(dateTimeFormatter)
            val toStr = baseTime.format(dateTimeFormatter)
            val url = "/api/v1/payments?from=$fromStr&to=$toStr&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(2, body.items.size)
            assertEquals(2L, body.summary.count)
            assertEquals(BigDecimal("5000"), body.summary.totalAmount) // 2000+3000 (10:00, 11:00만)
        }
    }

    @Nested
    @DisplayName("복합 필터링 테스트")
    inner class CombinedFilterTest {

        @Test
        @DisplayName("partnerId와 status를 함께 필터링한다")
        fun `partnerId와 status를 함께 필터링한다`() {
            // Given
            val baseTime = Instant.parse("2024-01-01T10:00:00Z")
            // Partner 1 APPROVED 2건
            createPayment(partnerId = 1L, amount = 1000, status = "APPROVED", createdAt = baseTime)
            createPayment(partnerId = 1L, amount = 1100, status = "APPROVED", createdAt = baseTime.plusSeconds(1))
            // Partner 1 CANCELED 1건
            createPayment(partnerId = 1L, amount = 1200, status = "CANCELED", createdAt = baseTime.plusSeconds(2))
            // Partner 2 APPROVED 2건
            createPayment(partnerId = 2L, amount = 2000, status = "APPROVED", createdAt = baseTime.plusSeconds(3))
            createPayment(partnerId = 2L, amount = 2100, status = "APPROVED", createdAt = baseTime.plusSeconds(4))

            // When - Partner 1의 APPROVED만 조회
            val url = "/api/v1/payments?partnerId=1&status=APPROVED&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(2, body.items.size)
            assertTrue(body.items.all { it.partnerId == 1L && it.status.name == "APPROVED" })
            assertEquals(2L, body.summary.count)
            assertEquals(BigDecimal("2100"), body.summary.totalAmount) // 1000+1100
        }

        @Test
        @DisplayName("partnerId, status, from, to를 모두 함께 필터링한다")
        fun `partnerId, status, from, to를 모두 함께 필터링한다`() {
            // Given
            val baseTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0)

            // Partner 1 APPROVED - 기간 내
            createPayment(
                partnerId = 1L,
                amount = 1000,
                status = "APPROVED",
                createdAt = baseTime.toInstant(ZoneOffset.UTC),
            )
            createPayment(
                partnerId = 1L,
                amount = 1100,
                status = "APPROVED",
                createdAt = baseTime.plusHours(1).toInstant(ZoneOffset.UTC),
            )

            // Partner 1 APPROVED - 기간 외
            createPayment(
                partnerId = 1L,
                amount = 1200,
                status = "APPROVED",
                createdAt = baseTime.plusHours(3).toInstant(ZoneOffset.UTC),
            )

            // Partner 1 CANCELED - 기간 내
            createPayment(
                partnerId = 1L,
                amount = 1300,
                status = "CANCELED",
                createdAt = baseTime.plusMinutes(30).toInstant(ZoneOffset.UTC),
            )

            // Partner 2 APPROVED - 기간 내
            createPayment(
                partnerId = 2L,
                amount = 2000,
                status = "APPROVED",
                createdAt = baseTime.plusMinutes(45).toInstant(ZoneOffset.UTC),
            )

            // When - Partner 1, APPROVED, 10:00 ~ 12:00
            val fromStr = baseTime.format(dateTimeFormatter)
            val toStr = baseTime.plusHours(2).format(dateTimeFormatter)
            val url = "/api/v1/payments?partnerId=1&status=APPROVED&from=$fromStr&to=$toStr&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(2, body.items.size)
            assertTrue(body.items.all { it.partnerId == 1L && it.status.name == "APPROVED" })
            assertEquals(2L, body.summary.count)
            assertEquals(BigDecimal("2100"), body.summary.totalAmount) // 1000+1100
        }
    }

    @Nested
    @DisplayName("Summary 통계 검증")
    inner class SummaryValidationTest {

        @Test
        @DisplayName("summary는 필터링된 데이터의 통계를 반영한다")
        fun `summary는 필터링된 데이터의 통계를 반영한다`() {
            // Given - 다양한 금액의 결제 생성
            val baseTime = Instant.parse("2024-01-01T10:00:00Z")
            createPayment(partnerId = 1L, amount = 10000, feeAmount = 300, createdAt = baseTime)
            createPayment(partnerId = 1L, amount = 20000, feeAmount = 600, createdAt = baseTime.plusSeconds(1))
            createPayment(partnerId = 1L, amount = 30000, feeAmount = 900, createdAt = baseTime.plusSeconds(2))

            // When
            val url = "/api/v1/payments?partnerId=1&limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(3L, body.summary.count)
            assertEquals(BigDecimal("60000"), body.summary.totalAmount) // 10000+20000+30000
            assertEquals(BigDecimal("58200"), body.summary.totalNetAmount) // 60000-1800
        }

        @Test
        @DisplayName("페이지네이션 후에도 summary는 전체 필터링된 데이터를 반영한다")
        fun `페이지네이션 후에도 summary는 전체 필터링된 데이터를 반영한다`() {
            // Given - 10건 생성
            val baseTime = Instant.parse("2024-01-01T10:00:00Z")
            repeat(10) { i ->
                createPayment(
                    partnerId = 1L,
                    amount = 1000,
                    feeAmount = 30,
                    createdAt = baseTime.plusSeconds(i.toLong()),
                )
            }

            // When - limit=5로 첫 페이지만 조회
            val url = "/api/v1/payments?partnerId=1&limit=5"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then - items는 5개지만 summary는 전체 10건을 반영
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(5, body.items.size) // 페이지당 5개
            assertTrue(body.hasNext)
            assertEquals(10L, body.summary.count) // 전체 10건
            assertEquals(BigDecimal("10000"), body.summary.totalAmount) // 전체 합계
            assertEquals(BigDecimal("9700"), body.summary.totalNetAmount) // 전체 순액
        }
    }

    @Nested
    @DisplayName("정렬 순서 검증")
    inner class SortOrderTest {

        @Test
        @DisplayName("결과는 createdAt DESC, id DESC 순서로 정렬된다")
        fun `결과는 createdAt DESC, id DESC 순서로 정렬된다`() {
            // Given - 동일 시간에 여러 건 생성 (id가 다름)
            val baseTime = Instant.parse("2024-01-01T10:00:00Z")
            val payment1 = createPayment(partnerId = 1L, amount = 1000, createdAt = baseTime)
            val payment2 = createPayment(partnerId = 1L, amount = 2000, createdAt = baseTime)
            val payment3 = createPayment(partnerId = 1L, amount = 3000, createdAt = baseTime.plusSeconds(1))

            // When
            val url = "/api/v1/payments?limit=100"
            val response = restTemplate.getForEntity(url, QueryResponse::class.java)

            // Then - createdAt이 큰 것부터, 같으면 id가 큰 것부터
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(3, body.items.size)

            // 최신 시간이 먼저
            assertTrue(body.items[0].createdAt >= body.items[1].createdAt)
            assertTrue(body.items[1].createdAt >= body.items[2].createdAt)

            // 같은 시간이면 id가 큰 것이 먼저
            val sameTimeItems = body.items.filter {
                it.createdAt.toInstant(ZoneOffset.UTC) == baseTime
            }
            if (sameTimeItems.size >= 2) {
                assertTrue(sameTimeItems[0].id!! > sameTimeItems[1].id!!)
            }
        }
    }

    // Helper method
    private fun createPayment(
        partnerId: Long,
        amount: Int,
        feeAmount: Int = 30,
        status: String = "APPROVED",
        createdAt: Instant,
    ): PaymentEntity {
        val netAmount = amount - feeAmount
        return paymentRepository.save(
            PaymentEntity(
                partnerId = partnerId,
                amount = BigDecimal(amount),
                appliedFeeRate = BigDecimal("0.03"),
                feeAmount = BigDecimal(feeAmount),
                netAmount = BigDecimal(netAmount),
                cardBin = "123456",
                cardLast4 = "4242",
                approvalCode = "APPROVAL-${System.nanoTime()}",
                approvedAt = createdAt,
                status = status,
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )
    }
}
