package helianthus.core.pipeline

/**
 * Represents an incoming operation request from the HTTP layer.
 *
 * @property operationId the operation identifier to look up in the catalog
 * @property configurationId optional configuration variant (defaults to "default")
 * @property format the requested output format (json, html, etc.)
 * @property params query parameters passed with the request
 */
data class OperationRequest(
    val operationId: String,
    val configurationId: String = "default",
    val format: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * Represents a fully resolved operation ready for execution.
 *
 * Produced by [ResolveStep] after looking up the operation in the catalog.
 *
 * @property operationId unique identifier for this operation
 * @property configurationId the configuration variant being used
 * @property datasource optional explicit datasource override
 * @property sql the SQL statement to execute
 * @property parameters list of parameter definitions
 * @property pipelineConfig pipeline-level transformations to apply
 */
data class ResolvedOperation(
    val operationId: String,
    val configurationId: String = "default",
    val datasource: String? = null,
    val sql: String,
    val parameters: List<ParameterDefinition> = emptyList(),
    val pipelineConfig: PipelineConfig = PipelineConfig()
)

/**
 * Defines a single parameter expected by an operation.
 *
 * @property name the parameter name used in the SQL
 * @property type the expected type for coercion (defaults to "string")
 * @property required whether the parameter must be provided (defaults to false)
 */
data class ParameterDefinition(
    val name: String,
    val type: String = "string",
    val required: Boolean = false
)

/**
 * Configuration for pipeline-level data transformations.
 *
 * @property project list of columns to include in the output (null means all)
 * @property filter map of column conditions to filter rows
 * @property limit maximum number of rows to return
 * @property derive computed columns derived from existing ones
 */
data class PipelineConfig(
    val project: List<String>? = null,
    val filter: Map<String, Any>? = null,
    val limit: Int? = null,
    val derive: Map<String, String>? = null
) {
    companion object {
        /**
         * Builds a PipelineConfig from a list of step configuration maps.
         *
         * @param steps list of step configurations (e.g., from YAML)
         * @return a merged PipelineConfig
         */
        fun fromYamlSteps(steps: List<Map<String, Any>>): PipelineConfig {
            var project: List<String>? = null
            var filter: Map<String, Any>? = null
            var limit: Int? = null
            var derive: Map<String, String>? = null

            for (step in steps) {
                step["project"]?.let { value ->
                    @Suppress("UNCHECKED_CAST")
                    project = (value as List<*>).map { it.toString() }
                }
                step["filter"]?.let { value ->
                    @Suppress("UNCHECKED_CAST")
                    filter = value as? Map<String, Any>
                }
                step["limit"]?.let { value ->
                    limit = (value as Number).toInt()
                }
                step["derive"]?.let { value ->
                    @Suppress("UNCHECKED_CAST")
                    val map = value as? Map<*, *>
                    derive = map?.map { (k, v) -> k.toString() to v.toString() }?.toMap()
                }
            }

            return PipelineConfig(project, filter, limit, derive)
        }
    }
}

/**
 * Holds the bound parameter values for an operation execution.
 *
 * @property values map of parameter name to resolved value
 */
data class BoundParameters(
    val values: Map<String, Any> = emptyMap()
)

/**
 * Carries state through the pipeline execution.
 *
 * Starts with an [operationRequest] and accumulates resolved operation,
 * bound parameters, row stream, and final result frame as it progresses
 * through each [PipelineComponent].
 *
 * @property operationRequest the initial operation request
 */
data class PipelineContext(
    val operationRequest: OperationRequest
) {
    var resolvedOperation: ResolvedOperation? = null
    var boundParameters: BoundParameters? = null
    var rowStream: helianthus.core.result.CloseableRowStream? = null
    var resultFrame: helianthus.core.result.ResultFrame? = null
    var error: Exception? = null
}
