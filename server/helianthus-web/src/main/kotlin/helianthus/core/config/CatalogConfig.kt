package helianthus.core.config

import helianthus.core.access.GenericDataAccess
import helianthus.core.access.impl.db.JdbcGenericDataAccess
import helianthus.core.catalog.AppMetadata
import helianthus.core.catalog.ConfigurationDef
import helianthus.core.catalog.DatasourceDef
import helianthus.core.catalog.OperationCatalog
import helianthus.core.catalog.OperationDef
import helianthus.core.catalog.ParameterDef
import helianthus.core.catalog.QueryDef
import helianthus.core.catalog.QueryParameterDef
import helianthus.core.catalog.SecurityDef
import helianthus.core.pipeline.PipelineConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.yaml.snakeyaml.Yaml
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
class CatalogConfig(
    @Value("\${helianthus.catalog.path:classpath:operations.yml}")
    private val catalogResource: Resource
) {

    @Bean
    fun genericDataAccess(dataSource: DataSource): GenericDataAccess {
        return JdbcGenericDataAccess(dataSource)
    }

    @Bean
    fun operationCatalog(): OperationCatalog {
        log.info("Loading operations catalog from: {}", catalogResource)

        val yaml = Yaml()
        val data = catalogResource.inputStream.use { inputStream ->
            @Suppress("UNCHECKED_CAST")
            yaml.load<Map<String, Any>>(inputStream)
        } ?: throw IllegalStateException("operations.yml is empty")

        val app = parseApp(data["app"])
        val datasources = parseDatasources(data["datasources"])
        val queries = parseQueries(data["queries"])
        val operations = parseOperations(data["operations"])

        val catalog = OperationCatalog(app, datasources, queries, operations)

        log.info("Loaded catalog: {} operations, {} queries, {} datasources",
            operations.size, queries.size, datasources.size)
        return catalog
    }

    private fun parseApp(raw: Any?): AppMetadata? {
        if (raw == null) return null
        @Suppress("UNCHECKED_CAST")
        val map = raw as? Map<String, Any> ?: return null
        return AppMetadata(name = map["name"] as? String)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDatasources(raw: Any?): Map<String, DatasourceDef> {
        if (raw == null) return emptyMap()
        val map = raw as? Map<String, Map<String, Any>> ?: return emptyMap()
        return map.mapValues { (_, v) ->
            DatasourceDef(type = v["type"] as? String ?: "postgres")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseQueries(raw: Any?): Map<String, QueryDef> {
        if (raw == null) return emptyMap()
        val map = raw as? Map<String, Map<String, Any>> ?: return emptyMap()
        return map.mapValues { (name, def) ->
            val sql = def["sql"] as? String
                ?: throw IllegalStateException("Query '$name' must have 'sql'")
            val parameters = parseQueryParameters(def["parameters"])
            QueryDef(
                datasource = def["datasource"] as? String ?: "default",
                sql = sql,
                parameters = parameters
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseQueryParameters(raw: Any?): Map<String, QueryParameterDef> {
        if (raw == null) return emptyMap()
        val map = raw as? Map<String, Map<String, Any>> ?: return emptyMap()
        return map.mapValues { (_, v) ->
            QueryParameterDef(
                type = v["type"] as? String ?: "string",
                required = v["required"] as? Boolean ?: false
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOperations(raw: Any?): Map<String, OperationDef> {
        require(raw != null) {
            "operations.yml must contain an 'operations' section"
        }

        return when (raw) {
            is List<*> -> {
                log.warn("Using legacy list-style operations format; migrate to map-based format")
                raw.filterIsInstance<Map<String, Any>>().associate { op ->
                    val name = op["name"] as? String
                        ?: throw IllegalStateException("Operation entry must have a 'name' field")
                    name to parseOperationDef(name, op)
                }
            }
            is Map<*, *> -> {
                (raw as Map<String, Map<String, Any>>).mapValues { (name, def) ->
                    parseOperationDef(name, def)
                }
            }
            else -> throw IllegalStateException(
                "'operations' must be a list or map, got: ${raw.javaClass.simpleName}"
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOperationDef(name: String, raw: Map<String, Any>): OperationDef {
        val params = (raw["parameters"] as? List<Map<String, Any>>)?.map { p ->
            ParameterDef(
                name = p["name"] as? String ?: "",
                type = p["type"] as? String ?: "string",
                required = p["required"] as? Boolean ?: false
            )
        } ?: emptyList()

        val configurations = parseConfigurations(raw["configurations"])

        return OperationDef(
            queryRef = raw["queryRef"] as? String,
            query = raw["query"] as? String,
            datasource = raw["datasource"] as? String,
            parameters = params,
            security = parseSecurity(raw["security"]),
            configurations = configurations
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseConfigurations(raw: Any?): Map<String, ConfigurationDef> {
        if (raw == null) {
            return mapOf("default" to ConfigurationDef())
        }
        val map = raw as? Map<String, Map<String, Any>>
            ?: return mapOf("default" to ConfigurationDef())

        return map.mapValues { (_, def) ->
            val pipelineSteps = (def["pipeline"] as? List<Map<String, Any>>) ?: emptyList()
            ConfigurationDef(
                security = parseSecurity(def["security"]),
                pipeline = PipelineConfig.fromYamlSteps(pipelineSteps)
            )
        }.ifEmpty {
            mapOf("default" to ConfigurationDef())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSecurity(raw: Any?): SecurityDef? {
        if (raw == null) return null
        val map = raw as? Map<String, Any> ?: return null
        return SecurityDef(
            roles = (map["roles"] as? List<*>)?.map { it.toString() },
            realm = map["realm"] as? String
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CatalogConfig::class.java)
    }
}
