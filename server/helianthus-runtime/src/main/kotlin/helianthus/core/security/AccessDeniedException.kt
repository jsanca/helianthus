package helianthus.core.security

/**
 * Signals that an authenticated caller is not permitted to access a resource.
 *
 * This is the framework-neutral representation of an authorization failure.
 * The HTTP adapter maps it to a 403 response; an embedded consumer can catch
 * it directly without any Spring dependency.
 */
class AccessDeniedException(message: String) : RuntimeException(message)
