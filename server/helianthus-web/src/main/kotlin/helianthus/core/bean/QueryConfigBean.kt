package helianthus.core.bean

import java.io.Serializable
import java.util.ArrayList

/**
 * Represents a declarative operation configuration loaded from the operations catalog.
 *
 * Each bean holds the SQL statement, target datasource, expected parameters,
 * and metadata needed to execute the operation at runtime.
 *
 * @property name unique identifier for this operation configuration
 * @property datasource name of the datasource to query against
 * @property query the SQL statement to execute
 * @property parameters list of expected query parameters with their types
 */
data class QueryConfigBean @JvmOverloads constructor(
    var name: String? = null,
    var datasource: String? = null,
    var query: String? = null,
    var parameters: ArrayList<QueryParameterBean>? = null
) : Serializable
