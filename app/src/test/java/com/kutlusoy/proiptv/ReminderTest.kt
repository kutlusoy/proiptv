package com.kutlusoy.proiptv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.reminder.ReminderManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderTest {
    private lateinit var db: AppDatabase
    private lateinit var manager: ReminderManager
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        manager = ReminderManager(app, db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addFindAndRemove() = runBlocking {
        val start = System.currentTimeMillis() + 3_600_000
        val id = manager.add(1, "10", "Channel", "Show", start)
        assertNotNull(manager.forProgram(1, "10", start))
        manager.remove(id)
        assertNull(manager.forProgram(1, "10", start))
    }

    @Test
    fun rescheduleAndExpire() = runBlocking {
        manager.add(1, "10", "Channel", "Soon", System.currentTimeMillis() + 3_600_000)
        manager.add(1, "11", "Channel", "Past", System.currentTimeMillis() - 1000)
        manager.removeExpired()
        manager.rescheduleAll()
        assertEquals(1, db.reminders().all().size)
    }
}
