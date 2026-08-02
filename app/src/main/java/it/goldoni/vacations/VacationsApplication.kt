package it.goldoni.vacations

import android.app.Application
import it.goldoni.vacations.data.AppDatabase

class VacationsApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
}
