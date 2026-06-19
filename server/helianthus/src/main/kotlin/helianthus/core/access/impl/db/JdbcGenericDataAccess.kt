package helianthus.core.access.impl.db

import helianthus.core.DataAccessErrorException
import helianthus.core.IncongruentColumnValueLengthException
import helianthus.core.access.GenericDataAccess
import helianthus.core.result.CloseableRowStream
import helianthus.core.result.ResultType
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

class JdbcGenericDataAccess(
    private val dataSources: Map<String, DataSource>
) : GenericDataAccess {

    override fun executeQueryStream(
        query: String,
        typeNameArray: Array<String>,
        dataSource: String,
        fetchSize: Int,
        vararg params: Any
    ): CloseableRowStream {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            val ds = dataSources[dataSource] 
                ?: dataSources[GenericDataAccess.DEFAULT_DATA_SOURCE]
                ?: throw DataAccessErrorException(
                    RuntimeException("No datasource found for key: $dataSource")
                )
            
            connection = ds.connection

            statement = connection.prepareStatement(query)

            val effectiveFetchSize = fetchSize.takeIf { it > 0 } ?: DEFAULT_FETCH_SIZE
            statement.fetchSize = effectiveFetchSize

            setParams(statement, typeNameArray, params)

            resultSet = statement.executeQuery()

            val schema = JdbcRowStream.buildSchema(resultSet)

            val stream = JdbcRowStream(schema, resultSet, statement, connection)

            resultSet = null
            statement = null
            connection = null

            return stream

        } catch (e: SQLException) {
            log.info(e.message, e)
            closeQuiet(resultSet, statement, connection)
            throw DataAccessErrorException(e)
        }
    }

    private fun setParams(
        statement: PreparedStatement,
        typeNameArray: Array<String>,
        params: Array<out Any>
    ) {
        if (params.isEmpty()) return

        if (typeNameArray.size == params.size) {
            typeNameArray.zip(params).forEachIndexed { i, (typeName, value) ->
                val resultType = ResultType.fromTypeName(typeName)
                JdbcParamBinder.bind(statement, i + 1, value, resultType)
            }
        } else {
            throw IncongruentColumnValueLengthException(
                "You are sending ${params.size} values, but the query is expecting ${typeNameArray.size}"
            )
        }
    }

    companion object {
        private const val DEFAULT_FETCH_SIZE = 1000
        private val log = LoggerFactory.getLogger(JdbcGenericDataAccess::class.java)

        private fun closeQuiet(vararg closeables: AutoCloseable?) {
            for (closeable in closeables) {
                try {
                    closeable?.close()
                } catch (_: Exception) {
                }
            }
        }
    }
}
