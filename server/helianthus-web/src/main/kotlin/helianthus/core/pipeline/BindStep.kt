package helianthus.core.pipeline

import helianthus.core.InvalidParameterException
import org.slf4j.LoggerFactory

/**
 * Pipeline step that binds incoming HTTP request parameters to the operation's defined parameters.
 *
 * Reads the parameter definitions from the resolved operation and maps the
 * incoming request parameters by name into a [BoundParameters] entry stored
 * in the context.
 *
 * Validates:
 * - Required parameters are present
 * - Parameter values can be coerced to the expected type
 */
class BindStep : PipelineComponent {

    private val log = LoggerFactory.getLogger(BindStep::class.java)

    override fun process(context: PipelineContext): PipelineContext {
        val op = context.resolvedOperation
                ?: throw IllegalStateException("No resolved operation in context")

        val params = context.operationRequest.params
        val values = mutableMapOf<String, Any>()

        for (paramDef in op.parameters) {
            val value = params[paramDef.name]
            
            // Check required parameters
            if (value == null || value.isBlank()) {
                if (paramDef.required) {
                    throw InvalidParameterException(
                        "Missing required parameter: '${paramDef.name}'"
                    )
                }
                continue
            }
            
            // Validate and coerce type
            val coercedValue = coerceType(value, paramDef.type, paramDef.name)
            values[paramDef.name] = coercedValue
        }

        context.boundParameters = BoundParameters(values)

        log.debug("Bound {} parameters for operation: {}", values.size, op.operationId)

        return context
    }
    
    /**
     * Coerces a string value to the expected type.
     * 
     * @param value the string value from the request
     * @param type the expected type (string, number, integer, boolean, date)
     * @param paramName the parameter name for error messages
     * @return the coerced value
     * @throws InvalidParameterException if the value cannot be coerced
     */
    private fun coerceType(value: String, type: String, paramName: String): Any {
        return try {
            when (type.lowercase()) {
                "string", "text", "varchar" -> value
                "number", "decimal", "double", "float" -> {
                    value.toDouble()
                }
                "integer", "int", "long" -> {
                    value.toLong()
                }
                "boolean", "bool" -> {
                    when (value.lowercase()) {
                        "true", "1", "yes" -> true
                        "false", "0", "no" -> false
                        else -> throw InvalidParameterException(
                            "Parameter '$paramName' must be a boolean, got: '$value'"
                        )
                    }
                }
                "date" -> {
                    // Accept ISO date format, pass through as string for JDBC
                    value
                }
                else -> {
                    // Unknown type, pass through as string
                    log.warn("Unknown parameter type '{}' for parameter '{}', treating as string", type, paramName)
                    value
                }
            }
        } catch (e: InvalidParameterException) {
            throw e
        } catch (e: Exception) {
            throw InvalidParameterException(
                "Parameter '$paramName' must be of type '$type', got: '$value'"
            )
        }
    }
}
