package it.goldoni.vacations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.goldoni.vacations.VacationsApplication
import it.goldoni.vacations.data.Vacation
import it.goldoni.vacations.data.VacationDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class VacationListViewModel(private val vacationDao: VacationDao) : ViewModel() {

    val vacations: StateFlow<List<Vacation>> = vacationDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addVacation(title: String, startDate: LocalDate) {
        viewModelScope.launch {
            vacationDao.insert(Vacation(title = title, startEpochDay = startDate.toEpochDay()))
        }
    }

    fun deleteVacation(vacation: Vacation) {
        viewModelScope.launch { vacationDao.delete(vacation) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as VacationsApplication
                VacationListViewModel(app.database.vacationDao())
            }
        }
    }
}
