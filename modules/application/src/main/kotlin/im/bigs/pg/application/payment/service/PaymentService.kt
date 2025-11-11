package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.exception.PgAuthenticationException
import im.bigs.pg.application.payment.exception.PgUnexpectedException
import im.bigs.pg.application.payment.exception.PgValidationException
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.application.pghistory.port.out.PgHistoryOutPort
import im.bigs.pg.domain.calculation.FeeCalculator
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.domain.pghistory.PgHistory
import im.bigs.pg.domain.pghistory.PgRequestStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 결제 생성 유스케이스 구현체.
 * - 입력(REST 등) → 도메인/외부PG/영속성 포트를 순차적으로 호출하는 흐름을 담당합니다.
 * - 수수료 정책 조회 및 적용(계산)은 도메인 유틸리티를 통해 수행합니다.
 */
@Service
class PaymentService(
    private val partnerRepository: PartnerOutPort,
    private val feePolicyRepository: FeePolicyOutPort,
    private val paymentRepository: PaymentOutPort,
    private val pgHistoryOutPort: PgHistoryOutPort,
    private val pgClients: List<PgClientOutPort>,
) : PaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 결제 승인/수수료 계산/저장을 순차적으로 수행합니다.
     * - 현재 예시 구현은 하드코드된 수수료(3% + 100)로 계산합니다.
     * - 과제: 제휴사별 수수료 정책을 적용하도록 개선해 보세요.
     */
    @Transactional
    override fun pay(command: PaymentCommand): Payment {
        val partner = partnerRepository.findById(command.partnerId)
            ?: throw IllegalArgumentException("Partner not found: ${command.partnerId}")
        require(partner.active) { "Partner is inactive: ${partner.id}" }

        val pgClient = pgClients.firstOrNull { it.supports(partner.id) }
            ?: throw IllegalStateException("No PG client for partner ${partner.id}")

        // PG사 요청 내역 우선 저장(상태: PENDING)
        val pgHistory = pgHistoryOutPort.save(
            PgHistory(
                amount = command.amount,
                cardBin = command.cardBin,
                cardLast4 = command.cardLast4,
                pgProvider = "TEST_PG",
                status = PgRequestStatus.PENDING,
            )
        )

        val approve = try {
            pgClient.approve(
                PgApproveRequest(
                    partnerId = partner.id,
                    amount = command.amount,
                    cardBin = command.cardBin,
                    cardLast4 = command.cardLast4,
                    productName = command.productName,
                    enc = command.enc,
                ),
            )
        } catch (e: PgAuthenticationException) {
            logger.warn("PG 인증 실패 [${pgHistory.pgProvider}]: ${e.message}", e)
            pgHistoryOutPort.updateHistory(
                id = pgHistory.id!!,
                status = PgRequestStatus.FAILED
            )
            throw e
        } catch (e: PgValidationException) {
            logger.warn("PG사 결제 실패 [${pgHistory.pgProvider}]: ${e.message}]")
            pgHistoryOutPort.updateHistory(
                id = pgHistory.id!!,
                status = PgRequestStatus.FAILED
            )
            throw e
        } catch (e: PgUnexpectedException) {
            logger.warn("PG사 결제 확인 불가 상태 확인 필요 [${pgHistory.pgProvider}]: ${e.message}]")
            throw e
        }

        // PG사 요청 내역 승인(상태: APPROVED)
        val updated = pgHistoryOutPort.updateHistory(
            id = pgHistory.id!!,
            status = PgRequestStatus.APPROVED
        )
        check(updated == 1) { "PG history out was not updated" }

        val feePolicy: FeePolicy? = feePolicyRepository.findEffectivePolicy(partner.id, LocalDateTime.now())
        require(feePolicy != null) { "Fee policy not found!" }

        val rate = feePolicy.percentage
        val fixed = feePolicy.fixedFee
        val (fee, net) = FeeCalculator.calculateFee(command.amount, rate, fixed)
        val payment = Payment(
            partnerId = partner.id,
            amount = command.amount,
            appliedFeeRate = rate,
            feeAmount = fee,
            netAmount = net,
            cardBin = command.cardBin,
            cardLast4 = command.cardLast4,
            approvalCode = approve.approvalCode,
            approvedAt = approve.approvedAt,
            status = PaymentStatus.APPROVED,
        )

        return paymentRepository.save(payment)
    }
}
