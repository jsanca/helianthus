package helianthus.core.pipeline

import helianthus.core.InvalidParameterException
import helianthus.core.access.GenericDataAccess
import helianthus.core.access.SqlExecutionPlan
import helianthus.core.result.CloseableRowStream
import helianthus.core.result.ResultColumn
import helianthus.core.result.ResultSchema
import helianthus.core.result.ResultType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class QueryStepTest {

    private val emptySchema = ResultSchema(emptyList())

    private fun emptyStream(): CloseableRowStream {
        return object : CloseableRowStream {
            override val schema = emptySchema
            override val rows = emptySequence<Map<String, Any?>>()
            override fun close() {}
            override fun withSchema(s: ResultSchema) = this
            override fun transformRows(t: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>) = this
        }
    }

    private fun capturingDataAccess(captured: MutableList<SqlExecutionPlan>): GenericDataAccess {
        return object : GenericDataAccess {
            override fun executeQueryStream(
                plan: SqlExecutionPlan,
                dataSource: String,
                fetchSize: Int
            ): CloseableRowStream {
                captured.add(plan)
                return emptyStream()
            }
        }
    }

    @Test
    fun `execution plan expands repeated named params`() {
        val captured = mutableListOf<SqlExecutionPlan>()
        val step = QueryStep(capturingDataAccess(captured))

        val request = OperationRequest(
            "products-search", "default", "json",
            params = mapOf("productLine" to "Motorcycles", "minPrice" to "23.0")
        )
        val context = PipelineContext(request)
        context.resolvedOperation = ResolvedOperation(
            operationId = "products-search",
            sql = """
                SELECT * FROM products
                WHERE (:productLine IS NULL OR PRODUCTLINE = :productLine)
                  AND (:minPrice IS NULL OR BUYPRICE >= :minPrice)
                ORDER BY PRODUCTCODE
            """.trimIndent(),
            parameters = listOf(
                ParameterDefinition("productLine", "string", false),
                ParameterDefinition("minPrice", "number", false)
            )
        )
        context.boundParameters = BoundParameters(
            mapOf("productLine" to "Motorcycles", "minPrice" to 23.0)
        )

        step.process(context)

        assertEquals(1, captured.size)
        val plan = captured[0]
        assertEquals(4, plan.params.size)
        assertEquals("productLine", plan.params[0].name)
        assertEquals("Motorcycles", plan.params[0].value)
        assertEquals("string", plan.params[0].type)
        assertEquals("productLine", plan.params[1].name)
        assertEquals("Motorcycles", plan.params[1].value)
        assertEquals("minPrice", plan.params[2].name)
        assertEquals(23.0, plan.params[2].value)
        assertEquals("number", plan.params[2].type)
        assertEquals("minPrice", plan.params[3].name)
        assertEquals(23.0, plan.params[3].value)
        assertFalse(plan.sql.contains(":productLine"))
        assertFalse(plan.sql.contains(":minPrice"))
    }

    @Test
    fun `execution plan binds null for missing optional named params`() {
        val captured = mutableListOf<SqlExecutionPlan>()
        val step = QueryStep(capturingDataAccess(captured))

        val request = OperationRequest("products-search", "default", "json")
        val context = PipelineContext(request)
        context.resolvedOperation = ResolvedOperation(
            operationId = "products-search",
            sql = """
                SELECT * FROM products
                WHERE (:productLine IS NULL OR PRODUCTLINE = :productLine)
                  AND (:minPrice IS NULL OR BUYPRICE >= :minPrice)
                ORDER BY PRODUCTCODE
            """.trimIndent(),
            parameters = listOf(
                ParameterDefinition("productLine", "string", false),
                ParameterDefinition("minPrice", "number", false)
            )
        )
        context.boundParameters = BoundParameters(emptyMap())

        step.process(context)

        val plan = captured[0]
        assertEquals(4, plan.params.size)
        assertEquals(null, plan.params[0].value)
        assertEquals(null, plan.params[1].value)
        assertEquals(null, plan.params[2].value)
        assertEquals(null, plan.params[3].value)
    }

    @Test
    fun `undeclared named param throws InvalidParameterException`() {
        val captured = mutableListOf<SqlExecutionPlan>()
        val step = QueryStep(capturingDataAccess(captured))

        val request = OperationRequest("test", "default", "json")
        val context = PipelineContext(request)
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM products WHERE PRODUCTLINE = :missingParam",
            parameters = emptyList()
        )
        context.boundParameters = BoundParameters(emptyMap())

        assertThrows<InvalidParameterException> {
            step.process(context)
        }
    }

    @Test
    fun `positional SQL passes only non-null bound params`() {
        val captured = mutableListOf<SqlExecutionPlan>()
        val step = QueryStep(capturingDataAccess(captured))

        val request = OperationRequest(
            "test", "default", "json",
            params = mapOf("productCode" to "S10_1678")
        )
        val context = PipelineContext(request)
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM products WHERE PRODUCTCODE = ?",
            parameters = listOf(ParameterDefinition("productCode", "string", true))
        )
        context.boundParameters = BoundParameters(mapOf("productCode" to "S10_1678"))

        step.process(context)

        val plan = captured[0]
        assertEquals("SELECT * FROM products WHERE PRODUCTCODE = ?", plan.sql)
        assertEquals(1, plan.params.size)
        assertEquals("S10_1678", plan.params[0].value)
    }

    private fun assertFalse(value: Boolean) {
        assertEquals(false, value)
    }
}
