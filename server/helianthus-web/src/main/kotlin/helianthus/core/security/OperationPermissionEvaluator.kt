package helianthus.core.security

import helianthus.core.catalog.OperationCatalog
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class OperationPermissionEvaluator(
    private val catalog: OperationCatalog
) {

    fun checkPermission(
        authentication: Authentication,
        operationId: String,
        configurationId: String? = null
    ): Boolean {
        // ADMIN passes everything
        if (authentication.authorities.any { it.authority == "ROLE_ADMIN" }) {
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
        val hasRole = requiredRoles.any { role ->
            authentication.authorities.any { it.authority == "ROLE_$role" }
        }

        if (!hasRole) {
            log.debug(
                "Access denied for user '{}' to operation '{}' config '{}': requires roles {}",
                authentication.name, operationId, configId, requiredRoles
            )
        }

        return hasRole
    }

    fun filterVisibleOperations(
        authentication: Authentication
    ): Map<String, helianthus.core.catalog.OperationDef> {
        // ADMIN sees everything
        if (authentication.authorities.any { it.authority == "ROLE_ADMIN" }) {
            return catalog.operations
        }

        return catalog.operations.filter { (_, op) ->
            val roles = op.security?.roles
            if (roles.isNullOrEmpty()) {
                true // No security defined, visible to all authenticated users
            } else {
                roles.any { role ->
                    authentication.authorities.any { it.authority == "ROLE_$role" }
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OperationPermissionEvaluator::class.java)
    }
}
