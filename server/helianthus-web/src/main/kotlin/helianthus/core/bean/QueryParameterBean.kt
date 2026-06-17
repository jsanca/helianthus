package helianthus.core.bean

import java.io.Serializable

data class QueryParameterBean @JvmOverloads constructor(
    var name: String? = null,
    var type: String? = null
) : Serializable
