package helianthus.core.bean

import java.io.Serializable

data class PathMappingResultBean @JvmOverloads constructor(
    var operationId: String? = null,
    var format: String? = null
) : Serializable
