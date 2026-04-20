package com.powerme.app.data.csvimport

/**
 * Detects the source app format of a CSV file by inspecting its header line.
 *
 * Detection is header-only (case-insensitive, order-independent). No network calls.
 *
 * | Format  | Required lowercased headers              | Delimiter |
 * |---------|------------------------------------------|-----------|
 * | STRONG  | "workout name" + "set order"             | ;         |
 * | HEVY    | "exercise_title" + "start_time"          | ,         |
 * | FITBOD  | "bodyweight" + "iswarmup"                | ,         |
 * | JEFIT   | "log_date" + "e_id"                      | ,         |
 * | GENERIC | none of the above                        | ,         |
 */
object CsvFormatDetector {

    /**
     * Analyses [headerLine] and returns a [DetectionResult] with the detected format,
     * delimiter, and a map of lowercased header name → column index.
     */
    fun detect(headerLine: String): DetectionResult {
        // Strip BOM if present
        val cleaned = headerLine.trimStart('\uFEFF').trim()
        if (cleaned.isEmpty()) {
            return DetectionResult(CsvFormat.GENERIC, emptyList(), ',', emptyMap())
        }

        val delimiter = detectDelimiter(cleaned)
        val rawHeaders = splitLine(cleaned, delimiter)
        val lowerHeaders = rawHeaders.map { it.trim().lowercase() }
        val headerMap = lowerHeaders.withIndex().associate { (i, h) -> h to i }

        val format = when {
            headerMap.containsKey("workout name") && headerMap.containsKey("set order") -> CsvFormat.STRONG
            headerMap.containsKey("exercise_title") && headerMap.containsKey("start_time") -> CsvFormat.HEVY
            headerMap.containsKey("bodyweight") && headerMap.containsKey("iswarmup") -> CsvFormat.FITBOD
            headerMap.containsKey("log_date") && headerMap.containsKey("e_id") -> CsvFormat.JEFIT
            else -> CsvFormat.GENERIC
        }

        return DetectionResult(
            format = format,
            headers = rawHeaders,
            delimiter = delimiter,
            headerMap = headerMap
        )
    }

    /**
     * Picks the delimiter: if the header has more semicolons than commas (outside quotes),
     * it is semicolon-delimited (Strong export style); otherwise comma.
     */
    internal fun detectDelimiter(line: String): Char {
        var semicolons = 0
        var commas = 0
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == ';' -> semicolons++
                !inQuotes && ch == ',' -> commas++
            }
        }
        return if (semicolons > commas) ';' else ','
    }

    /**
     * Splits a delimited line respecting double-quoted fields.
     * Surrounding quotes are stripped; embedded escaped quotes ("") are preserved.
     */
    internal fun splitLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    // Escaped quote inside quoted field
                    current.append('"')
                    i += 2
                    continue
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
