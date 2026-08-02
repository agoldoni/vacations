package it.goldoni.vacations

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.goldoni.vacations.ui.VacationDetailScreen
import it.goldoni.vacations.ui.VacationListScreen
import it.goldoni.vacations.ui.theme.VacationsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VacationsTheme {
                VacationsNavHost()
            }
        }
    }
}

@Composable
private fun VacationsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "vacations") {
        composable("vacations") {
            VacationListScreen(
                onVacationClick = { id -> navController.navigate("vacation/$id") },
            )
        }
        composable(
            route = "vacation/{vacationId}",
            arguments = listOf(navArgument("vacationId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val vacationId = backStackEntry.arguments?.getLong("vacationId") ?: return@composable
            VacationDetailScreen(
                vacationId = vacationId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
