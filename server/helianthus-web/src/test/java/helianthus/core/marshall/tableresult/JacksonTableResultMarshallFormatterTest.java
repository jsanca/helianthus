package helianthus.core.marshall.tableresult;

import helianthus.core.bean.TableResultBean;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonTableResultMarshallFormatterTest {

    @Test
    void shouldSerializeTableResultToJson() throws Exception {
        TableResultBean bean = new TableResultBean();
        bean.setRowsCount(1);

        LinkedHashSet<String> columns = new LinkedHashSet<>();
        columns.add("id");
        columns.add("name");
        bean.setColumnNames(columns);

        ArrayList<Object> row = new ArrayList<>();
        row.add(1);
        row.add("test");
        ArrayList<ArrayList<Object>> rows = new ArrayList<>();
        rows.add(row);
        bean.setRowResultBeans(rows);

        JacksonTableResultMarshallFormatter formatter =
                new JacksonTableResultMarshallFormatter();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        formatter.process(outputStream, bean);

        String json = outputStream.toString("UTF-8");
        assertNotNull(json);
        assertTrue(json.contains("id"));
        assertTrue(json.contains("name"));
        assertTrue(json.contains("test"));
    }
}
