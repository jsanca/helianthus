package helianthus.core.result

/**
 * Default in-memory implementation of [CloseableRowStream] backed by an
 * arbitrary [Sequence] of rows and an optional close action invoked on [close].
 *
 * Use this for transformations applied on top of an existing stream where the
 * underlying rows have already been materialized, or as a wrapper to attach a
 * schema/close lifecycle to any row sequence.
 *
 * @property schema the schema describing the rows in this stream
 * @property rows the row sequence exposed by this stream
 * @param closeAction invoked once when [close] is called
 */
class DefaultRowStream(
    override val schema: ResultSchema,
    override val rows: Sequence<Map<String, Any?>>,
    private val closeAction: () -> Unit = {}
) : CloseableRowStream {

    /** Invokes the close action supplied at construction time. */
    override fun close() = closeAction()

    /**
     * Returns a new [CloseableRowStream] that exposes the same rows under
     * [newSchema], sharing the same close lifecycle as this stream.
     */
    override fun withSchema(newSchema: ResultSchema): CloseableRowStream {
        return DefaultRowStream(newSchema, rows, closeAction)
    }

    /**
     * Returns a new [CloseableRowStream] whose rows are produced by applying
     * [transform] to this stream's row sequence.
     */
    override fun transformRows(
        transform: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>
    ): CloseableRowStream {
        return DefaultRowStream(schema, transform(rows), closeAction)
    }

    companion object {
        /**
         * Convenience factory that builds a [DefaultRowStream] from a fully
         * materialized row list.
         */
        fun fromRows(schema: ResultSchema, rows: List<Map<String, Any?>>): DefaultRowStream {
            return DefaultRowStream(schema, rows.asSequence())
        }
    }
}
