package helianthus.core.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

@Configuration
class DataSourceConfig {

    private val log = LoggerFactory.getLogger(DataSourceConfig::class.java)

    @Bean
    @Primary
    fun primaryDataSource(
        @Value("\${spring.datasource.url}") url: String,
        @Value("\${spring.datasource.username}") username: String,
        @Value("\${spring.datasource.password}") password: String,
        @Value("\${spring.datasource.driver-class-name}") driverClassName: String
    ): DataSource {
        log.info("Creating primary datasource with URL: {}", url)
        val config = HikariConfig().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            this.driverClassName = driverClassName
            poolName = "PrimaryHikariPool"
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 300000
            connectionTimeout = 20000
            maxLifetime = 1200000
        }
        return HikariDataSource(config)
    }

    @Bean
    fun secondaryDataSource(
        @Value("\${spring.datasource-secondary.url}") url: String,
        @Value("\${spring.datasource-secondary.username}") username: String,
        @Value("\${spring.datasource-secondary.password}") password: String,
        @Value("\${spring.datasource-secondary.driver-class-name}") driverClassName: String
    ): DataSource {
        log.info("Creating secondary datasource with URL: {}", url)
        val config = HikariConfig().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            this.driverClassName = driverClassName
            poolName = "SecondaryHikariPool"
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 300000
            connectionTimeout = 20000
            maxLifetime = 1200000
        }
        return HikariDataSource(config)
    }

    @Bean
    fun dataSources(
        @Qualifier("primaryDataSource") primaryDataSource: DataSource,
        @Qualifier("secondaryDataSource") secondaryDataSource: DataSource
    ): Map<String, DataSource> {
        val map = mapOf(
            "default" to primaryDataSource,
            "secondary" to secondaryDataSource
        )
        log.info("Created dataSources map with keys: {}", map.keys)
        return map
    }
}
