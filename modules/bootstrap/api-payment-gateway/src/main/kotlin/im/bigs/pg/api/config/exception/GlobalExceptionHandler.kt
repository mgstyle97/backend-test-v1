package im.bigs.pg.api.config.exception

import im.bigs.pg.application.payment.exception.PaymentException
import im.bigs.pg.application.payment.exception.PgException
import im.bigs.pg.application.payment.exception.PgUnexpectedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(PaymentException::class)
    fun handlePaymentException(e: PaymentException): ResponseEntity<ErrorResponse> {
        log.warn("[Exception] Payment Exception: {}", e.message)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("PAYMENT_EXCEPTION", e.message))
    }

    @ExceptionHandler(PgUnexpectedException::class)
    fun handlePgUnexpectedException(e: PgUnexpectedException): ResponseEntity<ErrorResponse> {
        log.warn("[Exception] PgUnexpectedException: {}", e.message)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("PG_INTERNAL_SERVER_ERROR", e.message))
    }

    @ExceptionHandler(PgException::class)
    fun handlePgException(e: PgException): ResponseEntity<ErrorResponse> {
        log.warn("[Exception] PG Exception: {}", e.message)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("PG_EXCEPTION", e.message))
    }
}

data class ErrorResponse(
    val code: String,
    val message: String
)
