package helianthus.core

/**
 * Thrown when a low-level data-access operation fails. Wraps the underlying
 * driver or SQL exception with a normalized runtime type for upstream layers.
 */
internal class DataAccessErrorException : RuntimeException {
    /**
     * Creates a new [DataAccessErrorException] with an optional message and cause.
     *
     * @param message description of the failure
     * @param cause the underlying cause, if any
     */
    @JvmOverloads
    constructor(message: String? = null, cause: Throwable? = null) : super(message, cause)

    /**
     * Creates a new [DataAccessErrorException] wrapping the given [cause].
     */
    constructor(cause: Throwable?) : this(null, cause)
}
