package com.qingheng.weight.data

import android.content.ContentResolver
import android.net.Uri
import jxl.Workbook
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt

data class FitdaysParseResult(val records: List<WeightRecord>, val skipped: Int)

data class FitdaysImportSummary(
    val total: Int,
    val added: Int,
    val updated: Int,
    val skipped: Int,
)

class FitdaysHistoryImporter(private val resolver: ContentResolver) {
    fun read(uri: Uri): FitdaysParseResult {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("没有读到导出文件")
        require(bytes.isNotEmpty()) { "导出文件是空的" }
        return if (bytes.isOleWorkbook()) {
            val workbook = Workbook.getWorkbook(ByteArrayInputStream(bytes))
            try {
                val sheet = workbook.getSheet(0)
                val rows = (0 until sheet.rows).map { row ->
                    (0 until sheet.columns).map { column -> sheet.getCell(column, row).contents }
                }
                FitdaysHistoryParser.parse(rows)
            } finally {
                workbook.close()
            }
        } else {
            val text = decodeText(bytes)
            FitdaysHistoryParser.parse(parseCsv(text))
        }
    }

    private fun ByteArray.isOleWorkbook(): Boolean {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        return size >= ole.size && ole.indices.all { this[it] == ole[it] }
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return if (utf8.count { it == '\uFFFD' } > 2) bytes.toString(Charset.forName("GB18030")) else utf8
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        fun finishCell() { row.add(cell.toString()); cell.clear() }
        fun finishRow() { finishCell(); rows.add(row); row = mutableListOf() }
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"'); index++
                }
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> finishCell()
                !quoted && char == '\n' -> finishRow()
                !quoted && char == '\r' -> Unit
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }
}

object FitdaysHistoryParser {
    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("HH:mm MMM.d,yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
    )

    fun parse(rows: List<List<String>>): FitdaysParseResult {
        if (rows.size < 2) return FitdaysParseResult(emptyList(), rows.size.coerceAtMost(1))
        var skipped = 0
        val records = rows.drop(1).mapNotNull { cells ->
            runCatching { parseRow(cells) }.getOrElse { skipped++; null }
        }.distinctBy { it.id }
        return FitdaysParseResult(records.sortedByDescending { it.measuredAt }, skipped)
    }

    private fun parseRow(cells: List<String>): WeightRecord {
        require(cells.size >= 3) { "列数不足" }
        val measuredAt = parseDate(cells[0])
        val weightKg = parseMassKg(cells[1]) ?: error("体重为空")
        require(weightKg in 5.0..500.0) { "体重超出范围" }
        val bmi = number(cells.getOrNull(2))
        val bodyFat = number(cells.getOrNull(3))
        val bodyWater = number(cells.getOrNull(8))
        val skeletalMuscle = number(cells.getOrNull(9))
        val muscleMass = parseMassKg(cells.getOrNull(10))
        val boneMass = parseMassKg(cells.getOrNull(11))
        val fatFreeMass = bodyFat?.let { weightKg * (1.0 - it / 100.0) }
        val idKey = "$measuredAt|${(weightKg * 100).roundToInt()}"
        val id = "fitdays-" + MessageDigest.getInstance("SHA-256")
            .digest(idKey.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
        return WeightRecord(
            id = id,
            measuredAt = measuredAt,
            source = "fitdays_import",
            deviceName = "Fitdays",
            weightKg = weightKg,
            bmi = bmi,
            bodyFatPercent = bodyFat,
            bodyWaterPercent = bodyWater,
            skeletalMusclePercent = skeletalMuscle,
            bmrKcal = number(cells.getOrNull(13))?.roundToInt(),
            fatFreeMassKg = fatFreeMass,
            subcutaneousFatPercent = number(cells.getOrNull(4)),
            visceralFat = number(cells.getOrNull(7)),
            muscleMassKg = muscleMass,
            boneMassKg = boneMass,
            proteinPercent = number(cells.getOrNull(12)),
            bodyAge = number(cells.getOrNull(14))?.roundToInt(),
            isEstimated = false,
        )
    }

    private fun parseDate(value: String): Long {
        val text = value.trim()
        for (formatter in dateFormats) {
            try {
                return LocalDateTime.parse(text, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // Try the next format used by another Fitdays locale.
            }
        }
        error("时间格式不支持")
    }

    private fun parseMassKg(value: String?): Double? {
        val text = value?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (text.isBlank() || text.contains("- -") || text == "--") return null
        val values = Regex("-?\\d+(?:[.,]\\d+)?").findAll(text)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }.toList()
        if (values.isEmpty()) return null
        return when {
            "st:lb" in text && values.size >= 2 -> (values[0] * 14.0 + values[1]) * 0.45359237
            "lb" in text -> values[0] * 0.45359237
            "斤" in text || "jin" in text -> values[0] / 2.0
            else -> values[0]
        }
    }

    private fun number(value: String?): Double? {
        val text = value?.trim() ?: return null
        if (text.isBlank() || text.contains("- -") || text == "--") return null
        return Regex("-?\\d+(?:[.,]\\d+)?").find(text)?.value?.replace(',', '.')?.toDoubleOrNull()
    }
}
