package helianthus.core.access.impl.db

import helianthus.core.result.CloseableRowStream
import helianthus.core.result.DefaultRowStream
import helianthus.core.result.ResultColumn
import helianthus.core.result.ResultSchema
import helianthus.core.result.ResultType
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class JdbcRowStream(
    override val schema: ResultSchema,
    private val resultSet: ResultSet,
    private val statement: PreparedStatement,
    private val connection: Connection
) : CloseableRowStream {

    override val rows: Sequence<Map<String, Any?>> = sequence {
        try {
            while (resultSet.next()) {
                val row = linkedMapOf<String, Any?>()
                for (col in schema.columns) {
                    val value = resultSet.getObject(col.name)
                    row[col.name] = if (resultSet.wasNull()) null else value
                }
                yield(row)
            }
        } finally {
            close()
        }
    }

    @Volatile
    private var closed = false

    override fun close() {
        if (!closed) {
            closed = true
            closeQuiet(resultSet)
            closeQuiet(statement)
            closeQuiet(connection)
        }
    }

    override fun withSchema(newSchema: ResultSchema): CloseableRowStream {
        return DefaultRowStream(newSchema, rows, this::close)
    }

    override fun transformRows(
        transform: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>
    ): CloseableRowStream {
        return DefaultRowStream(schema, transform(rows), this::close)
    }

    companion object {
        private fun closeQuiet(closeable: AutoCloseable?) {
            try {
                closeable?.close()
            } catch (_: Exception) {
            }
        }

        @JvmStatic
        fun buildSchema(resultSet: ResultSet): ResultSchema {
            val metaData = resultSet.metaData
            val columnCount = metaData.columnCount
            val columns = (1..columnCount).map { i ->
                ResultColumn(
                    name = metaData.getColumnName(i),
                    type = mapJdbcType(metaData.getColumnType(i)),
                    nullable = metaData.isNullable(i) != java.sql.ResultSetMetaData.columnNoNulls
                )
            }
            return ResultSchema(columns)
        }

        fun mapJdbcType(jdbcType: Int): ResultType = when (jdbcType) {
            Types.TINYINT, Types.SMALLINT, Types.INTEGER -> ResultType.INTEGER
            Types.BIGINT -> ResultType.LONG
            Types.FLOAT, Types.REAL -> ResultType.FLOAT
            Types.DOUBLE -> ResultType.DOUBLE
            Types.BOOLEAN, Types.BIT -> ResultType.BOOLEAN
            Types.DECIMAL, Types.NUMERIC -> ResultType.DECIMAL
            Types.DATE, Types.TIME, Types.TIMESTAMP,
            Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE -> ResultType.DATE
            Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
            Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
            Types.CLOB, Types.NCLOB -> ResultType.STRING
            Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB ->
                ResultType.BYTE_ARRAY
            else -> ResultType.UNKNOWN
        }
    }
}
