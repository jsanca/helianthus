package helianthus.core.pipeline

import helianthus.core.access.GenericDataAccess
import helianthus.core.catalog.OperationCatalog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Factory for creating configured [Pipeline] instances for operation execution.
 *
 * Produces a pipeline with the standard step chain: Resolve, Bind, Query,
 * Project, Filter, Limit, and materialize to ResultFrame.
 *
 * @param catalog the operation catalog used to resolve operation definitions
 * @param genericDataAccess the data access layer for executing queries
 */
@Component
class PipelineFactory(
    private val catalog: OperationCatalog,
    private val genericDataAccess: GenericDataAccess
) {

    private val log = LoggerFactory.getLogger(PipelineFactory::class.java)

    /**
     * Creates a new pipeline configured for the given operation request.
     *
     * @param request the operation request to build a pipeline for
     * @return a ready-to-execute Pipeline instance
     */
    fun createPipeline(request: OperationRequest): Pipeline {
        val steps = listOf<PipelineComponent>(
            ResolveStep(catalog),
            BindStep(),
            QueryStep(genericDataAccess),
            ProjectStep(),
            FilterStep(),
            LimitStep(),
            ToResultFrameStep()
        )
        log.debug("Created pipeline for {} with {} steps", request.operationId, steps.size)
        return Pipeline(steps)
    }
}
