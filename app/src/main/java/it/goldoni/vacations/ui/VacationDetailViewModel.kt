package it.goldoni.vacations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.goldoni.vacations.VacationsApplication
import it.goldoni.vacations.data.AppDatabase
import it.goldoni.vacations.data.Place
import it.goldoni.vacations.data.PlaceWithActivities
import it.goldoni.vacations.data.PlannedActivity
import it.goldoni.vacations.data.Vacation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class VacationDetailViewModel(
    private val db: AppDatabase,
    private val vacationId: Long,
) : ViewModel() {

    val vacation: StateFlow<Vacation?> = db.vacationDao().observe(vacationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val places: StateFlow<List<PlaceWithActivities>> =
        db.placeDao().observeForVacation(vacationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateVacation(title: String, startDate: LocalDate) {
        viewModelScope.launch {
            val current = vacation.value ?: return@launch
            db.vacationDao().update(
                current.copy(title = title, startEpochDay = startDate.toEpochDay())
            )
        }
    }

    /** Aggiunge una località; la prima diventa automaticamente il baricentro. */
    fun addPlace(name: String) {
        viewModelScope.launch { db.placeDao().insertAutoMain(vacationId, name) }
    }

    /** Rende la località il nuovo baricentro delle attività. */
    fun setMain(place: Place) {
        viewModelScope.launch { db.placeDao().setMain(vacationId, place.id) }
    }

    fun deletePlace(place: Place) {
        viewModelScope.launch { db.placeDao().delete(place) }
    }

    fun addActivity(place: Place, name: String) {
        viewModelScope.launch {
            db.activityDao().insert(PlannedActivity(placeId = place.id, name = name))
        }
    }

    fun deleteActivity(activity: PlannedActivity) {
        viewModelScope.launch { db.activityDao().delete(activity) }
    }

    companion object {
        fun factory(vacationId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as VacationsApplication
                VacationDetailViewModel(app.database, vacationId)
            }
        }
    }
}
