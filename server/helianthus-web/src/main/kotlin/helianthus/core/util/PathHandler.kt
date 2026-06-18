package helianthus.core.util

import helianthus.core.bean.PathMappingResultBean
import helianthus.core.exception.InvalidOperationPathException
import org.springframework.stereotype.Component

/**
 * Parses incoming operation request paths into structured components.
 *
 * Supports two path formats:
 *
 * **Legacy (default configuration):**
 * ```
 * /api/op/{operationId}.{format}
 * ```
 * Examples: /api/op/all-products.json, /api/op/get-product.json
 *
 * **Named configuration:**
 * ```
 * /api/op/{operationId}/{configurationId}.{format}
 * ```
 * Examples: /api/op/users/all.json, /api/op/users/names-and-ages.json
 *
 * The operation id is extracted as-is from the path without modification.
 * When no configuration id is present in the path, the "default" configuration is used.
 *
 * @throws InvalidOperationPathException if the path is null, blank, missing the prefix,
 *         missing a format extension, or otherwise malformed
 */
@Component
class PathHandler {

    companion object {
        private const val PATH_PREFIX = "/api/op/"
        const val DEFAULT_CONFIGURATION = "default"
    }

    fun parsePath(path: String?): PathMappingResultBean {
        if (path.isNullOrBlank()) {
            throw InvalidOperationPathException("path must not be null or blank")
        }
        if (!path.startsWith(PATH_PREFIX)) {
            throw InvalidOperationPathException("path must start with $PATH_PREFIX")
        }

        val content = path.removePrefix(PATH_PREFIX)

        if (content.isEmpty() || content == "/") {
            throw InvalidOperationPathException("path must contain an operation id")
        }

        val lastDotIndex = content.lastIndexOf('.')
        if (lastDotIndex <= 0) {
            throw InvalidOperationPathException("path must contain a format extension: '$path'")
        }
        if (lastDotIndex >= content.length - 1) {
            throw InvalidOperationPathException("format must not be blank: '$path'")
        }

        val format = content.substring(lastDotIndex + 1)
        if (format.isBlank()) {
            throw InvalidOperationPathException("format must not be blank: '$path'")
        }

        val beforeFormat = content.substring(0, lastDotIndex)

        val lastSlashIndex = beforeFormat.lastIndexOf('/')

        return if (lastSlashIndex >= 0) {
            val operationId = beforeFormat.substring(0, lastSlashIndex)
            val configurationId = beforeFormat.substring(lastSlashIndex + 1)

            if (operationId.isBlank()) {
                throw InvalidOperationPathException("operation id must not be blank: '$path'")
            }
            if (configurationId.isBlank()) {
                throw InvalidOperationPathException("configuration id must not be blank: '$path'")
            }

            PathMappingResultBean(
                operationId = operationId,
                format = format,
                configurationId = configurationId
            )
        } else {
            val rawOperationId = beforeFormat
            if (rawOperationId.isBlank()) {
                throw InvalidOperationPathException("operation id must not be blank: '$path'")
            }

            PathMappingResultBean(
                operationId = rawOperationId,
                format = format,
                configurationId = null
            )
        }
    }
}
