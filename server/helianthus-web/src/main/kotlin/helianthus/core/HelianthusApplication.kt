package helianthus.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot entry point for the Helianthus web module.
 *
 * Auto-configures component scanning and boots the embedded server. The
 * application exposes catalog-backed `/api/op/&#42;&#42;` and entity
 * `/api/entities/&#42;&#42;` endpoints along with a `/health` probe.
 */
@SpringBootApplication
class HelianthusApplication

/**
 * Boots [HelianthusApplication] using Spring Boot's default launcher.
 */
fun main(args: Array<String>) {
    runApplication<HelianthusApplication>(*args)
}
