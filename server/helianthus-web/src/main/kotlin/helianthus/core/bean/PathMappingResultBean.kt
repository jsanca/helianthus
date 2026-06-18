package helianthus.core.bean

import java.io.Serializable

/**
 * Holds the parsed components of an incoming operation request path.
 *
 * Produced by [helianthus.core.util.PathHandler] after extracting the
 * operation identifier and format from a request path such as `/api/op/customers.json`.
 *
 * @property operationId operation identifier matching the catalog key
 * @property format the output format extension (e.g., json, html)
 * @property configurationId optional configuration variant (defaults to "default")
 */
data class PathMappingResultBean @JvmOverloads constructor(
    var operationId: String? = null,
    var format: String? = null,
    var configurationId: String? = null
) : Serializable
