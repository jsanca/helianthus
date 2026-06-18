package helianthus.core.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Filter that adds request correlation ID and structured logging context.
 *
 * Generates a unique request ID for each request (or uses X-Request-ID header if provided)
 * and adds it to the MDC (Mapped Diagnostic Context) for structured logging.
 *
 * Also logs request start/end with timing information.
 */
@Component
@Order(1)
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-ID"
        const val REQUEST_ID_MDC_KEY = "requestId"
        const val USER_MDC_KEY = "user"
        const val OPERATION_ID_MDC_KEY = "operationId"
        const val CONFIGURATION_ID_MDC_KEY = "configurationId"
        const val FORMAT_MDC_KEY = "format"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startTime = System.currentTimeMillis()
        
        // Generate or extract request ID
        val requestId = request.getHeader(REQUEST_ID_HEADER) ?: UUID.randomUUID().toString()
        
        // Add to MDC for structured logging
        MDC.put(REQUEST_ID_MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)
        
        // Log request start
        log.info(
            "Request started: method={} path={} requestId={}",
            request.method,
            request.requestURI,
            requestId
        )
        
        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            
            // Log request completion
            log.info(
                "Request completed: method={} path={} status={} duration={}ms requestId={}",
                request.method,
                request.requestURI,
                response.status,
                duration,
                requestId
            )
            
            // Clear MDC
            MDC.clear()
        }
    }
}
