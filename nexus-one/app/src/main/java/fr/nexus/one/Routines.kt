package fr.nexus.one

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Routine : suite de commandes Gabriel. heure = minutes depuis minuit, ou -1 si manuelle. */
data class Routine(val nom: String, val commandes: List<String>, val heure: Int = -1)

private const val PREFS_ROUTINES = "nexus_routines"

fun chargerRoutines(context: Context): List<Routine> = try {
    val texte = context.getSharedPreferences(PREFS_ROUTINES, Context.MODE_PRIVATE).getString("liste", "[]") ?: "[]"
    val tableau = JSONArray(texte)
    (0 until tableau.length()).map { i ->
        val o = tableau.getJSONObject(i)
        val cmds = o.getJSONArray("commandes")
        Routine(o.getString("nom"), (0 until cmds.length()).map { cmds.getString(it) }, o.optInt("heure", -1))
    }
} catch (e: Exception) {
    emptyList()
}

fun sauverRoutines(context: Context, routines: List<Routine>) {
    val tableau = JSONArray()
    routines.forEach { r ->
        tableau.put(JSONObject().put("nom", r.nom).put("commandes", JSONArray(r.commandes)).put("heure", r.heure))
    }
    context.getSharedPreferences(PREFS_ROUTINES, Context.MODE_PRIVATE).edit().putString("liste", tableau.toString()).apply()
}

/** Exécute les commandes une à une et renvoie les réponses. */
suspend fun executerRoutine(context: Context, routine: Routine, apps: List<AppEntry>, villeMeteo: String): List<String> =
    routine.commandes.filter { it.isNotBlank() }.map { interpreterGabriel(context, it, apps, villeMeteo) }

private fun aujourdhui(): String = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())

/** Lance les routines programmées dont l'heure est passée et qui n'ont pas encore tourné aujourd'hui. */
suspend fun verifierRoutinesProgrammees(context: Context, apps: List<AppEntry>, villeMeteo: String) {
    val cal = Calendar.getInstance()
    val maintenant = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val prefs = context.getSharedPreferences(PREFS_ROUTINES, Context.MODE_PRIVATE)
    chargerRoutines(context).filter { it.heure in 0..maintenant }.forEach { r ->
        val cle = "derniere_${r.nom}"
        if (prefs.getString(cle, "") != aujourdhui()) {
            prefs.edit().putString(cle, aujourdhui()).apply()
            executerRoutine(context, r, apps, villeMeteo)
        }
    }
}

/** Lit "HH:MM" ou "HHhMM". Renvoie -1 si vide ou invalide. */
fun lireHeure(texte: String): Int {
    val m = Regex("^\\s*(\\d{1,2})\\s*[:hH]\\s*(\\d{0,2})\\s*$").find(texte) ?: return -1
    val h = m.groupValues[1].toInt()
    val mn = m.groupValues[2].toIntOrNull() ?: 0
    return if (h in 0..23 && mn in 0..59) h * 60 + mn else -1
}

@Composable
fun RoutinesDialog(apps: List<AppEntry>, villeMeteo: String, accent: Color, onFermer: () -> Unit) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    var routines by remember { mutableStateOf(chargerRoutines(context)) }
    var nom by remember { mutableStateOf("") }
    var commandes by remember { mutableStateOf("") }
    var heure by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Une commande Gabriel par ligne. Exemple : lampe, puis ouvre musique.") }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Routines", color = accent) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                routines.forEach { r ->
                    val quand = if (r.heure >= 0) " (chaque jour à ${r.heure / 60} h ${"%02d".format(r.heure % 60)})" else ""
                    Text(r.nom + quand, color = Color.White)
                    Row {
                        TextButton(onClick = {
                            portee.launch {
                                message = executerRoutine(context, r, apps, villeMeteo).joinToString(" ")
                            }
                        }) { Text("Lancer", color = accent) }
                        TextButton(onClick = {
                            routines = routines.filter { it.nom != r.nom }
                            sauverRoutines(context, routines)
                        }) { Text("Supprimer", color = accent) }
                    }
                }
                Text(message, color = Color.White)
                OutlinedTextField(value = nom, onValueChange = { nom = it }, singleLine = true,
                    label = { Text("Nom de la routine") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = commandes, onValueChange = { commandes = it },
                    label = { Text("Commandes (une par ligne)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = heure, onValueChange = { heure = it }, singleLine = true,
                    label = { Text("Heure auto, ex. 07:30 (vide = manuelle)") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    val n = nom.trim()
                    val h = lireHeure(heure)
                    when {
                        n.isEmpty() -> message = "Donne un nom à la routine."
                        commandes.isBlank() -> message = "Ajoute au moins une commande."
                        heure.isNotBlank() && h < 0 -> message = "Heure invalide. Écris par exemple 07:30."
                        else -> {
                            routines = routines.filter { it.nom != n } + Routine(n, commandes.lines().map { it.trim() }.filter { it.isNotEmpty() }, h)
                            sauverRoutines(context, routines)
                            nom = ""; commandes = ""; heure = ""
                            message = "Routine « $n » enregistrée."
                        }
                    }
                }) { Text("Enregistrer la routine", color = accent) }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } },
    )
}
