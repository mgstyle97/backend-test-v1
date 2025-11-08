package im.bigs.pg.api.config.filter

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.slf4j.MDC
import java.util.UUID

class TraceIdFilter : Filter {
    override fun doFilter(
        request: ServletRequest?,
        response: ServletResponse?,
        chain: FilterChain?
    ) {
        val traceId = UUID.randomUUID().toString()

        MDC.put("traceId", traceId)

        try {
            chain?.doFilter(request, response)
        } finally {
            MDC.remove("traceId")
        }
    }
}
