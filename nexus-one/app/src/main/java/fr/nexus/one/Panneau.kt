package fr.nexus.one

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Lampe torche : allume ou éteint le flash. Renvoie false si l'appareil n'a pas de flash. */
fun reglerLampe(context: Context, allumer: Boolean): Boolean = try {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val id = cm.cameraIdList.firstOrNull {
        cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
    if (id == null) false else { cm.setTorchMode(id, allumer); true }
} catch (e: Exception) {
    false
}

/** Ouvre un écran de réglage Android. Les panneaux flottants existent depuis Android 10. */
private fun ouvrir(context: Context, action: String, actionAncienne: String = action) {
    val choisie = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) action else actionAncienne
    try {
        context.startActivity(Intent(choisie).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Réglage indisponible sur ce téléphone", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PanneauRapide(accent: Color, onFermer: () -> Unit) {
    val context = LocalContext.current
    var lampe by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Panneau rapide") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    if (reglerLampe(context, !lampe)) lampe = !lampe
                    else Toast.makeText(context, "Pas de lampe sur cet appareil", Toast.LENGTH_SHORT).show()
                }) { Text(if (lampe) "Lampe : allumée" else "Lampe : éteinte", color = accent) }
                TextButton(onClick = { ouvrir(context, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS) }) {
                    Text("Wi-Fi", color = Color.White)
                }
                TextButton(onClick = { ouvrir(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS) }) {
                    Text("Internet et données mobiles", color = Color.White)
                }
                TextButton(onClick = { ouvrir(context, Settings.ACTION_BLUETOOTH_SETTINGS) }) {
                    Text("Bluetooth", color = Color.White)
                }
                TextButton(onClick = { ouvrir(context, Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS) }) {
                    Text("Volume", color = Color.White)
                }
                TextButton(onClick = { ouvrir(context, Settings.ACTION_DISPLAY_SETTINGS) }) {
                    Text("Luminosité et affichage", color = Color.White)
                }
                TextButton(onClick = { ouvrir(context, Settings.ACTION_SETTINGS) }) {
                    Text("Tous les paramètres", color = Color.White)
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } },
    )
}
