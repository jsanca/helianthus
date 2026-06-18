package helianthus.core;

import helianthus.core.catalog.OperationCatalog;
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
    private OperationCatalog catalog;

    @Test
    void dataSourceShouldBeConfigured() {
        assertNotNull(dataSource, "DataSource should be auto-configured by Spring Boot");
    }

    @Test
    void operationsCatalogShouldBeLoaded() {
        assertNotNull(catalog, "OperationCatalog should be loaded from operations.yml");
        assertNotNull(
            catalog.resolveOperation("all-products", null),
            "Operation all-products should be loaded"
        );
        assertNotNull(
            catalog.resolveOperation("get-product", null),
            "Operation get-product should be loaded"
        );
    }
}
