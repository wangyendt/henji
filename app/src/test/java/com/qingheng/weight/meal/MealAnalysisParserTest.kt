package com.qingheng.weight.meal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealAnalysisParserTest {
    @Test
    fun `parses successful structured result`() {
        val result = MealAnalysisParser.parse(
            """
            {
              "dishes": ["番茄炒蛋", "米饭"],
              "foods": [
                {
                  "name": "鸡蛋",
                  "displayName": "番茄炒蛋中的鸡蛋",
                  "category": "肉蛋水产",
                  "estimatedGrams": {"min": 55, "max": 70},
                  "caloriesKcal": {"min": 75, "max": 110},
                  "confidence": 0.94
                },
                {
                  "name": "番茄",
                  "displayName": "炒番茄",
                  "category": "蔬菜",
                  "estimatedGrams": {"min": 120, "max": 180},
                  "caloriesKcal": {"min": 25, "max": 45},
                  "confidence": 0.9
                },
                {
                  "name": "米饭",
                  "displayName": "白米饭",
                  "category": "主食",
                  "estimatedGrams": {"min": 160, "max": 210},
                  "caloriesKcal": {"min": 185, "max": 245},
                  "confidence": 0.97
                }
              ],
              "caloriesKcal": {"min": 520, "max": 680},
              "proteinGrams": {"min": 20, "max": 28},
              "carbohydrateGrams": {"min": 65, "max": 82},
              "fatGrams": {"min": 18, "max": 27},
              "advice": "减少炒制用油，并搭配一份绿叶菜。"
            }
            """.trimIndent(),
        )

        assertEquals(listOf("番茄炒蛋", "米饭"), result.dishes)
        assertEquals(listOf("鸡蛋", "番茄", "米饭"), result.foods.map { it.name })
        assertEquals("番茄炒蛋中的鸡蛋", result.foods.first().displayName)
        assertEquals(EstimateRange(55.0, 70.0), result.foods.first().estimatedGrams)
        assertEquals(0.94, result.foods.first().confidence, 0.001)
        assertEquals(EstimateRange(520.0, 680.0), result.caloriesKcal)
        assertEquals(EstimateRange(20.0, 28.0), result.proteinGrams)
        assertEquals(EstimateRange(65.0, 82.0), result.carbohydrateGrams)
        assertEquals(EstimateRange(18.0, 27.0), result.fatGrams)
        assertEquals("减少炒制用油，并搭配一份绿叶菜。", result.advice)
    }

    @Test
    fun `accepts JSON wrapped in markdown code fence`() {
        val result = MealAnalysisParser.parse(
            """
            ```json
            {
              "dishes": ["鸡胸肉沙拉"],
              "foods": [
                {
                  "name": "鸡肉",
                  "displayName": "烤鸡胸肉",
                  "category": "肉蛋水产",
                  "estimatedGrams": {"min": 130, "max": 170},
                  "caloriesKcal": {"min": 215, "max": 280},
                  "confidence": 0.96
                }
              ],
              "caloriesKcal": {"min": 310, "max": 390},
              "proteinGrams": {"min": 32, "max": 40},
              "carbohydrateGrams": {"min": 16, "max": 24},
              "fatGrams": {"min": 10, "max": 16},
              "advice": "可补充少量全谷物主食。"
            }
            ```
            """.trimIndent(),
        )

        assertEquals(listOf("鸡胸肉沙拉"), result.dishes)
        assertEquals("鸡肉", result.foods.single().name)
        assertEquals(EstimateRange(310.0, 390.0), result.caloriesKcal)
    }

    @Test
    fun `deduplicates canonical foods within one meal`() {
        val result = MealAnalysisParser.parse(
            """
            {
              "dishes": ["双蛋饭"],
              "foods": [
                {"name":"鸡蛋","displayName":"煎鸡蛋","category":"肉蛋水产","estimatedGrams":{"min":50,"max":60},"caloriesKcal":{"min":90,"max":120},"confidence":0.9},
                {"name":"鸡蛋","displayName":"卤鸡蛋","category":"肉蛋水产","estimatedGrams":{"min":50,"max":60},"caloriesKcal":{"min":70,"max":90},"confidence":0.8}
              ],
              "caloriesKcal":{"min":300,"max":400},
              "proteinGrams":{"min":20,"max":30},
              "carbohydrateGrams":{"min":30,"max":40},
              "fatGrams":{"min":12,"max":18},
              "advice":"搭配蔬菜。"
            }
            """.trimIndent(),
        )

        assertEquals(listOf("鸡蛋"), result.foods.map { it.name })
    }

    @Test
    fun `parses failed asynchronous job status`() {
        val outcome = CodexTaskProtocol.parseSnapshot(
            JSONObject(
                """
                {
                  "status": "failed",
                  "result": {
                    "status": "failed",
                    "taskId": "task-1",
                    "backend": "direct",
                    "artifacts": [],
                    "error": {"code": "DIRECT_REQUEST_FAILED", "message": "upstream unavailable", "retryable": true}
                  }
                }
                """.trimIndent(),
            ),
        )

        assertTrue(outcome is JobOutcome.Failed)
        outcome as JobOutcome.Failed
        assertEquals("DIRECT_REQUEST_FAILED", outcome.code)
        assertEquals("upstream unavailable", outcome.message)
    }
}
