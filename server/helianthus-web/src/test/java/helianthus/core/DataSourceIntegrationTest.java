package helianthus.core;

import helianthus.core.util.OperationMappingHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
    classes = HelianthusApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class DataSourceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OperationMappingHelper mappingHelper;

    @Test
    void dataSourceShouldBeConfigured() {
        assertNotNull(dataSource, "DataSource should be auto-configured by Spring Boot");
    }

    @Test
    void queryConfigShouldBeLoaded() {
        assertNotNull(mappingHelper, "OperationMappingHelper should be loaded from queryConfig.xml");
        assertNotNull(mappingHelper.getQuery("/all-products"),
                "Operation /all-products should be loaded");
        assertNotNull(mappingHelper.getQuery("/all-productlines"),
                "Operation /all-productlines should be loaded");
        assertNotNull(mappingHelper.getQuery("/get-product"),
                "Operation /get-product should be loaded");
    }
}
