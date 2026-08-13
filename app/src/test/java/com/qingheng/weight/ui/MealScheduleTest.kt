package com.qingheng.weight.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MealScheduleTest {
    @Test
    fun `chooses meal label from entry time`() {
        assertEquals("早餐", defaultMealType(LocalTime.of(8, 0)))
        assertEquals("上午", defaultMealType(LocalTime.of(10, 30)))
        assertEquals("午餐", defaultMealType(LocalTime.of(12, 30)))
        assertEquals("下午", defaultMealType(LocalTime.of(16, 0)))
        assertEquals("晚餐", defaultMealType(LocalTime.of(19, 0)))
        assertEquals("夜宵", defaultMealType(LocalTime.of(23, 30)))
    }
}
