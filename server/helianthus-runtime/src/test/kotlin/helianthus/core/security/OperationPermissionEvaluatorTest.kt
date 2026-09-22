package helianthus.core.security

import helianthus.core.catalog.ConfigurationDef
import helianthus.core.catalog.OperationCatalog
import helianthus.core.catalog.OperationDef
import helianthus.core.catalog.SecurityDef
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies [OperationPermissionEvaluator] role checks (per operation and per
 * configuration, unioned) and visibility filtering, including ADMIN bypass
 * and no-role-configured (allow-all) cases.
 */
class OperationPermissionEvaluatorTest {

    private fun principal(name: String, vararg roles: String): Principal =
        Principal(name = name, roles = roles.toSet())

    private fun security(vararg roles: String): SecurityDef = SecurityDef(roles = roles.toList())

    private fun operation(
        security: SecurityDef? = null,
        configurations: Map<String, ConfigurationDef> = mapOf("default" to ConfigurationDef())
    ): OperationDef = OperationDef(
        query = "SELECT 1",
        security = security,
        configurations = configurations
    )

    private fun evaluator(vararg operations: Pair<String, OperationDef>): OperationPermissionEvaluator =
        OperationPermissionEvaluator(OperationCatalog(operations = operations.toMap()))

    @Test
    fun `operation-level role is accepted`() {
        val e = evaluator("op" to operation(security = security("GUEST")))

        assertTrue(e.checkPermission(principal("guest", "GUEST"), "op"))
    }

    @Test
    fun `operation-level role is rejected`() {
        val e = evaluator("op" to operation(security = security("ADMIN")))

        assertFalse(e.checkPermission(principal("guest", "GUEST"), "op"))
    }

    @Test
    fun `configuration-level role is accepted`() {
        val e = evaluator("op" to operation(
            configurations = mapOf("default" to ConfigurationDef(security = security("GUEST")))
        ))

        assertTrue(e.checkPermission(principal("guest", "GUEST"), "op", "default"))
    }

    @Test
    fun `configuration-level role is rejected`() {
        val e = evaluator("op" to operation(
            configurations = mapOf("default" to ConfigurationDef(security = security("ADMIN")))
        ))

        assertFalse(e.checkPermission(principal("guest", "GUEST"), "op", "default"))
    }

    @Test
    fun `operation and configuration roles are unioned`() {
        val e = evaluator("op" to operation(
            security = security("GUEST"),
            configurations = mapOf("default" to ConfigurationDef(security = security("STAFF")))
        ))

        assertTrue(e.checkPermission(principal("guest", "GUEST"), "op", "default"))
        assertTrue(e.checkPermission(principal("staff", "STAFF"), "op", "default"))
        assertFalse(e.checkPermission(principal("nobody", "OTHER"), "op", "default"))
    }

    @Test
    fun `admin bypasses operation-level roles`() {
        val e = evaluator("op" to operation(security = security("GUEST")))

        assertTrue(e.checkPermission(principal("admin", "ADMIN"), "op"))
    }

    @Test
    fun `admin bypasses configuration-level roles`() {
        val e = evaluator("op" to operation(
            configurations = mapOf("default" to ConfigurationDef(security = security("GUEST")))
        ))

        assertTrue(e.checkPermission(principal("admin", "ADMIN"), "op", "default"))
    }

    @Test
    fun `no required roles allows any authenticated principal`() {
        val e = evaluator("op" to operation(security = null))

        assertTrue(e.checkPermission(principal("user", "USER"), "op"))
    }

    @Test
    fun `empty required roles allows any authenticated principal`() {
        val e = evaluator("op" to operation(security = security()))

        assertTrue(e.checkPermission(principal("user", "USER"), "op"))
    }

    @Test
    fun `principal with multiple roles is accepted when any role matches`() {
        val e = evaluator("op" to operation(security = security("STAFF")))

        assertTrue(e.checkPermission(principal("user", "GUEST", "STAFF"), "op"))
    }

    @Test
    fun `unknown operation is rejected`() {
        val e = evaluator("op" to operation(security = security("GUEST")))

        assertFalse(e.checkPermission(principal("guest", "GUEST"), "unknown"))
    }

    @Test
    fun `unknown configuration with no operation roles is treated as no required roles`() {
        val e = evaluator("op" to operation(security = null))

        assertTrue(e.checkPermission(principal("user", "USER"), "op", "unknown-config"))
    }

    @Test
    fun `unknown configuration still enforces operation-level roles`() {
        val e = evaluator("op" to operation(security = security("GUEST")))

        assertTrue(e.checkPermission(principal("guest", "GUEST"), "op", "unknown-config"))
        assertFalse(e.checkPermission(principal("other", "OTHER"), "op", "unknown-config"))
    }

    @Test
    fun `filterVisibleOperations returns everything for admin`() {
        val e = evaluator(
            "public" to operation(security = null),
            "secret" to operation(security = security("ADMIN"))
        )

        val visible = e.filterVisibleOperations(principal("admin", "ADMIN"))

        assertEquals(2, visible.size)
    }

    @Test
    fun `filterVisibleOperations filters by role for non-admin`() {
        val e = evaluator(
            "public" to operation(security = null),
            "guest" to operation(security = security("GUEST")),
            "secret" to operation(security = security("ADMIN"))
        )

        val visible = e.filterVisibleOperations(principal("guest", "GUEST"))

        assertEquals(setOf("public", "guest"), visible.keys)
    }
}
