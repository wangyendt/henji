package com.qingheng.weight

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingheng.weight.data.AppDatabase
import com.qingheng.weight.data.AppRepository
import com.qingheng.weight.data.MealRecord
import com.qingheng.weight.data.HealthConnectMeasurement
import com.qingheng.weight.data.WeightRecord
import com.qingheng.weight.sync.SyncPayloadCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncTransactionTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = AppRepository(database.weightDao(), database.mealDao(), database.syncDao(), database)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun deletingMealAtomicallyReplacesUpsertWithPermanentTombstone() = runBlocking {
        val meal = MealRecord(
            id = "meal-delete-test",
            createdAt = 1_786_617_600_000,
            mealType = "午餐",
            imageUri = "content://local-only-photo",
            foodNames = "鸡蛋",
            calorieLow = 80,
            calorieHigh = 100,
            advice = "",
        )
        repository.saveMeal(meal, emptyList())
        assertEquals("upsert", database.syncDao().pendingForEntity(SyncPayloadCodec.MEAL, meal.id)?.operation)

        repository.deleteMeal(meal)

        assertNull(database.mealDao().findById(meal.id))
        assertNotNull(database.syncDao().tombstone(SyncPayloadCodec.MEAL, meal.id))
        val pending = database.syncDao().pendingForEntity(SyncPayloadCodec.MEAL, meal.id)
        assertEquals("delete", pending?.operation)
        assertEquals("{}", pending?.payloadJson)
    }

    @Test
    fun deletedWeightCannotReturnThroughAlternateHealthConnectId() = runBlocking {
        val record = WeightRecord(
            id = "fitdays-file-id",
            measuredAt = 1_786_617_600_123,
            source = "fitdays_import",
            deviceName = "Fitdays",
            weightKg = 80.2,
        )
        database.weightDao().insert(record)
        repository.deleteWeight(record)

        val changed = repository.saveHealthConnectMeasurement(
            HealthConnectMeasurement(
                healthConnectId = "different-source-id",
                measuredAt = record.measuredAt + 5_000,
                weightKg = record.weightKg,
                bmi = 24.2,
                bodyFatPercent = 22.0,
                bodyWaterPercent = 53.0,
                bmrKcal = 1_680,
                fatFreeMassKg = 62.0,
                boneMassKg = 3.1,
            ),
        )

        assertFalse(changed)
        assertNull(database.weightDao().findById("health-connect-different-source-id"))
        assertEquals(
            "delete",
            database.syncDao().pendingForEntity(SyncPayloadCodec.WEIGHT, record.id)?.operation,
        )
    }
}
