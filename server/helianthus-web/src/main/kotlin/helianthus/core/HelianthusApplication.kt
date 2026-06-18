package helianthus.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HelianthusApplication

fun main(args: Array<String>) {
    runApplication<HelianthusApplication>(*args)
}
