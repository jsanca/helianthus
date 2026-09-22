package helianthus.core

import helianthus.core.security.Principal
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies [HelianthusRuntimeBuilder] produces a runtime whose catalog summary
 * correctly reflects the loaded YAML, including role-based filtering of
 * operations and entities.
 */
class HelianthusRuntimeBuilderTest {

    private val catalog = """
        app:
          name: Test App
        datasources:
          default:
            type: h2
        operations:
          all:
            query: SELECT 1
          admin-only:
            query: SELECT 2
            security:
              roles: [ADMIN]
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

    private fun build(): HelianthusRuntime =
        HelianthusRuntimeBuilder(emptyMap())
            .build(ByteArrayInputStream(catalog.toByteArray()))

    private fun principal(name: String, vararg roles: String): Principal =
        Principal(name, roles.toSet())

    @Test
    fun `build produces a runtime that exposes the catalog summary`() {
        val summary = build().catalogSummary(principal("admin", "ADMIN"))

        assertEquals("Test App", summary.app)
        assertEquals(listOf("json", "html", "csv", "xml"), summary.formats)
        assertEquals(2, summary.operations.size)
        assertEquals(1, summary.entities.size)
    }

    @Test
    fun `catalog summary filters operations by role`() {
        val summary = build().catalogSummary(principal("guest", "GUEST"))

        assertEquals(setOf("all"), summary.operations.map { it.name }.toSet())
    }

    @Test
    fun `catalog summary includes entities visible to the caller`() {
        val summary = build().catalogSummary(principal("guest", "GUEST"))

        assertTrue(summary.entities.any { it.name == "products" })
    }
}
