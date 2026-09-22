package helianthus.core.access

import helianthus.core.access.impl.db.H2Dialect
import helianthus.core.access.impl.db.JdbcGenericDataAccess
import helianthus.core.access.impl.db.PostgresDialect
import javax.sql.DataSource

/**
 * Constructs the JDBC-backed data-access implementation without exposing the
 * underlying implementation classes.
 *
 * This is a cross-module contract: the runtime (via its builder) consumes it to
 * assemble a [GenericDataAccess] and a dialect map from a set of named
 * [DataSource]s, while the concrete JDBC/dialect implementations remain hidden.
 */
object DataAccessFactory {

    /**
     * Creates a JDBC-backed [GenericDataAccess] over the given named datasources.
     */
    fun jdbc(dataSources: Map<String, DataSource>): GenericDataAccess =
        JdbcGenericDataAccess(dataSources)

    /**
     * Derives a [SqlDialect] per named datasource. Datasource names containing
     * "h2" select the H2 dialect; all others select the PostgreSQL dialect.
     */
    fun sqlDialects(dataSources: Map<String, DataSource>): Map<String, SqlDialect> =
        dataSources.mapValues { (name, _) ->
            when {
                name.contains("h2", ignoreCase = true) -> H2Dialect()
                else -> PostgresDialect()
            }
        }
}
