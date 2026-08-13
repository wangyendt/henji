package com.qingheng.weight

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qingheng.weight.data.AppDatabase
import com.qingheng.weight.sync.PersonalSyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CloudRestoreTest {
    @Test
    fun emptyDeviceRestoresWeightsMealsAndStructuredFoodsWithoutPhotos() = runBlocking {
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenFile = listOf(
            File(testContext.filesDir, "sync_token"),
            File("/data/user/0/com.qingheng.weight/files/sync_token"),
        ).firstOrNull(File::isFile) ?: File(testContext.filesDir, "sync_token")
        assumeTrue("只在 hx470 端到端验证时运行", tokenFile.isFile)
        val database = Room.inMemoryDatabaseBuilder(testContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val result = PersonalSyncEngine(database).sync(
                "http://100.84.108.13:8787",
                tokenFile.readText().trim(),
            )
            val weights = database.weightDao().allForSync()
            val meals = database.mealDao().allForSync()
            var foodCount = 0
            for (meal in meals) foodCount += database.mealDao().foodItemsForMeal(meal.id).size

            assertTrue(weights.isNotEmpty())
            assertTrue(meals.isNotEmpty())
            assertTrue(foodCount > 0)
            assertTrue(meals.all { it.imageUri == null })
            assertEquals(0, result.pending)
            assertTrue(database.syncDao().metadata()!!.pullCursor > 0)
            assertNull(database.syncDao().metadata()!!.lastError)
        } finally {
            database.close()
        }
    }
}
