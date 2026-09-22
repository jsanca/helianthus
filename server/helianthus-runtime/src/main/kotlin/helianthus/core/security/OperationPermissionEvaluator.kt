package helianthus.core.security

import helianthus.core.catalog.OperationCatalog
import helianthus.core.catalog.OperationDef
import org.slf4j.LoggerFactory

/**
 * Evaluates operation-level permission rules against an [OperationCatalog].
 *
 * Permission rules are read from the catalog at both the operation and the
 * configuration level and unioned. [ADMIN_ROLE] bypasses all checks; missing
 * `security.roles` allow any authenticated caller.
 */
internal class OperationPermissionEvaluator(
    private val catalog: OperationCatalog
) {

    /**
     * Returns true if [principal] may execute the given operation/configuration.
     *
     * @param principal the caller to check
     * @param operationId operation identifier from the catalog
     * @param configurationId optional configuration variant; defaults to `default`
     */
    fun checkPermission(
        principal: Principal,
        operationId: String,
        configurationId: String? = null
    ): Boolean {
        // ADMIN passes everything
        if (principal.roles.contains(ADMIN_ROLE)) {
            return true
        }

        val op = catalog.operations[operationId] ?: return false

        val configId = configurationId ?: "default"
        val config = op.configurations[configId]

        val opRoles = op.security?.roles ?: emptyList()
        val configRoles = config?.security?.roles ?: emptyList()

        val requiredRoles = (opRoles + configRoles).toSet()

        // No roles defined: allow any authenticated user
        if (requiredRoles.isEmpty()) {
            log.debug("No roles required for operation '{}' config '{}'", operationId, configId)
            return true
        }

        // User must have at least one required role
        val hasRole = requiredRoles.any { role -> role in principal.roles }

        if (!hasRole) {
            log.debug(
                "Access denied for user '{}' to operation '{}' config '{}': requires roles {}",
                principal.name, operationId, configId, requiredRoles
            )
        }

        return hasRole
    }

    /**
     * Returns the subset of operations that [principal] is allowed to see.
     *
     * Used by catalog-summary endpoints to scope the visible operations to the
     * caller's roles. ADMIN sees everything.
     */
    fun filterVisibleOperations(
        principal: Principal
    ): Map<String, OperationDef> {
        // ADMIN sees everything
        if (principal.roles.contains(ADMIN_ROLE)) {
            return catalog.operations
        }

        return catalog.operations.filter { (_, op) ->
            val roles = op.security?.roles
            if (roles.isNullOrEmpty()) {
                true // No security defined, visible to all authenticated users
            } else {
                roles.any { role -> role in principal.roles }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OperationPermissionEvaluator::class.java)
    }
}
