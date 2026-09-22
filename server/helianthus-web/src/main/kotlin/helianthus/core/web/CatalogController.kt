package helianthus.core.web

import helianthus.core.HelianthusRuntime
import helianthus.core.catalog.CatalogSummary
import helianthus.core.security.toPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Exposes the runtime's catalog summary at `/api/admin/catalog`.
 *
 * Used by the Helianthus client UI to discover available operations and
 * entities. Returns an empty summary when the request is unauthenticated.
 */
@RestController
class CatalogController(
    private val runtime: HelianthusRuntime
) {

    /**
     * Returns the catalog summary scoped to the authenticated caller. An empty
     * summary is returned when no Spring authentication is present.
     */
    @GetMapping("/api/admin/catalog")
    fun catalog(): CatalogSummary {
        val principal = SecurityContextHolder.getContext().authentication?.toPrincipal()
            ?: return CatalogSummary(
                app = null,
                formats = emptyList(),
                operations = emptyList(),
                entities = emptyList()
            )

        return runtime.catalogSummary(principal)
    }
}
