package helianthus.core

/**
 * Exception thrown when a parameter validation fails.
 *
 * This includes missing required parameters, invalid parameter types,
 * or parameters that cannot be coerced to the expected type.
 */
class InvalidParameterException(
    message: String
) : RuntimeException(message)
