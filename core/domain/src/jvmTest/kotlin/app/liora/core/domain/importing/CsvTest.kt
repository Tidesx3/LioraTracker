package app.liora.core.domain.importing

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CsvTest {
    @Test
    fun quotedFieldsHoldSeparatorsQuotesAndLineBreaks() {
        val rows = Csv.read("name,note\r\n\"Bench, paused\",\"Seat on 4\nsay \"\"wide\"\"\"\r\n\"\",x\r\n")
        assertEquals(
            listOf(
                listOf("name", "note"),
                listOf("Bench, paused", "Seat on 4\nsay \"wide\""),
                listOf("", "x"),
            ),
            rows,
        )
    }

    @Test
    fun semicolonsSeparateWhenTheHeaderUsesThem() {
        val rows = Csv.read("${Char(0xFEFF)}Weight;Reps\n82,5;8\n\n100;5")
        assertEquals(listOf(listOf("Weight", "Reps"), listOf("82,5", "8"), listOf("100", "5")), rows)
    }

    @Test
    fun columnsAreFoundByNameInAnyCase() {
        val table = CsvTable.of("Exercise Name,Reps\nSquat,5\nDip")
        assertEquals("Squat", table.rows[0]["exercise name"])
        assertEquals("5", table.rows[0]["REPS"])
        // A short row and a missing column read as empty.
        assertNull(table.rows[1]["reps"])
        assertNull(table.rows[0]["weight"])
    }

    @Test
    fun numbersWithAPointOrAComma() {
        assertEquals(82.5, parseNumber("82.5"))
        assertEquals(82.5, parseNumber(" 82,5 "))
        assertNull(parseNumber(""))
        assertNull(parseNumber("eighty"))
    }

    @Test
    fun hevyDatesInGermanAndEnglish() {
        assertEquals(LocalDateTime(2026, 9, 30, 17, 39), ExportDates.parse("30 Sept. 2026, 17:39"))
        assertEquals(LocalDateTime(2026, 5, 14, 0, 0), ExportDates.parse("14 Mai 2026, 00:00"))
        assertEquals(LocalDateTime(2026, 3, 3, 7, 5), ExportDates.parse("3 März 2026, 7:05"))
        assertEquals(LocalDateTime(2026, 6, 1, 18, 0), ExportDates.parse("1 Juni 2026, 18:00"))
        assertEquals(LocalDateTime(2026, 10, 12, 8, 0), ExportDates.parse("12 Okt. 2026, 08:00"))
        assertEquals(LocalDateTime(2026, 12, 5, 9, 15), ExportDates.parse("5 Dez. 2026, 09:15"))
        assertEquals(LocalDateTime(2026, 9, 30, 17, 39), ExportDates.parse("30 Sep 2026, 17:39"))
        assertEquals(LocalDateTime(2026, 9, 30, 17, 39), ExportDates.parse("Sep 30, 2026, 5:39 PM"))
        assertEquals(LocalDateTime(2026, 10, 12, 0, 5), ExportDates.parse("12 October 2026, 12:05 AM"))
    }

    @Test
    fun isoAndGermanNumberDates() {
        assertEquals(LocalDateTime(2026, 9, 30, 17, 39, 18), ExportDates.parse("2026-09-30 17:39:18"))
        assertEquals(LocalDateTime(2026, 9, 30, 17, 39), ExportDates.parse("2026-09-30T17:39"))
        assertEquals(LocalDateTime(2026, 9, 30, 0, 0), ExportDates.parse("2026-09-30"))
        assertEquals(LocalDateTime(2026, 9, 30, 17, 39), ExportDates.parse("30.09.2026, 17:39"))
    }

    @Test
    fun whatIsNoDateIsNull() {
        assertNull(ExportDates.parse("31 Foo 2026, 10:00"))
        assertNull(ExportDates.parse("2026-02-30 10:00"))
        assertNull(ExportDates.parse("30 Sept. 2026, 25:00"))
        assertNull(ExportDates.parse("yesterday"))
    }

    @Test
    fun strongDurations() {
        assertEquals(1.hours + 9.minutes, ExportDates.duration("1h 9m"))
        assertEquals(45.minutes, ExportDates.duration("45m"))
        assertEquals(1.hours, ExportDates.duration("1h"))
        assertEquals(30.seconds, ExportDates.duration("30s"))
        assertEquals(3_600.seconds, ExportDates.duration("3600"))
        assertNull(ExportDates.duration("long"))
    }
}
