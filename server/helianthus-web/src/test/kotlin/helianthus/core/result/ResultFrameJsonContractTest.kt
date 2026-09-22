package helianthus.core.result

import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals

/**
 * Pins the JSON representation of [ResultFrame] consumed by Helianthus clients.
 *
 * This is the shape produced by the runtime JSON converter (Jackson 3). Any
 * accidental change to schema, column metadata, rows, null handling, or result
 * metadata must fail this test.
 */
class ResultFrameJsonContractTest {

    private val mapper = ObjectMapper()

    @Test
    fun `serializes schema columns rows null values and metadata`() {
        val frame = ResultFrame(
            schema = ResultSchema(
                listOf(
                    ResultColumn("ID", ResultType.INTEGER, nullable = false),
                    ResultColumn("NAME", ResultType.STRING, nullable = true),
                    ResultColumn("ACTIVE", ResultType.BOOLEAN, nullable = true)
                )
            ),
            rows = listOf(
                mapOf("ID" to 1, "NAME" to "Widget", "ACTIVE" to true),
                mapOf("ID" to 2, "NAME" to null, "ACTIVE" to null)
            ),
            metadata = ResultMetadata(rowCount = 2)
        )

        val json = mapper.writeValueAsString(frame)

        assertEquals(
            """{"metadata":{"executionTimeMs":0,"rowCount":2},"rows":[{"ID":1,"NAME":"Widget","ACTIVE":true},{"ID":2,"NAME":null,"ACTIVE":null}],"schema":{"columnCount":3,"columns":[{"name":"ID","nullable":false,"type":"INTEGER"},{"name":"NAME","nullable":true,"type":"STRING"},{"name":"ACTIVE","nullable":true,"type":"BOOLEAN"}]}}""",
            json
        )
    }
}
