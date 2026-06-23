package helianthus.core.catalog

import helianthus.core.EntityNotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EntityCatalogTest {

    private val datasources = mapOf(
        "default" to DatasourceDef("postgres"),
        "secondary" to DatasourceDef("postgres")
    )

    private fun createEntity(
        datasource: String = "default",
        table: String = "products",
        primaryKey: PrimaryKeyDef = PrimaryKeyDef("id"),
        fields: List<String> = listOf("id", "name")
    ) = EntityDef(
        datasource = datasource,
        table = table,
        primaryKey = primaryKey,
        fields = fields
    )

    @Test
    fun `resolveEntity should return entity when it exists`() {
        val entity = createEntity()
        val catalog = EntityCatalog(mapOf("products" to entity))

        val resolved = catalog.resolveEntity("products")

        assertEquals(entity, resolved)
    }

    @Test
    fun `resolveEntity should throw EntityNotFoundException when entity does not exist`() {
        val catalog = EntityCatalog(emptyMap())

        val exception = assertThrows<EntityNotFoundException> {
            catalog.resolveEntity("nonexistent")
        }

        assertEquals("Entity not found: nonexistent", exception.message)
    }

    @Test
    fun `validate should pass for valid entity`() {
        val entity = createEntity()
        val catalog = EntityCatalog(mapOf("products" to entity))

        catalog.validate(datasources)
    }

    @Test
    fun `validate should fail when entity has no fields`() {
        val entity = createEntity(fields = emptyList())
        val catalog = EntityCatalog(mapOf("products" to entity))

        val exception = assertThrows<IllegalStateException> {
            catalog.validate(datasources)
        }

        assertEquals("Entity 'products' must have at least one field", exception.message)
    }

    @Test
    fun `validate should fail when entity has no primary key`() {
        val entity = createEntity(primaryKey = PrimaryKeyDef(emptyList()))
        val catalog = EntityCatalog(mapOf("products" to entity))

        val exception = assertThrows<IllegalStateException> {
            catalog.validate(datasources)
        }

        assertEquals("Entity 'products' must have a primary key", exception.message)
    }

    @Test
    fun `validate should fail when primary key column is not in fields`() {
        val entity = createEntity(
            primaryKey = PrimaryKeyDef("id"),
            fields = listOf("name", "description")
        )
        val catalog = EntityCatalog(mapOf("products" to entity))

        val exception = assertThrows<IllegalStateException> {
            catalog.validate(datasources)
        }

        assertEquals(
            "Entity 'products' primary key column 'id' is not in fields list [name, description]",
            exception.message
        )
    }

    @Test
    fun `validate should fail when datasource does not exist`() {
        val entity = createEntity(datasource = "nonexistent")
        val catalog = EntityCatalog(mapOf("products" to entity))

        val exception = assertThrows<IllegalStateException> {
            catalog.validate(datasources)
        }

        assertEquals(
            "Entity 'products' references unknown datasource 'nonexistent'. Available datasources: [default, secondary]",
            exception.message
        )
    }

    @Test
    fun `validate should pass for composite primary key when all columns are in fields`() {
        val entity = createEntity(
            primaryKey = PrimaryKeyDef(listOf("orderId", "lineNumber")),
            fields = listOf("orderId", "lineNumber", "product")
        )
        val catalog = EntityCatalog(mapOf("orderlines" to entity))

        catalog.validate(datasources)
    }

    @Test
    fun `validate should fail for composite primary key when one column is not in fields`() {
        val entity = createEntity(
            primaryKey = PrimaryKeyDef(listOf("orderId", "lineNumber")),
            fields = listOf("orderId", "product")
        )
        val catalog = EntityCatalog(mapOf("orderlines" to entity))

        val exception = assertThrows<IllegalStateException> {
            catalog.validate(datasources)
        }

        assertEquals(
            "Entity 'orderlines' primary key column 'lineNumber' is not in fields list [orderId, product]",
            exception.message
        )
    }

    @Test
    fun `validate should pass for entity with secondary datasource`() {
        val entity = createEntity(datasource = "secondary")
        val catalog = EntityCatalog(mapOf("customers" to entity))

        catalog.validate(datasources)
    }

    @Test
    fun `validate should validate all entities in catalog`() {
        val validEntity = createEntity()
        val invalidEntity = createEntity(fields = emptyList())
        val catalog = EntityCatalog(mapOf(
            "products" to validEntity,
            "customers" to invalidEntity
        ))

        val exception = assertThrows<IllegalStateException> {
            catalog.validate(datasources)
        }

        assertEquals("Entity 'customers' must have at least one field", exception.message)
    }
}
