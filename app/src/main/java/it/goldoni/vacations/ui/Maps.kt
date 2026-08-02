package it.goldoni.vacations.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Apre Google Maps centrata sulla località. */
fun openPlaceInMaps(context: Context, place: String) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}"))
    try {
        context.startActivity(geo)
    } catch (_: ActivityNotFoundException) {
        // Nessuna app mappe installata: apre la versione web
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(place)}"),
            )
        )
    }
}

/** Apre Google Maps con il percorso da una località a un'altra. */
fun openRouteInMaps(context: Context, from: String, to: String) {
    // L'URL universale viene intercettato dall'app Maps se installata,
    // altrimenti si apre nel browser.
    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1" +
            "&origin=${Uri.encode(from)}&destination=${Uri.encode(to)}"
    )
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
