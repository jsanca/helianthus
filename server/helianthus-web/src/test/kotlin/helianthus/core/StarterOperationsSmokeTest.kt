package helianthus.core

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.beans.factory.SmartInitializingSingleton
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
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

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
    "DROP TABLE IF EXISTS customers",
    "DROP TABLE IF EXISTS orders",
    "CREATE TABLE products (PRODUCTCODE VARCHAR(50) PRIMARY KEY, PRODUCTNAME VARCHAR(100), PRODUCTLINE VARCHAR(50), BUYPRICE DECIMAL(10,2), QUANTITYINSTOCK INTEGER DEFAULT 0)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE, QUANTITYINSTOCK) VALUES ('S10_1678', '1969 Harley Davidson', 'Motorcycles', 50.50, 100)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE, QUANTITYINSTOCK) VALUES ('S10_1949', '1952 Alpine Renault 1300', 'Classic Cars', 85.00, 50)",
    "INSERT INTO products (PRODUCTCODE, PRODUCTNAME, PRODUCTLINE, BUYPRICE, QUANTITYINSTOCK) VALUES ('S12_1099', '1968 Ford Mustang', 'Classic Cars', 95.00, 0)",
    "CREATE TABLE productlines (PRODUCTLINE VARCHAR(50) PRIMARY KEY, TEXTDESCRIPTION VARCHAR(255))",
    "INSERT INTO productlines (PRODUCTLINE, TEXTDESCRIPTION) VALUES ('Classic Cars', 'Vintage cars')",
    "INSERT INTO productlines (PRODUCTLINE, TEXTDESCRIPTION) VALUES ('Motorcycles', 'Two-wheel vehicles')",
    "INSERT INTO productlines (PRODUCTLINE, TEXTDESCRIPTION) VALUES ('Planes', 'Aircraft models')",
    "CREATE TABLE customers (CUSTOMERNUMBER INTEGER PRIMARY KEY, CUSTOMERNAME VARCHAR(100), CONTACTFIRSTNAME VARCHAR(50), CONTACTLASTNAME VARCHAR(50), CITY VARCHAR(50), COUNTRY VARCHAR(50), CREDITLIMIT DECIMAL(10,2))",
    "INSERT INTO customers (CUSTOMERNUMBER, CUSTOMERNAME, CONTACTFIRSTNAME, CONTACTLASTNAME, CITY, COUNTRY, CREDITLIMIT) VALUES (103, 'Atelier graphique', 'Carine', 'Schmitt', 'Nantes', 'France', 21000.00)",
    "INSERT INTO customers (CUSTOMERNUMBER, CUSTOMERNAME, CONTACTFIRSTNAME, CONTACTLASTNAME, CITY, COUNTRY, CREDITLIMIT) VALUES (112, 'Signal Gift Stores', 'Jean', 'King', 'Las Vegas', 'USA', 71800.00)",
    "INSERT INTO customers (CUSTOMERNUMBER, CUSTOMERNAME, CONTACTFIRSTNAME, CONTACTLASTNAME, CITY, COUNTRY, CREDITLIMIT) VALUES (114, 'Australian Collectors', 'Peter', 'Ferguson', 'Melbourne', 'Australia', 117300.00)",
    "CREATE TABLE orders (ORDERNUMBER INTEGER PRIMARY KEY, ORDERDATE DATE, REQUIREDDATE DATE, SHIPPEDDATE DATE, STATUS VARCHAR(15), CUSTOMERNUMBER INTEGER)",
    "INSERT INTO orders (ORDERNUMBER, ORDERDATE, REQUIREDDATE, SHIPPEDDATE, STATUS, CUSTOMERNUMBER) VALUES (10100, '2003-01-06', '2003-01-13', '2003-01-10', 'Shipped', 103)",
    "INSERT INTO orders (ORDERNUMBER, ORDERDATE, REQUIREDDATE, SHIPPEDDATE, STATUS, CUSTOMERNUMBER) VALUES (10101, '2003-01-09', '2003-01-18', '2003-01-11', 'Shipped', 112)"
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

        @Bean
        fun secondaryDatabaseInitializer(
            @Qualifier("secondaryDataSource") ds: javax.sql.DataSource
        ): SmartInitializingSingleton {
            return SmartInitializingSingleton {
                ds.connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("DROP TABLE IF EXISTS customers")
                        stmt.execute("DROP TABLE IF EXISTS orders")
                        stmt.execute("CREATE TABLE customers (CUSTOMERNUMBER INTEGER PRIMARY KEY, CUSTOMERNAME VARCHAR(100), CONTACTFIRSTNAME VARCHAR(50), CONTACTLASTNAME VARCHAR(50), CITY VARCHAR(50), COUNTRY VARCHAR(50), CREDITLIMIT DECIMAL(10,2))")
                        stmt.execute("INSERT INTO customers (CUSTOMERNUMBER, CUSTOMERNAME, CONTACTFIRSTNAME, CONTACTLASTNAME, CITY, COUNTRY, CREDITLIMIT) VALUES (103, 'Atelier graphique', 'Carine', 'Schmitt', 'Nantes', 'France', 21000.00)")
                        stmt.execute("INSERT INTO customers (CUSTOMERNUMBER, CUSTOMERNAME, CONTACTFIRSTNAME, CONTACTLASTNAME, CITY, COUNTRY, CREDITLIMIT) VALUES (112, 'Signal Gift Stores', 'Jean', 'King', 'Las Vegas', 'USA', 71800.00)")
                        stmt.execute("INSERT INTO customers (CUSTOMERNUMBER, CUSTOMERNAME, CONTACTFIRSTNAME, CONTACTLASTNAME, CITY, COUNTRY, CREDITLIMIT) VALUES (114, 'Australian Collectors', 'Peter', 'Ferguson', 'Melbourne', 'Australia', 117300.00)")
                        stmt.execute("CREATE TABLE orders (ORDERNUMBER INTEGER PRIMARY KEY, ORDERDATE DATE, REQUIREDDATE DATE, SHIPPEDDATE DATE, STATUS VARCHAR(15), CUSTOMERNUMBER INTEGER)")
                        stmt.execute("INSERT INTO orders (ORDERNUMBER, ORDERDATE, REQUIREDDATE, SHIPPEDDATE, STATUS, CUSTOMERNUMBER) VALUES (10100, '2003-01-06', '2003-01-13', '2003-01-10', 'Shipped', 103)")
                        stmt.execute("INSERT INTO orders (ORDERNUMBER, ORDERDATE, REQUIREDDATE, SHIPPEDDATE, STATUS, CUSTOMERNUMBER) VALUES (10101, '2003-01-09', '2003-01-18', '2003-01-11', 'Shipped', 112)")
                    }
                }
            }
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

    @Test
    fun `products-by-line with select parameter should return filtered data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products-by-line/default.json?productLine=Classic Cars"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Classic Cars"))
    }

    @Test
    fun `products-by-price with number parameters should return filtered data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products-by-price/default.json?minPrice=50&maxPrice=100"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("rows"))
    }

    @Test
    fun `products-search with no params returns all products`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products-search/default.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("rows"))
        assertTrue(response.body!!.contains("1969 Harley Davidson"))
        assertTrue(response.body!!.contains("1952 Alpine Renault 1300"))
        assertTrue(response.body!!.contains("1968 Ford Mustang"))
    }

    @Test
    fun `products-search with productLine filter returns filtered data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products-search/default.json?productLine=Classic Cars"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Classic Cars"))
        assertTrue(response.body!!.contains("1952 Alpine Renault 1300"))
        assertTrue(response.body!!.contains("1968 Ford Mustang"))
        assertFalse(response.body!!.contains("Harley Davidson"))
    }

    @Test
    fun `products-search with minPrice filter returns filtered data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products-search/default.json?minPrice=80"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("1952 Alpine Renault 1300"))
        assertTrue(response.body!!.contains("1968 Ford Mustang"))
        assertFalse(response.body!!.contains("Harley Davidson"))
    }

    @Test
    fun `products-search with both filters returns intersection`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products-search/default.json?productLine=Classic Cars&minPrice=90"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("1968 Ford Mustang"))
        assertFalse(response.body!!.contains("1952 Alpine Renault 1300"))
        assertFalse(response.body!!.contains("Harley Davidson"))
    }

    @Test
    fun `products-in-stock with boolean parameter should return data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/products-in-stock/default.json?inStockOnly=true"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("rows"))
    }

    @Test
    fun `inventory-report should be accessible to admin only`() {
        val adminTemplate = authTemplate("admin", "admin")
        val response = adminTemplate.getForEntity(
            url("/api/op/inventory-report/default.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("rows"))
    }

    @Test
    fun `inventory-report should be denied to guest`() {
        val guestTemplate = authTemplate("guest", "guest")
        try {
            guestTemplate.getForEntity(
                url("/api/op/inventory-report/default.json"),
                String::class.java
            )
            fail("Expected 403 Forbidden")
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertEquals(HttpStatus.FORBIDDEN, e.statusCode)
        }
    }

    @Test
    fun `public-catalog should be accessible to guest`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/public-catalog/default.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("rows"))
    }

    @Test
    fun `customers from secondary datasource should return data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/customers/default.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("rows"))
        assertTrue(response.body!!.contains("Atelier graphique"))
    }

    @Test
    fun `customer from secondary datasource with parameter should return data`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/customer/default.json?customerNumber=103"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Atelier graphique"))
    }

    @Test
    fun `customer-orders from secondary datasource should return orders for customer`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/customer-orders/default.json?customerNumber=103"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Atelier graphique"))
        assertTrue(response.body!!.contains("10100"))
    }

    @Test
    fun `high-value-customers from secondary datasource should return customers above threshold`() {
        val template = authTemplate("guest", "guest")
        val response = template.getForEntity(
            url("/api/op/high-value-customers/default.json"),
            String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Signal Gift Stores"))
        assertTrue(response.body!!.contains("Australian Collectors"))
        assertFalse(response.body!!.contains("Atelier graphique"))
    }

    @Test
    fun `unauthenticated request should return 401`() {
        try {
            restTemplate.getForEntity(
                url("/api/op/products/default.json"),
                String::class.java
            )
            fail("Expected 401 Unauthorized")
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.statusCode)
        }
    }

    @Test
    fun `products-search with invalid minPrice type should return 400`() {
        val template = authTemplate("guest", "guest")
        try {
            template.getForEntity(
                url("/api/op/products-search/default.json?minPrice=abc"),
                String::class.java
            )
            fail("Expected 400 Bad Request")
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
        }
    }
}
