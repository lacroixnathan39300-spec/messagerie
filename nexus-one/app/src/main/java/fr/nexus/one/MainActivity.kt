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
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import android.os.BatteryManager
import android.content.IntentFilter
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

/** Élément de la grille : une appli libre, ou un dossier. */
sealed class Ligne {
    data class Appli(val app: AppEntry) : Ligne()
    data class DossierLigne(val dossier: Dossier) : Ligne()
}

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
    val parPaquet = remember(apps) { apps.associateBy { it.packageName } }

    var dock by remember { mutableStateOf(chargerDock(context)) }
    var dossiers by remember { mutableStateOf(chargerDossiers(context)) }
    var reglages by remember { mutableStateOf(chargerReglages(context)) }
    var ouvertReglages by remember { mutableStateOf(false) }
    var ouvertPanneau by remember { mutableStateOf(false) }
    var ouvertGabriel by remember { mutableStateOf(false) }
    var ouvertRoutines by remember { mutableStateOf(false) }
    // Routines programmées : vérifiées toutes les 30 secondes tant que le launcher tourne.
    LaunchedEffect(reglages.villeMeteo) {
        while (true) {
            verifierRoutinesProgrammees(context, apps, reglages.villeMeteo)
            delay(30_000L)
        }
    }
    var ouvertNotifs by remember { mutableStateOf(false) }
    val notifs by NotifService.notifications.collectAsState()
    val parAppli = notifs.groupingBy { it.paquet }.eachCount()
    var appChoisie by remember { mutableStateOf<AppEntry?>(null) }
    var recherche by remember { mutableStateOf("") }
    var dossierOuvert by remember { mutableStateOf<Dossier?>(null) }
    val focusRecherche = remember { FocusRequester() }
    val clavier = LocalSoftwareKeyboardController.current

    val accent = Color(reglages.accent)
    val paquetsDansDossiers = dossiers.flatMap { it.paquets }.toSet()
    val requete = recherche.trim()
    val lignes: List<Ligne> =
        if (requete.isEmpty()) {
            apps.filter { it.packageName !in paquetsDansDossiers }
                .let { if (reglages.ordreAZ) it.sortedBy { a -> a.label.lowercase() } else it.sortedByDescending { a -> a.label.lowercase() } }
                .map { Ligne.Appli(it) } +
                dossiers.map { Ligne.DossierLigne(it) }
        } else {
            apps.filter { it.label.contains(requete, ignoreCase = true) }.map { Ligne.Appli(it) }
        }
    val appsDuDock = dock.mapNotNull { parPaquet[it] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(reglages.fond))
            .padding(top = 48.dp, bottom = 12.dp)
            // Glisser vers le bas ouvre les réglages, vers le haut la recherche.
            .pointerInput(Unit) {
                var cumul = 0f
                detectVerticalDragGestures(
                    onDragStart = { cumul = 0f },
                    onVerticalDrag = { _, montant -> cumul += montant },
                    onDragEnd = {
                        if (cumul > 150f) {
                            ouvertReglages = true
                        } else if (cumul < -150f) {
                            // Glisser vers le haut : place le curseur dans la recherche.
                            focusRecherche.requestFocus()
                            clavier?.show()
                        }
                    },
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(text = "NEXUS ONE", color = accent, style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    if (accesNotificationsAccorde(context)) ouvertNotifs = true
                    else ouvrirReglageAccesNotifications(context)
                }) {
                    Text(text = "Notifs (${notifs.size})", color = accent)
                }
                TextButton(onClick = { ouvertGabriel = true }) {
                    Text(text = "Gabriel", color = accent)
                }
                TextButton(onClick = { ouvertRoutines = true }) {
                    Text(text = "Routines", color = accent)
                }
                TextButton(onClick = { ouvertPanneau = true }) {
                    Text(text = "Panneau", color = accent)
                }
                TextButton(onClick = { ouvertReglages = true }) {
                    Text(text = "Réglages", color = accent)
                }
            }
        }

        if (reglages.afficherHeure) HorlogeWidget(accent)
        if (reglages.villeMeteo.isNotBlank()) MeteoWidget(reglages.villeMeteo, Color.White)
        if (reglages.afficherBatterie) BatterieWidget(Color.White)

        OutlinedTextField(
            value = recherche,
            onValueChange = { recherche = it },
            singleLine = true,
            label = { Text("Rechercher une appli", color = Color.White) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRecherche)
                .padding(horizontal = 16.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(reglages.colonnes),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            items(lignes, key = { if (it is Ligne.Appli) "app:${it.app.packageName}" else "dos:${(it as Ligne.DossierLigne).dossier.nom}" }) { ligne ->
                when (ligne) {
                    is Ligne.Appli -> AppTile(
                        label = ligne.app.label,
                        icone = ligne.app.icon,
                        taille = reglages.tailleIcone,
                        afficherNom = reglages.afficherNoms,
                        onClick = { launchApp(context, ligne.app.packageName) },
                        onLongClick = { appChoisie = ligne.app },
                        pastille = parAppli[ligne.app.packageName] ?: 0,
                        couleurPastille = accent,
                    )
                    is Ligne.DossierLigne -> DossierTile(
                        dossier = ligne.dossier,
                        taille = reglages.tailleIcone,
                        onClick = { dossierOuvert = ligne.dossier },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .border(1.dp, accent, RoundedCornerShape(16.dp))
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            appsDuDock.forEach { app ->
                AppTile(
                    label = app.label,
                    icone = app.icon,
                    taille = reglages.tailleIcone,
                    afficherNom = reglages.afficherNoms,
                    pastille = parAppli[app.packageName] ?: 0,
                    couleurPastille = accent,
                    onClick = { launchApp(context, app.packageName) },
                    onLongClick = {
                        dock = dock - app.packageName
                        sauverDock(context, dock)
                    },
                )
            }
        }
    }

    appChoisie?.let { app ->
        ActionsAppDialog(
            app = app,
            dansDock = app.packageName in dock,
            dossiers = dossiers,
            onDock = {
                if (app.packageName in dock) {
                    dock = dock - app.packageName
                } else if (dock.size < DOCK_MAX) {
                    dock = dock + app.packageName
                } else {
                    Toast.makeText(context, "Dock plein ($DOCK_MAX applis)", Toast.LENGTH_SHORT).show()
                }
                sauverDock(context, dock)
                appChoisie = null
            },
            onAjouterAuDossier = { nomDossier ->
                val nettoye = nomDossier.trim().replace("|", " ").replace(";", " ")
                if (nettoye.isNotEmpty()) {
                    dossiers = dossiers.map { d ->
                        if (d.nom == nettoye) d.copy(paquets = d.paquets.filter { it != app.packageName } + app.packageName)
                        else d.copy(paquets = d.paquets.filter { it != app.packageName })
                    }.let { liste ->
                        if (liste.none { it.nom == nettoye }) liste + Dossier(nettoye, listOf(app.packageName)) else liste
                    }
                    dossiers = dossiers.filter { it.paquets.isNotEmpty() }
                    sauverDossiers(context, dossiers)
                }
                appChoisie = null
            },
            onFermer = { appChoisie = null },
        )
    }

    dossierOuvert?.let { dossier ->
        val actuel = dossiers.firstOrNull { it.nom == dossier.nom } ?: dossier
        AlertDialog(
            onDismissRequest = { dossierOuvert = null },
            title = { Text(actuel.nom) },
            text = {
                Column {
                    actuel.paquets.mapNotNull { parPaquet[it] }.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    launchApp(context, app.packageName)
                                    dossierOuvert = null
                                },
                            ) { Text(app.label, color = Color.White) }
                            // Retire l'appli du dossier : elle revient sur l'accueil.
                            TextButton(onClick = {
                                dossiers = dossiers.map { d ->
                                    if (d.nom == actuel.nom) d.copy(paquets = d.paquets - app.packageName) else d
                                }.filter { it.paquets.isNotEmpty() }
                                sauverDossiers(context, dossiers)
                                if (dossiers.none { it.nom == actuel.nom }) dossierOuvert = null
                            }) { Text("Retirer", color = accent) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Retire le dossier, les applis retournent sur l'accueil.
                    dossiers = dossiers.filter { it.nom != actuel.nom }
                    sauverDossiers(context, dossiers)
                    dossierOuvert = null
                }) { Text("Supprimer le dossier", color = accent) }
            },
            dismissButton = {
                TextButton(onClick = { dossierOuvert = null }) { Text("Fermer") }
            },
        )
    }

    if (ouvertNotifs) {
        AlertDialog(
            onDismissRequest = { ouvertNotifs = false },
            title = { Text("Notifications") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (notifs.isEmpty()) Text("Aucune notification", color = Color.White)
                    notifs.forEach { n ->
                        val nomAppli = parPaquet[n.paquet]?.label ?: n.paquet
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    launchApp(context, n.paquet)
                                    ouvertNotifs = false
                                },
                            ) {
                                Text("$nomAppli : ${n.titre} ${n.texte}".trim(), color = Color.White, maxLines = 2)
                            }
                            TextButton(onClick = { NotifService.effacer(n.cle) }) { Text("X", color = accent) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { NotifService.toutEffacer() }) { Text("Tout effacer", color = accent) }
            },
            dismissButton = { TextButton(onClick = { ouvertNotifs = false }) { Text("Fermer") } },
        )
    }

    if (ouvertGabriel) {
        GabrielDialog(apps = apps, villeMeteo = reglages.villeMeteo, accent = accent, onFermer = { ouvertGabriel = false })
    }

    if (ouvertRoutines) {
        RoutinesDialog(apps = apps, villeMeteo = reglages.villeMeteo, accent = accent, onFermer = { ouvertRoutines = false })
    }

    if (ouvertPanneau) {
        PanneauRapide(accent = accent, onFermer = { ouvertPanneau = false })
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
fun ActionsAppDialog(
    app: AppEntry,
    dansDock: Boolean,
    dossiers: List<Dossier>,
    onDock: () -> Unit,
    onAjouterAuDossier: (String) -> Unit,
    onFermer: () -> Unit,
) {
    var nouveauNom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(app.label) },
        text = {
            Column {
                TextButton(onClick = onDock) {
                    Text(if (dansDock) "Retirer du dock" else "Ajouter au dock", color = Color.White)
                }
                dossiers.forEach { d ->
                    TextButton(onClick = { onAjouterAuDossier(d.nom) }) {
                        Text("Ajouter au dossier « ${d.nom} »", color = Color.White)
                    }
                }
                OutlinedTextField(
                    value = nouveauNom,
                    onValueChange = { nouveauNom = it },
                    label = { Text("Nouveau dossier") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAjouterAuDossier(nouveauNom) },
                enabled = nouveauNom.isNotBlank(),
            ) { Text("Créer") }
        },
        dismissButton = {
            TextButton(onClick = onFermer) { Text("Fermer") }
        },
    )
}

/** Widget horloge : heure et date, mises à jour toutes les 30 secondes. */
@Composable
fun HorlogeWidget(couleur: Color) {
    val affichage by produceState(initialValue = formaterHeure()) {
        while (true) {
            value = formaterHeure()
            delay(30_000L)
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(text = affichage.first, color = couleur, style = MaterialTheme.typography.headlineMedium)
        Text(text = affichage.second, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formaterHeure(): Pair<String, String> {
    val maintenant = java.util.Date()
    val heure = SimpleDateFormat("HH:mm", Locale.FRANCE).format(maintenant)
    val date = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(maintenant)
    return heure to date
}

/** Widget météo : ville choisie dans les réglages, mise à jour toutes les 30 minutes. */
@Composable
fun MeteoWidget(ville: String, couleur: Color) {
    val meteo by produceState<MeteoActuelle?>(initialValue = null, ville) {
        while (true) {
            value = withContext(Dispatchers.IO) { chargerMeteo(ville) }
            delay(30 * 60_000L)
        }
    }
    val texte = meteo?.let { "${it.ville} : ${Math.round(it.temperature)} °C, ${it.description}" }
        ?: "Météo de $ville : indisponible"
    Text(
        text = texte,
        color = couleur,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

/** Widget batterie : niveau et charge, mis à jour chaque minute. */
@Composable
fun BatterieWidget(couleur: Color) {
    val context = LocalContext.current
    val etat by produceState(initialValue = lireBatterie(context)) {
        while (true) {
            value = lireBatterie(context)
            delay(60_000L)
        }
    }
    Text(
        text = "Batterie : ${etat.first} %" + if (etat.second) " (en charge)" else "",
        color = couleur,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

fun lireBatterie(context: Context): Pair<Int, Boolean> {
    val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val niveau = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val echelle = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val statut = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val enCharge = statut == BatteryManager.BATTERY_STATUS_CHARGING || statut == BatteryManager.BATTERY_STATUS_FULL
    val pourcent = if (niveau >= 0 && echelle > 0) niveau * 100 / echelle else 0
    return pourcent to enCharge
}

@Composable
fun ReglagesDialog(
    actuels: Reglages,
    onEnregistrer: (Reglages) -> Unit,
    onFermer: () -> Unit,
) {
    var colonnes by remember { mutableStateOf(actuels.colonnes.toFloat()) }
    var taille by remember { mutableStateOf(actuels.tailleIcone.toFloat()) }
    var accent by remember { mutableStateOf(actuels.accent) }
    var afficherNoms by remember { mutableStateOf(actuels.afficherNoms) }
    var fond by remember { mutableStateOf(actuels.fond) }
    var ordreAZ by remember { mutableStateOf(actuels.ordreAZ) }
    var afficherHeure by remember { mutableStateOf(actuels.afficherHeure) }
    var villeMeteo by remember { mutableStateOf(actuels.villeMeteo) }
    var afficherBatterie by remember { mutableStateOf(actuels.afficherBatterie) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Réglages de l'accueil") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Colonnes : ${colonnes.toInt()}", color = Color.White)
                Slider(value = colonnes, onValueChange = { colonnes = it }, valueRange = 3f..6f, steps = 2)
                Text("Taille des icônes : ${taille.toInt()} dp", color = Color.White)
                Slider(value = taille, onValueChange = { taille = it }, valueRange = 40f..80f)
                TextButton(onClick = { afficherNoms = !afficherNoms }) {
                    Text(if (afficherNoms) "Noms des applis : affichés" else "Noms des applis : masqués", color = Color.White)
                }
                OutlinedTextField(
                    value = villeMeteo,
                    onValueChange = { villeMeteo = it },
                    singleLine = true,
                    label = { Text("Ville météo (vide = masqué)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { afficherBatterie = !afficherBatterie }) {
                    Text(if (afficherBatterie) "Widget batterie : affiché" else "Widget batterie : masqué", color = Color.White)
                }
                TextButton(onClick = { afficherHeure = !afficherHeure }) {
                    Text(if (afficherHeure) "Widget heure : affiché" else "Widget heure : masqué", color = Color.White)
                }
                TextButton(onClick = { ordreAZ = !ordreAZ }) {
                    Text(if (ordreAZ) "Ordre des applis : A à Z" else "Ordre des applis : Z à A", color = Color.White)
                }
                Text("Thème", color = Color.White)
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    THEMES.forEach { (nom, theFond, theAccent) ->
                        TextButton(onClick = { fond = theFond; accent = theAccent }) {
                            Text(nom, color = Color.White)
                        }
                    }
                }
                Text("Couleur de fond", color = Color.White)
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    COULEURS_FOND.forEach { (nom, couleur) ->
                        TextButton(onClick = { fond = couleur }) {
                            Text(if (fond == couleur) "● $nom" else nom, color = Color.White)
                        }
                    }
                }
                Text("Couleur d'accent", color = Color.White)
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    COULEURS_ACCENT.forEach { (nom, couleur) ->
                        TextButton(onClick = { accent = couleur }) {
                            Text(
                                if (accent == couleur) "● $nom" else nom,
                                color = Color(couleur),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onEnregistrer(Reglages(colonnes.toInt(), taille.toInt(), accent, afficherNoms, fond, ordreAZ, afficherHeure, villeMeteo.trim(), afficherBatterie))
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onFermer) { Text("Annuler") }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    label: String,
    icone: Drawable,
    taille: Int,
    afficherNom: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    pastille: Int = 0,
    couleurPastille: Color = Color.Red,
) {
    Column(
        modifier = Modifier
            .padding(6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = icone.toBitmap(160, 160).asImageBitmap(),
            contentDescription = label,
            modifier = Modifier.size(taille.dp),
        )
        if (pastille > 0) {
            Text(
                text = if (pastille > 9) "9+" else pastille.toString(),
                color = couleurPastille,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (afficherNom) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DossierTile(dossier: Dossier, taille: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .size(taille.dp)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "${dossier.paquets.size}", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = dossier.nom,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
