package helianthus.core.util

import helianthus.core.EntityNotFoundException
import helianthus.core.exception.InvalidOperationPathException
import org.springframework.stereotype.Component

@Component
class EntityPathHandler {

    companion object {
        private const val PATH_PREFIX = "/api/entities/"
    }

    fun parsePath(path: String?): EntityPathResult {
        if (path.isNullOrBlank()) {
            throw InvalidOperationPathException("path must not be null or blank")
        }
        if (!path.startsWith(PATH_PREFIX)) {
            throw InvalidOperationPathException("path must start with $PATH_PREFIX")
        }

        val content = path.removePrefix(PATH_PREFIX)

        if (content.isEmpty() || content == "/") {
            throw InvalidOperationPathException("path must contain an entity name")
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
            val entityName = beforeFormat.substring(0, lastSlashIndex)
            val id = beforeFormat.substring(lastSlashIndex + 1)

            if (entityName.isBlank()) {
                throw InvalidOperationPathException("entity name must not be blank: '$path'")
            }
            if (id.isBlank()) {
                throw InvalidOperationPathException("entity id must not be blank: '$path'")
            }

            EntityPathResult(entityName = entityName, id = id, format = format)
        } else {
            val entityName = beforeFormat
            if (entityName.isBlank()) {
                throw InvalidOperationPathException("entity name must not be blank: '$path'")
            }

            EntityPathResult(entityName = entityName, id = null, format = format)
        }
    }
}

data class EntityPathResult(
    val entityName: String,
    val id: String?,
    val format: String
)
