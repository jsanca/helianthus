package helianthus.core.bean

import java.io.Serializable

/**
 * Defines a single query parameter expected by an operation.
 *
 * @property name the parameter name used in the SQL statement
 * @property type the expected Java type name for type coercion (e.g., string, integer, long)
 */
data class QueryParameterBean @JvmOverloads constructor(
    var name: String? = null,
    var type: String? = null
) : Serializable
