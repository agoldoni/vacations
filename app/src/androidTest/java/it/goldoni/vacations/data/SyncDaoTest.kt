package it.goldoni.vacations.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SyncDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.syncDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun primoImportCreaVacanzaCompleta() = runBlocking {
        val id = dao.upsertFromSync(
            syncId = "sync-1",
            title = "Eolie",
            startEpochDay = 100,
            places = listOf(
                SyncPlaceData("Lipari", isMain = true, activities = listOf("Spiaggia", "Museo")),
                SyncPlaceData("Vulcano", isMain = false, activities = emptyList()),
            ),
        )

        val vacation = dao.getVacationBySyncId("sync-1")
        assertNotNull(vacation)
        assertEquals(id, vacation!!.id)
        assertEquals("Eolie", vacation.title)

        val places = dao.getPlacesWithActivities(id)
        assertEquals(2, places.size)
        assertEquals("Lipari", places[0].place.name)
        assertEquals(true, places[0].place.isMain)
        assertEquals(listOf("Spiaggia", "Museo"), places[0].activities.map { it.name })
    }

    @Test
    fun reimportSostituisceSenzaDuplicareEPreservaId() = runBlocking {
        val firstId = dao.upsertFromSync(
            syncId = "sync-1",
            title = "Eolie",
            startEpochDay = 100,
            places = listOf(
                SyncPlaceData("Lipari", isMain = true, activities = listOf("Spiaggia")),
                SyncPlaceData("Vulcano", isMain = false, activities = listOf("Cratere")),
            ),
        )

        // Secondo invio: titolo cambiato, Vulcano rimossa, attività diverse
        val secondId = dao.upsertFromSync(
            syncId = "sync-1",
            title = "Eolie 2026",
            startEpochDay = 101,
            places = listOf(
                SyncPlaceData("Lipari", isMain = true, activities = listOf("Museo")),
            ),
        )

        assertEquals(firstId, secondId)

        val all = db.vacationDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Eolie 2026", all[0].title)
        assertEquals(101, all[0].startEpochDay)

        val places = dao.getPlacesWithActivities(secondId)
        assertEquals(1, places.size)
        assertEquals("Lipari", places[0].place.name)
        assertEquals(listOf("Museo"), places[0].activities.map { it.name })
    }

    @Test
    fun stessoTitoloConSyncIdDiversiRestanoDistinte() = runBlocking {
        dao.upsertFromSync("sync-1", "Mare", 100, emptyList())
        dao.upsertFromSync("sync-2", "Mare", 100, emptyList())

        val all = db.vacationDao().observeAll().first()
        assertEquals(2, all.size)
    }
}
