package it.goldoni.vacations.sync

import it.goldoni.vacations.data.SyncDao
import it.goldoni.vacations.data.SyncPlaceData

/** Ponte tra database e trasporto: esporta una vacanza in payload e viceversa. */
class VacationSyncRepository(private val syncDao: SyncDao) {

    /** Snapshot completo della vacanza, o null se non esiste più. */
    suspend fun export(vacationId: Long): VacationPayload? {
        val vacation = syncDao.getVacation(vacationId) ?: return null
        val places = syncDao.getPlacesWithActivities(vacationId)
        return VacationPayload(
            syncId = vacation.syncId,
            title = vacation.title,
            startEpochDay = vacation.startEpochDay,
            places = places.map { pwa ->
                PlacePayload(
                    name = pwa.place.name,
                    isMain = pwa.place.isMain,
                    activities = pwa.activities.map { it.name },
                )
            },
        )
    }

    /** True se una vacanza con questo syncId è già presente: l'import la sovrascriverebbe. */
    suspend fun existsLocally(syncId: String): Boolean =
        syncDao.getVacationBySyncId(syncId) != null

    /** Importa (o sostituisce) la vacanza ricevuta. @return l'id Room locale. */
    suspend fun import(payload: VacationPayload): Long =
        syncDao.upsertFromSync(
            syncId = payload.syncId,
            title = payload.title,
            startEpochDay = payload.startEpochDay,
            places = payload.places.map { SyncPlaceData(it.name, it.isMain, it.activities) },
        )
}
