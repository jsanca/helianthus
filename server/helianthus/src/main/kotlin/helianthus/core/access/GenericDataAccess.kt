package helianthus.core.access

import helianthus.core.result.CloseableRowStream
import java.io.Serializable

/**
 * Encapsulate a methods to handle the data access
 */
interface GenericDataAccess  {

    companion object {
        const val DEFAULT_DATA_SOURCE = "default"
    }

    /**
     * Executes a query and returns a closeable streamed row stream.
     * The caller must close the stream to release JDBC resources.
     */
    fun executeQueryStream(
        query: String,
        typeNameArray: Array<String>,
        dataSource: String,
        fetchSize: Int,
        vararg params: Any
    ): CloseableRowStream
}
