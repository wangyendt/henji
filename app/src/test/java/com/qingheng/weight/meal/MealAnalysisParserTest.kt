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
              "caloriesKcal": {"min": 520, "max": 680},
              "proteinGrams": {"min": 20, "max": 28},
              "carbohydrateGrams": {"min": 65, "max": 82},
              "fatGrams": {"min": 18, "max": 27},
              "advice": "减少炒制用油，并搭配一份绿叶菜。"
            }
            """.trimIndent(),
        )

        assertEquals(listOf("番茄炒蛋", "米饭"), result.dishes)
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
        assertEquals(EstimateRange(310.0, 390.0), result.caloriesKcal)
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
