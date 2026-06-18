package helianthus.core

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = [
    "helianthus.security.oauth2.enabled=true",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
])
@Import(StarterOperationsSmokeTest.UserConfig::class)
@Sql(statements = [
    "DROP TABLE IF EXISTS products",
    "DROP TABLE IF EXISTS productlines",
    "CREATE TABLE products (PRODUCTCODE VARCHAR(50) PRIMARY KEY, PRODUCTNAME VARCHAR(100), PRODUCTLINE VARCHAR(50), BUYPRICE DECIMAL(10,2))",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE) VALUES ('S10_1678', '1969 Harley Davidson', 'Motorcycles', 50.50)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE) VALUES ('S10_1949', '1952 Alpine Renault 1300', 'Classic Cars', 85.00)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE) VALUES ('S12_1099', '1968 Ford Mustang', 'Classic Cars', 95.00)",
    "CREATE TABLE productlines (PRODUCTLINE VARCHAR(50) PRIMARY KEY, TEXTDESCRIPTION VARCHAR(255))",
    "INSERT INTO productlines (PRODUCTLINE, TEXTDESCRIPTION) VALUES ('Classic Cars', 'Vintage cars')",
    "INSERT INTO productlines (PRODUCTLINE, TEXTDESCRIPTION) VALUES ('Motorcycles', 'Two-wheel vehicles')"
])
class StarterOperationsSmokeTest {

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
    fun `products default should return data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products/default.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("rows"))
    }

    @Test
    fun `products compact should return projected columns`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products/compact.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("PRODUCTCODE"))
    }

    @Test
    fun `products expensive should return filtered data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products/expensive.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("BUYPRICE"))
    }

    @Test
    fun `productlines default should return data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/productlines/default.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Classic Cars"))
    }

    @Test
    fun `product with valid code should return single product`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/product/default.json?productCode=S10_1678"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("S10_1678"))
    }

    @Test
    fun `product without required parameter should return 400`() {
        val template = authTemplate("guest", "guest")
        try {
            template.getForEntity(
                url("/api/op/product/default.json"),
                String::class.java
            )
            kotlin.test.fail("Expected 400 Bad Request")
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
            assertTrue(e.responseBodyAsString.contains("Missing required parameter"))
        }
    }

    @Test
    fun `products should work in CSV format`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products/default.csv"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("PRODUCTCODE"))
    }

    @Test
    fun `products should work in XML format`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products/default.xml"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("PRODUCTCODE") || response.body!!.contains("productcode"))
    }

    @Test
    fun `products should work in HTML format`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products/default.html"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("<table>"))
    }
}
