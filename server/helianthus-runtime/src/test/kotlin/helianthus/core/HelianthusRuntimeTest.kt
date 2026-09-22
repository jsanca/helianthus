package helianthus.core

import helianthus.core.access.GenericDataAccess
import helianthus.core.access.SqlDialect
import helianthus.core.access.SqlExecutionPlan
import helianthus.core.catalog.ConfigurationDef
import helianthus.core.catalog.EntityCatalog
import helianthus.core.catalog.EntityDef
import helianthus.core.catalog.EntityRoleDef
import helianthus.core.catalog.EntitySecurityDef
import helianthus.core.catalog.FieldDef
import helianthus.core.catalog.OperationCatalog
import helianthus.core.catalog.OperationDef
import helianthus.core.catalog.PrimaryKeyDef
import helianthus.core.catalog.SecurityDef
import helianthus.core.pipeline.PipelineFactory
import helianthus.core.result.CloseableRowStream
import helianthus.core.result.DefaultRowStream
import helianthus.core.result.ResultColumn
import helianthus.core.result.ResultSchema
import helianthus.core.result.ResultType
import helianthus.core.security.AccessDeniedException
import helianthus.core.security.EntityPermissionEvaluator
import helianthus.core.security.OperationPermissionEvaluator
import helianthus.core.security.Principal
import helianthus.core.service.EntityListRequest
import helianthus.core.service.EntityService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * Unit tests for [HelianthusRuntime] using a hand-built [OperationCatalog] and
 * a [FakeDataAccess] that returns a fixed result set.
 *
 * Covers operation execution authorization (allow/deny/admin), entity list/get
 * delegation, and unknown-operation lookup.
 */
class HelianthusRuntimeTest {

    private val schema = ResultSchema(listOf(ResultColumn("id", ResultType.INTEGER)))

    private val operationCatalog = OperationCatalog(
        operations = mapOf(
            "all" to OperationDef(
                query = "SELECT * FROM t",
                configurations = mapOf("default" to ConfigurationDef())
            ),
            "admin-only" to OperationDef(
                query = "SELECT * FROM t",
                security = SecurityDef(listOf("ADMIN")),
                configurations = mapOf("default" to ConfigurationDef())
            )
        )
    )

    private val entityCatalog = EntityCatalog(
        mapOf(
            "products" to EntityDef(
                datasource = "default",
                table = "products",
                primaryKey = PrimaryKeyDef("id"),
                fields = listOf(FieldDef("id")),
                security = EntitySecurityDef(read = EntityRoleDef(listOf("GUEST")))
            )
        )
    )

    private class FakeDataAccess : GenericDataAccess {
        var response: CloseableRowStream = DefaultRowStream(
            ResultSchema(listOf(ResultColumn("id", ResultType.INTEGER))),
            sequenceOf(mapOf("id" to 1))
        )

        override fun executeQueryStream(
            plan: SqlExecutionPlan,
            dataSource: String,
            fetchSize: Int
        ): CloseableRowStream = response
    }

    private fun runtime(): HelianthusRuntime {
        val dataAccess = FakeDataAccess()
        val entityService = EntityService(
            entityCatalog = entityCatalog,
            permissionEvaluator = EntityPermissionEvaluator(entityCatalog),
            dataAccess = dataAccess,
            dialects = mapOf("default" to testDialect())
        )
        return HelianthusRuntime(
            operationCatalog = operationCatalog,
            operationPermissionEvaluator = OperationPermissionEvaluator(operationCatalog),
            pipelineFactory = PipelineFactory(operationCatalog, dataAccess),
            entityService = entityService,
            entityCatalog = entityCatalog,
            entityPermissionEvaluator = EntityPermissionEvaluator(entityCatalog)
        )
    }

    private fun testDialect(): SqlDialect = object : SqlDialect {
        override fun quoteIdentifier(name: String): String = "\"$name\""
        override fun limitOffset(limit: Int, offset: Int): String = "LIMIT $limit OFFSET $offset"
    }

    private fun principal(name: String, vararg roles: String): Principal =
        Principal(name, roles.toSet())

    @Test
    fun `executeOperation returns result frame for authorized caller`() {
        val result = runtime().executeOperation(principal("guest", "GUEST"), "all")

        assertEquals(1, result.metadata.rowCount)
        assertEquals(1, result.rows.size)
    }

    @Test
    fun `executeOperation throws NoMappingException for unknown operation`() {
        assertThrows<NoMappingException> {
            runtime().executeOperation(principal("guest", "GUEST"), "unknown")
        }
    }

    @Test
    fun `executeOperation throws AccessDeniedException for insufficient role`() {
        assertThrows<AccessDeniedException> {
            runtime().executeOperation(principal("guest", "GUEST"), "admin-only")
        }
    }

    @Test
    fun `executeOperation allows admin to bypass roles`() {
        val result = runtime().executeOperation(principal("admin", "ADMIN"), "admin-only")
        assertEquals(1, result.metadata.rowCount)
    }

    @Test
    fun `listEntities delegates to entity service`() {
        val result = runtime().listEntities(
            principal("guest", "GUEST"),
            EntityListRequest(entityName = "products")
        )
        assertEquals(1, result.metadata.rowCount)
    }

    @Test
    fun `getEntity delegates to entity service`() {
        val result = runtime().getEntity(principal("guest", "GUEST"), "products", "1")
        assertEquals(1, result.metadata.rowCount)
    }
}
