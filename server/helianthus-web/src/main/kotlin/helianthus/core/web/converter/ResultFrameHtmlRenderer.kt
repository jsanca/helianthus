package helianthus.core.web.converter

import helianthus.core.result.ResultFrame
import org.springframework.web.util.HtmlUtils

/**
 * Renders a ResultFrame as an HTML table.
 *
 * TODO: Consider using a template engine (Thymeleaf, Pebble) or a helper-based
 *       rendering approach for more flexible HTML generation.
 */
class ResultFrameHtmlRenderer {

    fun render(resultFrame: ResultFrame): String {
        val columns = resultFrame.schema.columns
        
        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"en\">\n")
            append("<head>\n")
            append("  <meta charset=\"UTF-8\">\n")
            append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            append("  <title>Query Results</title>\n")
            append("  <style>\n")
            append("    body { font-family: system-ui, -apple-system, sans-serif; margin: 20px; }\n")
            append("    table { border-collapse: collapse; width: 100%; }\n")
            append("    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n")
            append("    th { background-color: #f5f5f5; font-weight: 600; }\n")
            append("    tr:nth-child(even) { background-color: #fafafa; }\n")
            append("    .metadata { color: #666; font-size: 14px; margin-bottom: 10px; }\n")
            append("  </style>\n")
            append("</head>\n")
            append("<body>\n")
            append("  <div class=\"metadata\">${resultFrame.metadata.rowCount} row(s)</div>\n")
            append("  <table>\n")
            append("    <thead>\n")
            append("      <tr>\n")
            
            columns.forEach { col ->
                append("        <th>${HtmlUtils.htmlEscape(col.name)}</th>\n")
            }
            
            append("      </tr>\n")
            append("    </thead>\n")
            append("    <tbody>\n")
            
            resultFrame.rows.forEach { row ->
                append("      <tr>\n")
                columns.forEach { col ->
                    val value = row[col.name]
                    append("        <td>${HtmlUtils.htmlEscape(value?.toString() ?: "")}</td>\n")
                }
                append("      </tr>\n")
            }
            
            append("    </tbody>\n")
            append("  </table>\n")
            append("</body>\n")
            append("</html>\n")
        }
    }
}
