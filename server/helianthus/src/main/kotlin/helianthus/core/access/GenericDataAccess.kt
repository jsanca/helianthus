package helianthus.core.access

import helianthus.core.result.CloseableRowStream

/**
 * Generic, dialect-agnostic data-access contract used by the runtime.
 *
 * Implementations execute a pre-built [SqlExecutionPlan] against a named
 * datasource and return the rows as a streaming [CloseableRowStream].
 */
interface GenericDataAccess {

    companion object {
        /** Name of the fallback datasource used when a request omits one. */
        const val DEFAULT_DATA_SOURCE = "default"
    }

    /**
     * Executes [plan] against the datasource identified by [dataSource] and
     * returns the results as a [CloseableRowStream].
     *
     * @param plan the SQL and bound parameters to execute
     * @param dataSource name of the datasource to run against; falls back to the default datasource if unknown
     * @param fetchSize JDBC fetch size hint; values <= 0 select an implementation-defined default
     * @return a stream of result rows that the caller is responsible for closing
     */
    fun executeQueryStream(
        plan: SqlExecutionPlan,
        dataSource: String,
        fetchSize: Int
    ): CloseableRowStream
}
