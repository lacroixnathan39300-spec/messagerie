package fr.nexus.one

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/** Une application lançable, telle que listée par le système. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable,
)

private const val PREFS = "nexus"
private const val CLE_DOCK = "dock"
private const val DOCK_MAX = 4

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
                LauncherScreen()
            }
        }
    }
}

/** Liste les applis qui ont une icône dans le lanceur, triées par nom. */
fun loadLaunchableApps(pm: PackageManager): List<AppEntry> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { info ->
            AppEntry(
                label = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Ouvre une appli par son nom de paquet, si elle a une activité de lancement. */
fun launchApp(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** Dock : liste ordonnée des paquets, sauvegardée localement. */
fun chargerDock(context: Context): List<String> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(CLE_DOCK, "").orEmpty()
        .split(",")
        .filter { it.isNotBlank() }

fun sauverDock(context: Context, paquets: List<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(CLE_DOCK, paquets.joinToString(","))
        .apply()
}

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val apps = remember { loadLaunchableApps(context.packageManager) }
    var dock by remember { mutableStateOf(chargerDock(context)) }
    var reglages by remember { mutableStateOf(chargerReglages(context)) }
    var ouvertReglages by remember { mutableStateOf(false) }
    val parPaquet = remember(apps) { apps.associateBy { it.packageName } }
    val appsDuDock = dock.mapNotNull { parPaquet[it] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = 48.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "NEXUS ONE", color = Color.White, style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { ouvertReglages = true }) {
                Text(text = "Réglages", color = Color.White)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(reglages.colonnes),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(
                    app = app,
                    taille = reglages.tailleIcone,
                    onClick = { launchApp(context, app.packageName) },
                    onLongClick = {
                        if (app.packageName in dock) {
                            dock = dock - app.packageName
                        } else if (dock.size < DOCK_MAX) {
                            dock = dock + app.packageName
                        } else {
                            Toast.makeText(context, "Dock plein ($DOCK_MAX applis)", Toast.LENGTH_SHORT).show()
                        }
                        sauverDock(context, dock)
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            appsDuDock.forEach { app ->
                AppTile(
                    app = app,
                    taille = reglages.tailleIcone,
                    onClick = { launchApp(context, app.packageName) },
                    onLongClick = {
                        dock = dock - app.packageName
                        sauverDock(context, dock)
                    },
                )
            }
        }
    }

    if (ouvertReglages) {
        ReglagesDialog(
            actuels = reglages,
            onEnregistrer = { nouveaux ->
                reglages = nouveaux
                sauverReglages(context, nouveaux)
                ouvertReglages = false
            },
            onFermer = { ouvertReglages = false },
        )
    }
}

@Composable
fun ReglagesDialog(
    actuels: Reglages,
    onEnregistrer: (Reglages) -> Unit,
    onFermer: () -> Unit,
) {
    var colonnes by remember { mutableStateOf(actuels.colonnes.toFloat()) }
    var taille by remember { mutableStateOf(actuels.tailleIcone.toFloat()) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Réglages de l'accueil") },
        text = {
            Column {
                Text("Colonnes : ${colonnes.toInt()}", color = Color.White)
                Slider(value = colonnes, onValueChange = { colonnes = it }, valueRange = 3f..6f, steps = 2)
                Text("Taille des icônes : ${taille.toInt()} dp", color = Color.White)
                Slider(value = taille, onValueChange = { taille = it }, valueRange = 40f..80f)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onEnregistrer(Reglages(colonnes.toInt(), taille.toInt()))
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onFermer) { Text("Annuler") }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(app: AppEntry, taille: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = app.icon.toBitmap(160, 160).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(taille.dp),
        )
        Text(
            text = app.label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
