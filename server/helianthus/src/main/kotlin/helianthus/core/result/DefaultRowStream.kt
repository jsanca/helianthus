package helianthus.core.result

class DefaultRowStream(
    override val schema: ResultSchema,
    override val rows: Sequence<Map<String, Any?>>,
    private val closeAction: () -> Unit = {}
) : CloseableRowStream {

    override fun close() = closeAction()

    override fun withSchema(newSchema: ResultSchema): CloseableRowStream {
        return DefaultRowStream(newSchema, rows, closeAction)
    }

    override fun transformRows(
        transform: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>
    ): CloseableRowStream {
        return DefaultRowStream(schema, transform(rows), closeAction)
    }

    companion object {
        fun fromRows(schema: ResultSchema, rows: List<Map<String, Any?>>): DefaultRowStream {
            return DefaultRowStream(schema, rows.asSequence())
        }
    }
}
