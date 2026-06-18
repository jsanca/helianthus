package helianthus.core.result;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResultFrameTest {

    @Test
    void shouldCreateResultFrameWithSchemaAndRows() {
        List<ResultColumn> columns = List.of(
                new ResultColumn("id", ResultType.INTEGER, false),
                new ResultColumn("name", ResultType.STRING, true));
        ResultSchema schema = new ResultSchema(columns);

        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1, "name", "Widget"),
                Map.of("id", 2, "name", "Gadget"));

        ResultFrame frame = new ResultFrame(schema, rows,
                new ResultMetadata(2, 15L));

        assertEquals(2, frame.getSchema().getColumnCount());
        assertEquals("id", frame.getSchema().getColumns().get(0).getName());
        assertEquals(ResultType.INTEGER,
                frame.getSchema().getColumns().get(0).getType());
        assertFalse(frame.getSchema().getColumns().get(0).getNullable());

        assertEquals(2, frame.getRows().size());
        assertEquals(1, frame.getRows().get(0).get("id"));
        assertEquals("Widget", frame.getRows().get(0).get("name"));

        assertEquals(2, frame.getMetadata().getRowCount());
        assertEquals(15L, frame.getMetadata().getExecutionTimeMs());
    }

    @Test
    void shouldHandleNullValuesInRows() {
        List<ResultColumn> columns = List.of(
                new ResultColumn("id", ResultType.INTEGER, true),
                new ResultColumn("name", ResultType.STRING, true));
        ResultSchema schema = new ResultSchema(columns);

        List<Map<String, Object>> rows = List.of(
                toRow("id", 1, "name", (Object) null));

        ResultFrame frame = new ResultFrame(schema, rows);

        assertEquals(1, frame.getRows().size());
        assertEquals(1, frame.getRows().get(0).get("id"));
        assertNull(frame.getRows().get(0).get("name"));
    }

    @Test
    void shouldUseDefaultMetadataWhenNotProvided() {
        ResultSchema schema = new ResultSchema(List.of());
        ResultFrame frame = new ResultFrame(schema, List.of());

        assertEquals(0, frame.getMetadata().getRowCount());
        assertEquals(0L, frame.getMetadata().getExecutionTimeMs());
    }

    @Test
    void shouldLookupColumnByName() {
        ResultSchema schema = new ResultSchema(List.of(
                new ResultColumn("id", ResultType.INTEGER, false),
                new ResultColumn("name", ResultType.STRING, true)));

        ResultColumn col = schema.getColumn("name");
        assertNotNull(col);
        assertEquals(ResultType.STRING, col.getType());

        assertNull(schema.getColumn("nonexistent"));
    }

    @Test
    void shouldLookupColumnByIndex() {
        ResultSchema schema = new ResultSchema(List.of(
                new ResultColumn("id", ResultType.INTEGER, false),
                new ResultColumn("name", ResultType.STRING, true)));

        assertEquals("id", schema.getColumn(0).getName());
        assertEquals("name", schema.getColumn(1).getName());
        assertNull(schema.getColumn(2));
        assertNull(schema.getColumn(-1));
    }

    @Test
    void shouldPreserveColumnOrder() {
        List<ResultColumn> columns = List.of(
                new ResultColumn("a", ResultType.STRING),
                new ResultColumn("b", ResultType.STRING),
                new ResultColumn("c", ResultType.STRING));

        ResultSchema schema = new ResultSchema(columns);

        assertEquals(3, schema.getColumnCount());
        assertEquals("a", schema.getColumns().get(0).getName());
        assertEquals("b", schema.getColumns().get(1).getName());
        assertEquals("c", schema.getColumns().get(2).getName());
    }

    @Test
    void shouldDefaultToUnknownType() {
        ResultColumn col = new ResultColumn("x");
        assertEquals(ResultType.UNKNOWN, col.getType());
        assertTrue(col.getNullable());
    }

    private static Map<String, Object> toRow(Object... keysAndValues) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }
}
