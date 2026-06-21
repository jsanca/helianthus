package helianthus.core.access.impl.db

/**
 * Parses named parameters (:paramName) from SQL and rewrites them as positional (?) placeholders.
 *
 * Handles edge cases:
 * - PostgreSQL cast operator (::) is not treated as a named parameter
 * - String literals (single-quoted) are not parsed for parameters
 * - Line comments (--) and block comments (/* */) are skipped
 */
object NamedParameterSql {

    data class ParsedSql(
        val actualSql: String,
        val paramNames: List<String>
    )

    /**
     * Parses named parameters from SQL.
     *
     * @param sql SQL string potentially containing :paramName placeholders
     * @return ParsedSql with rewritten SQL using ? placeholders and ordered parameter names
     */
    fun parse(sql: String): ParsedSql {
        val paramNames = mutableListOf<String>()
        val result = StringBuilder(sql.length)
        var i = 0

        while (i < sql.length) {
            val c = sql[i]

            // String literal: skip until closing quote
            if (c == '\'') {
                result.append(c)
                i++
                while (i < sql.length) {
                    val sc = sql[i]
                    result.append(sc)
                    if (sc == '\'') {
                        // Check for escaped quote ''
                        if (i + 1 < sql.length && sql[i + 1] == '\'') {
                            result.append(sql[i + 1])
                            i += 2
                        } else {
                            i++
                            break
                        }
                    } else {
                        i++
                    }
                }
                continue
            }

            // Line comment: skip until end of line
            if (c == '-' && i + 1 < sql.length && sql[i + 1] == '-') {
                result.append(c)
                i++
                while (i < sql.length && sql[i] != '\n') {
                    result.append(sql[i])
                    i++
                }
                continue
            }

            // Block comment: skip until */
            if (c == '/' && i + 1 < sql.length && sql[i + 1] == '*') {
                result.append(c)
                result.append(sql[i + 1])
                i += 2
                while (i < sql.length) {
                    if (sql[i] == '*' && i + 1 < sql.length && sql[i + 1] == '/') {
                        result.append(sql[i])
                        result.append(sql[i + 1])
                        i += 2
                        break
                    }
                    result.append(sql[i])
                    i++
                }
                continue
            }

            // Named parameter: :paramName (but not ::)
            if (c == ':' && i + 1 < sql.length && sql[i + 1] != ':' && sql[i + 1].isLetterOrDigit()) {
                i++ // skip ':'
                val start = i
                while (i < sql.length && (sql[i].isLetterOrDigit() || sql[i] == '_')) {
                    i++
                }
                val paramName = sql.substring(start, i)
                paramNames.add(paramName)
                result.append('?')
                continue
            }

            result.append(c)
            i++
        }

        return ParsedSql(result.toString(), paramNames)
    }

    /**
     * Returns true if the SQL contains named parameters (:paramName).
     */
    fun hasNamedParameters(sql: String): Boolean {
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            if (c == '\'') {
                i++
                while (i < sql.length) {
                    if (sql[i] == '\'') {
                        i++
                        if (i < sql.length && sql[i] == '\'') {
                            i++
                        } else {
                            break
                        }
                    } else {
                        i++
                    }
                }
                continue
            }
            if (c == '-' && i + 1 < sql.length && sql[i + 1] == '-') {
                while (i < sql.length && sql[i] != '\n') i++
                continue
            }
            if (c == '/' && i + 1 < sql.length && sql[i + 1] == '*') {
                i += 2
                while (i < sql.length) {
                    if (sql[i] == '*' && i + 1 < sql.length && sql[i + 1] == '/') {
                        i += 2
                        break
                    }
                    i++
                }
                continue
            }
            if (c == ':' && i + 1 < sql.length && sql[i + 1] != ':' && sql[i + 1].isLetterOrDigit()) {
                return true
            }
            i++
        }
        return false
    }
}
