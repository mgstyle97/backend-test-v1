package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.application.pghistory.port.out.PgHistoryOutPort
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.partner.Partner
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.domain.pghistory.PgHistory
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

class PaymentServiceTest {
    private lateinit var partnerRepo: PartnerOutPort
    private lateinit var feeRepo: FeePolicyOutPort
    private lateinit var paymentRepo: PaymentOutPort
    private lateinit var pgHistoryOutPort: PgHistoryOutPort
    private lateinit var pgClient: PgClientOutPort
    private lateinit var service: PaymentService

    @BeforeEach
    fun setUp() {
        partnerRepo = mockk()
        feeRepo = mockk()
        paymentRepo = mockk()
        pgHistoryOutPort = mockk()

        // pgHistoryOutPort Mock 동작 정의
        every { pgHistoryOutPort.save(any()) } answers {
            val history = firstArg<PgHistory>()
            history.copy(id = 1L)
        }
        every { pgHistoryOutPort.updateHistory(any(), any()) } returns 1

        pgClient = object : PgClientOutPort {
            override fun supports(partnerId: Long) = true
            override fun approve(request: PgApproveRequest) =
                PgApproveResult("APPROVAL-123", LocalDateTime.of(2024, 1, 1, 0, 0), PaymentStatus.APPROVED)
        }
        service = PaymentService(partnerRepo, feeRepo, paymentRepo, pgHistoryOutPort, listOf(pgClient))
    }

    @Nested
    @DisplayName("정상 케이스")
    inner class SuccessCase {

        @Test
        @DisplayName("결제 시 수수료 정책을 적용하고 저장해야 한다")
        fun `결제 시 수수료 정책을 적용하고 저장해야 한다`() {
            // Given
            every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "Test", true)
            every { feeRepo.findEffectivePolicy(1L, any()) } returns FeePolicy(
                id = 10L,
                partnerId = 1L,
                effectiveFrom = LocalDateTime.ofInstant(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC),
                percentage = BigDecimal("0.0300"),
                fixedFee = BigDecimal("100"),
            )
            val savedSlot = slot<Payment>()
            every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 99L) }

            val cmd = PaymentCommand(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                cardLast4 = "4242",
                enc = "dummy-encrypted-data-for-test",
            )

            // When
            val result = service.pay(cmd)

            // Then
            assertEquals(99L, result.id)
            assertEquals(BigDecimal("10000"), result.amount)
            assertEquals(BigDecimal("0.0300"), result.appliedFeeRate)
            assertEquals(BigDecimal("400"), result.feeAmount) // 10000 * 0.03 + 100
            assertEquals(BigDecimal("9600"), result.netAmount)
            assertEquals(PaymentStatus.APPROVED, result.status)
            assertEquals("APPROVAL-123", result.approvalCode)
            assertEquals("4242", result.cardLast4)

            verify(exactly = 1) { partnerRepo.findById(1L) }
            verify(exactly = 1) { feeRepo.findEffectivePolicy(1L, any()) }
            verify(exactly = 1) { paymentRepo.save(any()) }
        }

        @Test
        @DisplayName("고정 수수료만 있는 정책을 적용할 수 있다")
        fun `고정 수수료만 있는 정책을 적용할 수 있다`() {
            // Given - 비율 0%, 고정 수수료 200원
            every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "Test", true)
            every { feeRepo.findEffectivePolicy(1L, any()) } returns FeePolicy(
                id = 10L,
                partnerId = 1L,
                effectiveFrom = LocalDateTime.now(),
                percentage = BigDecimal("0.0000"),
                fixedFee = BigDecimal("200"),
            )
            val savedSlot = slot<Payment>()
            every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 1L) }

            val cmd = PaymentCommand(
                partnerId = 1L,
                amount = BigDecimal("5000"),
                enc = "encrypted",
            )

            // When
            val result = service.pay(cmd)

            // Then
            assertEquals(BigDecimal("200"), result.feeAmount) // 고정 수수료만
            assertEquals(BigDecimal("4800"), result.netAmount) // 5000 - 200
        }

        @Test
        @DisplayName("비율 수수료만 있는 정책을 적용할 수 있다")
        fun `비율 수수료만 있는 정책을 적용할 수 있다`() {
            // Given - 비율 2%, 고정 수수료 0원
            every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "Test", true)
            every { feeRepo.findEffectivePolicy(1L, any()) } returns FeePolicy(
                id = 10L,
                partnerId = 1L,
                effectiveFrom = LocalDateTime.now(),
                percentage = BigDecimal("0.0200"),
                fixedFee = BigDecimal.ZERO,
            )
            val savedSlot = slot<Payment>()
            every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 1L) }

            val cmd = PaymentCommand(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                enc = "encrypted",
            )

            // When
            val result = service.pay(cmd)

            // Then
            assertEquals(BigDecimal("200"), result.feeAmount) // 10000 * 0.02
            assertEquals(BigDecimal("9800"), result.netAmount) // 10000 - 200
        }

        @Test
        @DisplayName("카드 BIN과 Last4 정보를 저장한다")
        fun `카드 BIN과 Last4 정보를 저장한다`() {
            // Given
            every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "Test", true)
            every { feeRepo.findEffectivePolicy(1L, any()) } returns FeePolicy(
                id = 10L,
                partnerId = 1L,
                effectiveFrom = LocalDateTime.now(),
                percentage = BigDecimal("0.0300"),
                fixedFee = BigDecimal("100"),
            )
            val savedSlot = slot<Payment>()
            every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 1L) }

            val cmd = PaymentCommand(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                cardBin = "123456",
                cardLast4 = "7890",
                enc = "encrypted",
            )

            // When
            val result = service.pay(cmd)

            // Then
            assertEquals("123456", result.cardBin)
            assertEquals("7890", result.cardLast4)
        }

        @Test
        @DisplayName("PG 승인 정보를 올바르게 저장한다")
        fun `PG 승인 정보를 올바르게 저장한다`() {
            // Given
            val approvedAt = LocalDateTime.of(2024, 12, 25, 10, 30)
            val customPgClient = object : PgClientOutPort {
                override fun supports(partnerId: Long) = true
                override fun approve(request: PgApproveRequest) =
                    PgApproveResult("CUSTOM-APPROVAL-999", approvedAt, PaymentStatus.APPROVED)
            }
            val customService = PaymentService(partnerRepo, feeRepo, paymentRepo, pgHistoryOutPort, listOf(customPgClient))

            every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "Test", true)
            every { feeRepo.findEffectivePolicy(1L, any()) } returns FeePolicy(
                id = 10L,
                partnerId = 1L,
                effectiveFrom = LocalDateTime.now(),
                percentage = BigDecimal("0.0300"),
                fixedFee = BigDecimal("100"),
            )
            val savedSlot = slot<Payment>()
            every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 1L) }

            val cmd = PaymentCommand(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                enc = "encrypted",
            )

            // When
            val result = customService.pay(cmd)

            // Then
            assertEquals("CUSTOM-APPROVAL-999", result.approvalCode)
            assertEquals(approvedAt, result.approvedAt)
            assertEquals(PaymentStatus.APPROVED, result.status)
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    inner class ExceptionCase {

        @Test
        @DisplayName("Partner가 존재하지 않으면 예외를 던진다")
        fun `Partner가 존재하지 않으면 예외를 던진다`() {
            // Given
            every { partnerRepo.findById(999L) } returns null

            val cmd = PaymentCommand(
                partnerId = 999L,
                amount = BigDecimal("10000"),
                enc = "encrypted",
            )

            // When & Then
            val exception = assertThrows<IllegalArgumentException> {
                service.pay(cmd)
            }
            assertEquals("Partner not found: 999", exception.message)
        }

        @Test
        @DisplayName("Partner가 비활성화 상태이면 예외를 던진다")
        fun `Partner가 비활성화 상태이면 예외를 던진다`() {
            // Given
            every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "Test", active = false)

            val cmd = PaymentCommand(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                enc = "encrypted",
            )

            // When & Then
            val exception = assertThrows<IllegalArgumentException> {
                service.pay(cmd)
            }
            assertEquals("Partner is inactive: 1", exception.message)
        }

        @Test
        @DisplayName("지원하는 PG Client가 없으면 예외를 던진다")
        fun `지원하는 PG Client가 없으면 예외를 던진다`() {
            // Given
            val nonSupportPgClient = object : PgClientOutPort {
                override fun supports(partnerId: Long) = false
                override fun approve(request: PgApproveRequest) =
                    PgApproveResult("", LocalDateTime.now(), PaymentStatus.APPROVED)
            }
            val customService = PaymentService(partnerRepo, feeRepo, paymentRepo, pgHistoryOutPort, listOf(nonSupportPgClient))

            every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "Test", true)

            val cmd = PaymentCommand(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                enc = "encrypted",
            )

            // When & Then
            val exception = assertThrows<IllegalStateException> {
                customService.pay(cmd)
            }
            assertEquals("No PG client for partner 1", exception.message)
        }

        @Test
        @DisplayName("FeePolicy가 존재하지 않으면 예외를 던진다")
        fun `FeePolicy가 존재하지 않으면 예외를 던진다`() {
            // Given
            every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "Test", true)
            every { feeRepo.findEffectivePolicy(1L, any()) } returns null

            val cmd = PaymentCommand(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                enc = "encrypted",
            )

            // When & Then
            val exception = assertThrows<IllegalArgumentException> {
                service.pay(cmd)
            }
            assertEquals("Fee policy not found!", exception.message)
        }
    }

    @Nested
    @DisplayName("PG Client 선택")
    inner class PgClientSelection {

        @Test
        @DisplayName("여러 PG Client 중 지원하는 클라이언트를 선택한다")
        fun `여러 PG Client 중 지원하는 클라이언트를 선택한다`() {
            // Given
            val pgClient1 = object : PgClientOutPort {
                override fun supports(partnerId: Long) = partnerId == 1L
                override fun approve(request: PgApproveRequest) =
                    PgApproveResult("CLIENT1-APPROVAL", LocalDateTime.now(), PaymentStatus.APPROVED)
            }
            val pgClient2 = object : PgClientOutPort {
                override fun supports(partnerId: Long) = partnerId == 2L
                override fun approve(request: PgApproveRequest) =
                    PgApproveResult("CLIENT2-APPROVAL", LocalDateTime.now(), PaymentStatus.APPROVED)
            }
            val multiClientService = PaymentService(partnerRepo, feeRepo, paymentRepo, pgHistoryOutPort, listOf(pgClient1, pgClient2))

            every { partnerRepo.findById(2L) } returns Partner(2L, "TEST2", "Test2", true)
            every { feeRepo.findEffectivePolicy(2L, any()) } returns FeePolicy(
                id = 10L,
                partnerId = 2L,
                effectiveFrom = LocalDateTime.now(),
                percentage = BigDecimal("0.0300"),
                fixedFee = BigDecimal("100"),
            )
            val savedSlot = slot<Payment>()
            every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 1L) }

            val cmd = PaymentCommand(
                partnerId = 2L,
                amount = BigDecimal("10000"),
                enc = "encrypted",
            )

            // When
            val result = multiClientService.pay(cmd)

            // Then
            assertEquals("CLIENT2-APPROVAL", result.approvalCode)
        }
    }
}
