package helianthus.core.service

import helianthus.core.EntityNotFoundException
import helianthus.core.InvalidParameterException
import helianthus.core.access.GenericDataAccess
import helianthus.core.access.SqlDialect
import helianthus.core.access.SqlExecutionPlan
import helianthus.core.catalog.EntityCatalog
import helianthus.core.catalog.EntityDef
import helianthus.core.catalog.EntityRoleDef
import helianthus.core.catalog.EntitySecurityDef
import helianthus.core.catalog.FieldDef
import helianthus.core.catalog.PrimaryKeyDef
import helianthus.core.result.CloseableRowStream
import helianthus.core.result.DefaultRowStream
import helianthus.core.result.ResultColumn
import helianthus.core.result.ResultSchema
import helianthus.core.result.ResultType
import helianthus.core.security.AccessDeniedException
import helianthus.core.security.EntityPermissionEvaluator
import helianthus.core.security.Principal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [EntityService] against an in-memory data access layer, covering
 * authorization, pagination/ordering validation, filter-column validation,
 * and primary-key coercion.
 */
class EntityServiceTest {

    private val dialect: SqlDialect = object : SqlDialect {
        override fun quoteIdentifier(name: String): String = "\"$name\""
        override fun limitOffset(limit: Int, offset: Int): String = "LIMIT $limit OFFSET $offset"
    }

    private val schema = ResultSchema(
        listOf(
            ResultColumn("id", ResultType.INTEGER),
            ResultColumn("name", ResultType.STRING),
            ResultColumn("price", ResultType.DECIMAL)
        )
    )

    private fun productsEntity(): EntityDef = EntityDef(
        datasource = "default",
        table = "products",
        primaryKey = PrimaryKeyDef("id"),
        fields = listOf(FieldDef("id"), FieldDef("name"), FieldDef("price")),
        security = EntitySecurityDef(read = EntityRoleDef(listOf("GUEST")))
    )

    private fun customersEntity(): EntityDef = EntityDef(
        datasource = "default",
        table = "customers",
        primaryKey = PrimaryKeyDef("id"),
        fields = listOf(FieldDef("id"), FieldDef("name")),
        security = EntitySecurityDef(read = EntityRoleDef(listOf("ADMIN")))
    )

    private fun catalog(): EntityCatalog = EntityCatalog(
        mapOf(
            "products" to productsEntity(),
            "customers" to customersEntity()
        )
    )

    private class FakeDataAccess : GenericDataAccess {
        var response: CloseableRowStream = DefaultRowStream(
            ResultSchema(emptyList()),
            emptySequence()
        )
        val plans = mutableListOf<SqlExecutionPlan>()

        override fun executeQueryStream(
            plan: SqlExecutionPlan,
            dataSource: String,
            fetchSize: Int
        ): CloseableRowStream {
            plans.add(plan)
            return response
        }
    }

    private fun service(
        dataAccess: GenericDataAccess,
        catalog: EntityCatalog = catalog()
    ): EntityService = EntityService(
        entityCatalog = catalog,
        permissionEvaluator = EntityPermissionEvaluator(catalog),
        dataAccess = dataAccess,
        dialects = mapOf("default" to dialect)
    )

    private fun streamOf(vararg rows: Map<String, Any?>): CloseableRowStream =
        DefaultRowStream(schema, rows.asList().asSequence())

    private fun principal(name: String, vararg roles: String): Principal =
        Principal(name, roles.toSet())

    @Test
    fun `list returns all rows with rowCount`() {
        val fake = FakeDataAccess().apply {
            response = streamOf(
                mapOf("id" to 1, "name" to "Widget", "price" to 10.0),
                mapOf("id" to 2, "name" to "Gadget", "price" to 20.0)
            )
        }
        val result = service(fake).listEntities(
            principal("guest", "GUEST"),
            EntityListRequest(entityName = "products")
        )

        assertEquals(2, result.metadata.rowCount)
        assertEquals(2, result.rows.size)
    }

    @Test
    fun `list passes filters and pagination into the execution plan`() {
        val fake = FakeDataAccess().apply { response = streamOf() }
        service(fake).listEntities(
            principal("guest", "GUEST"),
            EntityListRequest(
                entityName = "products",
                filters = mapOf("name" to "Widget"),
                orderBy = "id",
                orderDir = "desc",
                limit = "5",
                offset = "10"
            )
        )

        val plan = fake.plans.single()
        assertTrue(plan.sql.contains("WHERE"))
        assertTrue(plan.sql.contains("ORDER BY"))
        assertTrue(plan.sql.contains("LIMIT 5"))
        assertTrue(plan.sql.contains("OFFSET 10"))
        assertEquals(1, plan.params.size)
    }

    @Test
    fun `getEntity returns single row`() {
        val fake = FakeDataAccess().apply {
            response = streamOf(mapOf("id" to 1, "name" to "Widget", "price" to 10.0))
        }
        val result = service(fake).getEntity(principal("guest", "GUEST"), "products", "1")

        assertEquals(1, result.metadata.rowCount)
        assertEquals("Widget", result.rows[0]["name"])
    }

    @Test
    fun `getEntity throws when row missing`() {
        val fake = FakeDataAccess().apply { response = streamOf() }

        assertThrows<EntityNotFoundException> {
            service(fake).getEntity(principal("guest", "GUEST"), "products", "999")
        }
    }

    @Test
    fun `unknown entity throws EntityNotFoundException`() {
        val fake = FakeDataAccess()
        assertThrows<EntityNotFoundException> {
            service(fake).listEntities(
                principal("guest", "GUEST"),
                EntityListRequest(entityName = "nonexistent")
            )
        }
    }

    @Test
    fun `permission denied throws AccessDeniedException`() {
        val fake = FakeDataAccess().apply { response = streamOf() }
        assertThrows<AccessDeniedException> {
            service(fake).listEntities(
                principal("guest", "GUEST"),
                EntityListRequest(entityName = "customers")
            )
        }
    }

    @Test
    fun `admin bypasses entity read roles`() {
        val fake = FakeDataAccess().apply {
            response = streamOf(mapOf("id" to 1, "name" to "Acme"))
        }
        val result = service(fake).listEntities(
            principal("admin", "ADMIN"),
            EntityListRequest(entityName = "customers")
        )
        assertEquals(1, result.metadata.rowCount)
    }

    @Test
    fun `invalid limit throws InvalidParameterException`() {
        val fake = FakeDataAccess()
        assertThrows<InvalidParameterException> {
            service(fake).listEntities(
                principal("guest", "GUEST"),
                EntityListRequest(entityName = "products", limit = "abc")
            )
        }
    }

    @Test
    fun `unknown filter column throws InvalidParameterException`() {
        val fake = FakeDataAccess()
        val ex = assertThrows<InvalidParameterException> {
            service(fake).listEntities(
                principal("guest", "GUEST"),
                EntityListRequest(entityName = "products", filters = mapOf("INVALID" to "x"))
            )
        }
        assertEquals("Filter column 'INVALID' is not in entity fields", ex.message)
    }

    @Test
    fun `unknown orderBy column throws InvalidParameterException`() {
        val fake = FakeDataAccess()
        val ex = assertThrows<InvalidParameterException> {
            service(fake).listEntities(
                principal("guest", "GUEST"),
                EntityListRequest(entityName = "products", orderBy = "INVALID")
            )
        }
        assertEquals("orderBy column 'INVALID' is not in entity fields", ex.message)
    }
}
