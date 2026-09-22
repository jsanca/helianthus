package helianthus.core

/**
 * Thrown when a requested entity cannot be located by its primary key.
 *
 * @param message description of which entity could not be found
 */
class EntityNotFoundException(message: String) : RuntimeException(message)
