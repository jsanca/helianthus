package helianthus.core.exception

/**
 * Thrown when a request path does not conform to the expected operation path pattern.
 *
 * This exception indicates the path is missing the required `/api/op/` prefix,
 * lacks a format extension, has an empty operation id, or is otherwise malformed.
 */
class InvalidOperationPathException(message: String) : RuntimeException(message)
