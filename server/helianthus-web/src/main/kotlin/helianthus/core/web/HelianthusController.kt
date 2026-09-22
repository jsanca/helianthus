package helianthus.core.web

import helianthus.core.HelianthusRuntime
import helianthus.core.result.ResultFrame
import helianthus.core.security.toPrincipal
import helianthus.core.util.PathHandler
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Dispatcher for catalog-backed operation endpoints under `/api/op/&#42;&#42;`.
 *
 * Parses the request path into `(operationId, configurationId, format)` via
 * [PathHandler], delegates to [HelianthusRuntime.executeOperation], and emits a
 * response in the requested format.
 */
@RestController
class HelianthusController(
    private val pathHandler: PathHandler,
    private val runtime: HelianthusRuntime
) {

    companion object {
        private val log = LoggerFactory.getLogger(HelianthusController::class.java)
    }

    /**
     * Handles `GET /api/op/&#42;&#42;`. Returns the operation's [ResultFrame] rendered
     * as JSON, HTML, CSV, or XML depending on the path's format extension.
     */
    @GetMapping(
        "/api/op/**",
        produces = [
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE,
            "text/csv",
            MediaType.APPLICATION_XML_VALUE
        ]
    )
    fun handle(request: HttpServletRequest): ResponseEntity<ResultFrame> {
        val startTime = System.currentTimeMillis()

        val pathResult = pathHandler.parsePath(request.servletPath)

        val format = pathResult.format ?: "json"
        val configurationId = pathResult.configurationId
                ?: PathHandler.DEFAULT_CONFIGURATION
        val operationId = pathResult.operationId ?:
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing operation id")

        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("Not authenticated")

        val principal = auth.toPrincipal()
        val username = principal.name

        log.debug(
            "Operation request: operationId={} configurationId={} format={} user={} roles={}",
            operationId, configurationId, format, username, principal.roles
        )

        val resultFrame = runtime.executeOperation(
            principal = principal,
            operationId = operationId,
            configurationId = configurationId,
            params = extractParams(request)
        )

        val duration = System.currentTimeMillis() - startTime
        log.info(
            "Operation executed: operationId={} configurationId={} format={} rowCount={} duration={}ms user={}",
            operationId, configurationId, format, resultFrame.metadata.rowCount, duration, username
        )

        val mediaType = when (format) {
            "json" -> MediaType.APPLICATION_JSON
            "html" -> MediaType.TEXT_HTML
            "csv"  -> MediaType.parseMediaType("text/csv")
            "xml"  -> MediaType.APPLICATION_XML
            else   -> throw ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Unsupported format: $format")
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .body(resultFrame)
    }

    private fun extractParams(request: HttpServletRequest): Map<String, String> =
            request.parameterNames.asSequence().associateWith {
                request.getParameter(it)
            }
}
