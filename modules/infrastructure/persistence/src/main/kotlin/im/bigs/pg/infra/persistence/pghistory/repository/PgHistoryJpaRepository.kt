package im.bigs.pg.infra.persistence.pghistory.repository

import im.bigs.pg.infra.persistence.pghistory.entity.PgHistoryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PgHistoryJpaRepository : JpaRepository<PgHistoryEntity, Long> {
    @Modifying
    @Query(
        """
        UPDATE PgHistoryEntity p
        SET p.status = :status
        WHERE p.id = :id
    """
    )
    fun updateStatus(
        @Param("id") id: Long,
        @Param("status")status: String
    ): Int
}
