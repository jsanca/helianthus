package helianthus.core.security

import helianthus.core.catalog.EntityCatalog
import helianthus.core.catalog.EntityDef
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class EntityPermissionEvaluator(
    private val entityCatalog: EntityCatalog
) {
    private val log = LoggerFactory.getLogger(EntityPermissionEvaluator::class.java)

    fun checkReadPermission(authentication: Authentication, entityName: String): Boolean {
        if (authentication.authorities.any { it.authority == "ROLE_ADMIN" }) {
            return true
        }

        val entity = entityCatalog.entities[entityName] ?: return false
        val readRoles = entity.security?.read?.roles ?: emptyList()

        if (readRoles.isEmpty()) {
            log.debug("No read roles required for entity '{}'", entityName)
            return true
        }

        val hasRole = readRoles.any { role ->
            authentication.authorities.any { it.authority == "ROLE_$role" }
        }

        if (!hasRole) {
            log.debug(
                "Access denied for user '{}' to entity '{}': requires read roles {}",
                authentication.name, entityName, readRoles
            )
        }

        return hasRole
    }

    fun filterVisibleEntities(authentication: Authentication): Map<String, EntityDef> {
        if (authentication.authorities.any { it.authority == "ROLE_ADMIN" }) {
            return entityCatalog.entities
        }

        return entityCatalog.entities.filter { (_, entity) ->
            val readRoles = entity.security?.read?.roles
            if (readRoles.isNullOrEmpty()) {
                true
            } else {
                readRoles.any { role ->
                    authentication.authorities.any { it.authority == "ROLE_$role" }
                }
            }
        }
    }
}
