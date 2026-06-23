package helianthus.core.web

import helianthus.core.EntityNotFoundException
import helianthus.core.InvalidParameterException
import helianthus.core.access.GenericDataAccess
import helianthus.core.access.SqlDialect
import helianthus.core.catalog.EntityCatalog
import helianthus.core.catalog.EntityCrudSqlBuilder
import helianthus.core.catalog.EntityDef
import helianthus.core.result.ResultFrame
import helianthus.core.result.ResultMetadata
import helianthus.core.security.EntityPermissionEvaluator
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

@RestController
class EntityCrudController(
    private val entityPathHandler: EntityPathHandler,
    private val entityCatalog: EntityCatalog,
    private val permissionEvaluator: EntityPermissionEvaluator,
    private val dataAccess: GenericDataAccess,
    private val dialects: Map<String, SqlDialect>
) {
    companion object {
        private val log = LoggerFactory.getLogger(EntityCrudController::class.java)
        private const val DEFAULT_LIMIT = 100
        private const val MAX_LIMIT = 1000
    }

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

        val username = auth.name

        if (!entityCatalog.entities.containsKey(entityName)) {
            throw EntityNotFoundException("Entity not found: $entityName")
        }

        val permitted = permissionEvaluator.checkReadPermission(auth, entityName)
        if (!permitted) {
            throw AccessDeniedException("Access denied to entity '$entityName'")
        }

        val entity = entityCatalog.resolveEntity(entityName)
        val dialect = dialects[entity.datasource]
            ?: throw IllegalStateException("No SQL dialect for datasource '${entity.datasource}'")

        val sqlBuilder = EntityCrudSqlBuilder(dialect)

        val resultFrame = if (pathResult.id != null) {
            handleGetById(entity, pathResult.id, sqlBuilder)
        } else {
            handleList(entity, request, sqlBuilder)
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

    private fun handleGetById(
        entity: EntityDef,
        id: String,
        sqlBuilder: EntityCrudSqlBuilder
    ): ResultFrame {
        val coercedId = coercePrimaryKey(id, entity)
        val plan = sqlBuilder.buildGetByIdSql(entity, coercedId)

        val stream = dataAccess.executeQueryStream(plan, entity.datasource, 1)
        val rows = stream.rows.toList()
        stream.close()

        if (rows.isEmpty()) {
            throw EntityNotFoundException("Entity '${entity.table}' with id '$id' not found")
        }

        return ResultFrame(
            schema = stream.schema,
            rows = rows,
            metadata = ResultMetadata(rowCount = 1)
        )
    }

    private fun handleList(
        entity: EntityDef,
        request: HttpServletRequest,
        sqlBuilder: EntityCrudSqlBuilder
    ): ResultFrame {
        val limit = parseLimit(request.getParameter("limit"))
        val offset = parseOffset(request.getParameter("offset"))
        val orderBy = request.getParameter("orderBy")
        val orderDir = parseOrderDir(request.getParameter("orderDir"))

        if (orderBy != null && orderBy !in entity.fields) {
            throw InvalidParameterException("orderBy column '$orderBy' is not in entity fields")
        }

        val filters = extractFilters(entity, request)
        val plan = sqlBuilder.buildListSql(entity, filters, orderBy, orderDir, limit, offset)

        val stream = dataAccess.executeQueryStream(plan, entity.datasource, limit)
        val rows = stream.rows.toList()
        stream.close()

        return ResultFrame(
            schema = stream.schema,
            rows = rows,
            metadata = ResultMetadata(rowCount = rows.size)
        )
    }

    private fun parseLimit(value: String?): Int {
        if (value == null) return DEFAULT_LIMIT
        val limit = value.toIntOrNull()
            ?: throw InvalidParameterException("limit must be an integer")
        if (limit <= 0) {
            throw InvalidParameterException("limit must be positive")
        }
        if (limit > MAX_LIMIT) {
            throw InvalidParameterException("limit must not exceed $MAX_LIMIT")
        }
        return limit
    }

    private fun parseOffset(value: String?): Int {
        if (value == null) return 0
        val offset = value.toIntOrNull()
            ?: throw InvalidParameterException("offset must be an integer")
        if (offset < 0) {
            throw InvalidParameterException("offset must be non-negative")
        }
        return offset
    }

    private fun parseOrderDir(value: String?): String {
        if (value == null) return "ASC"
        val dir = value.uppercase()
        if (dir != "ASC" && dir != "DESC") {
            throw InvalidParameterException("orderDir must be 'asc' or 'desc'")
        }
        return dir
    }

    private fun extractFilters(entity: EntityDef, request: HttpServletRequest): Map<String, Any?> {
        val filters = mutableMapOf<String, Any?>()
        val reservedParams = setOf("limit", "offset", "orderBy", "orderDir")

        request.parameterNames.asSequence()
            .filter { it !in reservedParams }
            .forEach { paramName ->
                if (paramName !in entity.fields) {
                    throw InvalidParameterException("Filter column '$paramName' is not in entity fields")
                }
                val value = request.getParameter(paramName)
                filters[paramName] = value
            }

        return filters
    }

    private fun coercePrimaryKey(id: String, entity: EntityDef): Any {
        id.toLongOrNull()?.let { return it }
        id.toDoubleOrNull()?.let { return it }
        if (id.equals("true", ignoreCase = true) || id.equals("false", ignoreCase = true)) {
            return id.toBoolean()
        }
        return id
    }
}
