package helianthus.core.result

interface CloseableRowStream : AutoCloseable {
    val schema: ResultSchema
    val rows: Sequence<Map<String, Any?>>

    fun withSchema(newSchema: ResultSchema): CloseableRowStream

    fun transformRows(transform: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>): CloseableRowStream
}
