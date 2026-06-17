package helianthus.core.bean

import java.io.Serializable
import java.util.ArrayList

data class QueryConfigBean @JvmOverloads constructor(
    var name: String? = null,
    var datasource: String? = null,
    var query: String? = null,
    var parameters: ArrayList<QueryParameterBean>? = null
) : Serializable
