package helianthus.core.access.impl.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Data source implementation
 * Date: 5/22/14
 * Time: 10:12 PM
 * @author jsanca
 */
public class DataSourcePoolConnectionProvider implements ConnectionProvider {

    private static final Logger log = LoggerFactory.getLogger(DataSourcePoolConnectionProvider.class);

    private javax.sql.DataSource dataSource;

    /**
     * Get a connection
     *
     * @return Connection
     */
    @Override
    public Connection getConnection() {

        Connection connection = null;

        try {

            connection =  this.dataSource.getConnection();
        } catch (SQLException e) {

            log.error("Failed to obtain database connection", e);
        }

        return connection;
    } // getConnection.

    public void setDataSource(DataSource dataSource) {

        this.dataSource = dataSource;
    }
} // E:O:F:DataSourcePoolConnectionProvider.
