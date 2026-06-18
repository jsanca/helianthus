package helianthus.core.marshall.tableresult;

import helianthus.core.result.ResultColumn;
import helianthus.core.result.ResultFrame;
import helianthus.core.result.ResultMetadata;
import helianthus.core.result.ResultSchema;
import helianthus.core.result.ResultType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JacksonResultFrameMarshallFormatterTest {

    @Test
    void shouldSerializeResultFrameToJson() throws Exception {
        List<ResultColumn> columns = List.of(
                new ResultColumn("id", ResultType.INTEGER, false),
                new ResultColumn("name", ResultType.STRING, true));

        ResultSchema schema = new ResultSchema(columns);

        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1, "name", "Widget"),
                Map.of("id", 2, "name", "Gadget"));

        ResultFrame frame = new ResultFrame(schema, rows,
                new ResultMetadata(2, 42L));

        JacksonResultFrameMarshallFormatter formatter =
                new JacksonResultFrameMarshallFormatter();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        formatter.process(outputStream, frame);

        String json = outputStream.toString("UTF-8");
        assertNotNull(json);
        assertTrue(json.contains("\"schema\""));
        assertTrue(json.contains("\"columns\""));
        assertTrue(json.contains("\"name\":\"id\""));
        assertTrue(json.contains("\"type\":\"INTEGER\""));
        assertTrue(json.contains("\"name\":\"name\""));
        assertTrue(json.contains("\"type\":\"STRING\""));
        assertTrue(json.contains("\"rows\""));
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"name\":\"Widget\""));
        assertTrue(json.contains("\"id\":2"));
        assertTrue(json.contains("\"name\":\"Gadget\""));
        assertTrue(json.contains("\"metadata\""));
        assertTrue(json.contains("\"rowCount\":2"));
        assertTrue(json.contains("\"executionTimeMs\":42"));
    }

    @Test
    void shouldHandleNullValuesInJsonOutput() throws Exception {
        List<ResultColumn> columns = List.of(
                new ResultColumn("id", ResultType.INTEGER, true),
                new ResultColumn("name", ResultType.STRING, true));
        ResultSchema schema = new ResultSchema(columns);
        List<Map<String, Object>> rows = List.of(
                toRow("id", 1, "name", (Object) null));
        ResultFrame frame = new ResultFrame(schema, rows,
                new ResultMetadata(1));
        JacksonResultFrameMarshallFormatter formatter =
                new JacksonResultFrameMarshallFormatter();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        formatter.process(outputStream, frame);
        String json = outputStream.toString("UTF-8");
        assertNotNull(json);
        assertTrue(json.contains("\"name\":null"));
    }

    @Test
    void shouldHandleEmptyResultFrame() throws Exception {
        ResultSchema schema = new ResultSchema(List.of());
        ResultFrame frame = new ResultFrame(schema, List.of());
        JacksonResultFrameMarshallFormatter formatter =
                new JacksonResultFrameMarshallFormatter();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        formatter.process(outputStream, frame);
        String json = outputStream.toString("UTF-8");
        assertNotNull(json);
        assertTrue(json.contains("\"columns\":[]"));
        assertTrue(json.contains("\"rows\":[]"));
    }

    private static Map<String, Object> toRow(Object... keysAndValues) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }
}
