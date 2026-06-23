package helianthus.core.security

import helianthus.core.catalog.*
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            fields = listOf("id", "name"),
            security = security
        )
    }

    private fun createAuth(username: String, vararg roles: String): TestingAuthenticationToken {
        val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
        return TestingAuthenticationToken(username, null, authorities)
    }

    @Test
    fun `checkReadPermission should allow admin to access any entity`() {
        val entity = createEntity(readRoles = listOf("GUEST"))
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val adminAuth = createAuth("admin", "ADMIN")

        assertTrue(evaluator.checkReadPermission(adminAuth, "products"))
    }

    @Test
    fun `checkReadPermission should allow user with matching role`() {
        val entity = createEntity(readRoles = listOf("GUEST", "ADMIN"))
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val guestAuth = createAuth("guest", "GUEST")

        assertTrue(evaluator.checkReadPermission(guestAuth, "products"))
    }

    @Test
    fun `checkReadPermission should deny user without matching role`() {
        val entity = createEntity(readRoles = listOf("ADMIN"))
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val guestAuth = createAuth("guest", "GUEST")

        assertFalse(evaluator.checkReadPermission(guestAuth, "products"))
    }

    @Test
    fun `checkReadPermission should allow access when no read roles are defined`() {
        val entity = createEntity(readRoles = null)
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val userAuth = createAuth("user", "USER")

        assertTrue(evaluator.checkReadPermission(userAuth, "products"))
    }

    @Test
    fun `checkReadPermission should allow access when security is null`() {
        val entity = EntityDef(
            datasource = "default",
            table = "products",
            primaryKey = PrimaryKeyDef("id"),
            fields = listOf("id", "name"),
            security = null
        )
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val userAuth = createAuth("user", "USER")

        assertTrue(evaluator.checkReadPermission(userAuth, "products"))
    }

    @Test
    fun `checkReadPermission should return false for nonexistent entity`() {
        val catalog = EntityCatalog(emptyMap())
        val evaluator = EntityPermissionEvaluator(catalog)
        val userAuth = createAuth("user", "GUEST")

        assertFalse(evaluator.checkReadPermission(userAuth, "nonexistent"))
    }

    @Test
    fun `checkReadPermission should allow access with empty read roles list`() {
        val entity = createEntity(readRoles = emptyList())
        val catalog = EntityCatalog(mapOf("products" to entity))
        val evaluator = EntityPermissionEvaluator(catalog)
        val userAuth = createAuth("user", "USER")

        assertTrue(evaluator.checkReadPermission(userAuth, "products"))
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
        val adminAuth = createAuth("admin", "ADMIN")

        val visible = evaluator.filterVisibleEntities(adminAuth)

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
        val guestAuth = createAuth("guest", "GUEST")

        val visible = evaluator.filterVisibleEntities(guestAuth)

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
        val userAuth = createAuth("user", "USER")

        val visible = evaluator.filterVisibleEntities(userAuth)

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
        val userAuth = createAuth("user", "USER")

        val visible = evaluator.filterVisibleEntities(userAuth)

        assertEquals(1, visible.size)
        assertTrue(visible.containsKey("products"))
    }

    @Test
    fun `filterVisibleEntities should return empty map when user has no matching roles`() {
        val customers = createEntity(readRoles = listOf("ADMIN"))
        val catalog = EntityCatalog(mapOf("customers" to customers))
        val evaluator = EntityPermissionEvaluator(catalog)
        val guestAuth = createAuth("guest", "GUEST")

        val visible = evaluator.filterVisibleEntities(guestAuth)

        assertEquals(0, visible.size)
    }
}
