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
    "CREATE TABLE IF NOT EXISTS products (id INT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(50), name VARCHAR(100))",
    "DELETE FROM products",
    "INSERT INTO products (code, name) VALUES ('P001', 'Widget')",
    "INSERT INTO products (code, name) VALUES ('P002', 'Gadget')",
    "INSERT INTO products (code, name) VALUES ('P003', 'Thingamajig')"
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
