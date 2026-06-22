package helianthus.core.access

import helianthus.core.result.CloseableRowStream

interface GenericDataAccess {

    companion object {
        const val DEFAULT_DATA_SOURCE = "default"
    }

    fun executeQueryStream(
        plan: SqlExecutionPlan,
        dataSource: String,
        fetchSize: Int
    ): CloseableRowStream
}
