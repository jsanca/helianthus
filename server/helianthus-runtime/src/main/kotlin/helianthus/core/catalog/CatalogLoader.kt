package helianthus.core.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import helianthus.core.pipeline.PipelineConfig
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.InputStream

/**
 * Loads and validates a Helianthus catalog from YAML input.
 *
 * Owns the framework-neutral catalog parsing and JSON-Schema validation logic.
 * Produces both the [OperationCatalog] and [EntityCatalog] from a single read of
 * the catalog document.
 *
 * The input is a plain [InputStream]; resource acquisition and lifecycle are the
 * caller's responsibility.
 *
 * @param namingStrategy strategy used to derive physical column names from
 *                       logical field names during entity parsing
 */
internal class CatalogLoader(
    private val namingStrategy: PhysicalColumnNamingStrategy = LowercaseNamingStrategy()
) {

    /**
     * Loads a catalog from the given YAML [inputStream].
     *
     * @throws IllegalStateException if the document is empty, schema validation
     *         fails, or the catalog is structurally invalid
     */
    fun load(inputStream: InputStream): LoadedCatalog {
        val data = loadCatalogData(inputStream)
        validateSchema(data)

        val app = parseApp(data["app"])
        val datasources = parseDatasources(data["datasources"])
        val queries = parseQueries(data["queries"])
        val operations = parseOperations(data["operations"])

        val operationCatalog = OperationCatalog(app, datasources, queries, operations)
        log.info(
            "Loaded catalog: {} operations, {} queries, {} datasources",
            operations.size, queries.size, datasources.size
        )

        val entities = parseEntities(data["entities"])
        val entityCatalog = EntityCatalog(entities)
        entityCatalog.validate(datasources)
        log.info("Loaded entity catalog: {} entities", entities.size)

        return LoadedCatalog(operationCatalog, entityCatalog)
    }

    private fun loadCatalogData(inputStream: InputStream): Map<String, Any> {
        val yaml = Yaml()
        return inputStream.use { stream ->
            @Suppress("UNCHECKED_CAST")
            yaml.load<Map<String, Any>>(stream)
        } ?: throw IllegalStateException("operations.yml is empty")
    }

    private fun validateSchema(data: Map<String, Any>) {
        try {
            val schemaStream = CatalogLoader::class.java.classLoader
                ?.getResourceAsStream("schemas/operations.schema.json")
            if (schemaStream == null) {
                log.warn("Schema file not found, skipping validation")
                return
            }

            schemaStream.use { stream ->
                val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                val schema = factory.getSchema(stream)

                val mapper = ObjectMapper()
                val jsonNode = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(data)
                val violations = schema.validate(jsonNode)

                if (violations.isNotEmpty()) {
                    val errors = violations.joinToString("\n  - ") { it.message }
                    throw IllegalStateException("Schema validation failed:\n  - $errors")
                }
            }

            log.info("Schema validation passed")
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            log.warn("Schema validation error: {}", e.message)
        }
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
                required = p["required"] as? Boolean ?: false,
                label = p["label"] as? String,
                description = p["description"] as? String,
                placeholder = p["placeholder"] as? String,
                input = parseInputDef(p["input"])
            )
        } ?: emptyList()

        val configurations = parseConfigurations(raw["configurations"])

        return OperationDef(
            queryRef = raw["queryRef"] as? String,
            query = raw["query"] as? String,
            datasource = raw["datasource"] as? String,
            label = raw["label"] as? String,
            description = raw["description"] as? String,
            parameters = params,
            security = parseSecurity(raw["security"]),
            configurations = configurations
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseInputDef(raw: Any?): InputDef? {
        if (raw == null) return null
        val map = raw as? Map<String, Any> ?: return null
        return InputDef(
            kind = map["kind"] as? String ?: "text",
            options = (map["options"] as? List<*>)?.map { it.toString() },
            min = map["min"] as? Number,
            max = map["max"] as? Number,
            step = map["step"] as? Number
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
                label = def["label"] as? String,
                description = def["description"] as? String,
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

    @Suppress("UNCHECKED_CAST")
    private fun parseEntities(raw: Any?): Map<String, EntityDef> {
        if (raw == null) return emptyMap()
        val map = raw as? Map<String, Map<String, Any>> ?: return emptyMap()
        return map.mapValues { (name, def) ->
            parseEntityDef(name, def)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEntityDef(name: String, raw: Map<String, Any>): EntityDef {
        val primaryKey = when (val pk = raw["primaryKey"]) {
            is String -> PrimaryKeyDef(listOf(pk))
            is List<*> -> PrimaryKeyDef(pk.map { it.toString() })
            else -> throw IllegalStateException("Entity '$name' must have a 'primaryKey'")
        }

        val fields = (raw["fields"] as? List<*>)?.map { item ->
            when (item) {
                is String -> FieldDef(name = item, column = namingStrategy.toPhysicalColumn(item))
                is Map<*, *> -> {
                    val fieldName = item["name"]?.toString()
                        ?: throw IllegalStateException("Entity '$name' field entry must have a 'name'")
                    val column = item["column"]?.toString() ?: namingStrategy.toPhysicalColumn(fieldName)
                    FieldDef(name = fieldName, column = column)
                }
                else -> throw IllegalStateException("Entity '$name' field must be a string or {name, column} map")
            }
        } ?: throw IllegalStateException("Entity '$name' must have a 'fields' list")

        return EntityDef(
            label = raw["label"] as? String,
            description = raw["description"] as? String,
            datasource = raw["datasource"] as? String ?: "default",
            table = raw["table"] as? String ?: name,
            primaryKey = primaryKey,
            fields = fields,
            security = parseEntitySecurity(raw["security"])
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEntitySecurity(raw: Any?): EntitySecurityDef? {
        if (raw == null) return null
        val map = raw as? Map<String, Any> ?: return null
        return EntitySecurityDef(
            read = parseEntityRole(map["read"]),
            write = parseEntityRole(map["write"])
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEntityRole(raw: Any?): EntityRoleDef? {
        if (raw == null) return null
        val map = raw as? Map<String, Any> ?: return null
        val roles = (map["roles"] as? List<*>)?.map { it.toString() }
            ?: return null
        return EntityRoleDef(roles)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CatalogLoader::class.java)
    }
}

/**
 * Result of loading a Helianthus catalog: both the operation and entity
 * catalogs derived from a single YAML document.
 */
internal data class LoadedCatalog(
    val operationCatalog: OperationCatalog,
    val entityCatalog: EntityCatalog
)
