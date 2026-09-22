package helianthus.core.security

import helianthus.core.catalog.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies [EntityPermissionEvaluator] role checks and entity visibility
 * filtering, including ADMIN bypass, missing-role (allow-all), and
 * insufficient-role cases.
 */
class EntityPermissionEvaluatorTest {

    private fun createEntity(
        readRoles: List<String>? = null,
        writeRoles: List<String>? = null
    ): EntityDef {
        val security = if (readRoles != null || writeRoles != null) {
            EntitySecurityDef(
                read = readRoles?.let { EntityRoleDef(it) },
                write = writeRoles?.let { EntityRoleDef(it) }
            )
        } else null

        return EntityDef(
            datasource = "default",
            table = "products",
            primaryKey = PrimaryKeyDef("id"),
            fields = listOf(FieldDef("id"), FieldDef("name")),
            security = security
        )
    }

    private fun createPrincipal(name: String, vararg roles: String): Principal =
        Principal(name = name, roles = roles.toSet())

    @Test
    fun `checkReadPermission should allow admin to access any entity`() {
        val entity = createEntity(readRoles = listOf("GUEST"))
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val admin = createPrincipal("admin", "ADMIN")

        assertTrue(evaluator.checkReadPermission(admin, "products"))
    }

    @Test
    fun `checkReadPermission should allow user with matching role`() {
        val entity = createEntity(readRoles = listOf("GUEST", "ADMIN"))
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val guest = createPrincipal("guest", "GUEST")

        assertTrue(evaluator.checkReadPermission(guest, "products"))
    }

    @Test
    fun `checkReadPermission should deny user without matching role`() {
        val entity = createEntity(readRoles = listOf("ADMIN"))
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val guest = createPrincipal("guest", "GUEST")

        assertFalse(evaluator.checkReadPermission(guest, "products"))
    }

    @Test
    fun `checkReadPermission should allow access when no read roles are defined`() {
        val entity = createEntity(readRoles = null)
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val user = createPrincipal("user", "USER")

        assertTrue(evaluator.checkReadPermission(user, "products"))
    }

    @Test
    fun `checkReadPermission should allow access when security is null`() {
        val entity = EntityDef(
            datasource = "default",
            table = "products",
            primaryKey = PrimaryKeyDef("id"),
            fields = listOf(FieldDef("id"), FieldDef("name")),
            security = null
        )
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val user = createPrincipal("user", "USER")

        assertTrue(evaluator.checkReadPermission(user, "products"))
    }

    @Test
    fun `checkReadPermission should return false for nonexistent entity`() {
        val catalog = EntityCatalog(emptyMap())
        val evaluator = EntityPermissionEvaluator(catalog)
        val user = createPrincipal("user", "GUEST")

        assertFalse(evaluator.checkReadPermission(user, "nonexistent"))
    }

    @Test
    fun `checkReadPermission should allow access with empty read roles list`() {
        val entity = createEntity(readRoles = emptyList())
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val user = createPrincipal("user", "USER")

        assertTrue(evaluator.checkReadPermission(user, "products"))
    }

    @Test
    fun `filterVisibleEntities should return all entities for admin`() {
        val products = createEntity(readRoles = listOf("GUEST"))
        val customers = createEntity(readRoles = listOf("ADMIN"))
        val catalog = EntityCatalog(mapOf(
            "products" to products,
            "customers" to customers
        ))
        val evaluator = EntityPermissionEvaluator(catalog)
        val admin = createPrincipal("admin", "ADMIN")

        val visible = evaluator.filterVisibleEntities(admin)

        assertEquals(2, visible.size)
        assertTrue(visible.containsKey("products"))
        assertTrue(visible.containsKey("customers"))
    }

    @Test
    fun `filterVisibleEntities should filter entities by role for non-admin`() {
        val products = createEntity(readRoles = listOf("GUEST", "ADMIN"))
        val customers = createEntity(readRoles = listOf("ADMIN"))
        val catalog = EntityCatalog(mapOf(
            "products" to products,
            "customers" to customers
        ))
        val evaluator = EntityPermissionEvaluator(catalog)
        val guest = createPrincipal("guest", "GUEST")

        val visible = evaluator.filterVisibleEntities(guest)

        assertEquals(1, visible.size)
        assertTrue(visible.containsKey("products"))
        assertFalse(visible.containsKey("customers"))
    }

    @Test
    fun `filterVisibleEntities should include entities with no read roles`() {
        val products = createEntity(readRoles = null)
        val customers = createEntity(readRoles = listOf("ADMIN"))
        val catalog = EntityCatalog(mapOf(
            "products" to products,
            "customers" to customers
        ))
        val evaluator = EntityPermissionEvaluator(catalog)
        val user = createPrincipal("user", "USER")

        val visible = evaluator.filterVisibleEntities(user)

        assertEquals(1, visible.size)
        assertTrue(visible.containsKey("products"))
    }

    @Test
    fun `filterVisibleEntities should include entities with empty read roles`() {
        val products = createEntity(readRoles = emptyList())
        val customers = createEntity(readRoles = listOf("ADMIN"))
        val catalog = EntityCatalog(mapOf(
            "products" to products,
            "customers" to customers
        ))
        val evaluator = EntityPermissionEvaluator(catalog)
        val user = createPrincipal("user", "USER")

        val visible = evaluator.filterVisibleEntities(user)

        assertEquals(1, visible.size)
        assertTrue(visible.containsKey("products"))
    }

    @Test
    fun `filterVisibleEntities should return empty map when user has no matching roles`() {
        val customers = createEntity(readRoles = listOf("ADMIN"))
        val catalog = EntityCatalog(mapOf("customers" to customers))
        val evaluator = EntityPermissionEvaluator(catalog)
        val guest = createPrincipal("guest", "GUEST")

        val visible = evaluator.filterVisibleEntities(guest)

        assertEquals(0, visible.size)
    }
}
