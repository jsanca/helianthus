package helianthus.core

/**
 * Thrown when a value cannot be mapped from one representation to another
 * (for example, from a JDBC result to a target type) and no explicit mapping
 * is available.
 */
class NoMappingException : RuntimeException {
    /**
     * Creates a new [NoMappingException] with an optional message and cause.
     *
     * @param message description of the failure
     * @param cause the underlying cause, if any
     */
    @JvmOverloads
    constructor(message: String? = null, cause: Throwable? = null) : super(message, cause)

    /**
     * Creates a new [NoMappingException] wrapping the given [cause].
     */
    constructor(cause: Throwable?) : this(null, cause)
}
