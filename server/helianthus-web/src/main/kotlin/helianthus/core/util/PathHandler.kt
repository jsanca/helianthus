package helianthus.core.util

import helianthus.core.bean.PathMappingResultBean
import helianthus.core.exception.InvalidOperationPathException

class PathHandler {

    companion object {
        private const val PATH_PREFIX = "/api/op/"
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

        val rawOperationId = content.substring(0, lastDotIndex)
        val format = content.substring(lastDotIndex + 1)

        if (rawOperationId.isBlank()) {
            throw InvalidOperationPathException("operation id must not be blank: '$path'")
        }
        if (format.isBlank()) {
            throw InvalidOperationPathException("format must not be blank: '$path'")
        }

        return PathMappingResultBean(
            operationId = "/$rawOperationId",
            format = format
        )
    }
}
