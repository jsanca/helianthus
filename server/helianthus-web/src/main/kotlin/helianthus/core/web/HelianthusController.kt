package helianthus.core.web

import helianthus.core.NoMappingException
import helianthus.core.catalog.OperationCatalog
import helianthus.core.pipeline.OperationRequest
import helianthus.core.pipeline.PipelineContext
import helianthus.core.pipeline.PipelineFactory
import helianthus.core.result.ResultFrame
import helianthus.core.security.OperationPermissionEvaluator
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

@RestController
class HelianthusController(
    private val pathHandler: PathHandler,
    private val catalog: OperationCatalog,
    private val pipelineFactory: PipelineFactory,
    private val permissionEvaluator: OperationPermissionEvaluator
) {

    companion object {
        private val log = LoggerFactory.getLogger(HelianthusController::class.java)
    }

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

        val username = auth.name
        val roles = auth.authorities.map { it.authority }

        log.debug(
            "Operation request: operationId={} configurationId={} format={} user={} roles={}",
            operationId, configurationId, format, username, roles
        )

        // Check operation exists before checking permissions (404 vs 403)
        if (!catalog.operations.containsKey(operationId)) {
            throw NoMappingException("Operation not found: $operationId")
        }

        val permitted = permissionEvaluator.checkPermission(auth, operationId, configurationId)
        log.debug(
            "Permission check: operationId={} configurationId={} user={} permitted={}",
            operationId, configurationId, username, permitted
        )
        
        if (!permitted) {
            throw AccessDeniedException(
                "Access denied to operation '$operationId' configuration '$configurationId'"
            )
        }

        val operationRequest = OperationRequest(
            operationId = operationId,
            configurationId = configurationId,
            format = format,
            params = extractParams(request)
        )

        log.debug(
            "Executing pipeline: operationId={} configurationId={} format={} paramCount={}",
            operationId, configurationId, format, operationRequest.params.size
        )

        val pipeline = pipelineFactory.createPipeline(operationRequest)
        val context = PipelineContext(operationRequest)
        val result = pipeline.execute(context)

        if (result.error != null) {
            throw result.error!!
        }

        val resultFrame = result.resultFrame
                ?: throw IllegalStateException("Pipeline produced no result")

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
