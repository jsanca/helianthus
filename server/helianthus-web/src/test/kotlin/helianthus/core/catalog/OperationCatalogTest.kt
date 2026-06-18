package helianthus.core.catalog

import helianthus.core.config.CatalogConfig
import org.junit.jupiter.api.Test
import org.springframework.core.io.FileSystemResource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationCatalogTest {

    private fun loadMainCatalog(): OperationCatalog {
        val resource = FileSystemResource("src/main/resources/operations.yml")
        val config = CatalogConfig(resource)
        return config.operationCatalog()
    }

    @Test
    fun `should load catalog from operations yml`() {
        val catalog = loadMainCatalog()
        assertNotNull(catalog)
    }

    @Test
    fun `should parse app metadata`() {
        val catalog = loadMainCatalog()
        assertNotNull(catalog.app)
        assertEquals("Helianthus API", catalog.app!!.name)
    }

    @Test
    fun `should parse datasources`() {
        val catalog = loadMainCatalog()
        assertEquals(1, catalog.datasources.size)
        assertTrue(catalog.datasources.containsKey("default"))
        assertEquals("postgres", catalog.datasources["default"]!!.type)
    }

    @Test
    fun `should parse queries`() {
        val catalog = loadMainCatalog()
        assertEquals(2, catalog.queries.size)
        
        val productsBase = catalog.queries["products.base"]
        assertNotNull(productsBase)
        assertEquals("SELECT * FROM classicmodels.products", productsBase.sql)
        assertEquals("default", productsBase.datasource)
        
        val productlinesBase = catalog.queries["productlines.base"]
        assertNotNull(productlinesBase)
        assertEquals("SELECT * FROM classicmodels.productlines", productlinesBase.sql)
    }

    @Test
    fun `should parse all operations`() {
        val catalog = loadMainCatalog()
        assertEquals(4, catalog.operations.size)
        assertTrue(catalog.operations.containsKey("all-products"))
        assertTrue(catalog.operations.containsKey("all-productlines"))
        assertTrue(catalog.operations.containsKey("get-product"))
        assertTrue(catalog.operations.containsKey("products"))
    }

    @Test
    fun `should parse operation with inline query`() {
        val catalog = loadMainCatalog()
        val allProducts = catalog.operations["all-products"]
        
        assertNotNull(allProducts)
        assertEquals("SELECT * FROM classicmodels.products", allProducts.query)
        assertNull(allProducts.queryRef)
        assertEquals(1, allProducts.configurations.size)
        assertTrue(allProducts.configurations.containsKey("default"))
    }

    @Test
    fun `should parse operation with parameters`() {
        val catalog = loadMainCatalog()
        val getProduct = catalog.operations["get-product"]
        
        assertNotNull(getProduct)
        assertEquals("SELECT * FROM classicmodels.products where productCode = ?", getProduct.query)
        assertEquals(1, getProduct.parameters.size)
        
        val param = getProduct.parameters[0]
        assertEquals("productCode", param.name)
        assertEquals("string", param.type)
    }

    @Test
    fun `should parse operation with queryRef`() {
        val catalog = loadMainCatalog()
        val products = catalog.operations["products"]
        
        assertNotNull(products)
        assertEquals("products.base", products.queryRef)
        assertNull(products.query)
    }

    @Test
    fun `should parse multiple configurations`() {
        val catalog = loadMainCatalog()
        val products = catalog.operations["products"]
        
        assertNotNull(products)
        assertEquals(3, products.configurations.size)
        assertTrue(products.configurations.containsKey("default"))
        assertTrue(products.configurations.containsKey("compact"))
        assertTrue(products.configurations.containsKey("expensive"))
    }

    @Test
    fun `should parse pipeline steps in configurations`() {
        val catalog = loadMainCatalog()
        val products = catalog.operations["products"]
        
        assertNotNull(products)
        
        val defaultConfig = products.configurations["default"]
        assertNotNull(defaultConfig)
        assertEquals(100, defaultConfig.pipeline.limit)
        
        val compactConfig = products.configurations["compact"]
        assertNotNull(compactConfig)
        assertEquals(listOf("productCode", "productName", "productLine"), compactConfig.pipeline.project)
        assertEquals(50, compactConfig.pipeline.limit)
        
        val expensiveConfig = products.configurations["expensive"]
        assertNotNull(expensiveConfig)
        assertNotNull(expensiveConfig.pipeline.filter)
        assertEquals(listOf("productCode", "productName", "buyPrice"), expensiveConfig.pipeline.project)
        assertEquals(100, expensiveConfig.pipeline.limit)
    }

    @Test
    fun `should resolve operation with default configuration`() {
        val catalog = loadMainCatalog()
        val resolved = catalog.resolveOperation("all-products")
        
        assertEquals("all-products", resolved.operationId)
        assertEquals("default", resolved.configurationId)
        assertEquals("SELECT * FROM classicmodels.products", resolved.sql)
        assertEquals(100, resolved.pipelineConfig.limit)
    }

    @Test
    fun `should resolve operation with named configuration`() {
        val catalog = loadMainCatalog()
        val resolved = catalog.resolveOperation("products", "compact")
        
        assertEquals("products", resolved.operationId)
        assertEquals("compact", resolved.configurationId)
        assertEquals("SELECT * FROM classicmodels.products", resolved.sql)
        assertEquals(listOf("productCode", "productName", "productLine"), resolved.pipelineConfig.project)
        assertEquals(50, resolved.pipelineConfig.limit)
    }

    @Test
    fun `should resolve operation with queryRef`() {
        val catalog = loadMainCatalog()
        val resolved = catalog.resolveOperation("products")
        
        assertEquals("products", resolved.operationId)
        assertEquals("SELECT * FROM classicmodels.products", resolved.sql)
        assertEquals("default", resolved.datasource)
    }

    @Test
    fun `should resolve operation with parameters`() {
        val catalog = loadMainCatalog()
        val resolved = catalog.resolveOperation("get-product")
        
        assertEquals("get-product", resolved.operationId)
        assertEquals(1, resolved.parameters.size)
        assertEquals("productCode", resolved.parameters[0].name)
        assertEquals("string", resolved.parameters[0].type)
    }
}
