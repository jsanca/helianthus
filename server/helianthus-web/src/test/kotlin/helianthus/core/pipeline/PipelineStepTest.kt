package helianthus.core.pipeline

import helianthus.core.result.CloseableRowStream
import helianthus.core.result.ResultColumn
import helianthus.core.result.ResultFrame
import helianthus.core.result.ResultMetadata
import helianthus.core.result.ResultSchema
import helianthus.core.result.ResultType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipelineStepTest {

    private fun testSchema(): ResultSchema = ResultSchema(listOf(
        ResultColumn("id", ResultType.INTEGER, false),
        ResultColumn("name", ResultType.STRING, true),
        ResultColumn("age", ResultType.INTEGER, true)
    ))

    private fun testRows(): List<Map<String, Any?>> = listOf(
        mapOf("id" to 1, "name" to "Alice", "age" to 30),
        mapOf("id" to 2, "name" to "Bob", "age" to 25),
        mapOf("id" to 3, "name" to "Charlie", "age" to 35)
    )

    private fun testContext(): PipelineContext {
        val request = OperationRequest("test", "default", "json")
        val context = PipelineContext(request)
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test",
            pipelineConfig = PipelineConfig()
        )
        context.rowStream = RowStream.fromRows(testSchema(), testRows())
        return context
    }

    @Test
    fun `ProjectStep should project selected columns`() {
        val context = testContext()
        context.resolvedOperation = context.resolvedOperation!!.copy(
            pipelineConfig = PipelineConfig(project = listOf("id", "name"))
        )

        val step = ProjectStep()
        val result = step.process(context)

        val rows = result.rowStream!!.rows.toList()
        assertEquals(3, rows.size)
        assertEquals(setOf("id", "name"), rows[0].keys)
        assertEquals(1, rows[0]["id"])
        assertEquals("Alice", rows[0]["name"])
        assertNull(rows[0]["age"])
    }

    @Test
    fun `ProjectStep should be noop when no project config`() {
        val context = testContext()
        val step = ProjectStep()
        val result = step.process(context)

        assertEquals(3, result.rowStream!!.rows.toList().size)
        assertEquals(3, result.rowStream!!.schema.columnCount)
    }

    @Test
    fun `FilterStep should filter rows by exact match`() {
        val context = testContext()
        context.resolvedOperation = context.resolvedOperation!!.copy(
            pipelineConfig = PipelineConfig(filter = mapOf("name" to "Bob"))
        )

        val step = FilterStep()
        val result = step.process(context)

        val rows = result.rowStream!!.rows.toList()
        assertEquals(1, rows.size)
        assertEquals("Bob", rows[0]["name"])
    }

    @Test
    fun `FilterStep should filter rows by gt condition`() {
        val context = testContext()
        context.resolvedOperation = context.resolvedOperation!!.copy(
            pipelineConfig = PipelineConfig(
                filter = mapOf("age" to mapOf("gt" to 25)))
        )

        val step = FilterStep()
        val result = step.process(context)

        val rows = result.rowStream!!.rows.toList()
        assertEquals(2, rows.size)
        assertEquals("Alice", rows[0]["name"])
        assertEquals("Charlie", rows[1]["name"])
    }

    @Test
    fun `FilterStep should filter rows by lt condition`() {
        val context = testContext()
        context.resolvedOperation = context.resolvedOperation!!.copy(
            pipelineConfig = PipelineConfig(
                filter = mapOf("age" to mapOf("lt" to 30)))
        )

        val step = FilterStep()
        val result = step.process(context)

        val rows = result.rowStream!!.rows.toList()
        assertEquals(1, rows.size)
        assertEquals("Bob", rows[0]["name"])
    }

    @Test
    fun `FilterStep should filter rows by in condition`() {
        val context = testContext()
        context.resolvedOperation = context.resolvedOperation!!.copy(
            pipelineConfig = PipelineConfig(
                filter = mapOf("name" to mapOf("in" to listOf("Alice", "Charlie"))))
        )

        val step = FilterStep()
        val result = step.process(context)

        val rows = result.rowStream!!.rows.toList()
        assertEquals(2, rows.size)
    }

    @Test
    fun `FilterStep should be noop when no filter config`() {
        val context = testContext()
        val step = FilterStep()
        val result = step.process(context)

        assertEquals(3, result.rowStream!!.rows.toList().size)
    }

    @Test
    fun `LimitStep should limit rows`() {
        val context = testContext()
        context.resolvedOperation = context.resolvedOperation!!.copy(
            pipelineConfig = PipelineConfig(limit = 2)
        )

        val step = LimitStep()
        val result = step.process(context)

        val rows = result.rowStream!!.rows.toList()
        assertEquals(2, rows.size)
    }

    @Test
    fun `LimitStep should be noop when no limit config`() {
        val context = testContext()
        val step = LimitStep()
        val result = step.process(context)

        assertEquals(3, result.rowStream!!.rows.toList().size)
    }

    @Test
    fun `LimitStep should be noop when limit is larger than row count`() {
        val context = testContext()
        context.resolvedOperation = context.resolvedOperation!!.copy(
            pipelineConfig = PipelineConfig(limit = 100)
        )

        val step = LimitStep()
        val result = step.process(context)

        assertEquals(3, result.rowStream!!.rows.toList().size)
    }

    @Test
    fun `LimitStep with take should only consume limited rows from fake stream`() {
        var consumedCount = 0
        val rows = sequence {
            for (i in 1..10) {
                consumedCount++
                yield(mapOf("id" to i))
            }
        }
        val stream = helianthus.core.result.DefaultRowStream(
            ResultSchema(listOf(
                ResultColumn("id", ResultType.INTEGER, false))
            ), rows
        )

        val context = testContext()
        context.resolvedOperation = context.resolvedOperation!!.copy(
            pipelineConfig = PipelineConfig(limit = 2)
        )
        context.rowStream = stream

        val step = LimitStep()
        step.process(context)

        context.rowStream!!.rows.toList()

        assertEquals(2, consumedCount, "LimitStep should only consume 2 rows")
    }

    @Test
    fun `ToResultFrameStep should materialize row stream to result frame`() {
        val context = testContext()
        val step = ToResultFrameStep()
        val result = step.process(context)

        val frame = result.resultFrame
        assertNotNull(frame)
        assertEquals(3, frame.rows.size)
        assertEquals(3, frame.metadata.rowCount)
        assertEquals("id", frame.schema.columns[0].name)
        assertEquals("name", frame.schema.columns[1].name)
        assertEquals("age", frame.schema.columns[2].name)
    }

    @Test
    fun `ToResultFrameStep should close stream after successful materialization`() {
        var closed = false
        val stream = object : CloseableRowStream {
            override val schema = testSchema()
            override val rows = testRows().asSequence()
            override fun close() { closed = true }
            override fun withSchema(s: ResultSchema) = this
            override fun transformRows(t: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>) = this
        }
        val context = testContext()
        context.rowStream = stream

        val step = ToResultFrameStep()
        step.process(context)

        assertTrue(closed, "Stream should be closed after materialization")
        assertNull(context.rowStream, "Row stream should be cleared")
    }

    @Test
    fun `ToResultFrameStep should close stream when exception occurs`() {
        var closed = false
        val stream = object : CloseableRowStream {
            override val schema = testSchema()
            override val rows: Sequence<Map<String, Any?>> = sequence {
                yield(mapOf("id" to 1))
                throw RuntimeException("forced error")
            }
            override fun close() { closed = true }
            override fun withSchema(s: ResultSchema) = this
            override fun transformRows(t: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>) = this
        }
        val context = testContext()
        context.rowStream = stream

        val step = ToResultFrameStep()
        try {
            step.process(context)
        } catch (_: Exception) {
        }

        assertTrue(closed, "Stream should be closed even on exception")
        assertNull(context.rowStream)
    }

    @Test
    fun `Transformations should preserve close action`() {
        var closeCount = 0
        val baseStream = helianthus.core.result.DefaultRowStream(
            testSchema(),
            testRows().asSequence()
        ) { closeCount++ }

        val projected = baseStream.withSchema(testSchema())
        val filtered = projected.transformRows { rows -> rows.filter { true } }
        val limited = filtered.transformRows { rows -> rows.take(2) }

        limited.close()
        assertEquals(1, closeCount, "Only one close action should fire")
    }

    @Test
    fun `ToResultFrameStep should close stream after limit materialization`() {
        var closed = false
        val baseStream = helianthus.core.result.DefaultRowStream(
            testSchema(),
            testRows().asSequence()
        ) { closed = true }

        val limited = baseStream.transformRows { rows -> rows.take(2) }

        val context = testContext()
        context.rowStream = limited

        val step = ToResultFrameStep()
        step.process(context)

        assertTrue(closed, "Stream should be closed after limited materialization")
        assertEquals(2, context.resultFrame!!.rows.size,
                "Only 2 rows should be materialized")
    }

    @Test
    fun `ToResultFrameStep should close stream when row mapping throws`() {
        var closed = false
        val stream = object : CloseableRowStream {
            override val schema = testSchema()
            override val rows = testRows().asSequence().map {
                if (it["id"] == 2) throw RuntimeException("mapping failure")
                it
            }
            override fun close() { closed = true }
            override fun withSchema(s: ResultSchema) = this
            override fun transformRows(t: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>) = this
        }
        val context = testContext()
        context.rowStream = stream

        val step = ToResultFrameStep()
        try {
            step.process(context)
        } catch (_: Exception) {
        }

        assertTrue(closed, "Stream should be closed when mapping throws")
        assertNull(context.rowStream)
    }

    @Test
    fun `ToResultFrameStep should close stream when row filtering throws`() {
        var closed = false
        val stream = object : CloseableRowStream {
            override val schema = testSchema()
            override val rows = testRows().asSequence().filter {
                if (it["id"] == 2) throw RuntimeException("filter failure")
                true
            }
            override fun close() { closed = true }
            override fun withSchema(s: ResultSchema) = this
            override fun transformRows(t: (Sequence<Map<String, Any?>>) -> Sequence<Map<String, Any?>>) = this
        }
        val context = testContext()
        context.rowStream = stream

        val step = ToResultFrameStep()
        try {
            step.process(context)
        } catch (_: Exception) {
        }

        assertTrue(closed, "Stream should be closed when filtering throws")
        assertNull(context.rowStream)
    }

    @Test
    fun `steps should chain in order query project filter limit toResultFrame`() {
        val request = OperationRequest("test", "default", "json")
        val context = PipelineContext(request)
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test",
            pipelineConfig = PipelineConfig(
                project = listOf("id", "name"),
                filter = mapOf("name" to mapOf("in" to listOf("Alice", "Charlie"))),
                limit = 1
            )
        )
        context.rowStream = RowStream.fromRows(testSchema(), testRows())

        val project = ProjectStep()
        val filter = FilterStep()
        val limit = LimitStep()
        val toFrame = ToResultFrameStep()

        var result = project.process(context)
        result = filter.process(result)
        result = limit.process(result)
        result = toFrame.process(result)

        val frame = result.resultFrame!!
        assertEquals(1, frame.rows.size)
        assertEquals("Alice", frame.rows[0]["name"])
        assertEquals(setOf("id", "name"), frame.rows[0].keys)
        assertEquals(2, frame.schema.columnCount)
    }

    @Test
    fun `ResolveStep should set resolved operation in context`() {
        val request = OperationRequest("test", "default", "json")
        val context = PipelineContext(request)

        val catalog = helianthus.core.catalog.OperationCatalog(
            operations = mapOf(
                "test" to helianthus.core.catalog.OperationDef(
                    query = "SELECT * FROM test",
                    parameters = listOf(
                        helianthus.core.catalog.ParameterDef("name", "string")
                    ),
                    datasource = "default",
                    configurations = mapOf(
                        "default" to helianthus.core.catalog.ConfigurationDef()
                    )
                )
            )
        )

        val step = ResolveStep(catalog)

        val result = step.process(context)
        assertNotNull(result.resolvedOperation)
        assertEquals("test", result.resolvedOperation!!.operationId)
        assertEquals("SELECT * FROM test", result.resolvedOperation!!.sql)
        assertEquals(1, result.resolvedOperation!!.parameters.size)
        assertEquals("name", result.resolvedOperation!!.parameters[0].name)
    }

    @Test
    fun `BindStep should bind parameters from request`() {
        val request = OperationRequest("test", "default", "json",
                params = mapOf("name" to "Alice", "age" to "30"))
        val context = PipelineContext(request)
        context.resolvedOperation = ResolvedOperation(
            operationId = "test",
            sql = "SELECT * FROM test WHERE name = ?",
            parameters = listOf(
                ParameterDefinition("name", "string"),
                ParameterDefinition("age", "integer")
            )
        )

        val step = BindStep()
        val result = step.process(context)

        assertNotNull(result.boundParameters)
        assertEquals(2, result.boundParameters!!.values.size)
        assertEquals("Alice", result.boundParameters!!.values["name"])
        assertEquals(30L, result.boundParameters!!.values["age"])
    }
}
