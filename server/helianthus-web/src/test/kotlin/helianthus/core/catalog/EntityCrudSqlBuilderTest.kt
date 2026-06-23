package helianthus.core.catalog

import helianthus.core.access.impl.db.PostgresDialect
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityCrudSqlBuilderTest {

    private val dialect = PostgresDialect()
    private val builder = EntityCrudSqlBuilder(dialect)

    private fun createEntity(
        table: String = "products",
        primaryKey: PrimaryKeyDef = PrimaryKeyDef("id"),
        fields: List<String> = listOf("id", "name", "price")
    ) = EntityDef(
        datasource = "default",
        table = table,
        primaryKey = primaryKey,
        fields = fields
    )

    @Test
    fun `buildListSql should generate basic select without filters`() {
        val entity = createEntity()

        val plan = builder.buildListSql(
            entity = entity,
            filters = emptyMap(),
            orderBy = null,
            orderDir = "ASC",
            limit = 100,
            offset = 0
        )

        assertTrue(plan.sql.contains("SELECT"))
        assertTrue(plan.sql.contains("\"id\""))
        assertTrue(plan.sql.contains("\"name\""))
        assertTrue(plan.sql.contains("\"price\""))
        assertTrue(plan.sql.contains("FROM \"products\""))
        assertTrue(plan.sql.contains("LIMIT 100"))
        assertTrue(plan.sql.contains("OFFSET 0"))
        assertEquals(0, plan.params.size)
    }

    @Test
    fun `buildListSql should generate select with filters`() {
        val entity = createEntity()

        val plan = builder.buildListSql(
            entity = entity,
            filters = mapOf("name" to "Widget"),
            orderBy = null,
            orderDir = "ASC",
            limit = 100,
            offset = 0
        )

        assertTrue(plan.sql.contains("WHERE"))
        assertTrue(plan.sql.contains("\"name\" = ?"))
        assertEquals(1, plan.params.size)
        assertEquals("Widget", plan.params[0].value)
    }

    @Test
    fun `buildListSql should generate select with multiple filters`() {
        val entity = createEntity()

        val plan = builder.buildListSql(
            entity = entity,
            filters = mapOf("name" to "Widget", "price" to 50.0),
            orderBy = null,
            orderDir = "ASC",
            limit = 100,
            offset = 0
        )

        assertTrue(plan.sql.contains("WHERE"))
        assertTrue(plan.sql.contains("\"name\" = ?"))
        assertTrue(plan.sql.contains("\"price\" = ?"))
        assertTrue(plan.sql.contains("AND"))
        assertEquals(2, plan.params.size)
    }

    @Test
    fun `buildListSql should generate select with orderBy`() {
        val entity = createEntity()

        val plan = builder.buildListSql(
            entity = entity,
            filters = emptyMap(),
            orderBy = "name",
            orderDir = "DESC",
            limit = 100,
            offset = 0
        )

        assertTrue(plan.sql.contains("ORDER BY \"name\" DESC"))
    }

    @Test
    fun `buildListSql should ignore orderBy if not in fields`() {
        val entity = createEntity()

        val plan = builder.buildListSql(
            entity = entity,
            filters = emptyMap(),
            orderBy = "nonexistent",
            orderDir = "ASC",
            limit = 100,
            offset = 0
        )

        assertTrue(!plan.sql.contains("ORDER BY"))
    }

    @Test
    fun `buildListSql should ignore filters not in fields`() {
        val entity = createEntity()

        val plan = builder.buildListSql(
            entity = entity,
            filters = mapOf("nonexistent" to "value"),
            orderBy = null,
            orderDir = "ASC",
            limit = 100,
            offset = 0
        )

        assertTrue(!plan.sql.contains("WHERE"))
        assertEquals(0, plan.params.size)
    }

    @Test
    fun `buildListSql should generate select with limit and offset`() {
        val entity = createEntity()

        val plan = builder.buildListSql(
            entity = entity,
            filters = emptyMap(),
            orderBy = null,
            orderDir = "ASC",
            limit = 50,
            offset = 100
        )

        assertTrue(plan.sql.contains("LIMIT 50"))
        assertTrue(plan.sql.contains("OFFSET 100"))
    }

    @Test
    fun `buildGetByIdSql should generate select with primary key filter`() {
        val entity = createEntity()

        val plan = builder.buildGetByIdSql(entity, "123")

        assertTrue(plan.sql.contains("SELECT"))
        assertTrue(plan.sql.contains("FROM \"products\""))
        assertTrue(plan.sql.contains("WHERE \"id\" = ?"))
        assertEquals(1, plan.params.size)
        assertEquals("123", plan.params[0].value)
    }

    @Test
    fun `buildGetByIdSql should handle numeric id`() {
        val entity = createEntity()

        val plan = builder.buildGetByIdSql(entity, 456L)

        assertEquals(1, plan.params.size)
        assertEquals(456L, plan.params[0].value)
    }

    @Test
    fun `buildGetByIdSql should use first column of composite primary key`() {
        val entity = createEntity(
            primaryKey = PrimaryKeyDef(listOf("orderId", "lineNumber")),
            fields = listOf("orderId", "lineNumber", "product")
        )

        val plan = builder.buildGetByIdSql(entity, "100")

        assertTrue(plan.sql.contains("WHERE \"orderId\" = ?"))
        assertEquals(1, plan.params.size)
    }

    @Test
    fun `buildListSql should quote all identifiers`() {
        val entity = createEntity(
            table = "my-table",
            fields = listOf("my-id", "my-name")
        )

        val plan = builder.buildListSql(
            entity = entity,
            filters = mapOf("my-name" to "test"),
            orderBy = "my-id",
            orderDir = "ASC",
            limit = 10,
            offset = 0
        )

        assertTrue(plan.sql.contains("\"my-table\""))
        assertTrue(plan.sql.contains("\"my-id\""))
        assertTrue(plan.sql.contains("\"my-name\""))
    }

    @Test
    fun `buildListSql should handle empty entity fields gracefully`() {
        val entity = createEntity(fields = emptyList())

        val plan = builder.buildListSql(
            entity = entity,
            filters = emptyMap(),
            orderBy = null,
            orderDir = "ASC",
            limit = 100,
            offset = 0
        )

        assertTrue(plan.sql.contains("SELECT"))
        assertTrue(plan.sql.contains("FROM \"products\""))
    }
}
