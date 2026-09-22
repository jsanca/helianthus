package helianthus.core.config

import helianthus.core.HelianthusRuntime
import helianthus.core.HelianthusRuntimeBuilder
import helianthus.core.util.EntityPathHandler
import helianthus.core.util.PathHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import javax.sql.DataSource

/**
 * Spring bootstrap for the framework-neutral Helianthus runtime.
 *
 * The runtime is assembled through [HelianthusRuntimeBuilder]; this config only
 * supplies the catalog resource and the named datasources. No data-access or
 * pipeline implementation classes leak into the web layer.
 */
@Configuration(proxyBeanMethods = false)
class CatalogConfig(
    @Value("\${helianthus.catalog.path:classpath:operations.yml}")
    private val catalogResource: Resource
) {

    @Bean
    fun helianthusRuntime(
        @Qualifier("dataSources") dataSources: Map<String, DataSource>
    ): HelianthusRuntime {
        log.info("Building Helianthus runtime from catalog: {}", catalogResource)
        val builder = HelianthusRuntimeBuilder(dataSources)
        return catalogResource.inputStream.use { builder.build(it) }
    }

    @Bean
    fun pathHandler(): PathHandler = PathHandler()

    @Bean
    fun entityPathHandler(): EntityPathHandler = EntityPathHandler()

    companion object {
        private val log = LoggerFactory.getLogger(CatalogConfig::class.java)
    }
}
