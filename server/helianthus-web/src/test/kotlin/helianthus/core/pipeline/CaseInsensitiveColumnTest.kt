package helianthus.core.pipeline

import helianthus.core.result.ResultColumn
import helianthus.core.result.ResultSchema
import helianthus.core.result.ResultType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CaseInsensitiveColumnTest {

    @Test
    fun `ProjectStep should project with case-insensitive column names`() {
        val schema = ResultSchema(listOf(
            ResultColumn("productcode", ResultType.STRING),
            ResultColumn("productname", ResultType.STRING),
            ResultColumn("buyprice", ResultType.DECIMAL)
        ))
        
        val rows = listOf(
            mapOf("productcode" to "P001", "productname" to "Widget", "buyprice" to 50.0),
            mapOf("productcode" to "P002", "productname" to "Gadget", "buyprice" to 75.0)
        )
        
        val stream = RowStream.fromRows(schema, rows)
        
        val config = PipelineConfig(project = listOf("PRODUCTCODE", "buyPrice"))
        val resolvedOp = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM products",
            datasource = "default",
            pipelineConfig = config
        )
        
        val context = PipelineContext(
            operationRequest = OperationRequest("test", "default", "json", emptyMap())
        )
        context.resolvedOperation = resolvedOp
        context.rowStream = stream
        
        val step = ProjectStep()
        val result = step.process(context)
        
        val resultRows = result.rowStream!!.rows.toList()
        
        assertEquals(2, resultRows.size)
        assertEquals(2, result.rowStream!!.schema.columns.size)
        assertEquals("productcode", result.rowStream!!.schema.columns[0].name)
        assertEquals("buyprice", result.rowStream!!.schema.columns[1].name)
        assertEquals("P001", resultRows[0]["productcode"])
        assertEquals(50.0, resultRows[0]["buyprice"])
    }

    @Test
    fun `ProjectStep should preserve YAML order`() {
        val schema = ResultSchema(listOf(
            ResultColumn("productcode", ResultType.STRING),
            ResultColumn("productname", ResultType.STRING),
            ResultColumn("buyprice", ResultType.DECIMAL)
        ))
        
        val rows = listOf(
            mapOf("productcode" to "P001", "productname" to "Widget", "buyprice" to 50.0)
        )
        
        val stream = RowStream.fromRows(schema, rows)
        
        val config = PipelineConfig(project = listOf("buyPrice", "productCode"))
        val resolvedOp = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM products",
            datasource = "default",
            pipelineConfig = config
        )
        
        val context = PipelineContext(
            operationRequest = OperationRequest("test", "default", "json", emptyMap())
        )
        context.resolvedOperation = resolvedOp
        context.rowStream = stream
        
        val step = ProjectStep()
        val result = step.process(context)
        
        assertEquals("buyprice", result.rowStream!!.schema.columns[0].name)
        assertEquals("productcode", result.rowStream!!.schema.columns[1].name)
    }

    @Test
    fun `ProjectStep should throw on missing column`() {
        val schema = ResultSchema(listOf(
            ResultColumn("productcode", ResultType.STRING)
        ))
        
        val rows = listOf(mapOf("productcode" to "P001"))
        val stream = RowStream.fromRows(schema, rows)
        
        val config = PipelineConfig(project = listOf("productCode", "nonexistent"))
        val resolvedOp = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM products",
            datasource = "default",
            pipelineConfig = config
        )
        
        val context = PipelineContext(
            operationRequest = OperationRequest("test", "default", "json", emptyMap())
        )
        context.resolvedOperation = resolvedOp
        context.rowStream = stream
        
        val step = ProjectStep()
        
        val exception = assertThrows<IllegalArgumentException> {
            step.process(context)
        }
        
        assertEquals("Column 'nonexistent' not found in schema. Available columns: [productcode]", exception.message)
    }

    @Test
    fun `FilterStep should filter with case-insensitive column names`() {
        val schema = ResultSchema(listOf(
            ResultColumn("productcode", ResultType.STRING),
            ResultColumn("buyprice", ResultType.DECIMAL)
        ))
        
        val rows = listOf(
            mapOf("productcode" to "P001", "buyprice" to 50.0),
            mapOf("productcode" to "P002", "buyprice" to 75.0),
            mapOf("productcode" to "P003", "buyprice" to 100.0)
        )
        
        val stream = RowStream.fromRows(schema, rows)
        
        val config = PipelineConfig(filter = mapOf("buyPrice" to 75.0))
        val resolvedOp = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM products",
            datasource = "default",
            pipelineConfig = config
        )
        
        val context = PipelineContext(
            operationRequest = OperationRequest("test", "default", "json", emptyMap())
        )
        context.resolvedOperation = resolvedOp
        context.rowStream = stream
        
        val step = FilterStep()
        val result = step.process(context)
        
        val resultRows = result.rowStream!!.rows.toList()
        
        assertEquals(1, resultRows.size)
        assertEquals("P002", resultRows[0]["productcode"])
    }

    @Test
    fun `FilterStep should throw on missing column`() {
        val schema = ResultSchema(listOf(
            ResultColumn("productcode", ResultType.STRING)
        ))
        
        val rows = listOf(mapOf("productcode" to "P001"))
        val stream = RowStream.fromRows(schema, rows)
        
        val config = PipelineConfig(filter = mapOf("nonexistent" to "value"))
        val resolvedOp = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM products",
            datasource = "default",
            pipelineConfig = config
        )
        
        val context = PipelineContext(
            operationRequest = OperationRequest("test", "default", "json", emptyMap())
        )
        context.resolvedOperation = resolvedOp
        context.rowStream = stream
        
        val step = FilterStep()
        
        val exception = assertThrows<IllegalArgumentException> {
            step.process(context).rowStream!!.rows.toList()
        }
        
        assertEquals("Column 'nonexistent' not found in row. Available columns: [productcode]", exception.message)
    }
}
