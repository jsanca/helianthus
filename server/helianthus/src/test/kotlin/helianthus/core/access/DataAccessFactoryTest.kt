package helianthus.core.access

import helianthus.core.access.impl.db.H2Dialect
import helianthus.core.access.impl.db.PostgresDialect
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.assertTrue

/**
 * Verifies [DataAccessFactory] picks the correct [SqlDialect] based on the
 * datasource name (H2 when the name contains "h2", PostgreSQL otherwise) and
 * returns a [GenericDataAccess] over the supplied datasources.
 */
class DataAccessFactoryTest {

    private val fakeDataSource = object : DataSource {
        override fun getConnection(): Connection = throw UnsupportedOperationException()
        override fun getConnection(username: String, password: String): Connection = throw UnsupportedOperationException()
        override fun getLogWriter(): PrintWriter = throw UnsupportedOperationException()
        override fun setLogWriter(out: PrintWriter) = Unit
        override fun setLoginTimeout(seconds: Int) = Unit
        override fun getLoginTimeout(): Int = 0
        override fun getParentLogger(): Logger = Logger.getLogger("helianthus-test")
        override fun <T> unwrap(iface: Class<T>): T = throw UnsupportedOperationException()
        override fun isWrapperFor(iface: Class<*>): Boolean = false
    }

    @Test
    fun `jdbc returns a generic data access`() {
        val dataAccess = DataAccessFactory.jdbc(mapOf("default" to fakeDataSource))

        assertTrue(dataAccess is GenericDataAccess)
    }

    @Test
    fun `selects postgres dialect for names not containing h2`() {
        val dialects = DataAccessFactory.sqlDialects(mapOf("default" to fakeDataSource))

        assertTrue(dialects["default"] is PostgresDialect)
    }

    @Test
    fun `selects h2 dialect for names containing h2`() {
        val dialects = DataAccessFactory.sqlDialects(
            mapOf("default" to fakeDataSource, "secondary-h2" to fakeDataSource)
        )

        assertTrue(dialects["default"] is PostgresDialect)
        assertTrue(dialects["secondary-h2"] is H2Dialect)
    }
}
