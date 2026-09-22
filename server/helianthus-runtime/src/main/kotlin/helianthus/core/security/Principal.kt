package helianthus.core.security

/**
 * Framework-neutral representation of an authenticated caller.
 *
 * This is the only identity information Helianthus permission rules need:
 * a name and the set of role names granted to the caller.
 *
 * Role names are Helianthus-owned (e.g. "ADMIN", "GUEST") and carry no
 * framework prefix. The Spring Security `ROLE_` prefix is stripped at the
 * runtime boundary (see [toPrincipal]).
 */
data class Principal(
    val name: String,
    val roles: Set<String>
)

/**
 * Role that bypasses all permission checks.
 */
const val ADMIN_ROLE = "ADMIN"
