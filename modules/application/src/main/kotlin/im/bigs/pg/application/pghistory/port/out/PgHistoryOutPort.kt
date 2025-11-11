package im.bigs.pg.application.pghistory.port.out

import im.bigs.pg.domain.pghistory.PgHistory
import im.bigs.pg.domain.pghistory.PgRequestStatus

interface PgHistoryOutPort {
    fun save(pgHistory: PgHistory): PgHistory

    fun updateHistory(id: Long, status: PgRequestStatus): Int
}
