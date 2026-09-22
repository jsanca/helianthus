package helianthus.core;

import helianthus.core.HelianthusRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    classes = HelianthusApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class DataSourceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private HelianthusRuntime runtime;

    @Test
    void dataSourceShouldBeConfigured() {
        assertNotNull(dataSource, "DataSource should be auto-configured by Spring Boot");
    }

    @Test
    void helianthusRuntimeShouldBeBuiltFromCatalog() {
        assertNotNull(runtime, "HelianthusRuntime should be built from operations.yml");
    }
}
