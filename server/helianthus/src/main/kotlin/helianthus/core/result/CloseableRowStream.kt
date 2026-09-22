package helianthus.core.result

/**
 * A stream of result rows that can be lazily consumed and closed when no
 * longer needed.
 *
 * Implementations typically wrap a live database cursor; consumers must invoke
 * [close] when finished to release the underlying resources.
 */
interface CloseableRowStream : AutoCloseable {
    /** The schema describing the columns carried by this stream. */
    val schema: ResultSchema

    /** The rows of this stream as a lazily-evaluated sequence of column-name to value maps. */
    val rows: Sequence<Map<String, Any?>>

    /**
     * Returns a new stream that exposes the same rows under [newSchema].
     *
     * @param newSchema the replacement schema
     * @return a new [CloseableRowStream] sharing this stream's rows
     */
    fun withSchema(newSchema: ResultSchema): CloseableRowStream

    /**
     * Returns a new stream whose rows are derived by applying [transform] to
     * this stream's row sequence.
     *
     * @param transform a function that maps the input sequence to a new sequence
     * @return a new [CloseableRowStream] exposing the transformed rows
     */
    fun transformRows(transform: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>): CloseableRowStream
}
