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
     * Executes a query with named parameters and returns a closeable streamed row stream.
     * The caller must close the stream to release JDBC resources.
     *
     * @param query SQL string, may contain :paramName named placeholders or ? positional placeholders
     * @param paramNames ordered list of parameter names (empty for positional queries)
     * @param typeNames ordered list of parameter type names matching paramNames/params
     * @param dataSource the datasource key to use
     * @param fetchSize JDBC fetch size hint
     * @param params parameter values in order; may contain null for optional parameters
     */
    fun executeQueryStream(
        query: String,
        paramNames: Array<String>,
        typeNames: Array<String>,
        dataSource: String,
        fetchSize: Int,
        vararg params: Any?
    ): CloseableRowStream

    /**
     * Legacy positional-only overload. Delegates to the named-parameter method with empty names.
     */
    fun executeQueryStream(
        query: String,
        typeNameArray: Array<String>,
        dataSource: String,
        fetchSize: Int,
        vararg params: Any
    ): CloseableRowStream = executeQueryStream(
        query, emptyArray(), typeNameArray, dataSource, fetchSize, *params
    )
}
