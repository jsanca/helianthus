package helianthus.core.pipeline

import helianthus.core.InvalidParameterException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BindStepTest {

    private val bindStep = BindStep()

    @Test
    fun `should bind string parameter`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = mapOf("name" to "Alice")
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE name = ?",
            parameters = listOf(ParameterDefinition("name", "string", false))
        )

        val result = bindStep.process(context)

        assertNotNull(result.boundParameters)
        assertEquals("Alice", result.boundParameters!!.values["name"])
    }

    @Test
    fun `should bind integer parameter`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = mapOf("age" to "25")
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE age = ?",
            parameters = listOf(ParameterDefinition("age", "integer", false))
        )

        val result = bindStep.process(context)

        assertNotNull(result.boundParameters)
        assertEquals(25L, result.boundParameters!!.values["age"])
    }

    @Test
    fun `should bind number parameter`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = mapOf("price" to "19.99")
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE price > ?",
            parameters = listOf(ParameterDefinition("price", "number", false))
        )

        val result = bindStep.process(context)

        assertNotNull(result.boundParameters)
        assertEquals(19.99, result.boundParameters!!.values["price"])
    }

    @Test
    fun `should bind boolean parameter`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = mapOf("active" to "true")
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE active = ?",
            parameters = listOf(ParameterDefinition("active", "boolean", false))
        )

        val result = bindStep.process(context)

        assertNotNull(result.boundParameters)
        assertEquals(true, result.boundParameters!!.values["active"])
    }

    @Test
    fun `should throw exception for missing required parameter`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = emptyMap()
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE name = ?",
            parameters = listOf(ParameterDefinition("name", "string", true))
        )

        val exception = assertThrows<InvalidParameterException> {
            bindStep.process(context)
        }

        assertTrue(exception.message!!.contains("Missing required parameter"))
        assertTrue(exception.message!!.contains("name"))
    }

    @Test
    fun `should throw exception for invalid integer parameter`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = mapOf("age" to "not-a-number")
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE age = ?",
            parameters = listOf(ParameterDefinition("age", "integer", false))
        )

        val exception = assertThrows<InvalidParameterException> {
            bindStep.process(context)
        }

        assertTrue(exception.message!!.contains("must be of type 'integer'"))
    }

    @Test
    fun `should throw exception for invalid number parameter`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = mapOf("price" to "not-a-number")
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE price > ?",
            parameters = listOf(ParameterDefinition("price", "number", false))
        )

        val exception = assertThrows<InvalidParameterException> {
            bindStep.process(context)
        }

        assertTrue(exception.message!!.contains("must be of type 'number'"))
    }

    @Test
    fun `should throw exception for invalid boolean parameter`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = mapOf("active" to "maybe")
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE active = ?",
            parameters = listOf(ParameterDefinition("active", "boolean", false))
        )

        val exception = assertThrows<InvalidParameterException> {
            bindStep.process(context)
        }

        assertTrue(exception.message!!.contains("must be a boolean"))
    }

    @Test
    fun `should skip optional parameters when not provided`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = emptyMap()
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test",
            parameters = listOf(
                ParameterDefinition("name", "string", false),
                ParameterDefinition("age", "integer", false)
            )
        )

        val result = bindStep.process(context)

        assertNotNull(result.boundParameters)
        assertEquals(0, result.boundParameters!!.values.size)
    }

    @Test
    fun `should bind multiple parameters`() {
        val context = PipelineContext(
            operationRequest = OperationRequest(
                operationId = "test",
                configurationId = "default",
                format = "json",
                params = mapOf(
                    "name" to "Alice",
                    "age" to "30",
                    "active" to "true"
                )
            )
        )
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE name = ? AND age > ? AND active = ?",
            parameters = listOf(
                ParameterDefinition("name", "string", false),
                ParameterDefinition("age", "integer", false),
                ParameterDefinition("active", "boolean", false)
            )
        )

        val result = bindStep.process(context)

        assertNotNull(result.boundParameters)
        assertEquals(3, result.boundParameters!!.values.size)
        assertEquals("Alice", result.boundParameters!!.values["name"])
        assertEquals(30L, result.boundParameters!!.values["age"])
        assertEquals(true, result.boundParameters!!.values["active"])
    }

    @Test
    fun `should accept various boolean representations`() {
        val testCases = mapOf(
            "true" to true,
            "1" to true,
            "yes" to true,
            "false" to false,
            "0" to false,
            "no" to false
        )

        testCases.forEach { (input, expected) ->
            val context = PipelineContext(
                operationRequest = OperationRequest(
                    operationId = "test",
                    configurationId = "default",
                    format = "json",
                    params = mapOf("flag" to input)
                )
            )
            context.resolvedOperation = ResolvedOperation(
                operationId = "test",
                sql = "SELECT * FROM test WHERE flag = ?",
                parameters = listOf(ParameterDefinition("flag", "boolean", false))
            )

            val result = bindStep.process(context)

            assertEquals(expected, result.boundParameters!!.values["flag"])
        }
    }
}
