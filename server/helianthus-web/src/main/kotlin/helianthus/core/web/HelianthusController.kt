package helianthus.core.web

import helianthus.core.util.PathHandler
import helianthus.core.web.workflow.WorkFlowContext
import helianthus.core.web.workflow.WorkFlowFactory
import helianthus.core.web.workflow.WorkFlowStep
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelianthusController(
    private val pathHandler: PathHandler,
    private val workFlowFactory: WorkFlowFactory
) {

    @GetMapping("/api/op/**")
    fun handle(request: HttpServletRequest, response: HttpServletResponse) {
        val pathMappingResultBean = pathHandler.parsePath(request.servletPath)

        val workFlow = workFlowFactory.createWorkFlow(pathMappingResultBean)

        request.setAttribute(
            WorkFlowContext.PATH_MAPPING_RESULT_BEAN_KEY,
            pathMappingResultBean
        )
        executeWorkFlow(request, response, workFlow)
    }

    private fun executeWorkFlow(
        request: HttpServletRequest,
        response: HttpServletResponse,
        workFlow: MutableSet<WorkFlowStep>
    ) {
        val workFlowContext = WorkFlowContext()
        workFlowContext[WorkFlowContext.REQUEST_KEY] = request
        workFlowContext[WorkFlowContext.RESPONSE_KEY] = response

        for (step in workFlow) {
            if (!step.doStep(workFlowContext)) {
                break
            }
        }
    }
}
