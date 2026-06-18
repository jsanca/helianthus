package helianthus.core.web

import helianthus.core.NoMappingException
import helianthus.core.exception.InvalidOperationPathException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/**
 * Central exception handler for the Helianthus REST API.
 *
 * Translates platform exceptions into appropriate HTTP responses.
 * Returns plain text responses with the exception message for client errors
 * and a generic message for unexpected errors.
 */
@RestControllerAdvice
class HelianthusExceptionHandler {

    private val log = LoggerFactory.getLogger(HelianthusExceptionHandler::class.java)

    /**
     * Handles malformed operation request paths.
     *
     * @param ex the path exception
     * @return 400 Bad Request with the validation message
     */
    @ExceptionHandler(InvalidOperationPathException::class)
    fun handleInvalidOperationPath(ex: InvalidOperationPathException): ResponseEntity<String> {
        log.warn("Invalid operation path: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.TEXT_PLAIN)
            .body(ex.message)
    }

    /**
     * Handles operations that are not found in the catalog.
     *
     * @param ex the mapping exception
     * @return 404 Not Found with the identifier message
     */
    @ExceptionHandler(NoMappingException::class)
    fun handleNoMapping(ex: NoMappingException): ResponseEntity<String> {
        log.warn("Operation not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.TEXT_PLAIN)
            .body(ex.message)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<String> {
        log.warn("Access denied: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.TEXT_PLAIN)
            .body("Access denied")
    }

    /**
     * Catches all unexpected exceptions and returns a generic error response.
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with a generic message
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<String> {
        if (ex is ResponseStatusException) {
            return ResponseEntity.status(ex.statusCode)
                .contentType(MediaType.TEXT_PLAIN)
                .body(ex.reason)
        }
        log.error("Unexpected error: {}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.TEXT_PLAIN)
            .body("Internal server error")
    }
}
