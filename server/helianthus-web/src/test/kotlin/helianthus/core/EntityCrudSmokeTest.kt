package helianthus.core

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = [
    "helianthus.security.oauth2.enabled=true",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
])
@Sql(statements = [
    "DROP TABLE IF EXISTS products",
    "DROP TABLE IF EXISTS productlines",
    "DROP TABLE IF EXISTS customers",
    "CREATE TABLE products (PRODUCTCODE VARCHAR(50) PRIMARY KEY, PRODUCTNAME VARCHAR(100), PRODUCTLINE VARCHAR(50), BUYPRICE DECIMAL(10,2), QUANTITYINSTOCK INTEGER DEFAULT 0)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE, QUANTITYINSTOCK) VALUES ('S10_1678', '1969 Harley Davidson', 'Motorcycles', 50.50, 100)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE, QUANTITYINSTOCK) VALUES ('S10_1949', '1952 Alpine Renault 1300', 'Classic Cars', 85.00, 50)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE, QUANTITYINSTOCK) VALUES ('S12_1099', '1968 Ford Mustang', 'Classic Cars', 95.00, 0)",
    "CREATE TABLE productlines (PRODUCTLINE VARCHAR(50) PRIMARY KEY, TEXTDESCRIPTION VARCHAR(255))",
    "INSERT INTO productlines (PRODUCTLINE, TEXTDESCRIPTION) VALUES ('Classic Cars', 'Vintage cars')",
    "INSERT INTO productlines (PRODUCTLINE, TEXTDESCRIPTION) VALUES ('Motorcycles', 'Two-wheel vehicles')",
    "CREATE TABLE customers (CUSTOMERNUMBER INTEGER PRIMARY KEY, CUSTOMERNAME VARCHAR(100), CONTACTFIRSTNAME VARCHAR(50), CONTACTLASTNAME VARCHAR(50), CITY VARCHAR(50), COUNTRY VARCHAR(50))",
    "INSERT INTO customers (CUSTOMERNUMBER, CUSTOMERNAME, CONTACTFIRSTNAME, CONTACTLASTNAME, CITY, COUNTRY) VALUES (103, 'Atelier graphique', 'John', 'Doe', 'Nantes', 'France')"
])
class EntityCrudSmokeTest {

    @TestConfiguration
    class UserConfig {
        @Bean
        @Primary
        fun userDetailsService(): UserDetailsService {
            val guest = User.builder()
                .username("guest")
                .password("{noop}guest")
                .roles("GUEST")
                .build()
            val admin = User.builder()
                .username("admin")
                .password("{noop}admin")
                .roles("ADMIN")
                .build()
            return InMemoryUserDetailsManager(guest, admin)
        }
    }

    @LocalServerPort
    private var port: Int = 0

    private val restTemplate = RestTemplate()

    private fun url(path: String) = "http://localhost:$port$path"

    private fun authTemplate(username: String, password: String): RestTemplate {
        return RestTemplate().apply {
            interceptors.add { request, body, execution ->
                val encoded = java.util.Base64.getEncoder()
                    .encodeToString("$username:$password".toByteArray())
                request.headers.set("Authorization", "Basic $encoded")
                execution.execute(request, body)
            }
        }
    }

    @Test
    fun `entity list should return products`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/entities/products.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("rows"))
        assertTrue(response.body!!.contains("S10_1678"))
    }

    @Test
    fun `entity get by id should return single product`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/entities/products/S10_1678.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("S10_1678"))
        assertTrue(response.body!!.contains("1969 Harley Davidson"))
    }

    @Test
    fun `entity get by id with non-existent id should return 404`() {
        val template = authTemplate("guest", "guest")
        try {
            template.getForEntity(
                url("/api/entities/products/NONEXISTENT.json"),
                String::class.java
            )
            kotlin.test.fail("Expected 404 Not Found")
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
        }
    }

    @Test
    fun `entity list with filter should return filtered products`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/entities/products.json?PRODUCTLINE=Classic Cars"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Classic Cars"))
        assertFalse(response.body!!.contains("Motorcycles"))
    }

    @Test
    fun `entity list with limit should return limited products`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/entities/products.json?limit=2"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("rows"))
    }

    @Test
    fun `entity list with invalid filter column should return 400`() {
        val template = authTemplate("guest", "guest")
        try {
            template.getForEntity(
                url("/api/entities/products.json?INVALIDCOLUMN=value"),
                String::class.java
            )
            kotlin.test.fail("Expected 400 Bad Request")
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
        }
    }

    @Test
    fun `entity list for admin-only entity should be denied to guest`() {
        val guestTemplate = authTemplate("guest", "guest")
        try {
            guestTemplate.getForEntity(
                url("/api/entities/customers.json"),
                String::class.java
            )
            kotlin.test.fail("Expected 403 Forbidden")
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertEquals(HttpStatus.FORBIDDEN, e.statusCode)
        }
    }

    @Test
    fun `entity list for admin-only entity should work for admin`() {
        val adminTemplate = authTemplate("admin", "admin")
        val response = adminTemplate.getForEntity(
            url("/api/entities/customers.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Atelier graphique"))
    }

    @Test
    fun `non-existent entity should return 404`() {
        val template = authTemplate("guest", "guest")
        try {
            template.getForEntity(
                url("/api/entities/nonexistent.json"),
                String::class.java
            )
            kotlin.test.fail("Expected 404 Not Found")
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
        }
    }

    @Test
    fun `entity list should work in CSV format`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/entities/products.csv"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("PRODUCTCODE"))
    }

    @Test
    fun `entity list should work in XML format`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/entities/products.xml"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("PRODUCTCODE") || response.body!!.contains("productcode"))
    }

    @Test
    fun `entity list should work in HTML format`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/entities/products.html"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("<table>"))
    }
}
