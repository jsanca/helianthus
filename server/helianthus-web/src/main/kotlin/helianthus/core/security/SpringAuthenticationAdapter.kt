package helianthus.core.security

import org.springframework.security.core.Authentication

/**
 * Runtime adapter that converts a Spring Security [Authentication] into the
 * framework-neutral [Principal] understood by Helianthus permission rules.
 *
 * This is the single point where the Spring Security `ROLE_` authority prefix
 * is translated into Helianthus role names. Permission rules never see Spring
 * types.
 */
fun Authentication.toPrincipal(): Principal = Principal(
    name = name,
    roles = authorities
        .asSequence()
        .mapNotNull { it.authority }
        .filter { it.startsWith(ROLE_PREFIX) }
        .map { it.removePrefix(ROLE_PREFIX) }
        .toSet()
)

/** Spring Security authority prefix stripped when converting to [Principal]. */
private const val ROLE_PREFIX = "ROLE_"
