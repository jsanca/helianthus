package helianthus.core.web

import helianthus.core.exception.InvalidOperationPathException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class HelianthusExceptionHandler {

    private val log = LoggerFactory.getLogger(HelianthusExceptionHandler::class.java)

    @ExceptionHandler(InvalidOperationPathException::class)
    fun handleInvalidOperationPath(ex: InvalidOperationPathException): ResponseEntity<String> {
        log.warn("Invalid operation path: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message)
    }
}
