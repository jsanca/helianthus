package helianthus.core.service

import helianthus.core.EntityNotFoundException
import helianthus.core.InvalidParameterException
import helianthus.core.access.GenericDataAccess
import helianthus.core.access.SqlDialect
import helianthus.core.catalog.EntityCatalog
import helianthus.core.catalog.EntityCrudSqlBuilder
import helianthus.core.catalog.EntityDef
import helianthus.core.result.ResultFrame
import helianthus.core.result.ResultMetadata
import helianthus.core.security.AccessDeniedException
import helianthus.core.security.EntityPermissionEvaluator
import helianthus.core.security.Principal

/**
 * Framework-neutral entity execution: authorization, resolution, SQL generation,
 * query execution, and row materialization.
 *
 * Owns the entity use cases previously implemented inside the HTTP controller.
 */
internal class EntityService(
    private val entityCatalog: EntityCatalog,
    private val permissionEvaluator: EntityPermissionEvaluator,
    private val dataAccess: GenericDataAccess,
    private val dialects: Map<String, SqlDialect>
) {

    /**
     * Lists entities matching the given filters, ordering, and pagination.
     *
     * @throws EntityNotFoundException if the entity does not exist
     * @throws AccessDeniedException if the caller is not permitted to read the entity
     * @throws InvalidParameterException if any query input is invalid
     */
    fun listEntities(principal: Principal, request: EntityListRequest): ResultFrame {
        val entity = authorizeAndResolve(principal, request.entityName)
        val sqlBuilder = EntityCrudSqlBuilder(dialectFor(entity))

        val limit = parseLimit(request.limit)
        val offset = parseOffset(request.offset)
        val orderBy = request.orderBy
        val orderDir = parseOrderDir(request.orderDir)

        if (orderBy != null && entity.fieldByName(orderBy) == null) {
            throw InvalidParameterException("orderBy column '$orderBy' is not in entity fields")
        }

        val filters = request.filters
        filters.keys.forEach { paramName ->
            if (entity.fieldByName(paramName) == null) {
                throw InvalidParameterException("Filter column '$paramName' is not in entity fields")
            }
        }

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

    /**
     * Retrieves a single entity by primary key.
     *
     * @throws EntityNotFoundException if the entity or the row does not exist
     * @throws AccessDeniedException if the caller is not permitted to read the entity
     */
    fun getEntity(principal: Principal, entityName: String, id: String): ResultFrame {
        val entity = authorizeAndResolve(principal, entityName)
        val sqlBuilder = EntityCrudSqlBuilder(dialectFor(entity))

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

    private fun authorizeAndResolve(principal: Principal, entityName: String): EntityDef {
        if (!entityCatalog.entities.containsKey(entityName)) {
            throw EntityNotFoundException("Entity not found: $entityName")
        }
        if (!permissionEvaluator.checkReadPermission(principal, entityName)) {
            throw AccessDeniedException("Access denied to entity '$entityName'")
        }
        return entityCatalog.resolveEntity(entityName)
    }

    private fun dialectFor(entity: EntityDef): SqlDialect =
        dialects[entity.datasource]
            ?: throw IllegalStateException("No SQL dialect for datasource '${entity.datasource}'")

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

    private fun coercePrimaryKey(id: String, entity: EntityDef): Any {
        id.toLongOrNull()?.let { return it }
        id.toDoubleOrNull()?.let { return it }
        if (id.equals("true", ignoreCase = true) || id.equals("false", ignoreCase = true)) {
            return id.toBoolean()
        }
        return id
    }

    companion object {
        private const val DEFAULT_LIMIT = 100
        private const val MAX_LIMIT = 1000
    }
}

/**
 * Structured input for an entity list request, free of any HTTP/transport types.
 *
 * Numeric values ([limit], [offset]) are kept as strings so the service layer
 * can perform input validation before coercion.
 */
data class EntityListRequest(
    val entityName: String,
    val filters: Map<String, String> = emptyMap(),
    val orderBy: String? = null,
    val orderDir: String? = null,
    val limit: String? = null,
    val offset: String? = null
)
