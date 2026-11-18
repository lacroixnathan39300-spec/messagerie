package fr.nexus.one

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun demarrer(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (e: Exception) {
    false
}

private fun nombre(texte: String): Int? = Regex("\\d+").find(texte)?.value?.toIntOrNull()

/**
 * Gabriel : assistant local à commandes. Pas d'intelligence artificielle, pas de serveur.
 * Renvoie la réponse à afficher et à prononcer.
 */
suspend fun interpreterGabriel(
    context: Context,
    phraseBrute: String,
    apps: List<AppEntry>,
    villeMeteo: String,
): String {
    val phrase = phraseBrute.trim().lowercase(Locale.FRANCE)
    if (phrase.isEmpty()) return "Je n'ai rien entendu."

    fun commence(vararg mots: String) = mots.any { phrase.startsWith(it) }
    fun contient(vararg mots: String) = mots.any { phrase.contains(it) }

    return when {
        commence("ouvre ", "lance ", "démarre ") -> {
            val nom = phrase.substringAfter(" ").trim()
            val appli = apps.firstOrNull { it.label.lowercase(Locale.FRANCE) == nom }
                ?: apps.firstOrNull { it.label.lowercase(Locale.FRANCE).contains(nom) }
            if (appli != null) {
                launchApp(context, appli.packageName)
                "J'ouvre ${appli.label}."
            } else {
                "Je ne trouve pas d'appli qui s'appelle $nom."
            }
        }
        contient("lampe", "torche") -> {
            val allumer = !contient("éteins", "eteins", "éteindre", "eteindre", "coupe")
            if (reglerLampe(context, allumer)) (if (allumer) "Lampe allumée." else "Lampe éteinte.")
            else "Ce téléphone n'a pas de lampe utilisable."
        }
        contient("heure") -> "Il est " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date()) + "."
        contient("date", "quel jour", "on est quel") ->
            "Nous sommes le " + SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date()) + "."
        contient("batterie") -> {
            val (niveau, charge) = lireBatterie(context)
            "Batterie à $niveau pour cent" + if (charge) ", en charge." else "."
        }
        contient("météo", "meteo", "temps qu'il fait") -> {
            if (villeMeteo.isBlank()) "Choisis d'abord une ville météo dans les réglages."
            else {
                val m = withContext(Dispatchers.IO) { chargerMeteo(villeMeteo) }
                if (m == null) "Météo indisponible pour le moment."
                else "À ${m.ville}, ${Math.round(m.temperature)} degrés, ${m.description.lowercase(Locale.FRANCE)}."
            }
        }
        contient("minuteur") -> {
            val minutes = nombre(phrase) ?: return "Combien de minutes ? Dis par exemple : minuteur 10 minutes."
            val ok = demarrer(context, Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Gabriel")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            if (ok) "Minuteur de $minutes minutes lancé." else "Aucune appli horloge ne gère les minuteurs."
        }
        contient("réveil", "reveil", "alarme") -> {
            val heures = Regex("(\\d{1,2})\\s*(h|heure|heures|:)\\s*(\\d{0,2})").find(phrase)
                ?: return "À quelle heure ? Dis par exemple : réveil 7 h 30."
            val h = heures.groupValues[1].toInt().coerceIn(0, 23)
            val m = heures.groupValues[3].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val ok = demarrer(context, Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Gabriel"))
            if (ok) "Réveil réglé à $h h ${"%02d".format(m)}." else "Aucune appli horloge ne gère les réveils."
        }
        commence("appelle ", "appeler ") -> {
            val numero = phrase.filter { it.isDigit() || it == '+' }
            if (numero.length < 3) "Donne-moi le numéro, par exemple : appelle 0612345678."
            else if (demarrer(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero")))) "Je prépare l'appel au $numero."
            else "Pas d'appli téléphone."
        }
        commence("cherche ", "recherche ") -> {
            val sujet = phrase.substringAfter(" ").trim()
            val ok = demarrer(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(sujet))))
            if (ok) "Je cherche $sujet." else "Pas de navigateur."
        }
        contient("wifi", "wi-fi") -> { demarrer(context, Intent(Settings.ACTION_WIFI_SETTINGS)); "J'ouvre le Wi-Fi." }
        contient("bluetooth") -> { demarrer(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "J'ouvre le Bluetooth." }
        contient("paramètres", "parametres", "réglages système") -> { demarrer(context, Intent(Settings.ACTION_SETTINGS)); "J'ouvre les paramètres." }
        contient("aide", "que sais-tu", "tu sais faire") -> AIDE_GABRIEL
        else -> "Je n'ai pas compris. Dis « aide » pour la liste des commandes."
    }
}

const val AIDE_GABRIEL =
    "Je sais : ouvrir une appli, allumer ou éteindre la lampe, donner l'heure, la date, la batterie, la météo, " +
        "lancer un minuteur, régler un réveil, préparer un appel, chercher sur internet, ouvrir le Wi-Fi, le Bluetooth ou les paramètres."

@Composable
fun GabrielDialog(apps: List<AppEntry>, villeMeteo: String, accent: Color, onFermer: () -> Unit) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    var demande by remember { mutableStateOf("") }
    var reponse by remember { mutableStateOf("Bonjour, je suis Gabriel. Écris ou parle.") }
    var voixPrete by remember { mutableStateOf(false) }

    val voix = remember {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { statut ->
            if (statut == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRANCE
                voixPrete = true
            }
        }
        tts
    }
    DisposableEffect(Unit) { onDispose { voix.shutdown() } }

    fun traiter(texte: String) {
        demande = texte
        portee.launch {
            val r = interpreterGabriel(context, texte, apps, villeMeteo)
            reponse = r
            if (voixPrete) voix.speak(r, TextToSpeech.QUEUE_FLUSH, null, "gabriel")
        }
    }

    val ecoute = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultat ->
        if (resultat.resultCode == Activity.RESULT_OK) {
            val texte = resultat.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (texte != null) traiter(texte)
        }
    }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Gabriel", color = accent) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(reponse, color = Color.White)
                OutlinedTextField(
                    value = demande,
                    onValueChange = { demande = it },
                    singleLine = true,
                    label = { Text("Ta demande") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = { traiter(demande) }) { Text("Envoyer", color = accent) }
                    TextButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à Gabriel")
                        try { ecoute.launch(intent) } catch (e: Exception) { reponse = "La reconnaissance vocale n'est pas disponible sur ce téléphone." }
                    }) { Text("Parler", color = accent) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } },
    )
}
