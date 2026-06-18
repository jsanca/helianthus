package helianthus.core.pipeline

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterOperatorRegistryTest {

    private val registry = FilterOperatorRegistry()

    @Test
    fun `eq operator should match equal values`() {
        assertTrue(registry.evaluateCondition("hello", mapOf("eq" to "hello")))
        assertFalse(registry.evaluateCondition("hello", mapOf("eq" to "world")))
    }

    @Test
    fun `neq operator should match non-equal values`() {
        assertTrue(registry.evaluateCondition("hello", mapOf("neq" to "world")))
        assertFalse(registry.evaluateCondition("hello", mapOf("neq" to "hello")))
    }

    @Test
    fun `gt operator should match greater values`() {
        assertTrue(registry.evaluateCondition(10, mapOf("gt" to 5)))
        assertFalse(registry.evaluateCondition(5, mapOf("gt" to 10)))
        assertFalse(registry.evaluateCondition(5, mapOf("gt" to 5)))
    }

    @Test
    fun `gte operator should match greater or equal values`() {
        assertTrue(registry.evaluateCondition(10, mapOf("gte" to 5)))
        assertTrue(registry.evaluateCondition(5, mapOf("gte" to 5)))
        assertFalse(registry.evaluateCondition(3, mapOf("gte" to 5)))
    }

    @Test
    fun `lt operator should match lesser values`() {
        assertTrue(registry.evaluateCondition(3, mapOf("lt" to 5)))
        assertFalse(registry.evaluateCondition(10, mapOf("lt" to 5)))
        assertFalse(registry.evaluateCondition(5, mapOf("lt" to 5)))
    }

    @Test
    fun `lte operator should match lesser or equal values`() {
        assertTrue(registry.evaluateCondition(3, mapOf("lte" to 5)))
        assertTrue(registry.evaluateCondition(5, mapOf("lte" to 5)))
        assertFalse(registry.evaluateCondition(10, mapOf("lte" to 5)))
    }

    @Test
    fun `in operator should match list membership`() {
        assertTrue(registry.evaluateCondition("b", mapOf("in" to listOf("a", "b", "c"))))
        assertFalse(registry.evaluateCondition("d", mapOf("in" to listOf("a", "b", "c"))))
    }

    @Test
    fun `should handle null values`() {
        assertTrue(registry.evaluateCondition(null, mapOf("eq" to null)))
        assertFalse(registry.evaluateCondition(null, mapOf("gt" to 5)))
    }

    @Test
    fun `should list all registered operator keys`() {
        val keys = registry.operatorKeys()
        assertTrue(keys.containsAll(setOf("eq", "neq", "gt", "gte", "lt", "lte", "in")))
    }
}
