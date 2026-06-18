package helianthus.core

class IncongruentColumnValueLengthException : RuntimeException {
    @JvmOverloads
    constructor(message: String? = null, cause: Throwable? = null) : super(message, cause)
    constructor(cause: Throwable?) : this(null, cause)
}
