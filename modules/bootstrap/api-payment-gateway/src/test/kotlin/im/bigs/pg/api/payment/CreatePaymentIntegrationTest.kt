package im.bigs.pg.api.payment

import im.bigs.pg.api.payment.dto.CreatePaymentRequest
import im.bigs.pg.api.test.fixtures.TestPgData
import im.bigs.pg.infra.persistence.partner.entity.FeePolicyEntity
import im.bigs.pg.infra.persistence.partner.entity.PartnerEntity
import im.bigs.pg.infra.persistence.partner.repository.FeePolicyJpaRepository
import im.bigs.pg.infra.persistence.partner.repository.PartnerJpaRepository
import im.bigs.pg.infra.persistence.payment.repository.PaymentJpaRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("거래 등록 API 통합 테스트")
class CreatePaymentIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var paymentRepository: PaymentJpaRepository

    @Autowired
    private lateinit var partnerRepository: PartnerJpaRepository

    @Autowired
    private lateinit var feePolicyRepository: FeePolicyJpaRepository

    private var testPartner1Id: Long = 0L
    private var testPartner2Id: Long = 0L

    @BeforeEach
    fun setUp() {
        // 테스트 데이터 정리
        paymentRepository.deleteAll()
        feePolicyRepository.deleteAll()
        partnerRepository.deleteAll()

        // 테스트용 Partner와 FeePolicy 생성
        // TestPgClient는 짝수 파트너 ID만 지원하므로, 충분히 생성하여 짝수 ID 확보
        val partners = listOf(
            createTestPartnerWithFeePolicy("P1", "Test 1", BigDecimal("0.03"), BigDecimal("100")),
            createTestPartnerWithFeePolicy("P2", "Test 2", BigDecimal("0.03"), BigDecimal("100")),
            createTestPartnerWithFeePolicy("P3", "Test 3", BigDecimal("0.05"), BigDecimal("200")),
            createTestPartnerWithFeePolicy("P4", "Test 4", BigDecimal("0.05"), BigDecimal("200"))
        ).filter { it % 2L == 0L } // 짝수 ID만 선택 (TestPgClient 라우팅용)

        testPartner1Id = partners[0]
        testPartner2Id = partners[1]
    }

    @AfterEach
    fun tearDown() {
        paymentRepository.deleteAll()
        feePolicyRepository.deleteAll()
        partnerRepository.deleteAll()
    }

    @Test
    @DisplayName("유효한 결제 요청으로 결제를 생성한다")
    fun `유효한 결제 요청으로 결제를 생성한다`() {
        // Given
        val request = CreatePaymentRequest(
            partnerId = testPartner1Id,
            amount = BigDecimal("10000"),
            cardBin = "123456",
            cardLast4 = "3456",
            productName = "Test Product",
            enc = TestPgData.ENC_APPROVED_10000
        )

        // When
        val response = restTemplate.postForEntity(
            "/api/v1/payments",
            request,
            String::class.java
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)

        // DB에서 직접 확인
        val savedPayment = paymentRepository.findAll().maxByOrNull { it.createdAt }!!
        assertEquals(testPartner1Id, savedPayment.partnerId)
        assertEquals(BigDecimal("10000"), savedPayment.amount)
        assertEquals("3456", savedPayment.cardLast4)
        assertEquals("APPROVED", savedPayment.status)
        assertNotNull(savedPayment.approvalCode)

        // 수수료 계산 검증 (3% + 100원)
        assertEquals(0, BigDecimal("0.03").compareTo(savedPayment.appliedFeeRate))
        assertEquals(BigDecimal("400"), savedPayment.feeAmount) // 300 + 100
        assertEquals(BigDecimal("9600"), savedPayment.netAmount) // 10000 - 400
    }

    @Test
    @DisplayName("결제 생성 후 DB에 저장된다")
    fun `결제 생성 후 DB에 저장된다`() {
        // Given
        val request = CreatePaymentRequest(
            partnerId = testPartner1Id,
            amount = BigDecimal("10000"),
            cardBin = "123456",
            cardLast4 = "3456",
            enc = TestPgData.ENC_APPROVED_10000
        )

        val beforeCount = paymentRepository.count()

        // When
        val response = restTemplate.postForEntity(
            "/api/v1/payments",
            request,
            String::class.java
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)

        // DB에 저장되었는지 확인
        val afterCount = paymentRepository.count()
        assertEquals(beforeCount + 1, afterCount)

        val savedPayment = paymentRepository.findAll().maxByOrNull { it.createdAt }!!
        assertEquals(testPartner1Id, savedPayment.partnerId)
        assertEquals(BigDecimal("10000"), savedPayment.amount)
        assertEquals("APPROVED", savedPayment.status)
    }

    @Test
    @DisplayName("여러 파트너의 결제를 처리한다")
    fun `여러 파트너의 결제를 처리한다`() {
        // Given
        val request1 = CreatePaymentRequest(
            partnerId = testPartner1Id,
            amount = BigDecimal("10000"),
            cardBin = "111122",
            cardLast4 = "4444",
            enc = TestPgData.ENC_APPROVED_10000
        )

        val request2 = CreatePaymentRequest(
            partnerId = testPartner2Id,
            amount = BigDecimal("10000"),
            cardBin = "555566",
            cardLast4 = "8888",
            enc = TestPgData.ENC_APPROVED_10000
        )

        // When
        val response1 = restTemplate.postForEntity("/api/v1/payments", request1, String::class.java)
        val response2 = restTemplate.postForEntity("/api/v1/payments", request2, String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response1.statusCode)
        assertEquals(HttpStatus.OK, response2.statusCode)

        // DB에서 파트너별로 조회
        val allPayments = paymentRepository.findAll().sortedByDescending { it.createdAt }
        assertTrue(allPayments.size >= 2)

        val payment2 = allPayments[0] // 최신 (Partner 2)
        val payment1 = allPayments[1] // 그 다음 (Partner 1)

        // Partner 1 검증 (3% + 100원)
        assertEquals(testPartner1Id, payment1.partnerId)
        assertEquals(BigDecimal("400"), payment1.feeAmount) // 300 + 100
        assertEquals(BigDecimal("9600"), payment1.netAmount)

        // Partner 2 검증 (5% + 200원)
        assertEquals(testPartner2Id, payment2.partnerId)
        assertEquals(BigDecimal("700"), payment2.feeAmount) // 500 + 200 (10000 * 0.05 + 200)
        assertEquals(BigDecimal("9300"), payment2.netAmount) // 10000 - 700
    }

    @Test
    @DisplayName("파트너별 수수료 정책이 올바르게 적용된다")
    fun `파트너별 수수료 정책이 올바르게 적용된다`() {
        // Given
        val request1 = CreatePaymentRequest(
            partnerId = testPartner1Id,
            amount = BigDecimal("10000"),
            enc = TestPgData.ENC_APPROVED_10000
        )

        val request2 = CreatePaymentRequest(
            partnerId = testPartner2Id,
            amount = BigDecimal("10000"),
            enc = TestPgData.ENC_APPROVED_10000
        )

        // When
        restTemplate.postForEntity("/api/v1/payments", request1, String::class.java)
        restTemplate.postForEntity("/api/v1/payments", request2, String::class.java)

        // Then
        val allPayments = paymentRepository.findAll().sortedByDescending { it.createdAt }
        val payment2 = allPayments[0]
        val payment1 = allPayments[1]

        // Partner 1: 3% + 100 = 300 + 100 = 400
        assertEquals(BigDecimal("400"), payment1.feeAmount)
        assertEquals(BigDecimal("9600"), payment1.netAmount)

        // Partner 2: 5% + 200 = 500 + 200 = 700
        assertEquals(BigDecimal("700"), payment2.feeAmount)
        assertEquals(BigDecimal("9300"), payment2.netAmount)
    }

    @Test
    @DisplayName("PG 승인 후 approvalCode가 반환된다")
    fun `PG 승인 후 approvalCode가 반환된다`() {
        // Given
        val request = CreatePaymentRequest(
            partnerId = testPartner1Id,
            amount = BigDecimal("10000"),
            enc = TestPgData.ENC_APPROVED_10000
        )

        // When
        val response = restTemplate.postForEntity("/api/v1/payments", request, String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)

        val savedPayment = paymentRepository.findAll().maxByOrNull { it.createdAt }!!
        assertEquals("APPROVED", savedPayment.status)
        assertNotNull(savedPayment.approvalCode)
        assertTrue(savedPayment.approvalCode!!.isNotBlank())
        assertNotNull(savedPayment.approvedAt)
    }

    @Test
    @DisplayName("존재하지 않는 파트너로 결제 시도 시 에러가 발생한다")
    fun `존재하지 않는 파트너로 결제 시도 시 에러가 발생한다`() {
        // Given
        val request = CreatePaymentRequest(
            partnerId = 999L, // 존재하지 않는 파트너
            amount = BigDecimal("10000"),
            enc = TestPgData.ENC_APPROVED_10000
        )

        // When
        val response = restTemplate.postForEntity("/api/v1/payments", request, String::class.java)

        // Then
        assertTrue(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }

    @Test
    @DisplayName("음수 금액으로 결제 시도 시 에러가 발생한다")
    fun `음수 금액으로 결제 시도 시 에러가 발생한다`() {
        // Given
        val request = CreatePaymentRequest(
            partnerId = testPartner1Id,
            amount = BigDecimal("-1000"),
            enc = TestPgData.ENC_APPROVED_10000
        )

        // When
        val response = restTemplate.postForEntity("/api/v1/payments", request, String::class.java)

        // Then
        assertTrue(response.statusCode.is4xxClientError)
    }

    @Test
    @DisplayName("0원 결제 시도 시 에러가 발생한다")
    fun `0원 결제 시도 시 에러가 발생한다`() {
        // Given
        val request = CreatePaymentRequest(
            partnerId = testPartner1Id,
            amount = BigDecimal.ZERO,
            enc = TestPgData.ENC_APPROVED_10000
        )

        // When
        val response = restTemplate.postForEntity("/api/v1/payments", request, String::class.java)

        // Then
        assertTrue(response.statusCode.is4xxClientError)
    }

    // Helper method
    private fun createTestPartnerWithFeePolicy(
        partnerCode: String,
        partnerName: String,
        feePercentage: BigDecimal,
        fixedFee: BigDecimal
    ): Long {
        val partner = PartnerEntity(
            id = null,
            code = partnerCode,
            name = partnerName,
            active = true
        )
        val savedPartner = partnerRepository.save(partner)
        val partnerId = savedPartner.id!!

        val feePolicy = FeePolicyEntity(
            id = null,
            partnerId = partnerId,
            effectiveFrom = Instant.parse("2024-01-01T00:00:00Z"),
            percentage = feePercentage,
            fixedFee = fixedFee
        )
        feePolicyRepository.save(feePolicy)

        return partnerId
    }
}
