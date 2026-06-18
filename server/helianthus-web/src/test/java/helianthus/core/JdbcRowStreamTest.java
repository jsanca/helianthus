package helianthus.core;

import helianthus.core.access.GenericDataAccess;
import helianthus.core.result.CloseableRowStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    classes = HelianthusApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Sql(statements = {
    "CREATE TABLE IF NOT EXISTS products (id INT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(50), name VARCHAR(100))",
    "DELETE FROM products",
    "INSERT INTO products (code, name) VALUES ('P001', 'Widget')",
    "INSERT INTO products (code, name) VALUES ('P002', 'Gadget')",
    "INSERT INTO products (code, name) VALUES ('P003', 'Thingamajig')"
})
@SuppressWarnings({"unchecked", "rawtypes"})
class JdbcRowStreamTest {

    @Autowired
    private GenericDataAccess dataAccess;

    @Test
    void shouldStreamRowsFromDatabase() throws Exception {
        CloseableRowStream stream = dataAccess.executeQueryStream(
                "SELECT * FROM products ORDER BY id",
                new String[0],
                GenericDataAccess.DEFAULT_DATA_SOURCE,
                100,
                new Object[0]);

        try {
            assertNotNull(stream);
            assertNotNull(stream.getSchema());
            assertTrue(stream.getSchema().getColumnCount() >= 3);

            List<Map> rows = collectAll(stream.getRows().iterator());
            assertEquals(3, rows.size());
            assertEquals("Widget", rows.get(0).get("NAME"));
            assertEquals("Gadget", rows.get(1).get("NAME"));
            assertEquals("Thingamajig", rows.get(2).get("NAME"));
        } finally {
            stream.close();
        }
    }

    @Test
    void shouldLimitRowsFromStream() throws Exception {
        CloseableRowStream stream = dataAccess.executeQueryStream(
                "SELECT * FROM products ORDER BY id",
                new String[0],
                GenericDataAccess.DEFAULT_DATA_SOURCE,
                100,
                new Object[0]);

        try {
            List<Map> rows = collectLimit(stream.getRows().iterator(), 2);
            assertEquals(2, rows.size());
        } finally {
            stream.close();
        }
    }

    @Test
    void shouldCloseStreamWithoutErrors() throws Exception {
        CloseableRowStream stream = dataAccess.executeQueryStream(
                "SELECT * FROM products WHERE id = -1",
                new String[0],
                GenericDataAccess.DEFAULT_DATA_SOURCE,
                100,
                new Object[0]);

        stream.close();

        try {
            collectAll(stream.getRows().iterator());
        } catch (Exception ignored) {
        }
    }

    private static List<Map> collectAll(Iterator iterator) {
        List<Map> list = new ArrayList<>();
        while (iterator.hasNext()) {
            list.add((Map) iterator.next());
        }
        return list;
    }

    private static List<Map> collectLimit(Iterator iterator, int limit) {
        List<Map> list = new ArrayList<>();
        int count = 0;
        while (iterator.hasNext() && count < limit) {
            list.add((Map) iterator.next());
            count++;
        }
        return list;
    }
}
