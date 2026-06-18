package helianthus.core

import org.junit.jupiter.api.Test
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
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@SpringBootTest(
    classes = [HelianthusApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = [
    "helianthus.security.oauth2.enabled=true",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
])
@Import(SecurityIntegrationTest.UserConfig::class)
@Sql(statements = [
    "DROP TABLE IF EXISTS products",
    "CREATE TABLE products (PRODUCTCODE VARCHAR(50) PRIMARY KEY, PRODUCTNAME VARCHAR(100), PRODUCTLINE VARCHAR(50), BUYPRICE DECIMAL(10,2))",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE) VALUES ('P001', 'Widget', 'Classic Cars', 50.00)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE) VALUES ('P002', 'Gadget', 'Motorcycles', 75.00)"
])
class SecurityIntegrationTest {

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

    @Test
    fun `health endpoint is public`() {
        val response = restTemplate.getForEntity(url("/health"), String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("\"status\":\"ok\""))
    }

    @Test
    fun `api endpoint without auth returns 401`() {
        try {
            restTemplate.getForEntity(
                url("/api/op/all-products/default.json"), String::class.java)
            fail("Expected 401")
        } catch (e: HttpClientErrorException) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.statusCode)
        }
    }

    @Test
    fun `authenticated guest can call operation`() {
        val template = restTemplateWithAuth("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/all-products/default.json"), String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("Widget"))
    }

    @Test
    fun `admin can call operation`() {
        val template = restTemplateWithAuth("admin", "admin")
        val response = template.getForEntity(
            url("/api/op/all-products/default.json"), String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `guest cannot access admin-only operation`() {
        val template = restTemplateWithAuth("guest", "guest")
        try {
            template.getForEntity(
                url("/api/op/admin-only/default.json"), String::class.java)
            fail("Expected 403")
        } catch (e: HttpClientErrorException) {
            assertEquals(HttpStatus.FORBIDDEN, e.statusCode)
        }
    }

    @Test
    fun `admin can access admin-only operation`() {
        val template = restTemplateWithAuth("admin", "admin")
        val response = template.getForEntity(
            url("/api/op/admin-only/default.json"), String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `admin can call catalog endpoint`() {
        val template = restTemplateWithAuth("admin", "admin")
        val response = template.getForEntity(
            url("/api/admin/catalog"), String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("operations"))
    }

    @Test
    fun `guest can call catalog endpoint`() {
        val template = restTemplateWithAuth("guest", "guest")
        val response = template.getForEntity(
            url("/api/admin/catalog"), String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `catalog endpoint without auth returns 401`() {
        try {
            restTemplate.getForEntity(
                url("/api/admin/catalog"), String::class.java)
            fail("Expected 401")
        } catch (e: HttpClientErrorException) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.statusCode)
        }
    }

    private fun restTemplateWithAuth(username: String, password: String): RestTemplate {
        return RestTemplate().apply {
            interceptors.add { request, body, execution ->
                val encoded = java.util.Base64.getEncoder()
                    .encodeToString("$username:$password".toByteArray())
                request.headers.set("Authorization", "Basic $encoded")
                execution.execute(request, body)
            }
        }
    }
}
