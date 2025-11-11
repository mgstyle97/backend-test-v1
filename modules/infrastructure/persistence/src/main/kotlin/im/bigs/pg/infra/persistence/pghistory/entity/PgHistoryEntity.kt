package im.bigs.pg.infra.persistence.pghistory.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "pg_history")
class PgHistoryEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(length = 8)
    var cardBin: String? = null,
    @Column(length = 4)
    var cardLast4: String? = null,
    @Column(nullable = false, precision = 15, scale = 0)
    var amount: BigDecimal,
    @Column(nullable = false)
    var pgProvider: String,
    @Column(nullable = false, length = 20)
    var status: String,
    @Column(nullable = false)
    var createdAt: Instant,
    @Column(nullable = false)
    var updatedAt: Instant,
) {
    protected constructor() : this(
        id = null,
        amount = BigDecimal.ZERO,
        cardBin = null,
        cardLast4 = null,
        pgProvider = "",
        status = "",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
