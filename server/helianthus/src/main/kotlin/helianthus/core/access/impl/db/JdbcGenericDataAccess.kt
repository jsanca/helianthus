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
        paramNames: Array<String>,
        typeNames: Array<String>,
        dataSource: String,
        fetchSize: Int,
        vararg params: Any?
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

            val effectiveSql: String
            val effectiveParamNames: Array<String>
            val effectiveTypeNames: Array<String>
            val effectiveParams: Array<out Any?>

            if (paramNames.isNotEmpty()) {
                // Named parameter mode: parse SQL, rewrite to positional, map names to positions
                val parsed = NamedParameterSql.parse(query)
                effectiveSql = parsed.actualSql
                effectiveParamNames = parsed.paramNames.toTypedArray()
                effectiveTypeNames = typeNames
                effectiveParams = params

                if (effectiveParamNames.size != effectiveTypeNames.size ||
                    effectiveParamNames.size != effectiveParams.size) {
                    throw IncongruentColumnValueLengthException(
                        "Named parameter count mismatch: SQL has ${effectiveParamNames.size} named params, " +
                        "but ${effectiveTypeNames.size} type names and ${effectiveParams.size} values were provided"
                    )
                }
            } else {
                // Positional parameter mode (backward compatible)
                effectiveSql = query
                effectiveParamNames = emptyArray()
                effectiveTypeNames = typeNames
                effectiveParams = params

                if (effectiveTypeNames.size != effectiveParams.size) {
                    throw IncongruentColumnValueLengthException(
                        "You are sending ${effectiveParams.size} values, but the query is expecting ${effectiveTypeNames.size}"
                    )
                }
            }

            statement = connection.prepareStatement(effectiveSql)

            val effectiveFetchSize = fetchSize.takeIf { it > 0 } ?: DEFAULT_FETCH_SIZE
            statement.fetchSize = effectiveFetchSize

            if (effectiveParamNames.isNotEmpty()) {
                setParamsByName(statement, effectiveParamNames, effectiveTypeNames, effectiveParams)
            } else {
                setParams(statement, effectiveTypeNames, effectiveParams)
            }

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

    private fun setParamsByName(
        statement: PreparedStatement,
        paramNames: Array<String>,
        typeNames: Array<String>,
        params: Array<out Any?>
    ) {
        // Build a map from param name to (type, value), handling repeated names
        // For repeated names, we collect all positions
        val nameToPositions = mutableMapOf<String, MutableList<Int>>()
        paramNames.forEachIndexed { index, name ->
            nameToPositions.getOrPut(name) { mutableListOf() }.add(index + 1)
        }

        // Build a map from param name to (type, value) using the last occurrence
        val nameToValue = mutableMapOf<String, Pair<String, Any?>>()
        paramNames.indices.forEach { i ->
            nameToValue[paramNames[i]] = typeNames[i] to params[i]
        }

        // Bind each position
        paramNames.forEachIndexed { index, name ->
            val position = index + 1
            val (typeName, value) = nameToValue[name]
                ?: throw DataAccessErrorException(
                    RuntimeException("No value provided for named parameter: $name")
                )
            val resultType = ResultType.fromTypeName(typeName)
            JdbcParamBinder.bind(statement, position, value, resultType)
        }
    }

    private fun setParams(
        statement: PreparedStatement,
        typeNameArray: Array<String>,
        params: Array<out Any?>
    ) {
        if (params.isEmpty()) return

        typeNameArray.zip(params).forEachIndexed { i, (typeName, value) ->
            val resultType = ResultType.fromTypeName(typeName)
            JdbcParamBinder.bind(statement, i + 1, value, resultType)
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
