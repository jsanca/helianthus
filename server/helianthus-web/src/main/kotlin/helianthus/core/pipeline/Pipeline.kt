package helianthus.core.pipeline

/**
 * Executes a configured pipeline of processing steps against an operation request.
 *
 * The pipeline iterates through each [PipelineComponent], passing the mutable
 * [PipelineContext] until either all steps complete or an error is encountered.
 *
 * @param steps ordered list of components to execute
 */
class Pipeline(private val steps: List<PipelineComponent>) {

    /**
     * Executes the pipeline starting from the given context.
     *
     * @param context the initial pipeline context with the operation request
     * @return the updated context after all steps have been processed
     */
    fun execute(context: PipelineContext): PipelineContext {
        var current = context
        for (step in steps) {
            if (current.error != null) {
                break
            }
            try {
                current = step.process(current)
            } catch (e: Exception) {
                current.error = e
                break
            }
        }
        return current
    }
}
