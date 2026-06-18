package helianthus.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(statements = {
    "DROP TABLE IF EXISTS products",
    "CREATE TABLE products (PRODUCTCODE VARCHAR(50) PRIMARY KEY, PRODUCTNAME VARCHAR(100), PRODUCTLINE VARCHAR(50), BUYPRICE DECIMAL(10,2))",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE) VALUES ('P001', 'Widget', 'Classic Cars', 50.00)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE) VALUES ('P002', 'Gadget', 'Motorcycles', 75.00)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE) VALUES ('P003', 'Thingamajig', 'Planes', 100.00)"
})
class LegacyQueryApiTest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private DataSource dataSource;

    @Test
    void healthEndpointShouldWork() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"status\":\"ok\""));
    }

    @Test
    void shouldReturnJsonForAllProducts() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/op/all-products.json", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("Widget"));
        assertTrue(body.contains("Gadget"));
        assertTrue(body.contains("Thingamajig"));
    }

    @Test
    void shouldReturn400ForBadPath() {
        try {
            restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/op/", String.class);
            fail("Expected HttpClientErrorException");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertTrue(e.getResponseBodyAsString().contains("operation id"));
        }
    }

    @Test
    void shouldReturn404ForUnknownOperation() {
        try {
            restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/op/nonexistent.json", String.class);
            fail("Expected HttpClientErrorException");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @Test
    void shouldRejectUnsupportedFormat() {
        try {
            restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/op/all-products.bin", String.class);
            fail("Expected HttpClientErrorException");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.NOT_ACCEPTABLE, e.getStatusCode());
        }
    }
}
