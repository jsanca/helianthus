package helianthus.core.web

import helianthus.core.HelianthusRuntime
import helianthus.core.result.ResultFrame
import helianthus.core.security.toPrincipal
import helianthus.core.service.EntityListRequest
import helianthus.core.util.EntityPathHandler
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
 * Dispatcher for declarative entity endpoints under `/api/entities/&#42;&#42;`.
 *
 * Parses the request path into `(entityName, id?, format)` via
 * [EntityPathHandler] and delegates to [HelianthusRuntime.getEntity] or
 * [HelianthusRuntime.listEntities] depending on whether an id segment is
 * present.
 */
@RestController
class EntityCrudController(
    private val entityPathHandler: EntityPathHandler,
    private val runtime: HelianthusRuntime
) {
    companion object {
        private val log = LoggerFactory.getLogger(EntityCrudController::class.java)

        /** Query parameters reserved for pagination/ordering; everything else is treated as a filter. */
        private val RESERVED_PARAMS = setOf("limit", "offset", "orderBy", "orderDir")
    }

    /**
     * Handles `GET /api/entities/&#42;&#42;`. Returns the list/get result as JSON,
     * HTML, CSV, or XML based on the path's format extension.
     */
    @GetMapping(
        "/api/entities/**",
        produces = [
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE,
            "text/csv",
            MediaType.APPLICATION_XML_VALUE
        ]
    )
    fun handle(request: HttpServletRequest): ResponseEntity<ResultFrame> {
        val startTime = System.currentTimeMillis()
        val pathResult = entityPathHandler.parsePath(request.servletPath)
        val format = pathResult.format
        val entityName = pathResult.entityName

        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("Not authenticated")

        val principal = auth.toPrincipal()
        val username = principal.name

        val id = pathResult.id
        val resultFrame = if (id != null) {
            runtime.getEntity(principal, entityName, id)
        } else {
            runtime.listEntities(principal, extractListRequest(entityName, request))
        }

        val duration = System.currentTimeMillis() - startTime
        log.info(
            "Entity operation executed: entity={} format={} rowCount={} duration={}ms user={}",
            entityName, format, resultFrame.metadata.rowCount, duration, username
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

    private fun extractListRequest(entityName: String, request: HttpServletRequest): EntityListRequest {
        val filters = request.parameterNames.asSequence()
            .filter { it !in RESERVED_PARAMS }
            .associateWith { request.getParameter(it) }

        return EntityListRequest(
            entityName = entityName,
            filters = filters,
            orderBy = request.getParameter("orderBy"),
            orderDir = request.getParameter("orderDir"),
            limit = request.getParameter("limit"),
            offset = request.getParameter("offset")
        )
    }
}
