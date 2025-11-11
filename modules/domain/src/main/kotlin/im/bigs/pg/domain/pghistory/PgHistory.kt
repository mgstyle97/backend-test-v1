package im.bigs.pg.domain.pghistory

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDateTime

data class PgHistory(
    val id: Long? = null,
    val amount: BigDecimal,
    val cardBin: String? = null,
    val cardLast4: String? = null,
    val pgProvider: String,
    val status: PgRequestStatus,
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

enum class PgRequestStatus {
    PENDING,
    APPROVED,
    FAILED,
}
