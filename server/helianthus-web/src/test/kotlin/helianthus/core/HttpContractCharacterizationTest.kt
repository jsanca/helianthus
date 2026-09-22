package helianthus.core

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Black-box HTTP characterization tests.
 *
 * These tests drive Helianthus exclusively over HTTP using the JDK HTTP client
 * and assert exact status codes, Content-Type headers, and (where stable) exact
 * response bodies. They intentionally avoid Spring test utilities so the same
 * behavioral contract can later be executed against a different runtime.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = [
    "helianthus.security.oauth2.enabled=true",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
])
@Import(HttpContractCharacterizationTest.UserConfig::class)
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
/**
 * Characterization tests pinning the HTTP contract of the Helianthus web
 * module: response shapes, content-type negotiation, status codes, and
 * role-based authorization across operation and entity endpoints.
 */
class HttpContractCharacterizationTest {

    @TestConfiguration
    class UserConfig {
        @Bean
        @Primary
        fun userDetailsService(): UserDetailsService {
            val guest = User.builder().username("guest").password("{noop}guest").roles("GUEST").build()
            val admin = User.builder().username("admin").password("{noop}admin").roles("ADMIN").build()
            return InMemoryUserDetailsManager(guest, admin)
        }
    }

    @LocalServerPort
    private var port: Int = 0

    private val client = HttpClient.newBuilder().build()

    private data class HttpResult(val status: Int, val contentType: String?, val body: String)

    private fun get(path: String, user: String? = null, pass: String? = null): HttpResult {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        if (user != null && pass != null) {
            val encoded = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
            builder.header("Authorization", "Basic $encoded")
        }
        val response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
        return HttpResult(
            status = response.statusCode(),
            contentType = response.headers().firstValue("Content-Type").orElse(null),
            body = response.body()
        )
    }

    // ------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------

    @Test
    fun `operation executes successfully and returns JSON`() {
        val r = get("/api/op/all-products/default.json", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
        assertEquals(EXPECTED_ALL_PRODUCTS_JSON, r.body)
    }

    @Test
    fun `omitted configuration defaults to default configuration`() {
        val r = get("/api/op/all-products.json", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
        assertEquals(EXPECTED_ALL_PRODUCTS_JSON, r.body)
    }

    @Test
    fun `explicit configuration applies projection`() {
        val r = get("/api/op/products/compact.json", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
        assertTrue(r.body.contains("PRODUCTCODE"))
        assertTrue(r.body.contains("PRODUCTNAME"))
        assertTrue(r.body.contains("PRODUCTLINE"))
        assertFalse(r.body.contains("BUYPRICE"))
        assertFalse(r.body.contains("QUANTITYINSTOCK"))
    }

    @Test
    fun `operation with parameter returns single row`() {
        val r = get("/api/op/get-product/default.json?productCode=S10_1678", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
        assertTrue(r.body.contains("\"rowCount\":1"))
        assertTrue(r.body.contains("S10_1678"))
    }

    @Test
    fun `operation with missing required parameter returns 400`() {
        val r = get("/api/op/get-product/default.json", "guest", "guest")

        assertEquals(400, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("Missing required parameter: 'productCode'", r.body)
    }

    @Test
    fun `unknown operation returns 404`() {
        val r = get("/api/op/nonexistent/default.json", "guest", "guest")

        assertEquals(404, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("Operation not found: nonexistent", r.body)
    }

    @Test
    fun `unknown configuration returns 404`() {
        val r = get("/api/op/products/nonexistent.json", "guest", "guest")

        assertEquals(404, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("Configuration 'nonexistent' not found for operation: products", r.body)
    }

    // ------------------------------------------------------------------
    // Entities
    // ------------------------------------------------------------------

    @Test
    fun `entity list returns JSON`() {
        val r = get("/api/entities/products.json?orderBy=PRODUCTCODE&orderDir=asc", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
        assertTrue(r.body.contains("\"rowCount\":3"))
    }

    @Test
    fun `entity get by primary key returns exact JSON`() {
        val r = get("/api/entities/products/S10_1678.json", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
        assertEquals(EXPECTED_ENTITY_GET_JSON, r.body)
    }

    @Test
    fun `unknown entity returns 404`() {
        val r = get("/api/entities/nonexistent.json", "guest", "guest")

        assertEquals(404, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("Entity not found: nonexistent", r.body)
    }

    @Test
    fun `entity row missing returns 404`() {
        val r = get("/api/entities/products/NONEXISTENT.json", "guest", "guest")

        assertEquals(404, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("Entity 'PRODUCTS' with id 'NONEXISTENT' not found", r.body)
    }

    @Test
    fun `entity filtering by field returns filtered rows`() {
        val r = get("/api/entities/products.json?PRODUCTLINE=Classic%20Cars", "guest", "guest")

        assertEquals(200, r.status)
        assertTrue(r.body.contains("\"rowCount\":2"))
        assertTrue(r.body.contains("Classic Cars"))
        assertFalse(r.body.contains("Motorcycles"))
    }

    @Test
    fun `entity ordering returns descending rows`() {
        val r = get("/api/entities/products.json?orderBy=PRODUCTCODE&orderDir=desc", "guest", "guest")

        assertEquals(200, r.status)
        val first = r.body.indexOf("S12_1099")
        val last = r.body.indexOf("S10_1678")
        assertTrue(first >= 0 && last >= 0 && first < last)
    }

    @Test
    fun `entity pagination limits and offsets rows`() {
        val r = get("/api/entities/products.json?orderBy=PRODUCTCODE&orderDir=asc&limit=1&offset=1", "guest", "guest")

        assertEquals(200, r.status)
        assertTrue(r.body.contains("\"rowCount\":1"))
        assertTrue(r.body.contains("S10_1949"))
        assertFalse(r.body.contains("S10_1678"))
    }

    @Test
    fun `entity filtering by unknown field returns 400`() {
        val r = get("/api/entities/products.json?INVALIDCOLUMN=value", "guest", "guest")

        assertEquals(400, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("Filter column 'INVALIDCOLUMN' is not in entity fields", r.body)
    }

    // ------------------------------------------------------------------
    // Formats
    // ------------------------------------------------------------------

    @Test
    fun `entity list returns CSV`() {
        val r = get("/api/entities/products.csv?orderBy=PRODUCTCODE&orderDir=asc", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("text/csv", r.contentType)
        assertEquals(
            listOf(
                "PRODUCTCODE,PRODUCTNAME,PRODUCTLINE,BUYPRICE,QUANTITYINSTOCK",
                "S10_1678,1969 Harley Davidson,Motorcycles,50.50,100",
                "S10_1949,1952 Alpine Renault 1300,Classic Cars,85.00,50",
                "S12_1099,1968 Ford Mustang,Classic Cars,95.00,0"
            ),
            r.body.trim().lines().filter { it.isNotBlank() }
        )
    }

    @Test
    fun `entity get by id returns XML`() {
        val r = get("/api/entities/products/S10_1678.xml", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("application/xml", r.contentType)
        assertTrue(r.body.contains("<rowCount>1</rowCount>"))
        assertTrue(r.body.contains("<PRODUCTCODE>S10_1678</PRODUCTCODE>"))
        assertTrue(r.body.contains("1969 Harley Davidson"))
    }

    @Test
    fun `entity get by id returns HTML`() {
        val r = get("/api/entities/products/S10_1678.html", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("text/html", r.contentType)
        assertTrue(r.body.contains("<table>"))
        assertTrue(r.body.contains("<th>PRODUCTCODE</th>"))
        assertTrue(r.body.contains("<td>S10_1678</td>"))
        assertTrue(r.body.contains("1 row(s)"))
    }

    // ------------------------------------------------------------------
    // Security
    // ------------------------------------------------------------------

    @Test
    fun `unauthenticated request returns 401`() {
        val r = get("/api/entities/products.json")

        assertEquals(401, r.status)
    }

    @Test
    fun `operation-level role insufficient returns 403`() {
        val r = get("/api/op/inventory-report/default.json", "guest", "guest")

        assertEquals(403, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("Access denied", r.body)
    }

    @Test
    fun `operation-level role sufficient returns 200`() {
        val r = get("/api/op/inventory-report/default.json", "admin", "admin")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
    }

    @Test
    fun `configuration-level role insufficient returns 403`() {
        val r = get("/api/op/config-secured/admin-only.json", "guest", "guest")

        assertEquals(403, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("Access denied", r.body)
    }

    @Test
    fun `configuration-level role sufficient returns 200`() {
        val r = get("/api/op/config-secured/admin-only.json", "admin", "admin")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
    }

    @Test
    fun `configuration without roles is accessible to any authenticated user`() {
        val r = get("/api/op/config-secured/default.json", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
    }

    @Test
    fun `entity read role insufficient returns 403`() {
        val r = get("/api/entities/customers.json", "guest", "guest")

        assertEquals(403, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("Access denied", r.body)
    }

    @Test
    fun `admin bypasses entity read role and returns 200`() {
        val r = get("/api/entities/customers.json", "admin", "admin")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
        assertTrue(r.body.contains("Atelier graphique"))
    }

    @Test
    fun `public catalog operation is accessible to guest`() {
        val r = get("/api/op/public-catalog/default.json", "guest", "guest")

        assertEquals(200, r.status)
        assertEquals("application/json", r.contentType)
    }

    // ------------------------------------------------------------------
    // Error semantics
    // ------------------------------------------------------------------

    @Test
    fun `invalid limit returns 400`() {
        val r = get("/api/entities/products.json?limit=abc", "guest", "guest")

        assertEquals(400, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("limit must be an integer", r.body)
    }

    @Test
    fun `invalid orderBy column returns 400`() {
        val r = get("/api/entities/products.json?orderBy=INVALIDCOLUMN", "guest", "guest")

        assertEquals(400, r.status)
        assertEquals("text/plain", r.contentType)
        assertEquals("orderBy column 'INVALIDCOLUMN' is not in entity fields", r.body)
    }

    @Test
    fun `unsupported format returns 406`() {
        val r = get("/api/op/all-products/default.txt", "guest", "guest")

        assertEquals(406, r.status)
    }

    companion object {
        private val EXPECTED_ENTITY_GET_JSON =
            """{"metadata":{"executionTimeMs":0,"rowCount":1},"rows":[{"PRODUCTCODE":"S10_1678","PRODUCTNAME":"1969 Harley Davidson","PRODUCTLINE":"Motorcycles","BUYPRICE":50.50,"QUANTITYINSTOCK":100}],"schema":{"columnCount":5,"columns":[{"name":"PRODUCTCODE","nullable":false,"type":"STRING"},{"name":"PRODUCTNAME","nullable":true,"type":"STRING"},{"name":"PRODUCTLINE","nullable":true,"type":"STRING"},{"name":"BUYPRICE","nullable":true,"type":"DECIMAL"},{"name":"QUANTITYINSTOCK","nullable":true,"type":"INTEGER"}]}}"""

        private val EXPECTED_ALL_PRODUCTS_JSON =
            """{"metadata":{"executionTimeMs":0,"rowCount":3},"rows":[{"PRODUCTCODE":"S10_1678","PRODUCTNAME":"1969 Harley Davidson","PRODUCTLINE":"Motorcycles","BUYPRICE":50.50,"QUANTITYINSTOCK":100},{"PRODUCTCODE":"S10_1949","PRODUCTNAME":"1952 Alpine Renault 1300","PRODUCTLINE":"Classic Cars","BUYPRICE":85.00,"QUANTITYINSTOCK":50},{"PRODUCTCODE":"S12_1099","PRODUCTNAME":"1968 Ford Mustang","PRODUCTLINE":"Classic Cars","BUYPRICE":95.00,"QUANTITYINSTOCK":0}],"schema":{"columnCount":5,"columns":[{"name":"PRODUCTCODE","nullable":false,"type":"STRING"},{"name":"PRODUCTNAME","nullable":true,"type":"STRING"},{"name":"PRODUCTLINE","nullable":true,"type":"STRING"},{"name":"BUYPRICE","nullable":true,"type":"DECIMAL"},{"name":"QUANTITYINSTOCK","nullable":true,"type":"INTEGER"}]}}"""
    }
}
