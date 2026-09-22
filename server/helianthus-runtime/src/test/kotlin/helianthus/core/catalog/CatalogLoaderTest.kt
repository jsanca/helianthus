package helianthus.core.catalog

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises [CatalogLoader] against valid and invalid YAML inputs covering the
 * full catalog shape (app metadata, datasources, queries, operations,
 * entities, security) and verifies the parsed in-memory representation.
 */
class CatalogLoaderTest {

    private fun load(yaml: String): LoadedCatalog =
        CatalogLoader().load(ByteArrayInputStream(yaml.toByteArray()))

    private val validCatalog = """
        app:
          name: Test App
        datasources:
          default:
            type: h2
        queries:
          products.base:
            datasource: default
            sql: SELECT * FROM products
        operations:
          all-products:
            query: SELECT * FROM products
            configurations:
              default:
                pipeline:
                  - limit: 10
          secured:
            query: SELECT * FROM products
            security:
              roles: [ADMIN]
            configurations:
              admin-only:
                security:
                  roles: [ADMIN]
                pipeline:
                  - limit: 5
        entities:
          products:
            datasource: default
            table: products
            primaryKey: id
            fields:
              - id
              - name
            security:
              read:
                roles: [GUEST, ADMIN]
    """.trimIndent()

    @Test
    fun `loads a valid catalog into operation and entity catalogs`() {
        val loaded = load(validCatalog)

        assertNotNull(loaded.operationCatalog)
        assertNotNull(loaded.entityCatalog)
    }

    @Test
    fun `parses app metadata and datasources`() {
        val catalog = load(validCatalog).operationCatalog

        assertEquals("Test App", catalog.app?.name)
        assertEquals("h2", catalog.datasources["default"]?.type)
    }

    @Test
    fun `parses queries`() {
        val catalog = load(validCatalog).operationCatalog

        assertEquals("SELECT * FROM products", catalog.queries["products.base"]?.sql)
        assertEquals("default", catalog.queries["products.base"]?.datasource)
    }

    @Test
    fun `parses operation security and configuration security`() {
        val catalog = load(validCatalog).operationCatalog

        assertEquals(listOf("ADMIN"), catalog.operations["secured"]?.security?.roles)
        assertEquals(
            listOf("ADMIN"),
            catalog.operations["secured"]?.configurations?.get("admin-only")?.security?.roles
        )
    }

    @Test
    fun `resolves pipeline configuration from loaded catalog`() {
        val catalog = load(validCatalog).operationCatalog

        val resolved = catalog.resolveOperation("all-products")
        assertEquals(10, resolved.pipelineConfig.limit)
    }

    @Test
    fun `parses entities with security`() {
        val entityCatalog = load(validCatalog).entityCatalog

        val products = entityCatalog.resolveEntity("products")
        assertEquals(listOf("GUEST", "ADMIN"), products.security?.read?.roles)
        assertEquals(listOf("id", "name"), products.fieldNames)
    }

    @Test
    fun `rejects catalog missing operations section`() {
        val yaml = """
            datasources:
              default:
                type: h2
        """.trimIndent()

        assertThrows<IllegalStateException> {
            load(yaml)
        }
    }

    @Test
    fun `rejects entity with primary key not in fields`() {
        val yaml = """
            operations:
              all:
                query: SELECT 1
            entities:
              products:
                datasource: default
                table: products
                primaryKey: id
                fields:
                  - name
        """.trimIndent()

        val ex = assertThrows<IllegalStateException> {
            load(yaml)
        }
        assertTrue(ex.message!!.contains("primary key column 'id' is not in fields list"))
    }

    @Test
    fun `parses empty entities section`() {
        val yaml = """
            operations:
              all:
                query: SELECT 1
        """.trimIndent()

        val loaded = load(yaml)
        assertEquals(0, loaded.entityCatalog.entities.size)
    }
}
