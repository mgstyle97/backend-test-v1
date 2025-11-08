package im.bigs.pg.api.config.filter

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.util.UUID

class LoggingFilter : Filter {
    private val log: Logger = LoggerFactory.getLogger(LoggingFilter::class.java)

    override fun doFilter(
        request: ServletRequest?,
        response: ServletResponse?,
        chain: FilterChain?
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        val wrappedRequest = ContentCachingRequestWrapper(httpRequest)
        val wrappedResponse = ContentCachingResponseWrapper(httpResponse)

        val traceId = UUID.randomUUID().toString()
        MDC.put("traceId", traceId)

        try {
            log.info("[Request] Method: {}, URI: {}, Query: {}", httpRequest.method, httpRequest.requestURI, httpRequest.queryString)

            chain?.doFilter(wrappedRequest, wrappedResponse)

            wrappedResponse.copyBodyToResponse()
        } finally {
            MDC.remove("traceId")
        }
    }
}
