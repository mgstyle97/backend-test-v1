package im.bigs.pg.infra.persistence.pghistory.adapter

import im.bigs.pg.application.pghistory.port.out.PgHistoryOutPort
import im.bigs.pg.domain.pghistory.PgHistory
import im.bigs.pg.domain.pghistory.PgRequestStatus
import im.bigs.pg.infra.persistence.pghistory.entity.PgHistoryEntity
import im.bigs.pg.infra.persistence.pghistory.repository.PgHistoryJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset

@Component
class PgHistoryPersistenceAdapter(
    private val repo: PgHistoryJpaRepository
) : PgHistoryOutPort {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun save(pgHistory: PgHistory): PgHistory =
        repo.save(pgHistory.toEntity()).toDomain()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun updateHistory(id: Long, status: PgRequestStatus): Int =
        repo.updateStatus(id, status.name)

    private fun PgHistory.toEntity() =
        PgHistoryEntity(
            id = this.id,
            cardBin = this.cardBin,
            cardLast4 = this.cardLast4,
            amount = this.amount,
            pgProvider = this.pgProvider,
            status = this.status.name,
            createdAt = this.createdAt.toInstant(ZoneOffset.UTC),
            updatedAt = this.updatedAt.toInstant(ZoneOffset.UTC),
        )

    private fun PgHistoryEntity.toDomain() =
        PgHistory(
            id = this.id,
            cardBin = this.cardBin,
            cardLast4 = this.cardLast4,
            amount = this.amount,
            pgProvider = this.pgProvider,
            status = PgRequestStatus.valueOf(this.status),
            createdAt = java.time.LocalDateTime.ofInstant(this.createdAt, ZoneOffset.UTC),
            updatedAt = java.time.LocalDateTime.ofInstant(this.updatedAt, ZoneOffset.UTC),
        )
}
