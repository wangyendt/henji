package com.qingheng.weight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FitdaysHistoryParserTest {
    @Test fun importsAllFitdaysBodyCompositionColumns() {
        val result = FitdaysHistoryParser.parse(
            listOf(
                listOf("时间", "体重", "BMI", "脂肪率", "皮下脂肪", "心率", "心脏指数", "内脏脂肪", "体水分", "骨骼肌率", "肌肉量", "骨量", "蛋白质", "基础代谢", "身体年龄"),
                listOf("2026-08-12 07:35", "149.50kg", "48.8", "35.2%", "30.1%", "88bpm", "3.2", "19", "47.6%", "31.2%", "82.4kg", "4.8kg", "15.9%", "2450kcal", "41"),
            )
        )

        assertEquals(1, result.records.size)
        assertEquals(0, result.skipped)
        val record = result.records.single()
        assertEquals(149.5, record.weightKg, 0.001)
        assertEquals(48.8, record.bmi!!, 0.001)
        assertEquals(35.2, record.bodyFatPercent!!, 0.001)
        assertEquals(47.6, record.bodyWaterPercent!!, 0.001)
        assertEquals(31.2, record.skeletalMusclePercent!!, 0.001)
        assertEquals(82.4, record.muscleMassKg!!, 0.001)
        assertEquals(4.8, record.boneMassKg!!, 0.001)
        assertEquals(15.9, record.proteinPercent!!, 0.001)
        assertEquals(2450, record.bmrKcal)
        assertEquals(41, record.bodyAge)
        assertFalse(record.isEstimated)
    }

    @Test fun acceptsPoundsAndWeightOnlyRowsAndSkipsBrokenRows() {
        val rows = listOf(
            listOf("Date", "Weight", "BMI"),
            listOf("07:35 Aug.12,2026", "220.46lb", "31.0"),
            listOf("2026-08-13 07:35", "100kg", "30.0", "- -", "- -"),
            listOf("not-a-date", "100kg", "30.0"),
        )

        val result = FitdaysHistoryParser.parse(rows)

        assertEquals(2, result.records.size)
        assertEquals(1, result.skipped)
        val pounds = result.records.first { it.measuredAt == epoch("2026-08-12T07:35") }
        assertEquals(100.0, pounds.weightKg, 0.02)
        val weightOnly = result.records.first { it.measuredAt == epoch("2026-08-13T07:35") }
        assertNull(weightOnly.bodyFatPercent)
    }

    @Test fun repeatedExportCreatesTheSameIdForDeduplication() {
        val row = listOf("2026-08-12 07:35", "149.5kg", "48.8")
        val first = FitdaysHistoryParser.parse(listOf(listOf("时间", "体重", "BMI"), row)).records.single()
        val second = FitdaysHistoryParser.parse(listOf(listOf("时间", "体重", "BMI"), row)).records.single()
        assertEquals(first.id, second.id)
    }

    private fun epoch(value: String) = LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
