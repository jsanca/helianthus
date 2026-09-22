package helianthus.core.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public liveness probe exposed at `/health`. Returns a small static payload
 * suitable for container readiness checks.
 */
@RestController
class HealthController {

    /**
     * Returns a minimal health payload indicating the service is up.
     */
    @GetMapping("/health")
    fun health(): Map<String, String> {
        return mapOf("status" to "ok", "service" to "helianthus")
    }
}
