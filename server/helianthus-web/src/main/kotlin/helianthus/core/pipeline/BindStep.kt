package helianthus.core.pipeline

import org.slf4j.LoggerFactory

/**
 * Pipeline step that binds incoming HTTP request parameters to the operation's defined parameters.
 *
 * Reads the parameter definitions from the resolved operation and maps the
 * incoming request parameters by name into a [BoundParameters] entry stored
 * in the context.
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
            if (value != null) {
                values[paramDef.name] = value
            }
        }

        context.boundParameters = BoundParameters(values)

        log.debug("Bound {} parameters for operation: {}", values.size, op.operationId)

        return context
    }
}
