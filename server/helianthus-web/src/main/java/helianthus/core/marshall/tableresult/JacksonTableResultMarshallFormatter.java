package helianthus.core.marshall.tableresult;

import helianthus.core.MarshallFormatterException;
import helianthus.core.bean.TableResultBean;
import helianthus.core.marshall.MarshallFormatter;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;

public class JacksonTableResultMarshallFormatter implements MarshallFormatter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(OutputStream outputStream, TableResultBean tableResultBean) {
        try {
            objectMapper.writeValue(outputStream, tableResultBean);
        } catch (Exception e) {
            throw new MarshallFormatterException(e);
        }
    }
}
