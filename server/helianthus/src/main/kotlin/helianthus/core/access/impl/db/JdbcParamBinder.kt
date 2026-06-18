package helianthus.core.access.impl.db

import helianthus.core.result.ResultType
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types

object JdbcParamBinder {

    fun bind(statement: PreparedStatement, index: Int, value: Any?, type: ResultType) {
        if (value == null) {
            bindNull(statement, index, type)
            return
        }

        when (type) {
            ResultType.INTEGER -> statement.setInt(index, toInt(value))
            ResultType.LONG -> statement.setLong(index, toLong(value))
            ResultType.FLOAT -> statement.setFloat(index, toFloat(value))
            ResultType.DOUBLE -> statement.setDouble(index, toDouble(value))
            ResultType.BOOLEAN -> statement.setBoolean(index, toBoolean(value))
            ResultType.DECIMAL -> statement.setBigDecimal(index, toBigDecimal(value))
            ResultType.DATE -> statement.setTimestamp(index, toTimestamp(value))
            ResultType.STRING -> statement.setString(index, value.toString())
            ResultType.BYTE_ARRAY -> statement.setBytes(index, toBytes(value))
            ResultType.UNKNOWN -> statement.setObject(index, value)
        }
    }

    private fun bindNull(statement: PreparedStatement, index: Int, type: ResultType) {
        val sqlType = when (type) {
            ResultType.INTEGER -> Types.INTEGER
            ResultType.LONG -> Types.BIGINT
            ResultType.FLOAT -> Types.FLOAT
            ResultType.DOUBLE -> Types.DOUBLE
            ResultType.BOOLEAN -> Types.BOOLEAN
            ResultType.DECIMAL -> Types.DECIMAL
            ResultType.DATE -> Types.TIMESTAMP
            ResultType.STRING -> Types.VARCHAR
            ResultType.BYTE_ARRAY -> Types.BINARY
            ResultType.UNKNOWN -> Types.OTHER
        }
        statement.setNull(index, sqlType)
    }

    private fun toInt(value: Any): Int = when (value) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toInt()
        else -> throw IllegalArgumentException("Cannot convert ${value::class} to Int")
    }

    private fun toLong(value: Any): Long = when (value) {
        is Long -> value
        is Number -> value.toLong()
        is String -> value.toLong()
        else -> throw IllegalArgumentException("Cannot convert ${value::class} to Long")
    }

    private fun toFloat(value: Any): Float = when (value) {
        is Float -> value
        is Number -> value.toFloat()
        is String -> value.toFloat()
        else -> throw IllegalArgumentException("Cannot convert ${value::class} to Float")
    }

    private fun toDouble(value: Any): Double = when (value) {
        is Double -> value
        is Number -> value.toDouble()
        is String -> value.toDouble()
        else -> throw IllegalArgumentException("Cannot convert ${value::class} to Double")
    }

    private fun toBoolean(value: Any): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.toBoolean()
        else -> throw IllegalArgumentException("Cannot convert ${value::class} to Boolean")
    }

    private fun toBigDecimal(value: Any): BigDecimal = when (value) {
        is BigDecimal -> value
        is Number -> BigDecimal(value.toString())
        is String -> BigDecimal(value)
        else -> throw IllegalArgumentException("Cannot convert ${value::class} to BigDecimal")
    }

    private fun toTimestamp(value: Any): Timestamp = when (value) {
        is Timestamp -> value
        is java.sql.Date -> Timestamp(value.time)
        is java.util.Date -> Timestamp(value.time)
        is Long -> Timestamp(value)
        is String -> Timestamp.valueOf(value)
        else -> throw IllegalArgumentException("Cannot convert ${value::class} to Timestamp")
    }

    private fun toBytes(value: Any): ByteArray = when (value) {
        is ByteArray -> value
        is String -> value.toByteArray()
        else -> throw IllegalArgumentException("Cannot convert ${value::class} to ByteArray")
    }
}
