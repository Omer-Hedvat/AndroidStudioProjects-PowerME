package com.powerme.app.data.csvimport

import org.junit.Assert.*
import org.junit.Test

class CsvFormatDetectorTest {

    // ── Format detection ──────────────────────────────────────────────────────

    @Test
    fun `detects Strong format from semicolon header`() {
        val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
        val result = CsvFormatDetector.detect(header)
        assertEquals(CsvFormat.STRONG, result.format)
        assertEquals(';', result.delimiter)
    }

    @Test
    fun `detects Hevy format from comma header`() {
        val header = "id,start_time,end_time,name,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_kg,reps,distance_km,duration_seconds,rpe"
        val result = CsvFormatDetector.detect(header)
        assertEquals(CsvFormat.HEVY, result.format)
        assertEquals(',', result.delimiter)
    }

    @Test
    fun `detects FitBod format from comma header`() {
        val header = "Date,Exercise,Sets,Reps,Weight(lbs),Distance,Duration,IsWarmup,bodyweight"
        val result = CsvFormatDetector.detect(header)
        assertEquals(CsvFormat.FITBOD, result.format)
        assertEquals(',', result.delimiter)
    }

    @Test
    fun `detects Jefit format from comma header`() {
        val header = "log_date,e_id,ename,weight,reps,set_id"
        val result = CsvFormatDetector.detect(header)
        assertEquals(CsvFormat.JEFIT, result.format)
        assertEquals(',', result.delimiter)
    }

    @Test
    fun `falls back to Generic for unknown header`() {
        val header = "date,exercise,weight,reps,sets,notes"
        val result = CsvFormatDetector.detect(header)
        assertEquals(CsvFormat.GENERIC, result.format)
    }

    // ── Case insensitivity ────────────────────────────────────────────────────

    @Test
    fun `Strong detection is case-insensitive`() {
        val header = "\"Workout #\";\"DATE\";\"WORKOUT NAME\";\"Duration (sec)\";\"Exercise Name\";\"SET ORDER\";\"Weight (kg)\";\"Reps\""
        val result = CsvFormatDetector.detect(header)
        assertEquals(CsvFormat.STRONG, result.format)
    }

    @Test
    fun `Hevy detection is case-insensitive`() {
        val header = "id,START_TIME,end_time,name,EXERCISE_TITLE,set_index"
        val result = CsvFormatDetector.detect(header)
        assertEquals(CsvFormat.HEVY, result.format)
    }

    @Test
    fun `FitBod detection is case-insensitive`() {
        val header = "Date,Exercise,Sets,Reps,Weight,Duration,ISWARMUP,BODYWEIGHT"
        val result = CsvFormatDetector.detect(header)
        assertEquals(CsvFormat.FITBOD, result.format)
    }

    // ── Header index mapping ──────────────────────────────────────────────────

    @Test
    fun `headerMap contains correct column indices`() {
        val header = "date,exercise,reps,weight"
        val result = CsvFormatDetector.detect(header)
        assertEquals(0, result.headerMap["date"])
        assertEquals(1, result.headerMap["exercise"])
        assertEquals(2, result.headerMap["reps"])
        assertEquals(3, result.headerMap["weight"])
    }

    @Test
    fun `headerMap keys are lowercase`() {
        val header = "Date,Exercise,Reps,WEIGHT"
        val result = CsvFormatDetector.detect(header)
        assertTrue(result.headerMap.containsKey("date"))
        assertTrue(result.headerMap.containsKey("exercise"))
        assertTrue(result.headerMap.containsKey("reps"))
        assertTrue(result.headerMap.containsKey("weight"))
    }

    // ── BOM and whitespace ────────────────────────────────────────────────────

    @Test
    fun `strips BOM from header`() {
        val header = "\uFEFFdate,exercise,reps"
        val result = CsvFormatDetector.detect(header)
        // Should not crash and should produce a valid headerMap
        assertTrue(result.headerMap.containsKey("date"))
    }

    @Test
    fun `handles trailing whitespace in headers`() {
        val header = " date , exercise , reps "
        val result = CsvFormatDetector.detect(header)
        // After lowercase trim in splitLine each field is trimmed
        // headerMap keys are built from trimmed lowercase fields
        assertTrue(result.headerMap.isNotEmpty())
    }

    @Test
    fun `empty header line returns Generic`() {
        val result = CsvFormatDetector.detect("")
        assertEquals(CsvFormat.GENERIC, result.format)
    }

    // ── Delimiter detection ───────────────────────────────────────────────────

    @Test
    fun `detectDelimiter returns semicolon for Strong-style headers`() {
        val line = "\"Workout #\";\"Date\";\"Workout Name\""
        assertEquals(';', CsvFormatDetector.detectDelimiter(line))
    }

    @Test
    fun `detectDelimiter returns comma for comma-separated headers`() {
        val line = "date,exercise,reps,weight"
        assertEquals(',', CsvFormatDetector.detectDelimiter(line))
    }

    // ── splitLine ─────────────────────────────────────────────────────────────

    @Test
    fun `splitLine handles quoted fields with semicolons`() {
        val line = "\"hello\";\"world\";\"foo bar\""
        val fields = CsvFormatDetector.splitLine(line, ';')
        assertEquals(listOf("hello", "world", "foo bar"), fields)
    }

    @Test
    fun `splitLine handles quoted fields with commas`() {
        val line = "\"hello\",\"world\",\"foo, bar\""
        val fields = CsvFormatDetector.splitLine(line, ',')
        assertEquals(listOf("hello", "world", "foo, bar"), fields)
    }

    @Test
    fun `splitLine handles unquoted comma-separated fields`() {
        val line = "date,exercise,reps"
        val fields = CsvFormatDetector.splitLine(line, ',')
        assertEquals(listOf("date", "exercise", "reps"), fields)
    }

    @Test
    fun `splitLine handles escaped quotes inside quoted field`() {
        val line = "\"say \"\"hello\"\"\",\"world\""
        val fields = CsvFormatDetector.splitLine(line, ',')
        assertEquals("say \"hello\"", fields[0])
        assertEquals("world", fields[1])
    }

    @Test
    fun `splitLine handles empty quoted fields`() {
        val fields = CsvFormatDetector.splitLine("\"a\",\"\",\"c\"", ',')
        assertEquals(listOf("a", "", "c"), fields)
    }
}
