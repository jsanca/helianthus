package helianthus.core.pipeline

/**
 * A single stage in the operation execution pipeline.
 *
 * Each component receives a [PipelineContext], performs its transformation,
 * and returns the updated context for the next step.
 */
interface PipelineComponent {
    /**
     * Processes the pipeline context and returns the updated context.
     *
     * @param context the current pipeline context
     * @return the context after this component has processed it
     */
    fun process(context: PipelineContext): PipelineContext
}
