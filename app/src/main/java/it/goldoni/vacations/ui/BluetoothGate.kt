package it.goldoni.vacations.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** True se il permesso runtime Bluetooth è concesso (sotto API 31 non serve). */
fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Mostra [content] solo quando il Bluetooth è utilizzabile: permesso concesso
 * (richiesto automaticamente al primo ingresso) e radio accesa (attivabile
 * tramite il flusso di sistema).
 */
@Composable
fun BluetoothGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }

    if (adapter == null) {
        GateBox(modifier, "Questo dispositivo non supporta il Bluetooth.")
        return
    }

    var hasPermission by remember { mutableStateOf(hasBluetoothPermission(context)) }
    var enabled by remember { mutableStateOf(adapter.isEnabled) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) enabled = adapter.isEnabled
    }
    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        enabled = adapter.isEnabled
    }

    when {
        !hasPermission -> {
            LaunchedEffect(Unit) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            GateBox(
                modifier,
                "Per sincronizzare serve il permesso \"Dispositivi nelle vicinanze\".",
                actionLabel = "Concedi permesso",
                onAction = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            )
        }

        !enabled -> GateBox(
            modifier,
            "Il Bluetooth è spento.",
            actionLabel = "Attiva Bluetooth",
            onAction = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        )

        else -> content()
    }
}

@Composable
private fun GateBox(
    modifier: Modifier,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (actionLabel != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
